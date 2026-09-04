package com.aasa.service;

import com.aasa.entity.StudyProgress;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locks the SM-2 scheduler's contract.
 *
 * SM-2 splits its 0-5 recall scale at 3: {@code q >= 3} means the item was
 * recalled, {@code q < 3} means it was not and the repetition count resets.
 * A regression that maps any *correct* answer below 3 traps careful learners at
 * a permanent one-day interval, so these tests pin the mapping explicitly.
 */
class Sm2SchedulingTest {

    private final MasteryService masteryService = new MasteryService();

    private int quality(boolean correct, int seconds) {
        return (int) ReflectionTestUtils.invokeMethod(
                masteryService, "mapToSm2Quality", correct, seconds);
    }

    private MasteryService.SpacedRepetitionResult applySm2(StudyProgress progress, int quality) {
        Object result = ReflectionTestUtils.invokeMethod(masteryService, "applySm2", progress, quality);
        return new MasteryService.SpacedRepetitionResult(
                0.0,
                (int) ReflectionTestUtils.getField(result, "interval"),
                (double) ReflectionTestUtils.getField(result, "efactor"),
                (int) ReflectionTestUtils.getField(result, "repetitions"));
    }

    private StudyProgress progress(int repetitions, double efactor, Integer interval) {
        return StudyProgress.builder()
                .sm2Repetitions(repetitions)
                .sm2Efactor(efactor)
                .sm2Interval(interval)
                .build();
    }

    // ---------------------------------------------------------------- quality

    @Test
    @DisplayName("every correct answer scores at least 3, so it never resets the schedule")
    void correctNeverBelowThree() {
        for (int seconds : new int[] {0, 4, 5, 14, 15, 30, 120, 600}) {
            assertTrue(quality(true, seconds) >= 3,
                    "correct answer at " + seconds + "s mapped below SM-2's recall threshold");
        }
    }

    @Test
    @DisplayName("an incorrect answer scores below 3 so the schedule does reset")
    void incorrectBelowThree() {
        assertTrue(quality(false, 3) < 3);
        assertTrue(quality(false, 300) < 3);
    }

    @Test
    @DisplayName("faster correct answers score higher")
    void speedOrdersQuality() {
        assertEquals(5, quality(true, 3));
        assertEquals(4, quality(true, 10));
        assertEquals(3, quality(true, 25));
    }

    @Test
    @DisplayName("quality 5 is reachable, so the ease factor can grow")
    void qualityFiveIsReachable() {
        // The SM-2 ease term is exactly 0 at q=4 and negative below it; without
        // a reachable q=5 the ease factor could only ever decay.
        assertEquals(5, quality(true, 0));
    }

    // -------------------------------------------------------------- intervals

    @Test
    @DisplayName("a perfect learner's intervals grow 1 -> 6 -> longer")
    void intervalsGrow() {
        var first = applySm2(progress(0, 2.5, 0), 5);
        assertEquals(1, first.intervalDays);

        var second = applySm2(progress(1, first.efactor, first.intervalDays), 5);
        assertEquals(6, second.intervalDays);

        var third = applySm2(progress(2, second.efactor, second.intervalDays), 5);
        assertTrue(third.intervalDays > 6,
                "third interval should exceed 6 days, was " + third.intervalDays);
    }

    @Test
    @DisplayName("a correct but slow learner still progresses past one day")
    void slowButCorrectProgresses() {
        // The regression this guards: q=2 for a slow correct answer pinned the
        // interval at 1 day and the ease factor at its 1.3 floor forever.
        int q = quality(true, 25);
        var first = applySm2(progress(0, 2.5, 0), q);
        var second = applySm2(progress(first.repetitions, first.efactor, first.intervalDays), q);

        assertEquals(6, second.intervalDays);
        assertTrue(second.efactor > 1.3);
    }

    @Test
    @DisplayName("a wrong answer resets repetitions and the interval")
    void wrongAnswerResets() {
        var result = applySm2(progress(4, 2.5, 30), quality(false, 20));

        assertEquals(0, result.repetitions);
        assertEquals(1, result.intervalDays);
    }

    @Test
    @DisplayName("ease factor never falls below the SM-2 floor of 1.3")
    void easeFactorFloor() {
        StudyProgress p = progress(0, 1.31, 1);
        for (int i = 0; i < 10; i++) {
            var r = applySm2(p, 1);
            p = progress(r.repetitions, r.efactor, r.intervalDays);
        }
        assertTrue(p.getSm2Efactor() >= 1.3, "ease factor fell below 1.3");
    }

    @Test
    @DisplayName("a null stored interval does not blow up an established repetition")
    void nullIntervalIsSurvivable() {
        // Rows predating the SM-2 columns can carry a null interval.
        var result = applySm2(progress(3, 2.5, null), 5);
        assertTrue(result.intervalDays > 0);
    }
}
