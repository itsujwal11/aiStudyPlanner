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

    public StudyProgress.WeaknessLevel calculateWeaknessLevel(Double score) {
        if (score == null) {
            return StudyProgress.WeaknessLevel.NOT_ATTEMPTED;
        }

        if (score >= 75) {
            return StudyProgress.WeaknessLevel.LOW;
        } else if (score >= 50) {
            return StudyProgress.WeaknessLevel.MEDIUM;
        } else {
            return StudyProgress.WeaknessLevel.HIGH;
        }
    }

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

        StudyProgress.WeaknessLevel level = score >= 0.65
                ? StudyProgress.WeaknessLevel.HIGH
                : score >= 0.35
                    ? StudyProgress.WeaknessLevel.MEDIUM
                    : StudyProgress.WeaknessLevel.LOW;
        return new WeaknessResult(level, score);
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
