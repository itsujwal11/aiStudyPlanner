package com.aasa.service;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Adaptive study-priority engine of the Adaptive Knowledge-Tracing and RAG
 * Recommendation Algorithm.
 *
 * Replaces the old fixed weighted formula
 *   0.35*complexity + 0.25*importance + 0.25*manualWeakness + 0.15*urgency
 * with priorities derived from actual learner evidence:
 *
 *   priority = 0.40 * (1 - masteryProbability)      // BKT mastery gap
 *            + 0.25 * forgettingRisk                // exponential forgetting curve
 *            + 0.20 * adaptiveExamUrgency           // 1 / (daysUntilExam + 1)
 *            + 0.15 * topicImportance               // AI-assessed topic weight
 *
 * Every component changes as the learner answers quizzes and as time passes,
 * which makes the ranking genuinely adaptive instead of static.
 */
@Service
public class AdaptivePriorityService {

    public static final double WEIGHT_MASTERY_GAP = 0.40;
    public static final double WEIGHT_FORGETTING_RISK = 0.25;
    public static final double WEIGHT_EXAM_URGENCY = 0.20;
    public static final double WEIGHT_TOPIC_IMPORTANCE = 0.15;

    private static final double NEUTRAL_URGENCY = 0.5;
    private static final double DEFAULT_IMPORTANCE = 0.5;

    private final BayesianKnowledgeTracingService knowledgeTracingService;

    public AdaptivePriorityService(BayesianKnowledgeTracingService knowledgeTracingService) {
        this.knowledgeTracingService = knowledgeTracingService;
    }

    /**
     * Adaptive priority from a BKT mastery probability.
     *
     * @param masteryProbability current mastery P(K) from Bayesian Knowledge Tracing (0..1)
     * @param topicImportance    AI-assessed importance (0..1, nullable)
     * @param examDate           exam date driving urgency (nullable)
     * @param lastStudyDate      last revision date feeding the forgetting curve (nullable)
     * @return priority score clamped to [0,1]; higher means "study this sooner"
     */
    public double calculatePriority(double masteryProbability, Double topicImportance,
                                    LocalDate examDate, LocalDate lastStudyDate) {
        double mastery = clamp01(masteryProbability);
        double masteryGap = 1.0 - mastery;

        double forgettingRisk =
                knowledgeTracingService.forgettingRisk(mastery, daysSince(lastStudyDate));
        double examUrgency = examUrgency(examDate);
        double importance = topicImportance != null ? clamp01(topicImportance) : DEFAULT_IMPORTANCE;

        double priority = WEIGHT_MASTERY_GAP * masteryGap
                + WEIGHT_FORGETTING_RISK * forgettingRisk
                + WEIGHT_EXAM_URGENCY * examUrgency
                + WEIGHT_TOPIC_IMPORTANCE * importance;

        return clamp01(priority);
    }

    /**
     * Same calculation when only a weakness level (0 = strong, 1 = weakest) is known,
     * e.g. manual weakness updates or freshly created topics with no quiz history yet.
     */
    public double calculatePriorityFromWeakness(double weaknessLevel, Double topicImportance,
                                                LocalDate examDate, LocalDate lastStudyDate) {
        double masteryEstimate = 1.0 - clamp01(weaknessLevel);
        return calculatePriority(masteryEstimate, topicImportance, examDate, lastStudyDate);
    }

    /**
     * Exam urgency = 1 / (daysUntilExam + 1): approaches 1 on exam day,
     * decays towards 0 as the exam moves away; neutral when unknown.
     */
    public double examUrgency(LocalDate examDate) {
        if (examDate == null) {
            return NEUTRAL_URGENCY;
        }
        long days = ChronoUnit.DAYS.between(LocalDate.now(), examDate);
        if (days < 0) {
            days = 0;
        }
        return 1.0 / (days + 1.0);
    }

    private long daysSince(LocalDate date) {
        if (date == null) {
            return 0L; // never revised -> no forgetting has accumulated yet
        }
        long days = ChronoUnit.DAYS.between(date, LocalDate.now());
        return Math.max(0L, days);
    }

    private double clamp01(double value) {
        return value < 0.0 ? 0.0 : (Math.min(value, 1.0));
    }
}
