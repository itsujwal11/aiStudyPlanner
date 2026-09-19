package com.aasa.controller;

import com.aasa.dto.PredefinedAnswerDto;
import com.aasa.dto.RagAnswerDto;
import com.aasa.dto.RagQueryDto;
import com.aasa.entity.User;
import com.aasa.service.AuthService;
import com.aasa.service.AiServiceException;
import com.aasa.service.RagAugmentedService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.List;
import java.util.logging.Logger;

@RestController
@RequestMapping("/api/rag")
public class RagController {

    private static final Logger logger = Logger.getLogger(RagController.class.getName());

    @Autowired
    private RagAugmentedService ragAugmentedService;

    @Autowired
    private AuthService authService;

    @GetMapping("/predefined")
    public ResponseEntity<?> getPredefinedAnswers(
            @RequestParam(required = false) Long pdfId,
            Authentication authentication
    ) {
        try {
            User user = authService.getUserByEmail(authentication.getName());
            List<PredefinedAnswerDto> answers =
                    ragAugmentedService.getPredefinedAnswers(user, pdfId);
            return ResponseEntity.ok(answers);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "PDF not found"));
        } catch (AiServiceException e) {
            logger.warning("Predefined answers failed: " + e.getMessage());
            return ResponseEntity.status(e.getStatus())
                    .body(Map.of("error", e.getUserMessage()));
        } catch (Exception e) {
            logger.severe("Error loading predefined answers: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to load quick answers: " + e.getMessage()));
        }
    }

    /**
     * Full RAG question answering:
     * question → embedding → pgvector cosine retrieval (top 20) → hybrid reranking (top 5)
     * → grounded Gemini generation with per-source citations.
     */
    @PostMapping("/ask")
    public ResponseEntity<?> ask(@RequestBody RagQueryDto request, Authentication authentication) {
        try {
            User user = authService.getUserByEmail(authentication.getName());
            RagAnswerDto answer = ragAugmentedService.answerQuestion(
                    user, request.getQuestion(), request.getPdfId());
            return ResponseEntity.ok(answer);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "PDF not found"));
        } catch (AiServiceException e) {
            // Quota, auth and upstream outages each get their own status and a
            // message written for the student, rather than a blanket 500.
            logger.warning("RAG question failed: " + e.getMessage());
            return ResponseEntity.status(e.getStatus())
                    .body(Map.of("error", e.getUserMessage()));
        } catch (Exception e) {
            logger.severe("Error answering RAG question: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to answer question: " + e.getMessage()));
        }
    }
}
