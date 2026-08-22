package com.aasa.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the adaptive study-priority algorithm:
 * priority = 0.40*(1-mastery) + 0.25*forgettingRisk + 0.20*examUrgency + 0.15*importance.
 */
class AdaptivePriorityServiceTest {

    private final AdaptivePriorityService service =
            new AdaptivePriorityService(new BayesianKnowledgeTracingService());

    private static final double EPS = 1e-6;

    @Test
    void lowMasteryBeatsHighMasteryAtSameConditions() {
        double weak = service.calculatePriority(0.1, 0.5, null, LocalDate.now());
        double strong = service.calculatePriority(0.9, 0.5, null, LocalDate.now());
        assertTrue(weak > strong, "weaker mastery must yield higher priority");
    }

    @Test
    void overdueRevisionRaisesPriority() {
        double fresh = service.calculatePriority(0.5, 0.5, null, LocalDate.now());
        double overdue = service.calculatePriority(0.5, 0.5, null, LocalDate.now().minusDays(14));
        assertTrue(overdue > fresh, "forgetting risk from an old revision date must raise priority");
    }

    @Test
    void closerExamRaisesPriority() {
        double far = service.calculatePriority(0.5, 0.5, LocalDate.now().plusDays(60), null);
        double near = service.calculatePriority(0.5, 0.5, LocalDate.now().plusDays(2), null);
        assertTrue(near > far, "nearer exam must raise priority");
    }

    @Test
    void examDayUrgencyIsOne() {
        assertEquals(1.0, service.examUrgency(LocalDate.now()), EPS);
        assertEquals(0.5, service.examUrgency(LocalDate.now().plusDays(1)), EPS);
        assertEquals(0.5, service.examUrgency(null), "unknown exam date is neutral");
    }

    @Test
    void weightsMatchTheDefinedFormula() {
        // mastery=0, no forgetting (reviewed today), exam in 9 days (urgency=0.1), importance=0.5
        double priority = service.calculatePriority(0.0, 0.5, LocalDate.now().plusDays(9), LocalDate.now());
        double expected = 0.40 * 1.0 + 0.25 * 0.0 + 0.20 * 0.1 + 0.15 * 0.5;
        assertEquals(expected, priority, 1e-4);
    }

    @Test
    void priorityStaysWithinUnitInterval() {
        double max = service.calculatePriority(0.0, 1.0, LocalDate.now(), LocalDate.now().minusDays(3650));
        double min = service.calculatePriority(1.0, 0.0, LocalDate.now().plusDays(3650), LocalDate.now());
        assertTrue(max <= 1.0 && max > 0.9);
        assertTrue(min >= 0.0 && min < 0.2);
    }

    @Test
    void nullImportanceDefaultsToNeutral() {
        double withNull = service.calculatePriority(0.5, null, null, null);
        double withNeutral = service.calculatePriority(0.5, 0.5, null, null);
        assertEquals(withNeutral, withNull, EPS);
    }

    @Test
    void manualWeaknessMapsToMasteryEstimate() {
        double fromWeakness = service.calculatePriorityFromWeakness(0.8, 0.5, null, LocalDate.now());
        double fromMastery = service.calculatePriority(0.2, 0.5, null, LocalDate.now());
        assertEquals(fromMastery, fromWeakness, EPS, "weakness 0.8 == mastery 0.2");
    }
}
