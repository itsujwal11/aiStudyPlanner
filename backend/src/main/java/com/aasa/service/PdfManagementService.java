package com.aasa.service;

import com.aasa.dto.PdfDocumentDto;
import com.aasa.entity.PdfDocument;
import com.aasa.entity.Topic;
import com.aasa.entity.User;
import com.aasa.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
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

    @Value("${file.upload.dir}")
    private String uploadDir;

    @Transactional
    public PdfDocumentDto uploadPdf(MultipartFile file, User user, LocalDate examDate) {
        try {
            String fileName = file.getOriginalFilename();
            logger.info("Uploading PDF: " + fileName + " for user: " + user.getId());

            // Delete any existing PDFs/data for this user (one PDF per user)
            deleteAllUserData(user);
            logger.info("Existing data deleted for user: " + user.getId());

            String uniqueFileName = UUID.randomUUID() + "_" + fileName;
            String filePath = uploadDir + "/" + uniqueFileName;

            Files.createDirectories(Paths.get(uploadDir));
            Files.write(Paths.get(filePath), file.getBytes());

            String extractedText = pdfExtractionService.extractTextFromPdf(file);

            PdfDocument pdfDocument = PdfDocument.builder()
                    .user(user)
                    .fileName(fileName)
                    .filePath(filePath)
                    .examDate(examDate)
                    .extractedText(extractedText)
                    .isAnalyzed(false)
                    .build();

            PdfDocument saved = pdfDocumentRepository.save(pdfDocument);
            logger.info("PDF saved with ID: " + saved.getId());

            return PdfDocumentDto.builder()
                    .id(saved.getId())
                    .fileName(saved.getFileName())
                    .uploadDate(saved.getUploadDate())
                    .examDate(saved.getExamDate())
                    .isAnalyzed(false)
                    .topicCount(0)
                    .build();
        } catch (IOException e) {
            logger.severe("IO Error during PDF upload: " + e.getMessage());
            throw new RuntimeException("Failed to process PDF file: " + e.getMessage(), e);
        }
    }

    @Transactional
    public void deleteAllUserData(User user) {
        Long userId = user.getId();
        logger.info("Deleting all data for user ID: " + userId);

        // Delete in correct FK order: quiz_attempts → study_progress → quizzes → topics → pdf_documents
        quizAttemptRepository.deleteByUserId(userId);
        logger.info("Deleted quiz attempts for user: " + userId);

        studyProgressRepository.deleteByUserId(userId);
        logger.info("Deleted study progress for user: " + userId);

        // Get all topics for user, delete quizzes for each topic first
        List<Topic> topics = topicRepository.findByUserIdOrderByPriority(userId);
        for (Topic topic : topics) {
            try {
                quizRepository.deleteByTopicId(topic.getId());
            } catch (Exception e) {
                logger.warning("Error deleting quizzes for topic " + topic.getId() + ": " + e.getMessage());
            }
        }
        logger.info("Deleted quizzes for all topics");

        // Now delete PDFs (cascade will handle topics)
        List<PdfDocument> pdfs = pdfDocumentRepository.findByUserId(userId);
        for (PdfDocument pdf : pdfs) {
            try {
                Files.deleteIfExists(Paths.get(pdf.getFilePath()));
            } catch (IOException e) {
                logger.warning("Failed to delete file: " + pdf.getFilePath());
            }
            pdfDocumentRepository.delete(pdf);
        }
        logger.info("Deleted all PDFs for user: " + userId);
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

    public PdfDocumentDto getPdfByIdDto(Long pdfId) {
        PdfDocument pdf = getPdfById(pdfId);
        int topicCount = topicRepository.findByPdfDocumentId(pdfId).size();
        return PdfDocumentDto.builder()
                .id(pdf.getId())
                .fileName(pdf.getFileName())
                .uploadDate(pdf.getUploadDate())
                .examDate(pdf.getExamDate())
                .isAnalyzed(pdf.getIsAnalyzed())
                .topicCount(topicCount)
                .build();
    }

    @Transactional(readOnly = true)
    public com.aasa.dto.PdfDetailDto getPdfDetail(Long pdfId, Long userId) {
        PdfDocument pdf = pdfDocumentRepository.findById(pdfId)
                .orElseThrow(() -> new RuntimeException("PDF not found"));

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

    @Transactional
    public void deletePdf(Long pdfId) {
        PdfDocument pdf = getPdfById(pdfId);
        // Delete related data first
        List<Topic> topics = topicRepository.findByPdfDocumentId(pdfId);
        for (Topic topic : topics) {
            try {
                quizAttemptRepository.deleteByQuizTopicId(topic.getId());
            } catch (Exception e) {
                logger.warning("Error deleting attempts for topic " + topic.getId() + ": " + e.getMessage());
            }
            try {
                studyProgressRepository.deleteByTopicId(topic.getId());
            } catch (Exception e) {
                logger.warning("Error deleting study progress for topic " + topic.getId() + ": " + e.getMessage());
            }
            try {
                quizRepository.deleteByTopicId(topic.getId());
            } catch (Exception e) {
                logger.warning("Error deleting quizzes for topic " + topic.getId() + ": " + e.getMessage());
            }
        }
        try {
            Files.deleteIfExists(Paths.get(pdf.getFilePath()));
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete PDF file", e);
        }
        pdfDocumentRepository.delete(pdf);
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
                .build();
    }
}