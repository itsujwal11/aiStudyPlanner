package com.aasa.entity;

import jakarta.persistence.*;
import lombok.*;

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
    private Double completionPercentage = 0.0;

    @Column(name = "best_score")
    private Double bestScore = 0.0;

    @Column(name = "total_attempts")
    private Integer totalAttempts = 0;

    @Column(name = "correct_attempts")
    private Integer correctAttempts = 0;

    public enum WeaknessLevel {
        LOW, MEDIUM, HIGH, NOT_ATTEMPTED
    }
}
