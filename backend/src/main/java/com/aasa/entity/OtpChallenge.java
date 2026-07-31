package com.aasa.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "otp_challenges", indexes = {
        @Index(name = "idx_otp_user_purpose_created", columnList = "user_id,purpose,created_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OtpChallenge {
    public enum Purpose {
        EMAIL_VERIFICATION,
        LOGIN,
        PASSWORD_RESET
    }

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "otp_challenge_seq")
    @SequenceGenerator(name = "otp_challenge_seq", sequenceName = "otp_challenge_id_seq", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Purpose purpose;

    @Column(name = "code_hash", nullable = false)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "attempt_count", nullable = false)
    @Builder.Default
    private Integer attemptCount = 0;

    @Column(name = "consumed_at")
    private LocalDateTime consumedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (attemptCount == null) attemptCount = 0;
    }
}
