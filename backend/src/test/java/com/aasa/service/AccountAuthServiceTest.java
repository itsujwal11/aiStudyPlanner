package com.aasa.service;

import com.aasa.config.AuthException;
import com.aasa.dto.AuthRequest;
import com.aasa.entity.User;
import com.aasa.repository.UserRepository;
import com.aasa.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class AccountAuthServiceTest {
    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private JwtTokenProvider jwtTokenProvider;
    private OtpService otpService;
    private AccountAuthService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        jwtTokenProvider = mock(JwtTokenProvider.class);
        otpService = mock(OtpService.class);
        service = new AccountAuthService(
                userRepository,
                passwordEncoder,
                jwtTokenProvider,
                otpService,
                mock(AuthEmailService.class),
                mock(GoogleTokenService.class)
        );
    }

    @Test
    void loginRejectsAValidPasswordUntilEmailIsVerified() {
        User user = user(false);
        when(userRepository.findByEmailIgnoreCase("learner@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Password1", user.getPassword())).thenReturn(true);

        AuthException exception = assertThrows(AuthException.class,
                () -> service.login(AuthRequest.builder()
                        .email(" LEARNER@EXAMPLE.COM ")
                        .password("Password1")
                        .build()));

        assertEquals("EMAIL_NOT_VERIFIED", exception.getCode());
        verifyNoInteractions(jwtTokenProvider);
    }

    @Test
    void adminCanLoginWithoutEmailOtp() {
        User admin = user(false);
        admin.setRole("ADMIN");
        when(userRepository.findByEmailIgnoreCase("learner@example.com"))
                .thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("Password1", admin.getPassword())).thenReturn(true);
        when(jwtTokenProvider.generateToken(admin.getEmail())).thenReturn("admin-token");

        var response = service.login(AuthRequest.builder()
                .email("learner@example.com")
                .password("Password1")
                .build());

        assertEquals("ADMIN", response.getRole());
        assertEquals("admin-token", response.getToken());
    }

    @Test
    void verifiedEmailEndpointCannotMintAnotherTokenWithoutAnOtp() {
        User user = user(true);
        when(userRepository.findByEmailIgnoreCase("learner@example.com")).thenReturn(Optional.of(user));

        AuthException exception = assertThrows(AuthException.class,
                () -> service.verifyEmail("learner@example.com", "123456"));

        assertEquals("EMAIL_ALREADY_VERIFIED", exception.getCode());
        verifyNoInteractions(otpService, jwtTokenProvider);
    }

    private User user(boolean verified) {
        return User.builder()
                .id(1L)
                .email("learner@example.com")
                .name("Learner")
                .password("encoded")
                .role("USER")
                .emailVerified(verified)
                .build();
    }
}
