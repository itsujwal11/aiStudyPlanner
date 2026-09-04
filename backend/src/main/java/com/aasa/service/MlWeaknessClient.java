package com.aasa.service;

import com.aasa.service.LearnerFeatureService.LearnerFeatures;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * Client for the Python weakness-model service ({@code ml/serve.py}).
 *
 * <p><b>Fail-soft by design.</b> Every failure mode — disabled by config, service
 * down, model artifact missing (HTTP 503), timeout, malformed response — returns
 * an empty result rather than throwing. The caller then scores weakness from
 * evidence alone, exactly as the system did before the model was wired in. A
 * quiz submission must never fail because an optional model is unavailable.</p>
 *
 * <p>To avoid stalling every quiz submission while the service is down, repeated
 * failures open a cooldown: after {@link #FAILURES_BEFORE_COOLDOWN} consecutive
 * failures, calls are skipped locally for {@link #COOLDOWN_MILLIS} instead of
 * waiting for a fresh timeout each time. One success resets it.</p>
 */
@Service
public class MlWeaknessClient {

    private static final Logger logger = Logger.getLogger(MlWeaknessClient.class.getName());

    private static final int FAILURES_BEFORE_COOLDOWN = 3;
    private static final long COOLDOWN_MILLIS = 60_000L;

    @Value("${ml.weakness.enabled:true}")
    private boolean enabled;

    @Value("${ml.weakness.url:http://localhost:8000}")
    private String baseUrl;

    /** Kept short: this sits on the quiz-submission path. */
    @Value("${ml.weakness.timeout-ms:1500}")
    private long timeoutMillis;

    private final ObjectMapper objectMapper = new ObjectMapper();
    // HTTP/1.1 is pinned deliberately: the JDK client otherwise attempts an
    // h2c upgrade that uvicorn's parser rejects ("Invalid HTTP request
    // received"), so every prediction would silently fall back to evidence-only.
    // EmbeddingService pins the same version for the same reason.
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    private final AtomicLong consecutiveFailures = new AtomicLong();
    private final AtomicLong cooldownUntil = new AtomicLong();

    /**
     * Model weakness ({@code 1 - P(correct)}) for a single learner/topic pair.
     *
     * @return the weakness in [0,1], or empty when the model is unavailable
     */
    public Optional<Double> predictWeakness(LearnerFeatures features) {
        if (features == null) {
            return Optional.empty();
        }
        List<Double> scores = predictWeakness(List.of(features));
        return scores.isEmpty() ? Optional.empty() : Optional.ofNullable(scores.get(0));
    }

    /**
     * Batch variant — one request for many topics, used when priorities are
     * recalculated across a whole study set.
     *
     * @return one weakness per input in order, or an empty list when unavailable
     */
    public List<Double> predictWeakness(List<LearnerFeatures> features) {
        if (!enabled || features == null || features.isEmpty() || inCooldown()) {
            return List.of();
        }

        try {
            ObjectNode root = objectMapper.createObjectNode();
            var instances = root.putArray("instances");
            for (LearnerFeatures f : features) {
                ObjectNode instance = instances.addObject();
                instance.put("previous_attempts", f.previousAttempts());
                instance.put("previous_accuracy", f.previousAccuracy());
                if (f.averageResponseTime() == null) {
                    // Explicit null -> the pipeline's median imputer fills it.
                    instance.putNull("average_response_time");
                } else {
                    instance.put("average_response_time", f.averageResponseTime());
                }
                instance.put("recent_accuracy", f.recentAccuracy());
                instance.put("opportunity", f.opportunity());
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(trimmedBaseUrl() + "/predict"))
                    .timeout(Duration.ofMillis(timeoutMillis))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(root)))
                    .build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                // 503 is the service's documented "model not loaded" signal.
                return recordFailure("weakness model service returned HTTP "
                        + response.statusCode());
            }

            JsonNode predictions = objectMapper.readTree(response.body()).path("predictions");
            if (!predictions.isArray() || predictions.size() != features.size()) {
                return recordFailure("weakness model returned "
                        + predictions.size() + " predictions for "
                        + features.size() + " inputs");
            }

            List<Double> weaknesses = new ArrayList<>(predictions.size());
            for (JsonNode prediction : predictions) {
                JsonNode weakness = prediction.path("weakness");
                if (!weakness.isNumber()) {
                    return recordFailure("weakness model returned a non-numeric score");
                }
                weaknesses.add(clamp01(weakness.asDouble()));
            }

            consecutiveFailures.set(0);
            return weaknesses;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return recordFailure("weakness model request was interrupted");
        } catch (Exception e) {
            return recordFailure("weakness model request failed: " + e.getMessage());
        }
    }

    /** Whether the service is reachable and holding a model — for {@code /api/health}. */
    public boolean isModelAvailable() {
        if (!enabled) {
            return false;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(trimmedBaseUrl() + "/health"))
                    .timeout(Duration.ofMillis(timeoutMillis))
                    .GET()
                    .build();
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200
                    && objectMapper.readTree(response.body()).path("modelLoaded").asBoolean(false);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    private boolean inCooldown() {
        return System.currentTimeMillis() < cooldownUntil.get();
    }

    private List<Double> recordFailure(String message) {
        long failures = consecutiveFailures.incrementAndGet();
        if (failures == FAILURES_BEFORE_COOLDOWN) {
            cooldownUntil.set(System.currentTimeMillis() + COOLDOWN_MILLIS);
            logger.warning(message + " - pausing model calls for "
                    + (COOLDOWN_MILLIS / 1000) + "s; weakness falls back to evidence only");
        } else if (failures < FAILURES_BEFORE_COOLDOWN) {
            logger.warning(message + " - falling back to evidence-only weakness");
        }
        return List.of();
    }

    private String trimmedBaseUrl() {
        String url = baseUrl == null ? "" : baseUrl.trim();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
