package com.aasa.service;

import com.aasa.dto.AiAnalysisResponse;
import com.aasa.dto.TopicDto;
import com.aasa.entity.PdfDocument;
import com.aasa.entity.Topic;
import com.aasa.repository.TopicRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Service
public class TopicAnalysisService {

    private static final Logger logger = Logger.getLogger(TopicAnalysisService.class.getName());

    @Autowired
    private GeminiAiService geminiAiService;

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private AdaptivePriorityService adaptivePriorityService;

    @Autowired
    private ScoringEngineService scoringEngineService;

    @Autowired
    private QuizEngineService quizEngineService;

    @Autowired
    private com.aasa.repository.QuizRepository quizRepository;

    @Transactional
    public List<Topic> analyzeAndCreateTopics(PdfDocument pdfDocument) throws Exception {
        logger.info("Analyzing PDF document: " + pdfDocument.getFileName());

        // Delete existing topics/quizzes for this PDF to avoid duplicates
        List<Topic> existing = topicRepository.findByPdfDocumentId(pdfDocument.getId());
        if (!existing.isEmpty()) {
            logger.info("Deleting " + existing.size() + " existing topics for PDF " + pdfDocument.getId());
            for (Topic t : existing) {
                quizRepository.deleteByTopicId(t.getId());
            }
            topicRepository.deleteByPdfDocumentId(pdfDocument.getId());
        }

        // Call external AI API OUTSIDE the transaction to avoid rollback-only issues
        // when the API fails or times out
        AiAnalysisResponse aiResponse;
        try {
            aiResponse = geminiAiService.analyzeContent(pdfDocument.getExtractedText());
        } catch (Exception e) {
            logger.severe("Gemini API call failed: " + e.getMessage());
            throw e; // Re-throw to fail the operation
        }

        logger.info("Received " + aiResponse.getTopics().size() + " topics from AI");

        long daysUntilExam = ChronoUnit.DAYS.between(LocalDate.now(), pdfDocument.getExamDate());

        List<Topic> topics = aiResponse.getTopics().stream()
                .map(topicAnalysis -> createTopicFromAnalysis(topicAnalysis, pdfDocument, (int) daysUntilExam))
                .collect(Collectors.toList());

        logger.info("Created " + topics.size() + " topic entities");
        List<Topic> savedTopics = topicRepository.saveAll(topics);

        for (int i = 0; i < savedTopics.size() && i < aiResponse.getTopics().size(); i++) {
            Topic topic = savedTopics.get(i);
            AiAnalysisResponse.TopicAnalysis analysis = aiResponse.getTopics().get(i);

            logger.info("Topic " + i + ": " + topic.getTitle() + ", Quiz list: " + (analysis.getQuiz() != null ? analysis.getQuiz().size() : "null"));

            if (analysis.getQuiz() != null && !analysis.getQuiz().isEmpty()) {
                logger.info("Generating " + analysis.getQuiz().size() + " quizzes for topic: " + topic.getTitle());
                try {
                    quizEngineService.generateQuizzesForTopic(topic, analysis.getQuiz());
                } catch (Exception e) {
                    logger.warning("Error generating quizzes for topic " + topic.getTitle() + ": " + e.getMessage());
                    e.printStackTrace();
                }
            } else {
                logger.warning("No quizzes found for topic: " + topic.getTitle());
            }
        }

        return savedTopics;
    }

    private Topic createTopicFromAnalysis(AiAnalysisResponse.TopicAnalysis analysis, PdfDocument pdfDocument, int daysUntilExam) {
        AiAnalysisResponse.TopicAnalysis.SemanticSignals signals = analysis.getSignals();
        if (signals == null) {
            signals = AiAnalysisResponse.TopicAnalysis.SemanticSignals.builder()
                    .conceptDensity(5.0)
                    .keywordDifficulty(5.0)
                    .formulaCount(0)
                    .length(100)
                    .build();
        }

        logger.info("Creating topic: " + analysis.getTitle());

        // Use AI-provided importance and complexity scores if available, otherwise calculate
        Double importanceScore = analysis.getImportance() != null ? analysis.getImportance() : calculateImportanceScore(signals);
        Double complexityScore = analysis.getComplexity() != null ? analysis.getComplexity() : scoringEngineService.calculateComplexityScore(
                signals.getConceptDensity() != null ? signals.getConceptDensity() : 5.0,
                signals.getKeywordDifficulty() != null ? signals.getKeywordDifficulty() : 5.0,
                signals.getFormulaCount() != null ? signals.getFormulaCount() : 0,
                signals.getLength() != null ? signals.getLength() : 100
        );

        // Initial adaptive priority before any quiz evidence exists:
        // full mastery gap (NOT_ATTEMPTED), no accumulated forgetting yet, exam urgency, AI importance.
        Double priorityScore = adaptivePriorityService.calculatePriorityFromWeakness(
                1.0, // Default weakness score for NOT_ATTEMPTED
                importanceScore,
                pdfDocument.getExamDate(),
                null
        );

        logger.info("Topic scores - Complexity: " + complexityScore + ", Importance: " + importanceScore + ", Priority: " + priorityScore);

        Topic topic = Topic.builder()
                .pdfDocument(pdfDocument)
                .title(analysis.getTitle())
                .description(analysis.getDescription())
                .conceptDensity(signals.getConceptDensity() != null ? signals.getConceptDensity() : 5.0)
                .keywordDifficulty(signals.getKeywordDifficulty() != null ? signals.getKeywordDifficulty() : 5.0)
                .formulaCount(signals.getFormulaCount() != null ? signals.getFormulaCount() : 0)
                .contentLength(signals.getLength() != null ? signals.getLength() : 100)
                .complexityScore(complexityScore)
                .importanceScore(importanceScore)
                .weaknessScore(1.0) // Default weakness score for NOT_ATTEMPTED topics
                .priorityScore(priorityScore)
                .build();

        return topic;
    }

    private Double calculateImportanceScore(AiAnalysisResponse.TopicAnalysis.SemanticSignals signals) {
        if (signals == null) {
            return 0.5;
        }

        double conceptWeight = (signals.getConceptDensity() != null ? signals.getConceptDensity() : 5.0) / 10.0;
        double difficultyWeight = (signals.getKeywordDifficulty() != null ? signals.getKeywordDifficulty() : 5.0) / 10.0;

        return (0.6 * conceptWeight) + (0.4 * difficultyWeight);
    }

    @Transactional(readOnly = true)
    public List<TopicDto> getTopicsByPdf(Long pdfId) {
        logger.info("Fetching topics for PDF ID: " + pdfId);
        return topicRepository.findByPdfDocumentId(pdfId)
                .stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TopicDto> getUserTopicsRankedByPriority(Long userId) {
        logger.info("Fetching ranked topics for user ID: " + userId);
        return topicRepository.findByUserIdOrderByPriority(userId)
                .stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    public Topic getTopicById(Long topicId) {
        return topicRepository.findById(topicId)
                .orElseThrow(() -> new RuntimeException("Topic not found with ID: " + topicId));
    }

    public void updateTopicPriority(Topic topic, Double newPriority) {
        logger.info("Updating priority for topic: " + topic.getTitle() + " to " + newPriority);
        topic.setPriorityScore(newPriority);
        topicRepository.save(topic);
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
