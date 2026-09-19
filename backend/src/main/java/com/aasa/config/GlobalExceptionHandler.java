package com.aasa.config;

import com.aasa.service.AiServiceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Last-resort translation of an exception into something the student can read.
 *
 * <p>The {@code error} field is the human-readable message, because that is the
 * field every controller already fills and the one the frontend renders in its
 * red banner. The exception class name goes in {@code exception} for the log and
 * for debugging, instead of being shown to the student as {@code error}.</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = Logger.getLogger(GlobalExceptionHandler.class.getName());

    /**
     * A Gemini failure that reached here without a controller classifying it —
     * quota, auth or upstream outage. Kept out of the generic 500 branch so the
     * student still sees the real reason.
     */
    @ExceptionHandler(AiServiceException.class)
    public ResponseEntity<Map<String, Object>> handleAiService(AiServiceException ex) {
        logger.warning("AI service failure: " + ex.getMessage());
        return new ResponseEntity<>(
                body(ex.getStatus(), ex.getUserMessage(), ex), ex.getStatus());
    }

    /**
     * Spring rejects an oversized multipart before the controller runs, so
     * PdfController's own 100MB check never sees it. Without this the student
     * got a 500 with a stack-trace message instead of the size limit.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleTooLarge(MaxUploadSizeExceededException ex) {
        logger.warning("Upload rejected as too large: " + ex.getMessage());
        return new ResponseEntity<>(
                body(HttpStatus.PAYLOAD_TOO_LARGE,
                        "That file is too large. Please upload a PDF smaller than 100MB.", ex),
                HttpStatus.PAYLOAD_TOO_LARGE);
    }

    /** An unknown URL is a 404, not a server fault. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(NoResourceFoundException ex) {
        return new ResponseEntity<>(
                body(HttpStatus.NOT_FOUND, "Not found: " + ex.getResourcePath(), ex),
                HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleAllExceptions(Exception ex) {
        logger.severe("Unhandled exception [" + ex.getClass().getSimpleName() + "]: " + ex.getMessage());
        ex.printStackTrace();

        String detail = ex.getMessage() != null && !ex.getMessage().isBlank()
                ? ex.getMessage()
                : ex.getClass().getSimpleName();

        return new ResponseEntity<>(
                body(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Something went wrong on the server: " + detail, ex),
                HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private Map<String, Object> body(HttpStatus status, String userMessage, Exception ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", status.value());
        body.put("error", userMessage);
        body.put("message", userMessage);
        body.put("exception", ex.getClass().getSimpleName());
        return body;
    }
}
