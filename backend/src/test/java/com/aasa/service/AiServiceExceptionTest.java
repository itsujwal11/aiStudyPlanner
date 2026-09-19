package com.aasa.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The point of this class is that a student is told which thing went wrong, so
 * these tests pin the mapping from an upstream Gemini failure to the message and
 * status the browser receives.
 */
class AiServiceExceptionTest {

    @Test
    void rateLimitIsReportedAsTooManyRequestsAndSaysToWait() {
        AiServiceException ex = AiServiceException.fromGeminiStatus(
                429, "Resource has been exhausted (e.g. check quota).");

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getStatus());
        String message = ex.getUserMessage().toLowerCase();
        assertTrue(message.contains("limit"), "should name the limit: " + message);
        assertTrue(message.contains("wait") || message.contains("try again"),
                "should tell the student what to do: " + message);
    }

    @Test
    void dailyQuotaTextIsTreatedAsRateLimitEvenWhenTheStatusIsNot429() {
        AiServiceException ex = AiServiceException.fromGeminiStatus(
                400, "You exceeded your current quota, please check your plan and billing details.");

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getStatus());
    }

    @Test
    void invalidApiKeyIsNotMistakenForAContextLengthProblem() {
        // Gemini reports a bad key as 400, which the status alone would classify
        // as a malformed request.
        AiServiceException ex = AiServiceException.fromGeminiStatus(
                400, "API key not valid. Please pass a valid API key.");

        assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatus());
        assertTrue(ex.getUserMessage().contains("API key"),
                "should name the API key: " + ex.getUserMessage());
    }

    @Test
    void upstreamOutageIsRetryableServiceUnavailable() {
        AiServiceException ex = AiServiceException.fromGeminiStatus(503, "The model is overloaded.");

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatus());
        assertTrue(ex.getUserMessage().toLowerCase().contains("try again"));
    }

    @Test
    void technicalDetailIsKeptForTheLogAndNotShownToTheStudent() {
        AiServiceException ex = AiServiceException.fromGeminiStatus(
                429, "Resource has been exhausted (e.g. check quota).");

        assertTrue(ex.getMessage().contains("429"), "log detail keeps the status");
        assertTrue(ex.getMessage().contains("Resource has been exhausted"),
                "log detail keeps the upstream text");
        assertTrue(!ex.getUserMessage().contains("429"),
                "the student message stays free of raw status codes");
    }

    @Test
    void anUnrecognisedStatusStillProducesAMessage() {
        AiServiceException ex = AiServiceException.fromGeminiStatus(418, "");

        assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatus());
        assertTrue(!ex.getUserMessage().isBlank());
    }
}
