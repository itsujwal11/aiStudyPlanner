package com.aasa.service;

import com.aasa.entity.Quiz;
import com.aasa.entity.QuizAttempt;
import com.aasa.entity.StudyProgress;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class WeaknessEngineService {

    public static final int MINIMUM_EVIDENCE_ATTEMPTS = 3;

    /**
     * Hybrid weighting from the offline experiment
     * ({@code ml/reports/metrics.json} -> {@code hybrid_formula}):
     * <pre>weakness = 0.70 * evidenceWeakness + 0.30 * modelWeakness</pre>
     * The deterministic evidence formula stays dominant because it is
     * explainable and always available; the trained Random Forest contributes a
     * learned correction from ~283k real practice opportunities.
     */
    public static final double HYBRID_EVIDENCE_WEIGHT = 0.70;
    public static final double HYBRID_MODEL_WEIGHT = 0.30;

    public Double getWeaknessScore(StudyProgress.WeaknessLevel level) {
        return switch (level) {
            case LOW -> 0.2;
            case MEDIUM -> 0.5;
            case HIGH -> 0.9;
            case INSUFFICIENT_DATA -> 0.6;
            case NOT_ATTEMPTED -> 1.0;
        };
    }

    public WeaknessResult calculateEvidenceBasedWeakness(
            List<QuizAttempt> attempts,
            Double masteryLevel,
            LocalDate nextReviewDate) {
        if (attempts == null || attempts.isEmpty()) {
            return new WeaknessResult(StudyProgress.WeaknessLevel.NOT_ATTEMPTED, 1.0);
        }

        double totalWeight = 0.0;
        double incorrectWeight = 0.0;
        double responseTimeTotal = 0.0;

        for (QuizAttempt attempt : attempts) {
            double difficultyWeight = difficultyWeight(attempt.getQuiz().getDifficulty());
            totalWeight += difficultyWeight;
            if (!Boolean.TRUE.equals(attempt.getIsCorrect())) {
                incorrectWeight += difficultyWeight;
            }

            long seconds = attempt.getTimeTakenSeconds() != null ? attempt.getTimeTakenSeconds() : 0L;
            responseTimeTotal += Math.min(Math.max(seconds, 0L) / 60.0, 1.0);
        }

        double weightedErrorRate = totalWeight == 0.0 ? 1.0 : incorrectWeight / totalWeight;
        double masteryGap = 1.0 - clamp(masteryLevel != null ? masteryLevel : 0.0);
        double slowResponseFactor = responseTimeTotal / attempts.size();
        double overdueFactor = nextReviewDate != null && nextReviewDate.isBefore(LocalDate.now()) ? 1.0 : 0.0;

        double score = clamp(
                0.60 * weightedErrorRate
                        + 0.25 * masteryGap
                        + 0.10 * slowResponseFactor
                        + 0.05 * overdueFactor
        );

        if (attempts.size() < MINIMUM_EVIDENCE_ATTEMPTS) {
            return new WeaknessResult(StudyProgress.WeaknessLevel.INSUFFICIENT_DATA, score);
        }

        return new WeaknessResult(levelForScore(score), score);
    }

    /**
     * Blends the deterministic evidence score with the trained model's
     * {@code 1 - P(correct)} estimate.
     *
     * <p>Returns the evidence result unchanged when {@code modelWeakness} is null
     * (model disabled, service down, or artifact missing), which is the system's
     * documented fallback. It is also returned unchanged for
     * {@code NOT_ATTEMPTED} topics: with no attempt history the model's inputs
     * are all defaults, so blending would only dilute the "never studied =
     * maximum priority" signal without adding information.</p>
     *
     * <p>The band ({@code LOW}/{@code MEDIUM}/{@code HIGH}) is recomputed from the
     * blended score so the label and the number never disagree.
     * {@code INSUFFICIENT_DATA} is preserved: it describes how much evidence
     * exists, not how weak the learner is.</p>
     */
    public WeaknessResult blendWithModel(WeaknessResult evidence, Double modelWeakness) {
        if (evidence == null) {
            return null;
        }
        if (modelWeakness == null
                || evidence.level() == StudyProgress.WeaknessLevel.NOT_ATTEMPTED) {
            return evidence;
        }

        double blended = clamp(
                HYBRID_EVIDENCE_WEIGHT * evidence.score()
                        + HYBRID_MODEL_WEIGHT * clamp(modelWeakness)
        );

        if (evidence.level() == StudyProgress.WeaknessLevel.INSUFFICIENT_DATA) {
            return new WeaknessResult(StudyProgress.WeaknessLevel.INSUFFICIENT_DATA, blended);
        }
        return new WeaknessResult(levelForScore(blended), blended);
    }

    /** Score-to-band mapping shared by the evidence and hybrid paths. */
    public StudyProgress.WeaknessLevel levelForScore(double score) {
        if (score >= 0.65) {
            return StudyProgress.WeaknessLevel.HIGH;
        }
        return score >= 0.35
                ? StudyProgress.WeaknessLevel.MEDIUM
                : StudyProgress.WeaknessLevel.LOW;
    }

    private double difficultyWeight(Quiz.DifficultyLevel difficulty) {
        if (difficulty == null) return 1.0;
        return switch (difficulty) {
            case EASY -> 1.0;
            case MEDIUM -> 1.5;
            case HARD -> 2.0;
        };
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    public record WeaknessResult(StudyProgress.WeaknessLevel level, double score) {}
}
