package com.aasa.controller;

import com.aasa.dto.TopicDto;
import com.aasa.dto.WeaknessUpdateRequest;
import com.aasa.entity.PdfDocument;
import com.aasa.service.ScoringEngineService;
import com.aasa.entity.Topic;
import com.aasa.service.PdfManagementService;
import com.aasa.service.TopicAnalysisService;
import com.aasa.service.AuthService;
import com.aasa.service.OllamaAiService;
import java.time.temporal.ChronoUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

@RestController

@RequestMapping("/api/topics")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class TopicController {

    private static final Logger logger = Logger.getLogger(TopicController.class.getName());

    @Autowired private TopicAnalysisService topicAnalysisService;
    @Autowired private PdfManagementService  pdfManagementService;
    @Autowired private AuthService           authService;
    @Autowired
    private ScoringEngineService scoringEngineService;
private OllamaAiService ollamaAiService;

    @PostMapping("/analyze/{pdfId}")
    public ResponseEntity<?> analyzePdf(@PathVariable Long pdfId) {
        try {
            logger.info("Starting PDF analysis for PDF ID: " + pdfId);

            PdfDocument pdf = pdfManagementService.getPdfById(pdfId);
            logger.info("PDF found: " + pdf.getFileName());

            // ── Guard: don't re-analyze an already-analyzed PDF ──────────────────
            if (Boolean.TRUE.equals(pdf.getIsAnalyzed())) {
                logger.warning("PDF " + pdfId + " has already been analyzed.");
                List<TopicDto> existing = topicAnalysisService.getTopicsByPdf(pdfId);
                return ResponseEntity.ok(existing);
            }

            // ── Guard: extracted text must be present ─────────────────────────────
            if (pdf.getExtractedText() == null || pdf.getExtractedText().isBlank()) {
                return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                        .body(errorBody("PDF has no extractable text. "
                                + "Make sure the PDF is not scanned/image-only."));
            }

            List<Topic> topics = topicAnalysisService.analyzeAndCreateTopics(pdf);
            logger.info("Topics created: " + topics.size());

            pdfManagementService.markAsAnalyzed(pdfId);

            List<TopicDto> topicDtos = topicAnalysisService.getTopicsByPdf(pdfId);
            return ResponseEntity.status(HttpStatus.CREATED).body(topicDtos);

        } catch (IllegalStateException e) {
            // Gemini API key not configured
            logger.severe("Config error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorBody(e.getMessage()));
        } catch (IllegalArgumentException e) {
            logger.severe("Bad input: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(errorBody(e.getMessage()));
        } catch (Exception e) {
            // ── FIX: surface the real Gemini error message ──────────────────────
            String rootMsg = getRootCauseMessage(e);
            logger.severe("Analysis failed for PDF " + pdfId + ": " + rootMsg);
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorBody(rootMsg));
        }
    }

    @GetMapping("/pdf/{pdfId}")
    public ResponseEntity<List<TopicDto>> getTopicsByPdf(@PathVariable Long pdfId) {
        try {
            return ResponseEntity.ok(topicAnalysisService.getTopicsByPdf(pdfId));
        } catch (Exception e) {
            logger.severe("Error fetching topics: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/ranked/pdf/{pdfId}")
    public ResponseEntity<List<TopicDto>> getRankedTopicsByPdf(@PathVariable Long pdfId, Authentication authentication) {
        try {
            List<TopicDto> topics = topicAnalysisService.getTopicsByPdf(pdfId);
            return ResponseEntity.ok(topics);
        } catch (Exception e) {
            logger.severe("Error fetching ranked topics: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/ranked")
    public ResponseEntity<List<TopicDto>> getRankedTopics(Authentication authentication) {
        try {
            com.aasa.entity.User user = authService.getUserByEmail(authentication.getName());
            return ResponseEntity.ok(topicAnalysisService.getUserTopicsRankedByPriority(user.getId()));
        } catch (Exception e) {
            logger.severe("Error fetching ranked topics: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{topicId}")
    public ResponseEntity<TopicDto> getTopic(@PathVariable Long topicId) {
        try {
            Topic topic = topicAnalysisService.getTopicById(topicId);
            return ResponseEntity.ok(convertToDto(topic));
        } catch (Exception e) {
            logger.severe("Error fetching topic: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @PostMapping("/{topicId}/update-weakness")
    public ResponseEntity<TopicDto> updateWeakness(
            @PathVariable Long topicId,
            @RequestBody WeaknessUpdateRequest request) {
        try {
            logger.info("Updating weakness for topic ID: " + topicId);
            Topic topic = topicAnalysisService.getTopicById(topicId);
            topic.setWeaknessScore(request.getWeakness());

            long daysUntilExam = ChronoUnit.DAYS.between(LocalDate.now(), topic.getPdfDocument().getExamDate());
            Double newPriority = scoringEngineService.calculatePriorityScore(
                    topic.getComplexityScore(),
                    topic.getImportanceScore(),
                    request.getWeakness(),
                    (int) daysUntilExam
            );

            topic.setPriorityScore(newPriority);
            topicAnalysisService.updateTopicPriority(topic, newPriority);
            return ResponseEntity.ok(convertToDto(topic));
        } catch (Exception e) {
            logger.severe("Error updating weakness: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private Map<String, String> errorBody(String message) {
        return Map.of("error", message);
    }

    /** Walk the cause chain to get the most descriptive message. */
    private String getRootCauseMessage(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String msg = root.getMessage();
        return (msg != null && !msg.isBlank()) ? msg : t.getClass().getSimpleName() + " (no message)";
    }

    private TopicDto convertToDto(Topic topic) {
        return TopicDto.builder()
                .id(topic.getId())
                .title(topic.getTitle())
                .description(topic.getDescription())
                .complexityScore(topic.getComplexityScore())
                .importanceScore(topic.getImportanceScore())
                .priorityScore(topic.getPriorityScore())
                .weaknessScore(topic.getWeaknessScore())
                .quizCount(topic.getQuizzes() != null ? topic.getQuizzes().size() : 0)
                .build();
    }
}