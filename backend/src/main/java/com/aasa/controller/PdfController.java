package com.aasa.controller;

import com.aasa.dto.PdfDocumentDto;
import com.aasa.entity.PdfDocument;
import com.aasa.entity.User;
import com.aasa.repository.*;
import com.aasa.service.AuthService;
import com.aasa.service.PdfManagementService;
import com.aasa.service.PdfProcessingService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

@RestController
@RequestMapping("/api/pdfs")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class PdfController {

    private static final Logger logger = Logger.getLogger(PdfController.class.getName());

    @Autowired
    private PdfManagementService pdfManagementService;

    @Autowired
    private PdfProcessingService pdfProcessingService;

    @Autowired
    private AuthService authService;

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private QuizRepository quizRepository;

    @Autowired
    private QuizAttemptRepository quizAttemptRepository;

    @Autowired
    private StudyProgressRepository studyProgressRepository;

    @PostMapping("/upload")
    public ResponseEntity<?> uploadPdf(
            @RequestParam("file") MultipartFile file,
            @RequestParam("examDate") String examDate,
            Authentication authentication) {
        logger.info("PDF upload request - File: " + (file != null ? file.getOriginalFilename() : "null") + ", Exam Date: " + examDate);

        if (file == null || file.isEmpty()) {
            logger.warning("Uploaded file is empty or null");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "File is empty"));
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.equalsIgnoreCase("application/pdf")) {
            logger.warning("Invalid file type: " + contentType);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid file type. Please upload a PDF"));
        }

        long maxSize = 50L * 1024 * 1024;
        if (file.getSize() > maxSize) {
            logger.warning("File exceeds size limit: " + file.getSize());
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(Map.of("error", "File size exceeds 50MB limit"));
        }

        User user = authService.getUserByEmail(authentication.getName());
        logger.info("User authenticated: " + user.getEmail());

        java.time.LocalDate parsedDate;
        try {
            parsedDate = java.time.LocalDate.parse(examDate);
        } catch (java.time.format.DateTimeParseException dtpe) {
            logger.warning("Invalid examDate format: " + examDate);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid examDate format. Expected YYYY-MM-DD"));
        }

        PdfDocumentDto result = pdfManagementService.uploadPdf(file, user, parsedDate);
        logger.info("PDF uploaded successfully with ID: " + result.getId());
        pdfProcessingService.processAsync(result.getId());

        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping
    public ResponseEntity<List<PdfDocumentDto>> getUserPdfs(Authentication authentication) {
        try {
            User user = authService.getUserByEmail(authentication.getName());
            List<PdfDocumentDto> pdfs = pdfManagementService.getUserPdfs(user.getId());
            return ResponseEntity.ok(pdfs);
        } catch (Exception e) {
            logger.severe("Error fetching PDFs: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{pdfId}")
    public ResponseEntity<PdfDocumentDto> getPdf(
            @PathVariable Long pdfId,
            Authentication authentication) {
        try {
            User user = authService.getUserByEmail(authentication.getName());
            return ResponseEntity.ok(pdfManagementService.getPdfByIdDto(pdfId, user.getId()));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (Exception e) {
            logger.severe("Error fetching PDF: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{pdfId}/detail")
    public ResponseEntity<?> getPdfDetail(@PathVariable Long pdfId, Authentication authentication) {
        try {
            User user = authService.getUserByEmail(authentication.getName());
            return ResponseEntity.ok(pdfManagementService.getPdfDetail(pdfId, user.getId()));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (Exception e) {
            logger.severe("Error fetching PDF detail: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @DeleteMapping("/{pdfId}")
    public ResponseEntity<Void> deletePdf(
            @PathVariable Long pdfId,
            Authentication authentication) {
        try {
            User user = authService.getUserByEmail(authentication.getName());
            pdfManagementService.deletePdf(pdfId, user.getId());
            return ResponseEntity.noContent().build();
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        } catch (Exception e) {
            logger.severe("Error deleting PDF: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @DeleteMapping("/reset")
    public ResponseEntity<?> resetAll(Authentication authentication) {
        try {
            User user = authService.getUserByEmail(authentication.getName());
            logger.info("Resetting all data for user ID: " + user.getId());

            pdfManagementService.resetAllUserData(user);

            logger.info("All data reset successfully for user ID: " + user.getId());
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            logger.severe("Error resetting data: " + e.getMessage());
            e.printStackTrace();
            String errorMessage = e.getMessage() != null ? e.getMessage() : "Unknown reset error";
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to reset user data", "message", errorMessage));
        }
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
