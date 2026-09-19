package com.aasa.service;

import org.springframework.http.HttpStatus;

/**
 * A Gemini call that failed for a reason the student should actually be told about.
 *
 * <p><b>Why this exists.</b> Every upstream failure used to collapse into one
 * string — "the AI answer service is temporarily unavailable" — returned as a
 * normal answer with HTTP 200. A daily quota that had run out, an API key that
 * was never configured and a dropped network connection all looked identical,
 * both to the student and in the browser's network tab. This carries the
 * distinction out of the service layer so the controller can pick a status code
 * and the UI can show the real reason.</p>
 *
 * <p>{@link #getUserMessage()} is written for a student reading a red banner, so
 * it says what happened and what to do next. {@link #getMessage()} keeps the raw
 * upstream detail for the log.</p>
 */
public class AiServiceException extends RuntimeException {

    private final HttpStatus status;
    private final String userMessage;

    public AiServiceException(HttpStatus status, String userMessage, String technicalDetail) {
        super(technicalDetail);
        this.status = status;
        this.userMessage = userMessage;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getUserMessage() {
        return userMessage;
    }

    /**
     * Maps an upstream Gemini HTTP status onto what the student is told.
     *
     * @param status the HTTP status Gemini returned
     * @param detail the {@code error.message} Gemini sent, already parsed
     */
    /** Keeps an upstream message short enough for a banner, without hiding it. */
    private static String safeDetail(String detail) {
        if (detail == null || detail.isBlank()) {
            return "no further detail was returned.";
        }
        return detail.length() > 200 ? detail.substring(0, 200) + "..." : detail;
    }

    public static AiServiceException fromGeminiStatus(int status, String detail) {
        String lower = detail == null ? "" : detail.toLowerCase();

        // Gemini reports an unusable API key as 400 INVALID_ARGUMENT, not 401, so
        // the status alone would send the student chasing the wrong problem.
        if (lower.contains("api key not valid") || lower.contains("api_key_invalid")
                || lower.contains("api key expired")) {
            return new AiServiceException(
                    HttpStatus.BAD_GATEWAY,
                    "The AI service rejected this application's API key. Please check that a "
                            + "valid GEMINI_API_KEY is configured on the server.",
                    "Gemini API key rejected (HTTP " + status + "): " + detail);
        }

        // A daily-quota exhaustion is also reported as RESOURCE_EXHAUSTED text.
        if (lower.contains("quota") || lower.contains("resource_exhausted")
                || lower.contains("rate limit")) {
            return new AiServiceException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "The AI service has hit its usage limit. The free Gemini tier allows a "
                            + "limited number of requests per minute and per day. Please wait a "
                            + "minute and try again.",
                    "Gemini quota exceeded (HTTP " + status + "): " + detail);
        }

        switch (status) {
            case 429:
                return new AiServiceException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "The AI service has hit its rate limit for now. The free Gemini tier "
                                + "allows a limited number of requests per minute and per day. "
                                + "Please wait about a minute and ask again.",
                        "Gemini quota exceeded (HTTP 429): " + detail);
            case 401:
            case 403:
                return new AiServiceException(
                        HttpStatus.BAD_GATEWAY,
                        "The AI service rejected this application's API key. The key is missing, "
                                + "expired, or not authorised for this model.",
                        "Gemini auth failure (HTTP " + status + "): " + detail);
            case 400:
                return new AiServiceException(
                        HttpStatus.BAD_GATEWAY,
                        "The AI service rejected the request: " + safeDetail(detail),
                        "Gemini rejected the request (HTTP 400): " + detail);
            case 404:
                return new AiServiceException(
                        HttpStatus.BAD_GATEWAY,
                        "The configured AI model is not available. Please contact support.",
                        "Gemini model not found (HTTP 404): " + detail);
            case 500:
            case 502:
            case 503:
            case 504:
                return new AiServiceException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "The AI service is temporarily overloaded. Please try again in a moment.",
                        "Gemini upstream error (HTTP " + status + "): " + detail);
            default:
                return new AiServiceException(
                        HttpStatus.BAD_GATEWAY,
                        "The AI service returned an unexpected error (HTTP " + status + "). "
                                + "Please try again.",
                        "Gemini unexpected status " + status + ": " + detail);
        }
    }
}
