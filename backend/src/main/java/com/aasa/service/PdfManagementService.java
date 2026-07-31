package com.aasa.service;

import com.aasa.dto.PdfDocumentDto;
import com.aasa.entity.PdfDocument;
import com.aasa.entity.Topic;
import com.aasa.entity.User;
import com.aasa.repository.*;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Service
public class PdfManagementService {

    private static final Logger logger = Logger.getLogger(PdfManagementService.class.getName());

    @Autowired
    private PdfDocumentRepository pdfDocumentRepository;

    @Autowired
    private PdfExtractionService pdfExtractionService;

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private QuizRepository quizRepository;

    @Autowired
    private QuizAttemptRepository quizAttemptRepository;

    @Autowired
    private StudyProgressRepository studyProgressRepository;

    @Autowired
    private DocumentChunkRepository documentChunkRepository;

    @Autowired
    private ReviewLogRepository reviewLogRepository;

    private final TransactionTemplate transactionTemplate;

    public PdfManagementService(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Value("${file.upload.dir}")
    private String uploadDir;

    public PdfDocumentDto uploadPdf(MultipartFile file, User user, LocalDate examDate) {
        java.nio.file.Path newFilePath = null;
        try {
            String fileName = file.getOriginalFilename();
            logger.info("Uploading PDF: " + fileName + " for user: " + user.getId());

            String uniqueFileName = UUID.randomUUID() + "_" + fileName;
            String filePath = uploadDir + "/" + uniqueFileName;
            newFilePath = Paths.get(filePath);

            Files.createDirectories(Paths.get(uploadDir));
            Files.write(newFilePath, file.getBytes());

            String extractedText = pdfExtractionService.extractTextFromPdf(file);

            PdfDocument pdfDocument = PdfDocument.builder()
                    .user(user)
                    .fileName(fileName)
                    .filePath(filePath)
                    .examDate(examDate)
                    .extractedText(extractedText)
                    .isAnalyzed(false)
                    .processingStatus(PdfDocument.ProcessingStatus.PENDING)
                    .build();

            // Commit the database replacement before optional RAG processing.
            // A caught RAG persistence error must not mark the upload rollback-only.
            List<String> obsoleteFilePaths = new ArrayList<>();
            PdfDocument saved = transactionTemplate.execute(status -> {
                obsoleteFilePaths.addAll(deleteAllUserData(user));
                logger.info("Existing data deleted for user: " + user.getId());
                return pdfDocumentRepository.saveAndFlush(pdfDocument);
            });

            if (saved == null) {
                throw new IllegalStateException("PDF database transaction returned no saved document");
            }
            deleteFilesBestEffort(obsoleteFilePaths);
            logger.info("PDF saved with ID: " + saved.getId());

            return PdfDocumentDto.builder()
                    .id(saved.getId())
                    .fileName(saved.getFileName())
                    .uploadDate(saved.getUploadDate())
                    .examDate(saved.getExamDate())
                    .isAnalyzed(false)
                    .topicCount(0)
                    .processingStatus(saved.getProcessingStatus().name())
                    .processingError(null)
                    .build();
        } catch (IOException e) {
            deleteIncompleteUpload(newFilePath);
            logger.severe("IO Error during PDF upload: " + e.getMessage());
            throw new RuntimeException("Failed to process PDF file: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            deleteIncompleteUpload(newFilePath);
            throw e;
        }
    }

    public void resetAllUserData(User user) {
        List<String> deletedFilePaths =
                transactionTemplate.execute(status -> deleteAllUserData(user));
        if (deletedFilePaths != null) {
            deleteFilesBestEffort(deletedFilePaths);
        }
    }

    private List<String> deleteAllUserData(User user) {
        Long userId = user.getId();
        logger.info("Deleting all data for user ID: " + userId);
        List<String> deletedFilePaths = new ArrayList<>();

        reviewLogRepository.deleteByUserId(userId);
        logger.info("Deleted review logs for user: " + userId);

        // Delete in correct FK order: quiz_attempts → study_progress → quizzes → topics → pdf_documents
        quizAttemptRepository.deleteByUserId(userId);
        logger.info("Deleted quiz attempts for user: " + userId);

        studyProgressRepository.deleteByUserId(userId);
        logger.info("Deleted study progress for user: " + userId);

        // Get all topics for user, delete quizzes for each topic first
        List<Topic> topics = topicRepository.findByUserIdOrderByPriority(userId);
        for (Topic topic : topics) {
            quizRepository.deleteByTopicId(topic.getId());
        }
        logger.info("Deleted quizzes for all topics");

        // Delete RAG chunks
        List<PdfDocument> pdfs = pdfDocumentRepository.findByUserId(userId);
        for (PdfDocument pdf : pdfs) {
            documentChunkRepository.deleteByPdfDocumentId(pdf.getId());
        }

        // Flush dependent deletes before deleting their parent PDFs.
        pdfDocumentRepository.flush();

        // Now delete PDFs (cascade will handle topics)
        for (PdfDocument pdf : pdfs) {
            deletedFilePaths.add(pdf.getFilePath());
            pdfDocumentRepository.delete(pdf);
        }
        pdfDocumentRepository.flush();
        logger.info("Deleted all PDFs for user: " + userId);
        return deletedFilePaths;
    }

    private void deleteIncompleteUpload(java.nio.file.Path filePath) {
        if (filePath == null) {
            return;
        }
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException cleanupError) {
            logger.warning("Failed to remove incomplete upload file: " + filePath);
        }
    }

    private void deleteFilesBestEffort(List<String> filePaths) {
        for (String filePath : filePaths) {
            try {
                Files.deleteIfExists(Paths.get(filePath));
            } catch (IOException | RuntimeException e) {
                logger.warning(
                        "Database cleanup committed, but file could not be removed: "
                                + filePath + " (" + e.getMessage() + ")"
                );
            }
        }
    }

    @Transactional(readOnly = true)
    public List<PdfDocumentDto> getUserPdfs(Long userId) {
        return pdfDocumentRepository.findByUserIdOrderByUploadDateDesc(userId)
                .stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    public PdfDocument getPdfById(Long pdfId) {
        return pdfDocumentRepository.findById(pdfId)
                .orElseThrow(() -> new RuntimeException("PDF not found"));
    }

    @Transactional(readOnly = true)
    public PdfDocumentDto getPdfByIdDto(Long pdfId, Long userId) {
        PdfDocument pdf = getOwnedPdfById(pdfId, userId);
        int topicCount = topicRepository.findByPdfDocumentId(pdfId).size();
        return PdfDocumentDto.builder()
                .id(pdf.getId())
                .fileName(pdf.getFileName())
                .uploadDate(pdf.getUploadDate())
                .examDate(pdf.getExamDate())
                .isAnalyzed(pdf.getIsAnalyzed())
                .topicCount(topicCount)
                .processingStatus(pdf.getProcessingStatus() == null ? null : pdf.getProcessingStatus().name())
                .processingError(pdf.getProcessingError())
                .build();
    }

    @Transactional(readOnly = true)
    public com.aasa.dto.PdfDetailDto getPdfDetail(Long pdfId, Long userId) {
        PdfDocument pdf = getOwnedPdfById(pdfId, userId);

        List<Topic> pdfTopics = topicRepository.findByPdfDocumentId(pdfId);
        int totalTopics = pdfTopics.size();
        long totalQuizzes = quizRepository.countByTopicIdIn(
                pdfTopics.stream().map(Topic::getId).collect(java.util.stream.Collectors.toList())
        );

        Double averageScore = quizAttemptRepository.getAverageScoreByPdf(userId, pdfId);
        if (averageScore == null) averageScore = 0.0;

        Double overallCompletion = studyProgressRepository.getAverageCompletionByPdf(userId, pdfId);
        if (overallCompletion == null) overallCompletion = 0.0;

        int daysUntilExam = pdf.getExamDate() != null
                ? (int) java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.now(), pdf.getExamDate())
                : 0;

        java.util.Map<Long, com.aasa.entity.StudyProgress> progressMap = studyProgressRepository
                .findByUserIdAndPdfId(userId, pdfId)
                .stream()
                .collect(java.util.stream.Collectors.toMap(sp -> sp.getTopic().getId(), sp -> sp));

        List<com.aasa.dto.PdfDetailDto.TopicDetail> topicDetails = pdfTopics.stream()
                .map(t -> {
                    com.aasa.entity.StudyProgress sp = progressMap.get(t.getId());
                    int qCount = t.getQuizzes() != null ? t.getQuizzes().size() : 0;
                    return com.aasa.dto.PdfDetailDto.TopicDetail.builder()
                            .id(t.getId())
                            .title(t.getTitle())
                            .description(t.getDescription())
                            .complexityScore(t.getComplexityScore())
                            .importanceScore(t.getImportanceScore())
                            .priorityScore(t.getPriorityScore())
                            .weaknessScore(t.getWeaknessScore())
                            .quizCount(qCount)
                            .totalAttempts(sp != null ? sp.getTotalAttempts() : 0)
                            .correctAttempts(sp != null ? sp.getCorrectAttempts() : 0)
                            .bestScore(sp != null ? sp.getBestScore() : 0.0)
                            .completionPercentage(sp != null ? sp.getCompletionPercentage() : 0.0)
                            .weaknessLevel(sp != null ? sp.getWeaknessLevel().toString() : "NOT_ATTEMPTED")
                            .build();
                })
                .collect(java.util.stream.Collectors.toList());

        return com.aasa.dto.PdfDetailDto.builder()
                .id(pdf.getId())
                .fileName(pdf.getFileName())
                .uploadDate(pdf.getUploadDate())
                .examDate(pdf.getExamDate())
                .isAnalyzed(pdf.getIsAnalyzed())
                .daysUntilExam(daysUntilExam)
                .totalTopics(totalTopics)
                .totalQuizzes((int) totalQuizzes)
                .averageScore(averageScore)
                .overallCompletionPercentage(overallCompletion)
                .topics(topicDetails)
                .build();
    }

    private PdfDocument getOwnedPdfById(Long pdfId, Long userId) {
        return pdfDocumentRepository.findByIdAndUserId(pdfId, userId)
                .orElseThrow(() -> new EntityNotFoundException("PDF not found"));
    }

    public void deletePdf(Long pdfId, Long userId) {
        String filePath = transactionTemplate.execute(status -> deletePdfData(pdfId, userId));
        if (filePath == null) {
            throw new IllegalStateException("PDF delete transaction returned no file path");
        }

        // The database is the source of truth. A missing or locked physical file
        // must not turn a committed database deletion into a 500 response.
        try {
            Files.deleteIfExists(Paths.get(filePath));
        } catch (IOException | RuntimeException e) {
            logger.warning("PDF " + pdfId
                    + " was deleted from the database, but its file could not be removed: "
                    + e.getMessage());
        }
    }

    private String deletePdfData(Long pdfId, Long userId) {
        PdfDocument pdf = getOwnedPdfById(pdfId, userId);
        String filePath = pdf.getFilePath();

        documentChunkRepository.deleteByPdfDocumentId(pdfId);

        List<Topic> topics = topicRepository.findByPdfDocumentId(pdfId);
        for (Topic topic : topics) {
            Long topicId = topic.getId();

            // Review logs point directly at topics, so they must be removed
            // before the topic rows.
            reviewLogRepository.deleteByTopicId(topicId);
            quizAttemptRepository.deleteByQuizTopicId(topicId);
            studyProgressRepository.deleteByTopicId(topicId);
            quizRepository.deleteByTopicId(topicId);
        }

        topicRepository.deleteByPdfDocumentId(pdfId);
        pdfDocumentRepository.flush();
        pdfDocumentRepository.deleteById(pdfId);
        pdfDocumentRepository.flush();

        return filePath;
    }

    public void markAsAnalyzed(Long pdfId) {
        PdfDocument pdf = getPdfById(pdfId);
        pdf.setIsAnalyzed(true);
        pdfDocumentRepository.save(pdf);
    }

    private PdfDocumentDto convertToDto(PdfDocument pdf) {
        return PdfDocumentDto.builder()
                .id(pdf.getId())
                .fileName(pdf.getFileName())
                .uploadDate(pdf.getUploadDate())
                .examDate(pdf.getExamDate())
                .isAnalyzed(pdf.getIsAnalyzed())
                .topicCount(pdf.getTopics() != null ? pdf.getTopics().size() : 0)
                .processingStatus(pdf.getProcessingStatus() == null ? null : pdf.getProcessingStatus().name())
                .processingError(pdf.getProcessingError())
                .build();
    }
}
