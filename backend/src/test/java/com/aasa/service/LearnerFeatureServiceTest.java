package com.aasa.service;

import com.aasa.entity.QuizAttempt;
import com.aasa.service.LearnerFeatureService.LearnerFeatures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Verifies the feature vector handed to the trained weakness model matches the
 * definitions used when the model was fitted in {@code ml/train_model.py}.
 */
class LearnerFeatureServiceTest {

    private final LearnerFeatureService service = new LearnerFeatureService();

    private QuizAttempt attempt(boolean correct, Long seconds) {
        return QuizAttempt.builder().isCorrect(correct).timeTakenSeconds(seconds).build();
    }

    @Test
    @DisplayName("no history yields the neutral cold-start vector")
    void coldStart() {
        LearnerFeatures f = service.extract(List.of());

        assertEquals(0.0, f.previousAttempts());
        assertEquals(0.5, f.previousAccuracy());
        assertEquals(0.5, f.recentAccuracy());
        // opportunity is 1-based: the attempt being predicted is the first one.
        assertEquals(1.0, f.opportunity());
        // null, not 0 - the pipeline's median imputer fills it from training data.
        assertNull(f.averageResponseTime());
    }

    @Test
    @DisplayName("null attempt list is treated as no history")
    void nullHistory() {
        assertEquals(1.0, service.extract(null).opportunity());
    }

    @Test
    @DisplayName("accuracy and response time average over the whole history")
    void aggregatesHistory() {
        LearnerFeatures f = service.extract(List.of(
                attempt(true, 10L),
                attempt(false, 30L),
                attempt(false, 20L),
                attempt(true, 40L)
        ));

        assertEquals(4.0, f.previousAttempts());
        assertEquals(0.5, f.previousAccuracy());
        assertEquals(25.0, f.averageResponseTime());
        assertEquals(5.0, f.opportunity());
    }

    @Test
    @DisplayName("recent accuracy uses only the last three attempts")
    void recentAccuracyWindow() {
        // Five correct then three wrong: lifetime accuracy stays high while
        // recent accuracy collapses - this is the signal the model reacts to.
        List<QuizAttempt> attempts = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            attempts.add(attempt(true, 10L));
        }
        for (int i = 0; i < 3; i++) {
            attempts.add(attempt(false, 10L));
        }

        LearnerFeatures f = service.extract(attempts);

        assertEquals(0.625, f.previousAccuracy());
        assertEquals(0.0, f.recentAccuracy());
    }

    @Test
    @DisplayName("recent window shrinks gracefully below three attempts")
    void shortHistoryRecentWindow() {
        LearnerFeatures f = service.extract(List.of(attempt(true, 5L), attempt(false, 5L)));

        assertEquals(0.5, f.recentAccuracy());
        assertEquals(2.0, f.previousAttempts());
    }

    @Test
    @DisplayName("missing and negative response times are excluded from the mean")
    void ignoresUnusableResponseTimes() {
        LearnerFeatures f = service.extract(List.of(
                attempt(true, null),
                attempt(true, -5L),
                attempt(false, 20L)
        ));

        assertEquals(20.0, f.averageResponseTime());
        assertEquals(3.0, f.previousAttempts());
    }

    @Test
    @DisplayName("response time is null when no attempt recorded a duration")
    void noUsableResponseTimes() {
        assertNull(service.extract(List.of(attempt(true, null))).averageResponseTime());
    }
}
