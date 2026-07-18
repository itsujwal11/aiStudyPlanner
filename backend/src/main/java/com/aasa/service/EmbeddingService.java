package com.aasa.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

@Service
public class EmbeddingService {

    private static final Logger logger = Logger.getLogger(EmbeddingService.class.getName());

    private static final String EMBEDDING_MODEL = "gemini-embedding-2";
    private static final String EMBEDDING_ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/"
                    + EMBEDDING_MODEL + ":batchEmbedContents";
    private static final int EMBEDDING_DIMENSION = 768;
    private static final int TIMEOUT_SECONDS = 90;
    private static final int MAX_ATTEMPTS = 3;
    private static final int BATCH_SIZE = 20;
    private static final int MAX_TEXT_CHARS = 24_000;

    @Value("${gemini.api.key}")
    private String apiKey;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    /**
     * Embeds a user's question for asymmetric question-answering retrieval.
     */
    public float[] generateEmbedding(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }

        try {
            List<float[]> embeddings = requestBatch(List.of(formatQuery(text)));
            return embeddings.size() == 1 ? embeddings.get(0) : null;
        } catch (Exception e) {
            logger.warning("Query embedding failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * Embeds document chunks in small batches so one PDF does not make one
     * network request per chunk.
     */
    public List<float[]> generateEmbeddings(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }

        List<float[]> result = new ArrayList<>(
                Collections.nCopies(texts.size(), null)
        );

        for (int batchStart = 0; batchStart < texts.size(); batchStart += BATCH_SIZE) {
            int batchEnd = Math.min(batchStart + BATCH_SIZE, texts.size());
            List<String> preparedTexts = new ArrayList<>();
            List<Integer> originalIndexes = new ArrayList<>();

            for (int i = batchStart; i < batchEnd; i++) {
                String text = texts.get(i);
                if (text != null && !text.isBlank()) {
                    preparedTexts.add(formatDocument(text));
                    originalIndexes.add(i);
                }
            }

            if (preparedTexts.isEmpty()) {
                continue;
            }

            try {
                List<float[]> batchEmbeddings = requestBatch(preparedTexts);
                if (batchEmbeddings.size() != preparedTexts.size()) {
                    throw new IllegalStateException(
                            "Embedding API returned " + batchEmbeddings.size()
                                    + " vectors for " + preparedTexts.size() + " inputs"
                    );
                }

                for (int i = 0; i < batchEmbeddings.size(); i++) {
                    result.set(originalIndexes.get(i), batchEmbeddings.get(i));
                }
            } catch (Exception e) {
                logger.warning(
                        "Document embedding batch " + batchStart + "-" + (batchEnd - 1)
                                + " failed: " + e.getMessage()
                );
                // A bad key/model or sustained rate limit will affect later
                // batches too. Stop here instead of spending more quota on an
                // index that cannot be committed completely.
                return result;
            }
        }

        long generated = result.stream().filter(java.util.Objects::nonNull).count();
        logger.info("Generated " + generated + " of " + texts.size() + " document embeddings");
        return result;
    }

    private List<float[]> requestBatch(List<String> preparedTexts) throws Exception {
        validateApiKey();

        var root = objectMapper.createObjectNode();
        var requests = root.putArray("requests");

        for (String preparedText : preparedTexts) {
            var request = requests.addObject();
            request.put("model", "models/" + EMBEDDING_MODEL);
            request.put("outputDimensionality", EMBEDDING_DIMENSION);
            var content = request.putObject("content");
            var parts = content.putArray("parts");
            parts.addObject().put("text", preparedText);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(EMBEDDING_ENDPOINT))
                .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(
                        objectMapper.writeValueAsString(root)
                ))
                .build();

        Exception lastError = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                HttpResponse<String> response = httpClient.send(
                        request,
                        HttpResponse.BodyHandlers.ofString()
                );
                int status = response.statusCode();

                if (status == 200) {
                    return parseEmbeddingResponse(response.body(), preparedTexts.size());
                }

                String apiMessage = parseErrorBody(response.body());
                if ((status == 429 || status == 503) && attempt < MAX_ATTEMPTS) {
                    long waitMs = retryDelayMillis(response, attempt);
                    logger.warning(
                            "Embedding API HTTP " + status + ", retry "
                                    + attempt + "/" + MAX_ATTEMPTS
                                    + " after " + waitMs + "ms"
                    );
                    Thread.sleep(waitMs);
                    continue;
                }

                throw new IllegalStateException(
                        "Embedding API returned HTTP " + status + ": " + apiMessage
                );
            } catch (IOException e) {
                lastError = e;
                if (attempt >= MAX_ATTEMPTS) {
                    break;
                }
                long waitMs = (long) Math.pow(2, attempt) * 1_000L;
                logger.warning(
                        "Embedding network error, retry " + attempt + "/"
                                + MAX_ATTEMPTS + " after " + waitMs + "ms"
                );
                Thread.sleep(waitMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Embedding request was interrupted", e);
            }
        }

