package com.aasa.service;

import com.aasa.config.AuthException;
import com.aasa.dto.AuthRequest;
import com.aasa.dto.AuthResponse;
import com.aasa.dto.GoogleLoginRequest;
import com.aasa.dto.RegisterRequest;
import com.aasa.dto.ResetPasswordRequest;
import com.aasa.entity.OtpChallenge;
import com.aasa.entity.User;
import com.aasa.repository.UserRepository;
import com.aasa.security.JwtTokenProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
public class AccountAuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final OtpService otpService;
    private final AuthEmailService authEmailService;
    private final GoogleTokenService googleTokenService;

    public AccountAuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider,
            OtpService otpService,
            AuthEmailService authEmailService,
            GoogleTokenService googleTokenService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.otpService = otpService;
        this.authEmailService = authEmailService;
        this.googleTokenService = googleTokenService;
    }

    @Transactional
    public String register(RegisterRequest request) {
        String email = normalizeEmail(request.getEmail());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new AuthException(HttpStatus.CONFLICT, "EMAIL_EXISTS", "Email already registered");
        }

        User user = userRepository.save(User.builder()
                .email(email)
                .name(request.getName().trim())
                .password(passwordEncoder.encode(request.getPassword()))
                .role("USER")
                .emailVerified(false)
                .build());
        String code = otpService.issue(user, OtpChallenge.Purpose.EMAIL_VERIFICATION, false);
        authEmailService.sendOtp(user, code, OtpChallenge.Purpose.EMAIL_VERIFICATION);
        return email;
    }

    public AuthResponse login(AuthRequest request) {
        User user = userRepository.findByEmailIgnoreCase(normalizeEmail(request.getEmail()))
                .orElseThrow(this::invalidCredentials);
        if (request.getPassword() == null
                || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw invalidCredentials();
        }
        boolean isAdmin = "ADMIN".equalsIgnoreCase(user.getRole());
        if (!isAdmin && !Boolean.TRUE.equals(user.getEmailVerified())) {
            throw new AuthException(HttpStatus.FORBIDDEN, "EMAIL_NOT_VERIFIED",
                    "Verify your email before signing in");
        }
        return authResponse(user);
    }

    @Transactional(noRollbackFor = AuthException.class)
    public AuthResponse verifyEmail(String email, String code) {
        User user = requireUser(email);
        if (Boolean.TRUE.equals(user.getEmailVerified())) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "EMAIL_ALREADY_VERIFIED",
                    "This email address is already verified");
        }
        otpService.verify(user, OtpChallenge.Purpose.EMAIL_VERIFICATION, code);
        user.setEmailVerified(true);
        userRepository.save(user);
        return authResponse(user);
    }

    @Transactional
    public void resendVerification(String email) {
        User user = requireUser(email);
        if (Boolean.TRUE.equals(user.getEmailVerified())) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "EMAIL_ALREADY_VERIFIED",
                    "This email address is already verified");
        }
        String code = otpService.issue(user, OtpChallenge.Purpose.EMAIL_VERIFICATION, true);
        authEmailService.sendOtp(user, code, OtpChallenge.Purpose.EMAIL_VERIFICATION);
    }

    @Transactional
    public void requestPasswordReset(String email) {
        userRepository.findByEmailIgnoreCase(normalizeEmail(email)).ifPresent(user -> {
            String code = otpService.issue(user, OtpChallenge.Purpose.PASSWORD_RESET, true);
            authEmailService.sendOtp(user, code, OtpChallenge.Purpose.PASSWORD_RESET);
        });
    }

    @Transactional(noRollbackFor = AuthException.class)
    public void resetPassword(ResetPasswordRequest request) {
        User user = requireUser(request.getEmail());
        otpService.verify(user, OtpChallenge.Purpose.PASSWORD_RESET, request.getCode());
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setEmailVerified(true);
        userRepository.save(user);
    }

    @Transactional
    public AuthResponse googleLogin(GoogleLoginRequest request) {
        GoogleTokenService.GoogleIdentity identity = googleTokenService.verify(request.getCredential());
        User user = userRepository.findByGoogleSubject(identity.subject())
                .or(() -> userRepository.findByEmailIgnoreCase(identity.email()))
                .orElseGet(() -> User.builder()
                        .email(identity.email())
                        .name(identity.name())
                        .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                        .role("USER")
                        .build());
        if (user.getGoogleSubject() != null && !user.getGoogleSubject().equals(identity.subject())) {
            throw new AuthException(HttpStatus.CONFLICT, "GOOGLE_ACCOUNT_CONFLICT",
                    "This email is linked to another Google account");
        }
        user.setGoogleSubject(identity.subject());
        user.setEmailVerified(true);
        if (user.getName() == null || user.getName().isBlank()) user.setName(identity.name());
        userRepository.save(user);
        return authResponse(user);
    }

    private User requireUser(String email) {
        return userRepository.findByEmailIgnoreCase(normalizeEmail(email))
                .orElseThrow(() -> new AuthException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
                        "Unable to process this request"));
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private AuthException invalidCredentials() {
        return new AuthException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
                "Invalid email or password");
    }

    private AuthResponse authResponse(User user) {
        return AuthResponse.builder()
                .token(jwtTokenProvider.generateToken(user.getEmail()))
                .email(user.getEmail())
                .name(user.getName())
                .userId(user.getId())
                .role(user.getRole())
                .build();
    }
}
