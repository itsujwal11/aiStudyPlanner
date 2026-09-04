package com.aasa.controller;

import com.aasa.config.AuthException;
import com.aasa.dto.AuthRequest;
import com.aasa.dto.AuthResponse;
import com.aasa.dto.EmailRequest;
import com.aasa.dto.GoogleLoginRequest;
import com.aasa.dto.OtpVerificationRequest;
import com.aasa.dto.RegisterRequest;
import com.aasa.dto.ResetPasswordRequest;
import com.aasa.service.AccountAuthService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AccountAuthService accountAuthService;

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        try {
            String email = accountAuthService.register(request);
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(Map.of("message", "Verification code sent", "email", email));
        } catch (Exception e) {
            return authError(e, HttpStatus.BAD_REQUEST);
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthRequest request) {
        try {
            AuthResponse response = accountAuthService.login(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return authError(e, HttpStatus.UNAUTHORIZED);
        }
    }

    @PostMapping("/verify-email")
    public ResponseEntity<?> verifyEmail(@Valid @RequestBody OtpVerificationRequest request) {
        try {
            return ResponseEntity.ok(accountAuthService.verifyEmail(request.getEmail(), request.getCode()));
        } catch (Exception e) {
            return authError(e, HttpStatus.BAD_REQUEST);
        }
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<?> resendVerification(@Valid @RequestBody EmailRequest request) {
        try {
            accountAuthService.resendVerification(request.getEmail());
            return ResponseEntity.ok(Map.of("message", "Verification code sent"));
        } catch (Exception e) {
            return authError(e, HttpStatus.BAD_REQUEST);
        }
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@Valid @RequestBody EmailRequest request) {
        try {
            accountAuthService.requestPasswordReset(request.getEmail());
            return ResponseEntity.ok(Map.of(
                    "message", "If an account exists, a password reset code has been sent"));
        } catch (Exception e) {
            return authError(e, HttpStatus.BAD_REQUEST);
        }
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        try {
            accountAuthService.resetPassword(request);
            return ResponseEntity.ok(Map.of("message", "Password reset successfully"));
        } catch (Exception e) {
            return authError(e, HttpStatus.BAD_REQUEST);
        }
    }

    @PostMapping("/google")
    public ResponseEntity<?> googleLogin(@Valid @RequestBody GoogleLoginRequest request) {
        try {
            return ResponseEntity.ok(accountAuthService.googleLogin(request));
        } catch (Exception e) {
            return authError(e, HttpStatus.UNAUTHORIZED);
        }
    }

    private ResponseEntity<?> authError(Exception exception, HttpStatus fallback) {
        if (exception instanceof AuthException authException) {
            return ResponseEntity.status(authException.getStatus()).body(Map.of(
                    "error", authException.getCode(),
                    "message", authException.getMessage()
            ));
        }
        return ResponseEntity.status(fallback).body(Map.of(
                "error", "AUTH_ERROR",
                "message", exception.getMessage() == null ? "Authentication failed" : exception.getMessage()
        ));
    }
}
