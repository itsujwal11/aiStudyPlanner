package com.aasa.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "review_log")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewLog {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "review_log_seq")
    @SequenceGenerator(name = "review_log_seq", sequenceName = "review_log_id_seq", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "topic_id", nullable = false)
    private Topic topic;

    @Column(name = "review_type")
    private String reviewType;

    @Column(name = "rating")
    private Integer rating;

    @Column(name = "response_time_ms")
    private Integer responseTimeMs;

    @Column(name = "scheduled_days")
    private Integer scheduledDays;

    @Column(name = "actual_interval")
    private Integer actualInterval;

    @Column(name = "mastery_before")
    private Double masteryBefore;

    @Column(name = "mastery_after")
    private Double masteryAfter;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
