package com.aasa.service;

import com.aasa.dto.AiAnalysisResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

@Service
public class GeminiAiService {

    private static final Logger logger = Logger.getLogger(GeminiAiService.class.getName());

    // Gemini endpoint – API key is appended as query parameter
    // IMPORTANT: model ids differ across Gemini accounts/versions. Your current model was returning 404.
    // Update to a model that exists for your API key.
    // Using model provided by user feedback: gemini-2.5-flash
    private static final String GEMINI_API_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=";



    private static final int TIMEOUT_SECONDS = 90;
    private static final int MAX_RETRIES     = 3;   // fewer retries needed
    private static final int MAX_TOPICS      = 20;  // cap total topics from one PDF

    // Free tier limits: 15 RPM, 1M TPM → safe delay between calls
    private static final int DELAY_BETWEEN_CALLS_MS = 5000;

    @Value("${gemini.api.key}")
    private String apiKey;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    /**
     * Analyse the full extracted text in a single Gemini call.
     * Returns a list of topics with quizzes.
     */
    public AiAnalysisResponse analyzeContent(String extractedText) throws Exception {
        if (extractedText == null || extractedText.isBlank()) {
            throw new IllegalArgumentException("Extracted text cannot be empty");
        }
        logger.info("Starting content analysis with Gemini 1.5 Flash");
        logger.info("Text length: " + extractedText.length() + " characters");

        // Build a single prompt that asks for up to 20 topics
        String prompt = buildPrompt(extractedText, MAX_TOPICS);
        String responseJson = null;
        int retries = 0;

        while (retries < MAX_RETRIES && responseJson == null) {
            try {
                responseJson = callGeminiApi(prompt);
            } catch (RateLimitException e) {
                retries++;
                long waitMs = e.retryAfterMs > 0
                        ? e.retryAfterMs + 1000
                        : (long) Math.pow(2, retries) * 5000;
                logger.warning("Rate limited, retry " + retries + "/" + MAX_RETRIES +
                        ", waiting " + waitMs + "ms");
                Thread.sleep(waitMs);
            } catch (ApiException e) {
                logger.severe("Gemini API error: " + e.getMessage());
                break; // non‑retryable error
            }
        }

        if (responseJson != null) {
            logger.info("Gemini raw response length: " + (responseJson == null ? 0 : responseJson.length()));
            if (responseJson != null) {
                logger.info("Gemini raw response preview: " + responseJson.substring(0, Math.min(200, responseJson.length())).replace("\n", " "));
            }

            AiAnalysisResponse parsed = parseResponse(responseJson);
            int size = (parsed.getTopics() == null) ? 0 : parsed.getTopics().size();
            logger.info("Parsed Gemini topics size: " + size);
            if (size == 0) {
                throw new IllegalStateException("Gemini returned empty/invalid analysis JSON (0 topics). Check Gemini response parsing.");
            }
            return parsed;

        } else {
            // Return empty result on failure after retries
            return AiAnalysisResponse.builder().topics(new ArrayList<>()).build();
        }

    }

    /**
     * Call Gemini API and return the raw text of the answer.
     */
    private String callGeminiApi(String prompt) throws Exception {
        if (apiKey == null || apiKey.isBlank() || apiKey.contains("YOUR_")) {
            throw new IllegalStateException(
                    "Gemini API key not configured. Set gemini.api.key in application.properties");
        }

        // Build request body
        var root = objectMapper.createObjectNode();
        var contents = root.putArray("contents");
        var content = contents.addObject();
        var parts = content.putArray("parts");
        parts.addObject().put("text", prompt);

        // Force JSON output – guaranteed valid JSON
        var genConfig = root.putObject("generationConfig");
        genConfig.put("temperature", 0.2);
        genConfig.put("responseMimeType", "application/json");

        String requestBody = objectMapper.writeValueAsString(root);

        String fullUrl = GEMINI_API_URL + apiKey;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(fullUrl))
                .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        logger.fine("Sending request to Gemini…");
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        int status = response.statusCode();

        if (status == 200) {
            return extractTextFromResponse(response.body());
        }

        String friendlyError = parseErrorBody(response.body(), status);
        logger.severe("Gemini API error [" + status + "]: " + friendlyError);

