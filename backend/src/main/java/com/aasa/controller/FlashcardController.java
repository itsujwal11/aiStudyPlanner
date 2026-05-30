package com.aasa.controller;

import com.aasa.dto.FlashcardDto;
import com.aasa.dto.FlashcardReviewRequest;
import com.aasa.dto.FlashcardReviewResponse;
import com.aasa.entity.*;
import com.aasa.service.AuthService;
import com.aasa.service.FlashcardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/flashcards")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class FlashcardController {

    private static final Logger logger = Logger.getLogger(FlashcardController.class.getName());

    @Autowired private FlashcardService flashcardService;
    @Autowired private AuthService authService;

    @GetMapping("/topic/{topicId}")
    public ResponseEntity<List<FlashcardDto>> getByTopic(@PathVariable Long topicId) {
        try {
            return ResponseEntity.ok(
                flashcardService.getFlashcardsByTopic(topicId).stream()
                    .map(this::toDto).collect(Collectors.toList())
            );
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/pdf/{pdfId}")
    public ResponseEntity<List<FlashcardDto>> getByPdf(@PathVariable Long pdfId) {
        try {
            return ResponseEntity.ok(
                flashcardService.getFlashcardsByPdf(pdfId).stream()
                    .map(this::toDto).collect(Collectors.toList())
            );
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/{flashcardId}/review")
    public ResponseEntity<?> review(@PathVariable Long flashcardId,
                                     @RequestBody FlashcardReviewRequest request,
                                     Authentication auth) {
        try {
            User user = authService.getUserByEmail(auth.getName());
            FlashcardReview review = flashcardService.reviewFlashcard(flashcardId, user, request.getRating());
            return ResponseEntity.ok(FlashcardReviewResponse.builder()
                    .nextReviewAt(review.getNextReviewAt())
                    .box(review.getBox())
                    .intervalDays(review.getIntervalDays())
                    .build());
        } catch (Exception e) {
            logger.severe("Flashcard review error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/due")
    public ResponseEntity<List<FlashcardDto>> getDue(Authentication auth) {
        try {
            User user = authService.getUserByEmail(auth.getName());
            List<FlashcardReview> due = flashcardService.getDueReviews(user.getId());
            return ResponseEntity.ok(
                due.stream().map(r -> toDto(r.getFlashcard())).collect(Collectors.toList())
            );
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private FlashcardDto toDto(Flashcard card) {
        return FlashcardDto.builder()
                .id(card.getId())
                .topicId(card.getTopic().getId())
                .frontText(card.getFrontText())
                .backText(card.getBackText())
                .difficultyEst(card.getDifficultyEst())
                .build();
    }
}
