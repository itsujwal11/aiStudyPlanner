package com.aasa.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(name = "study_progress")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudyProgress {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "study_progress_seq")
    @SequenceGenerator(name = "study_progress_seq", sequenceName = "study_progress_id_seq", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "topic_id", nullable = false)
    private Topic topic;

    @Column(name = "weakness_level")
    @Enumerated(EnumType.STRING)
    private WeaknessLevel weaknessLevel;

    @Column(name = "completion_percentage")
    @Builder.Default
    private Double completionPercentage = 0.0;

    @Column(name = "best_score")
    @Builder.Default
    private Double bestScore = 0.0;

    @Column(name = "total_attempts")
    @Builder.Default
    private Integer totalAttempts = 0;

    @Column(name = "correct_attempts")
    @Builder.Default
    private Integer correctAttempts = 0;

    @Column(name = "mastery_level")
    @Builder.Default
    private Double masteryLevel = 0.0;

    @Column(name = "alpha_param")
    @Builder.Default
    private Double alpha = 2.0;

    @Column(name = "beta_param")
    @Builder.Default
    private Double beta = 8.0;

    @Column(name = "sm2_interval")
    @Builder.Default
    private Integer sm2Interval = 0;

    @Column(name = "sm2_efactor")
    @Builder.Default
    private Double sm2Efactor = 2.5;

    @Column(name = "sm2_repetitions")
    @Builder.Default
    private Integer sm2Repetitions = 0;

    @Column(name = "next_review_date")
    private LocalDate nextReviewDate;

    @Column(name = "last_study_date")
    private LocalDate lastStudyDate;

    public enum WeaknessLevel {
        LOW, MEDIUM, HIGH, INSUFFICIENT_DATA, NOT_ATTEMPTED
    }
}