        throw new IllegalStateException(
                "Embedding request failed after " + MAX_ATTEMPTS + " attempts",
                lastError
        );
    }

    private List<float[]> parseEmbeddingResponse(String responseBody, int expectedCount)
            throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode embeddings = root.path("embeddings");

        if (!embeddings.isArray() || embeddings.size() != expectedCount) {
            throw new IllegalStateException(
                    "Unexpected embedding response count: " + embeddings.size()
                            + " (expected " + expectedCount + ")"
            );
        }

        List<float[]> parsed = new ArrayList<>(expectedCount);
        for (JsonNode embedding : embeddings) {
            JsonNode values = embedding.path("values");
            if (!values.isArray() || values.size() != EMBEDDING_DIMENSION) {
                throw new IllegalStateException(
                        "Unexpected embedding dimension: " + values.size()
                                + " (expected " + EMBEDDING_DIMENSION + ")"
                );
            }

            float[] vector = new float[EMBEDDING_DIMENSION];
            double magnitudeSquared = 0.0;
            for (int i = 0; i < values.size(); i++) {
                float value = (float) values.get(i).asDouble();
                if (!Float.isFinite(value)) {
                    throw new IllegalStateException("Embedding contains a non-finite value");
                }
                vector[i] = value;
                magnitudeSquared += (double) value * value;
            }

            if (magnitudeSquared == 0.0) {
                throw new IllegalStateException("Embedding API returned a zero vector");
            }
            parsed.add(vector);
        }
        return parsed;
    }

    private String formatQuery(String text) {
        return truncate("task: question answering | query: " + text.trim());
    }

    private String formatDocument(String text) {
        return truncate("title: none | text: " + text.trim());
    }

    private String truncate(String text) {
        return text.length() <= MAX_TEXT_CHARS
                ? text
                : text.substring(0, MAX_TEXT_CHARS);
    }

    private void validateApiKey() {
        if (apiKey == null || apiKey.isBlank() || apiKey.contains("YOUR_")) {
            throw new IllegalStateException(
                    "Gemini API key is not configured for embeddings"
            );
        }
    }

    private long retryDelayMillis(HttpResponse<?> response, int attempt) {
        return response.headers()
                .firstValue("retry-after")
                .map(value -> {
                    try {
                        return (long) (Double.parseDouble(value) * 1_000L);
                    } catch (NumberFormatException ignored) {
                        return 0L;
                    }
                })
                .filter(value -> value > 0)
                .orElse((long) Math.pow(2, attempt) * 1_000L);
    }

    private String parseErrorBody(String body) {
        try {
            String message = objectMapper.readTree(body)
                    .path("error")
                    .path("message")
                    .asText("");
            return message.isBlank() ? "Unknown API error" : message;
        } catch (Exception ignored) {
            return "Unknown API error";
        }
    }
}
