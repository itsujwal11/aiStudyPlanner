package com.aasa.controller;

import com.aasa.dto.RagAnswerDto;
import com.aasa.dto.RagQueryDto;
import com.aasa.entity.User;
import com.aasa.service.AuthService;
import com.aasa.service.RagAugmentedService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.logging.Logger;

@RestController
@RequestMapping("/api/rag")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class RagController {

    private static final Logger logger = Logger.getLogger(RagController.class.getName());
    private static final int MAX_QUESTION_LENGTH = 2_000;

    @Autowired
    private RagAugmentedService ragAugmentedService;

    @Autowired
    private AuthService authService;

    @PostMapping("/ask")
    public ResponseEntity<?> askQuestion(@RequestBody RagQueryDto query, Authentication authentication) {
        if (query == null || query.getQuestion() == null || query.getQuestion().trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Question is required"));
        }

        String question = query.getQuestion().trim();
        if (question.length() > MAX_QUESTION_LENGTH) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(
                            "error",
                            "Question must be " + MAX_QUESTION_LENGTH + " characters or fewer"
                    ));
        }
        logger.info("RAG ask request with " + question.length() + " characters");

        try {
            User user = authService.getUserByEmail(authentication.getName());
            RagAnswerDto answer = ragAugmentedService.answerQuestion(
                    user,
                    question,
                    query.getPdfId()
            );
            return ResponseEntity.ok(answer);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "PDF not found"));
        } catch (Exception e) {
            logger.severe("Error processing RAG query: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to process question: " + e.getMessage()));
        }
    }

    @PostMapping("/reprocess/{pdfId}")
    public ResponseEntity<?> reprocessPdf(@PathVariable Long pdfId, Authentication authentication) {
        logger.info("RAG reprocess request for PDF " + pdfId);

        try {
            User user = authService.getUserByEmail(authentication.getName());
            // Verify the PDF belongs to this user
            com.aasa.entity.PdfDocument pdf = ragAugmentedService.getPdfForUser(pdfId, user.getId());
            if (pdf == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "PDF not found"));
            }

            ragAugmentedService.reprocessPdfForRag(pdf);
            return ResponseEntity.ok(Map.of(
                    "message", "PDF chunks and embeddings reprocessed successfully"
            ));
        } catch (Exception e) {
            logger.severe("Error reprocessing PDF for RAG: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to reprocess PDF: " + e.getMessage()));
        }
    }
}
