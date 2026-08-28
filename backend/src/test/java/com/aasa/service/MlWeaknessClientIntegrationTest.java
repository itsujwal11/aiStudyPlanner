package com.aasa.service;

import com.aasa.entity.Quiz;
import com.aasa.entity.QuizAttempt;
import com.aasa.service.LearnerFeatureService.LearnerFeatures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration proof that the Java backend really drives the trained Random
 * Forest, end to end: attempt history -> feature vector -> HTTP -> sklearn
 * pipeline -> blended weakness.
 *
 * OPT-IN: requires only the model service — no database, so the client is built
 * directly rather than through a Spring context.
 *   cd ml && python train_model.py                (once, generates the joblib)
 *   uvicorn serve:app --port 8000
 *   set ML_INTEGRATION_TEST=true                  (then run `mvn test`)
 *
 * Proves the properties behind the hybrid claim:
 *  1. the service answers with a usable weakness in [0,1],
 *  2. the model separates a struggling learner from a strong one,
 *  3. batch and single-call paths agree,
 *  4. the blend moves the evidence score by exactly the documented weights,
 *  5. an unreachable service degrades to evidence-only instead of throwing.
 */
@EnabledIfEnvironmentVariable(named = "ML_INTEGRATION_TEST", matches = "true")
class MlWeaknessClientIntegrationTest {

    private static final String URL =
            System.getenv().getOrDefault("ML_WEAKNESS_URL", "http://127.0.0.1:8000");

    private final LearnerFeatureService learnerFeatureService = new LearnerFeatureService();
    private final WeaknessEngineService weaknessEngineService = new WeaknessEngineService();
    private MlWeaknessClient client;

    private MlWeaknessClient clientFor(String url, boolean enabled) {
        MlWeaknessClient c = new MlWeaknessClient();
        ReflectionTestUtils.setField(c, "enabled", enabled);
        ReflectionTestUtils.setField(c, "baseUrl", url);
        ReflectionTestUtils.setField(c, "timeoutMillis", 5000L);
        return c;
    }

    @BeforeEach
    void setUp() {
        client = clientFor(URL, true);
    }

    private List<QuizAttempt> attempts(int count, boolean correct, long seconds) {
        // A Quiz with a difficulty is required: the evidence formula weights
        // each attempt by its question difficulty.
        Quiz quiz = Quiz.builder().difficulty(Quiz.DifficultyLevel.MEDIUM).build();
        List<QuizAttempt> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(QuizAttempt.builder()
                    .quiz(quiz)
                    .isCorrect(correct)
                    .timeTakenSeconds(seconds)
                    .build());
        }
        return list;
    }

    @Test
    @DisplayName("service is reachable and reports a loaded model")
    void modelIsAvailable() {
        assertTrue(client.isModelAvailable(),
                "Start `uvicorn serve:app --port 8000` with the joblib present");
    }

    @Test
    @DisplayName("a struggling learner scores weaker than a strong one")
    void modelSeparatesLearners() {
        double struggling = client
                .predictWeakness(learnerFeatureService.extract(attempts(6, false, 55L)))
                .orElseThrow();
        double strong = client
                .predictWeakness(learnerFeatureService.extract(attempts(6, true, 6L)))
                .orElseThrow();

        assertTrue(struggling >= 0.0 && struggling <= 1.0, "weakness out of range: " + struggling);
        assertTrue(strong >= 0.0 && strong <= 1.0, "weakness out of range: " + strong);
        assertTrue(struggling > strong,
                "model should rate the failing learner weaker: " + struggling + " vs " + strong);
    }

    @Test
    @DisplayName("batch predictions match one-at-a-time predictions")
    void batchMatchesSingle() {
        LearnerFeatures a = learnerFeatureService.extract(attempts(4, false, 30L));
        LearnerFeatures b = learnerFeatureService.extract(attempts(4, true, 8L));

        List<Double> batch = client.predictWeakness(List.of(a, b));

        assertEquals(2, batch.size());
        assertEquals(client.predictWeakness(a).orElseThrow(), batch.get(0), 1e-9);
        assertEquals(client.predictWeakness(b).orElseThrow(), batch.get(1), 1e-9);
    }

    @Test
    @DisplayName("hybrid blend shifts the evidence score toward the model")
    void blendMovesEvidenceScore() {
        List<QuizAttempt> history = attempts(6, true, 6L);

        WeaknessEngineService.WeaknessResult evidence =
                weaknessEngineService.calculateEvidenceBasedWeakness(history, 0.9, null);
        double model = client
                .predictWeakness(learnerFeatureService.extract(history))
                .orElseThrow();
        WeaknessEngineService.WeaknessResult hybrid =
                weaknessEngineService.blendWithModel(evidence, model);

        assertEquals(0.70 * evidence.score() + 0.30 * model, hybrid.score(), 1e-9);
    }

    @Test
    @DisplayName("an unreachable service degrades to evidence-only, never throws")
    void unreachableServiceFallsBack() {
        // Port 9 (discard) is reserved and refuses connections quickly.
        MlWeaknessClient dead = clientFor("http://127.0.0.1:9", true);
        LearnerFeatures features = learnerFeatureService.extract(attempts(3, false, 20L));

        assertTrue(dead.predictWeakness(features).isEmpty());
        assertFalse(dead.isModelAvailable());

        WeaknessEngineService.WeaknessResult evidence =
                weaknessEngineService.calculateEvidenceBasedWeakness(
                        attempts(3, false, 20L), 0.2, null);
        // Null model score -> evidence result passes through unchanged.
        assertEquals(evidence.score(),
                weaknessEngineService.blendWithModel(evidence, null).score(), 1e-9);
    }

    @Test
    @DisplayName("disabling by config skips the service entirely")
    void disabledClientSkipsService() {
        MlWeaknessClient off = clientFor(URL, false);

        assertTrue(off.predictWeakness(
                learnerFeatureService.extract(attempts(3, true, 10L))).isEmpty());
        assertFalse(off.isModelAvailable());
    }
}
