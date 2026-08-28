package com.aasa.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "planner_task_completions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_planner_task_completion",
                columnNames = {"user_id", "topic_id", "activity_type", "completion_date", "session_index"}
        )
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlannerTaskCompletion {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "planner_task_completion_seq")
    @SequenceGenerator(name = "planner_task_completion_seq", sequenceName = "planner_task_completion_id_seq", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "topic_id", nullable = false)
    private Long topicId;

    @Column(name = "activity_type", nullable = false, length = 20)
    private String activityType;

    @Column(name = "completion_date", nullable = false)
    private LocalDate completionDate;

    @Column(name = "session_index", nullable = false)
    @Builder.Default
    private Integer sessionIndex = 0;

    @Column(name = "completed", nullable = false)
    @Builder.Default
    private Boolean completed = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (updatedAt == null) updatedAt = LocalDateTime.now();
        if (sessionIndex == null) sessionIndex = 0;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}