        if (status == 429 || status == 503) {
            // Try to read Retry-After header (seconds)
            long retryAfterMs = response.headers()
                    .firstValue("retry-after")
                    .map(val -> {
                        try { return (long) (Double.parseDouble(val) * 1000); }
                        catch (NumberFormatException e) { return 0L; }
                    })
                    .orElse(0L);
            throw new RateLimitException(friendlyError, retryAfterMs);
        }
        throw new ApiException("Gemini API error [" + status + "]: " + friendlyError);
    }

    /**
     * Extract generated text from Gemini's JSON response.
     * The response format:
     * { "candidates": [ { "content": { "parts": [ { "text": "..." } ] } } ] }
     */
    private String extractTextFromResponse(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);

        // Check for top-level error
        if (root.has("error")) {
            String msg = root.path("error").path("message").asText("Unknown error");
            throw new ApiException("Gemini returned error: " + msg);
        }

        JsonNode candidates = root.path("candidates");
        if (candidates.isEmpty()) {
            throw new ApiException("Gemini returned no candidates (possibly blocked content)");
        }

        String text = candidates.get(0)
                .path("content")
                .path("parts")
                .get(0)
                .path("text")
                .asText();

        if (text == null || text.isBlank()) {
            throw new ApiException("Empty text in Gemini response");
        }
        return text;
    }

    /**
     * Parse the JSON array we requested. Keep the existing robust extraction
     * just in case the API still wraps it in markdown fences (very rare).
     */
    private AiAnalysisResponse parseResponse(String jsonResponse) {
        try {
            String cleanJson = extractJsonArray(jsonResponse);
            if (cleanJson == null || cleanJson.trim().isEmpty() || cleanJson.equals("[]")) {
                logger.warning("Empty or unparseable JSON from Gemini: " + jsonResponse);
                return AiAnalysisResponse.builder().topics(new ArrayList<>()).build();
            }

            AiAnalysisResponse.TopicAnalysis[] topics =
                    objectMapper.readValue(cleanJson, AiAnalysisResponse.TopicAnalysis[].class);

            List<AiAnalysisResponse.TopicAnalysis> topicList = java.util.Arrays.asList(topics);

            // Cap at MAX_TOPICS just in case
            if (topicList.size() > MAX_TOPICS) {
                topicList = topicList.subList(0, MAX_TOPICS);
            }

            logger.info("Gemini returned " + topicList.size() + " topics");
            return AiAnalysisResponse.builder().topics(topicList).build();

        } catch (Exception e) {
            logger.warning("Failed to parse Gemini response: " + e.getMessage());
            return AiAnalysisResponse.builder().topics(new ArrayList<>()).build();
        }
    }

    // --- JSON cleanup (unchanged) ---
    private String extractJsonArray(String response) {
        if (response == null) return "[]";
        String s = response.trim();
        if (s.startsWith("```")) {
            int firstNewline = s.indexOf('\n');
            if (firstNewline != -1) s = s.substring(firstNewline + 1).trim();
            if (s.endsWith("```")) s = s.substring(0, s.lastIndexOf("```")).trim();
        }
        int start = s.indexOf('[');
        int end = s.lastIndexOf(']');
        if (start != -1 && end != -1 && start < end) {
            return s.substring(start, end + 1);
        }
        return "[]";
    }

    // --- Error handling ---
    private String parseErrorBody(String body, int status) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode error = root.path("error");
            if (!error.isMissingNode()) {
                String msg = error.path("message").asText("");
                if (!msg.isEmpty()) return msg;
            }
        } catch (Exception ignored) {}
        return "HTTP " + status;
    }

    // --- Prompt builder (adapted for whole-text) ---
    private String buildPrompt(String fullText, int maxTopics) {
        // Gemini can handle huge prompts, but we keep a reasonable cap
        // to avoid token overflow; 30000 chars ≈ 8000 tokens – plenty safe.
        String text = fullText.length() > 30000
                ? fullText.substring(0, 30000)
                : fullText;

        return "Analyze the following study material. Extract up to " + maxTopics
                + " distinct topics. For each topic, generate exactly 3 multiple-choice questions "
                + "(difficulty: easy, medium, hard) based ONLY on the provided text. "
                + "Return ONLY a valid JSON array following this exact structure:\n\n"
                + "[{\n"
                + "  \"title\": \"Topic Name\",\n"
                + "  \"description\": \"2-3 sentence summary\",\n"
                + "  \"signals\": {\n"
                + "    \"conceptDensity\": <number 1-10>,\n"
                + "    \"keywordDifficulty\": <number 1-10>,\n"
                + "    \"formulaCount\": <integer>,\n"
                + "    \"length\": <integer>\n"
                + "  },\n"
                + "  \"importance\": <number 0.0-1.0>,\n"
                + "  \"complexity\": <number 0.0-1.0>,\n"
                + "  \"quiz\": [\n"
                + "    {\n"
                + "      \"question\": \"...\",\n"
                + "      \"options\": [\"A\", \"B\", \"C\", \"D\"],\n"
                + "      \"answer\": \"Exactly one of the options\",\n"
                + "      \"difficulty\": \"easy\",\n"
                + "      \"explanation\": \"Why this answer is correct\"\n"
                + "    },\n"
                + "    { ... },\n"
                + "    { ... }\n"
                + "  ]\n"
                + "}]\n\n"
                + "Material:\n" + text;
    }

    // --- Custom exceptions (unchanged) ---
    private static class RateLimitException extends Exception {
        final long retryAfterMs;
        RateLimitException(String msg, long retryAfterMs) {
            super(msg);
            this.retryAfterMs = retryAfterMs;
        }
    }

    private static class ApiException extends Exception {
        ApiException(String msg) { super(msg); }
    }
}