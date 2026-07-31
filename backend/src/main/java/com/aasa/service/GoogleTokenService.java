package com.aasa.service;

import com.aasa.config.AuthException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

@Service
public class GoogleTokenService {
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper;

    @Value("${google.client-id:}")
    private String clientId;

    public GoogleTokenService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public GoogleIdentity verify(String credential) {
        if (clientId == null || clientId.isBlank()) {
            throw new AuthException(HttpStatus.SERVICE_UNAVAILABLE, "GOOGLE_NOT_CONFIGURED",
                    "Google Sign-In is not configured");
        }

        try {
            String encoded = URLEncoder.encode(credential, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://oauth2.googleapis.com/tokeninfo?id_token=" + encoded))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw invalidCredential();

            JsonNode claims = objectMapper.readTree(response.body());
            String issuer = claims.path("iss").asText();
            boolean validIssuer = "accounts.google.com".equals(issuer)
                    || "https://accounts.google.com".equals(issuer);
            boolean verifiedEmail = claims.path("email_verified").asBoolean(
                    "true".equalsIgnoreCase(claims.path("email_verified").asText()));
            if (!clientId.equals(claims.path("aud").asText())
                    || !validIssuer
                    || !verifiedEmail
                    || claims.path("exp").asLong(0) <= Instant.now().getEpochSecond()
                    || claims.path("sub").asText().isBlank()
                    || claims.path("email").asText().isBlank()) {
                throw invalidCredential();
            }

            String email = claims.path("email").asText().trim().toLowerCase();
            String name = claims.path("name").asText();
            if (name == null || name.isBlank()) name = email.substring(0, email.indexOf('@'));
            return new GoogleIdentity(claims.path("sub").asText(), email, name);
        } catch (AuthException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AuthException(HttpStatus.BAD_GATEWAY, "GOOGLE_VERIFICATION_FAILED",
                    "Could not verify the Google credential");
        }
    }

    private AuthException invalidCredential() {
        return new AuthException(HttpStatus.UNAUTHORIZED, "INVALID_GOOGLE_CREDENTIAL",
                "Invalid Google credential");
    }

    public record GoogleIdentity(String subject, String email, String name) {}
}
