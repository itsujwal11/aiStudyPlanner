package com.aasa.service;

import com.aasa.config.AuthException;
import com.aasa.entity.OtpChallenge;
import com.aasa.entity.User;
import com.aasa.repository.OtpChallengeRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
public class OtpService {
    private static final int MAX_ATTEMPTS = 5;

    private final OtpChallengeRepository otpChallengeRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.auth.otp.expiration-minutes:10}")
    private long expirationMinutes;

    @Value("${app.auth.otp.resend-cooldown-seconds:60}")
    private long resendCooldownSeconds;

    public OtpService(OtpChallengeRepository otpChallengeRepository, PasswordEncoder passwordEncoder) {
        this.otpChallengeRepository = otpChallengeRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public String issue(User user, OtpChallenge.Purpose purpose, boolean enforceCooldown) {
        LocalDateTime now = LocalDateTime.now();
        otpChallengeRepository.findTopByUserIdAndPurposeOrderByCreatedAtDesc(user.getId(), purpose)
                .filter(challenge -> enforceCooldown
                        && challenge.getCreatedAt().plusSeconds(resendCooldownSeconds).isAfter(now))
                .ifPresent(challenge -> {
                    throw new AuthException(
                            HttpStatus.TOO_MANY_REQUESTS,
                            "OTP_COOLDOWN",
                            "Please wait before requesting another code"
                    );
                });

        otpChallengeRepository.findByUserIdAndPurposeAndConsumedAtIsNull(user.getId(), purpose)
                .forEach(challenge -> challenge.setConsumedAt(now));

        String code = String.format("%06d", secureRandom.nextInt(1_000_000));
        otpChallengeRepository.save(OtpChallenge.builder()
                .user(user)
                .purpose(purpose)
                .codeHash(passwordEncoder.encode(code))
                .expiresAt(now.plusMinutes(expirationMinutes))
                .build());
        return code;
    }

    @Transactional(noRollbackFor = AuthException.class)
    public void verify(User user, OtpChallenge.Purpose purpose, String code) {
        OtpChallenge challenge = otpChallengeRepository
                .findTopByUserIdAndPurposeOrderByCreatedAtDesc(user.getId(), purpose)
                .orElseThrow(() -> invalidCode("No verification code has been requested"));

        LocalDateTime now = LocalDateTime.now();
        if (challenge.getConsumedAt() != null) {
            throw invalidCode("This verification code has already been used");
        }
        if (challenge.getExpiresAt().isBefore(now)) {
            throw invalidCode("This verification code has expired");
        }
        if (challenge.getAttemptCount() >= MAX_ATTEMPTS) {
            throw new AuthException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "OTP_ATTEMPTS_EXCEEDED",
                    "Too many incorrect attempts; request a new code"
            );
        }

        if (!passwordEncoder.matches(code, challenge.getCodeHash())) {
            challenge.setAttemptCount(challenge.getAttemptCount() + 1);
            throw invalidCode("Invalid verification code");
        }

        challenge.setConsumedAt(now);
    }

    private AuthException invalidCode(String message) {
        return new AuthException(HttpStatus.BAD_REQUEST, "INVALID_OTP", message);
    }
}
