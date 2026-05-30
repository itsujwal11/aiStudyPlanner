package com.aasa.repository;

import com.aasa.entity.FlashcardReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface FlashcardReviewRepository extends JpaRepository<FlashcardReview, Long> {
    Optional<FlashcardReview> findByFlashcardIdAndUserId(Long flashcardId, Long userId);

    @Query("SELECT fr FROM FlashcardReview fr WHERE fr.user.id = ?1 AND fr.nextReviewAt <= ?2 ORDER BY fr.nextReviewAt ASC")
    List<FlashcardReview> findDueForReview(Long userId, LocalDate date);

    List<FlashcardReview> findByUserId(Long userId);
}
