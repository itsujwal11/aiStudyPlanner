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
    public PdfDocument uploadPdf(MultipartFile file, User user, LocalDate examDate) throws IOException {
        String fileName = file.getOriginalFilename();
        logger.info("Uploading PDF: " + fileName + " for user: " + user.getId());

        // First, clean up all previous user data to enforce one-session-per-PDF
        deleteAllUserData(user);

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

        return pdfDocumentRepository.save(pdfDocument);
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