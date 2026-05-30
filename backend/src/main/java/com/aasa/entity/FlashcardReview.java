package com.aasa.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(name = "flashcard_reviews")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FlashcardReview {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "fc_review_seq")
    @SequenceGenerator(name = "fc_review_seq", sequenceName = "fc_review_id_seq", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "flashcard_id", nullable = false)
    private Flashcard flashcard;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "box")
    private Integer box = 0;

    @Column(name = "interval_days")
    private Integer intervalDays = 0;

    @Column(name = "efactor")
    private Double efactor = 2.5;

    @Column(name = "repetitions")
    private Integer repetitions = 0;

    @Column(name = "next_review_at")
    private LocalDate nextReviewAt;

    @Column(name = "last_rating")
    private Integer lastRating;
}
