package com.aasa.service;

import com.aasa.entity.Quiz;
import com.aasa.entity.QuizAttempt;
import com.aasa.entity.StudyProgress;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeaknessEngineServiceTest {

    private final WeaknessEngineService service = new WeaknessEngineService();

    @Test
    void keepsTopicUnclassifiedUntilMinimumEvidenceExists() {
        var result = service.calculateEvidenceBasedWeakness(
                List.of(attempt(true, Quiz.DifficultyLevel.EASY, 12),
                        attempt(false, Quiz.DifficultyLevel.HARD, 40)),
                0.3,
                LocalDate.now().plusDays(1)
        );

        assertEquals(StudyProgress.WeaknessLevel.INSUFFICIENT_DATA, result.level());
    }

    @Test
    void classifiesConsistentlyIncorrectAnswersAsHighWeakness() {
        var result = service.calculateEvidenceBasedWeakness(
                List.of(attempt(false, Quiz.DifficultyLevel.EASY, 20),
                        attempt(false, Quiz.DifficultyLevel.MEDIUM, 30),
                        attempt(false, Quiz.DifficultyLevel.HARD, 45)),
                0.2,
                LocalDate.now().minusDays(1)
        );

        assertEquals(StudyProgress.WeaknessLevel.HIGH, result.level());
        assertTrue(result.score() >= 0.65);
    }

    @Test
    void classifiesStrongEvidenceAsLowWeakness() {
        var result = service.calculateEvidenceBasedWeakness(
                List.of(attempt(true, Quiz.DifficultyLevel.EASY, 10),
                        attempt(true, Quiz.DifficultyLevel.MEDIUM, 12),
                        attempt(true, Quiz.DifficultyLevel.HARD, 14)),
                0.85,
                LocalDate.now().plusDays(2)
        );

        assertEquals(StudyProgress.WeaknessLevel.LOW, result.level());
        assertTrue(result.score() < 0.35);
    }

    private QuizAttempt attempt(boolean correct, Quiz.DifficultyLevel difficulty, long seconds) {
        return QuizAttempt.builder()
                .quiz(Quiz.builder().difficulty(difficulty).build())
                .isCorrect(correct)
                .timeTakenSeconds(seconds)
                .build();
    }
}
