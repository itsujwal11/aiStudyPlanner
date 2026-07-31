package com.aasa.service;

import com.aasa.config.AuthException;
import com.aasa.entity.OtpChallenge;
import com.aasa.entity.User;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.logging.Logger;

@Service
public class AuthEmailService {
    private static final Logger logger = Logger.getLogger(AuthEmailService.class.getName());
    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Value("${app.mail.enabled:false}")
    private boolean mailEnabled;
    @Value("${app.auth.otp.log-codes:false}")
    private boolean logCodes;
    @Value("${app.mail.from:no-reply@aasa.local}")
    private String from;

    public AuthEmailService(ObjectProvider<JavaMailSender> mailSenderProvider) {
        this.mailSenderProvider = mailSenderProvider;
    }

    public void sendOtp(User user, String code, OtpChallenge.Purpose purpose) {
        if (!mailEnabled) {
            if (logCodes) {
                logger.warning("Development OTP for " + user.getEmail() + " (" + purpose + "): " + code);
                return;
            }
            throw emailUnavailable();
        }

        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) throw emailUnavailable();

        String action = purpose == OtpChallenge.Purpose.PASSWORD_RESET
                ? "reset your password" : "verify your email address";
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(user.getEmail());
        message.setSubject("Your AASA verification code");
        message.setText("Hi " + user.getName() + ",\n\nUse this code to " + action + ":\n\n"
                + code + "\n\nThe code expires in 10 minutes. If you did not request it, you can ignore this email.");
        mailSender.send(message);
    }

    private AuthException emailUnavailable() {
        return new AuthException(HttpStatus.SERVICE_UNAVAILABLE, "EMAIL_NOT_CONFIGURED",
                "Email delivery is not configured");
    }
}
