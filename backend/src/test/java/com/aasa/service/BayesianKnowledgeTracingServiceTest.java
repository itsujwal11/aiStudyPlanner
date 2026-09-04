package com.aasa.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the Bayesian Knowledge Tracing core of the adaptive algorithm.
 */
class BayesianKnowledgeTracingServiceTest {

    private final BayesianKnowledgeTracingService service = new BayesianKnowledgeTracingService();

    @Test
    void correctAnswerIncreasesMastery() {
        double before = service.updateMastery(0.2, true);
        assertTrue(before > 0.2, "mastery must rise after a correct answer, was " + before);
    }

    @Test
    void incorrectAnswerDecreasesMastery() {
        double before = 0.8;
        double after = service.updateMastery(before, false);
        assertTrue(after < before, "mastery must fall after an incorrect answer");
        assertTrue(after > 0.0);
    }

    @Test
    void masteryNeverLeavesUnitInterval() {
        assertTrue(service.updateMastery(-0.5, false) >= 0.0 && service.updateMastery(-0.5, false) <= 1.0);
        assertTrue(service.updateMastery(0.0, true) <= 1.0);
        assertTrue(service.updateMastery(1.0, false) >= 0.0);
        assertTrue(service.updateMastery(2.0, true) <= 1.0);
    }

    @Test
    void repeatedCorrectAnswersConvergeTowardsMastery() {
        double p = 0.1;
        for (int i = 0; i < 20; i++) {
            p = service.updateMastery(p, true);
        }
        assertTrue(p > 0.9, "20 correct answers should approach mastery, was " + p);
    }

    @Test
    void forgettingRiskIsZeroWhenReviewedToday() {
        assertEquals(0.0, service.forgettingRisk(0.3, 0), 1e-9);
        assertEquals(0.0, service.forgettingRisk(0.9, -5), 1e-9);
    }

    @Test
    void forgettingRiskGrowsWithTime() {
        double d1 = service.forgettingRisk(0.5, 1);
        double d7 = service.forgettingRisk(0.5, 7);
        double d30 = service.forgettingRisk(0.5, 30);
        assertTrue(d1 > 0 && d7 > d1 && d30 > d7, "risk must grow with elapsed days");
        assertTrue(d30 < 1.0, "risk is asymptotic to 1, never reaching it");
    }

    @Test
    void weakTopicsForgetFasterThanMasteredOnes() {
        double weak = service.forgettingRisk(0.2, 10);
        double strong = service.forgettingRisk(0.95, 10);
        assertTrue(weak > strong, "low mastery must decay faster at the same elapsed time");
    }
}
