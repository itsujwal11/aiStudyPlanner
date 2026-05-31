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

    private static final String[][] GEMINI_ENDPOINTS = {
        {"v1", "gemini-1.5-flash"},
        {"v1", "gemini-2.5-flash"},
        {"v1beta", "gemini-1.5-flash"},
        {"v1beta", "gemini-2.5-flash"},
        {"v1", "gemini-2.0-flash-exp"},
        {"v1beta", "gemini-2.0-flash-exp"},
    };

    private static final int TIMEOUT_SECONDS = 300;
    private static final int MAX_RETRIES     = 3;
    private static final int MAX_TOPICS      = 20;
    private static final int MAX_TEXT_LENGTH = 100_000;

    @Value("${gemini.api.key}")
    private String apiKey;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    public AiAnalysisResponse analyzeContent(String extractedText) throws Exception {
        if (extractedText == null || extractedText.isBlank()) {
            throw new IllegalArgumentException("Extracted text cannot be empty");
        }
        logger.info("Starting content analysis, text length: " + extractedText.length() + " characters");

        String prompt = buildPrompt(extractedText, MAX_TOPICS);
        List<String> errors = new ArrayList<>();

        for (String[] endpoint : GEMINI_ENDPOINTS) {
            String apiVersion = endpoint[0];
            String model = endpoint[1];
            logger.info("Trying Gemini model: " + model + " (API " + apiVersion + ")");
            String url = "https://generativelanguage.googleapis.com/" + apiVersion + "/models/" + model + ":generateContent?key=";

            try {
                String responseJson = callGeminiApi(prompt, url);
                if (responseJson != null) {
                    AiAnalysisResponse parsed = parseResponse(responseJson);
                    if (parsed.getTopics() != null && !parsed.getTopics().isEmpty()) {
                        logger.info("Model " + model + " returned " + parsed.getTopics().size() + " topics");
                        return parsed;
                    }
                }
            } catch (Exception e) {
                String msg = model + " (API " + apiVersion + ") failed: " + e.getMessage();
                logger.warning(msg);
                errors.add(msg);
            }
        }

        throw new IllegalStateException("All Gemini models failed: " + String.join(" | ", errors));
    }

    private String callGeminiApi(String prompt, String baseUrl) throws Exception {
        if (apiKey == null || apiKey.isBlank() || apiKey.contains("YOUR_")) {
            throw new IllegalStateException(
                    "Gemini API key not configured. Set gemini.api.key in application.properties");
        }

        var root = objectMapper.createObjectNode();
        var contents = root.putArray("contents");
        var content = contents.addObject();
        var parts = content.putArray("parts");
        parts.addObject().put("text", prompt);

        var genConfig = root.putObject("generationConfig");
        genConfig.put("temperature", 0.2);
        genConfig.put("responseMimeType", "application/json");

        String requestBody = objectMapper.writeValueAsString(root);
        String fullUrl = baseUrl + apiKey;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(fullUrl))
                .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        int retries = 0;
        while (retries < MAX_RETRIES) {
            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();

                if (status == 200) {
                    return extractTextFromResponse(response.body());
                }

                if (status == 429 || status == 503) {
                    retries++;
                    long waitMs = response.headers()
                            .firstValue("retry-after")
                            .map(val -> {
                                try { return (long) (Double.parseDouble(val) * 1000); }
                                catch (NumberFormatException e) { return 0L; }
                            })
                            .orElse((long) Math.pow(2, retries) * 5000);
                    logger.warning("Rate limited (HTTP " + status + "), retry " + retries + "/" + MAX_RETRIES + ", waiting " + waitMs + "ms");
                    Thread.sleep(waitMs);
                    continue;
                }

                String friendlyError = parseErrorBody(response.body(), status);
                throw new Exception("HTTP " + status + ": " + friendlyError);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw e;
            }
        }

        throw new Exception("Exhausted retries for rate limiting");
    }

    private String extractTextFromResponse(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);

        if (root.has("error")) {
            String msg = root.path("error").path("message").asText("Unknown error");
            throw new Exception("Gemini returned error: " + msg);
        }

        JsonNode candidates = root.path("candidates");
        if (candidates.isEmpty()) {
            throw new Exception("Gemini returned no candidates (possibly blocked content)");
        }

        String text = candidates.get(0)
                .path("content")
                .path("parts")
                .get(0)
                .path("text")
                .asText();

        if (text == null || text.isBlank()) {
            throw new Exception("Empty text in Gemini response");
        }
        return text;
    }

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

    private String buildPrompt(String fullText, int maxTopics) {
        String text = fullText.length() > MAX_TEXT_LENGTH
                ? fullText.substring(0, MAX_TEXT_LENGTH)
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
}