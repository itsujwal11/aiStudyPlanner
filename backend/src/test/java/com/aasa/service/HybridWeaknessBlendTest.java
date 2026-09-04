package com.aasa.service;

import com.aasa.entity.StudyProgress;
import com.aasa.service.WeaknessEngineService.WeaknessResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Covers the hybrid weakness formula
 * {@code 0.70 * evidence + 0.30 * model} and, most importantly, the fallback
 * contract: a missing model must leave scoring exactly as it was.
 */
class HybridWeaknessBlendTest {

    private final WeaknessEngineService service = new WeaknessEngineService();

    @Test
    @DisplayName("blends evidence and model with the documented 70/30 weights")
    void blendsWithDocumentedWeights() {
        WeaknessResult evidence = new WeaknessResult(StudyProgress.WeaknessLevel.HIGH, 0.80);

        WeaknessResult blended = service.blendWithModel(evidence, 0.30);

        // 0.70*0.80 + 0.30*0.30 = 0.56 + 0.09 = 0.65
        assertEquals(0.65, blended.score(), 1e-9);
    }

    @Test
    @DisplayName("a null model score returns the evidence result untouched")
    void nullModelFallsBackToEvidence() {
        WeaknessResult evidence = new WeaknessResult(StudyProgress.WeaknessLevel.MEDIUM, 0.42);

        // Same instance: the fallback path must not recompute or perturb anything.
        assertSame(evidence, service.blendWithModel(evidence, null));
    }

    @Test
    @DisplayName("null evidence stays null")
    void nullEvidence() {
        assertNull(service.blendWithModel(null, 0.5));
    }

    @Test
    @DisplayName("NOT_ATTEMPTED keeps maximum weakness instead of being diluted")
    void neverAttemptedIsNotBlended() {
        WeaknessResult evidence =
                new WeaknessResult(StudyProgress.WeaknessLevel.NOT_ATTEMPTED, 1.0);

        WeaknessResult blended = service.blendWithModel(evidence, 0.2);

        assertSame(evidence, blended);
        assertEquals(1.0, blended.score(), 1e-9);
    }

    @Test
    @DisplayName("INSUFFICIENT_DATA blends the score but keeps its band")
    void insufficientDataKeepsBand() {
        WeaknessResult evidence =
                new WeaknessResult(StudyProgress.WeaknessLevel.INSUFFICIENT_DATA, 0.60);

        WeaknessResult blended = service.blendWithModel(evidence, 0.90);

        assertEquals(StudyProgress.WeaknessLevel.INSUFFICIENT_DATA, blended.level());
        assertEquals(0.69, blended.score(), 1e-9);
    }

    @Test
    @DisplayName("band is recomputed so the label matches the blended score")
    void bandFollowsBlendedScore() {
        // Evidence alone reads HIGH (0.70); a confident model pulls it under the
        // 0.65 threshold, so the label must move to MEDIUM with it.
        WeaknessResult evidence = new WeaknessResult(StudyProgress.WeaknessLevel.HIGH, 0.70);

        WeaknessResult blended = service.blendWithModel(evidence, 0.10);

        assertEquals(0.52, blended.score(), 1e-9);
        assertEquals(StudyProgress.WeaknessLevel.MEDIUM, blended.level());
    }

    @Test
    @DisplayName("model score is clamped into [0,1] before weighting")
    void clampsOutOfRangeModelScore() {
        WeaknessResult evidence = new WeaknessResult(StudyProgress.WeaknessLevel.HIGH, 1.0);

        assertEquals(1.0, service.blendWithModel(evidence, 5.0).score(), 1e-9);
        assertEquals(0.70, service.blendWithModel(evidence, -2.0).score(), 1e-9);
    }

    @Test
    @DisplayName("weights sum to 1 so the blend cannot leave the score range")
    void weightsSumToOne() {
        assertEquals(1.0,
                WeaknessEngineService.HYBRID_EVIDENCE_WEIGHT
                        + WeaknessEngineService.HYBRID_MODEL_WEIGHT,
                1e-9);
    }
}
