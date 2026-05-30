package com.aasa.repository;

import com.aasa.entity.StudyProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface StudyProgressRepository extends JpaRepository<StudyProgress, Long> {
    Optional<StudyProgress> findByUserIdAndTopicId(Long userId, Long topicId);
    List<StudyProgress> findByUserId(Long userId);

    @Query("SELECT sp FROM StudyProgress sp WHERE sp.user.id = ?1 ORDER BY sp.topic.priorityScore DESC")
    List<StudyProgress> findByUserIdOrderByPriority(Long userId);

    @Query("SELECT AVG(sp.completionPercentage) FROM StudyProgress sp WHERE sp.user.id = ?1")
    Double getAverageCompletion(Long userId);

    @Query("SELECT sp FROM StudyProgress sp WHERE sp.user.id = ?1 AND sp.topic.pdfDocument.id = ?2 ORDER BY sp.topic.priorityScore DESC")
    List<StudyProgress> findByUserIdAndPdfIdOrderByPriority(Long userId, Long pdfId);

    @Query("SELECT sp FROM StudyProgress sp WHERE sp.user.id = ?1 AND sp.topic.pdfDocument.id = ?2")
    List<StudyProgress> findByUserIdAndPdfId(Long userId, Long pdfId);

    @Query("SELECT AVG(sp.completionPercentage) FROM StudyProgress sp WHERE sp.user.id = ?1 AND sp.topic.pdfDocument.id = ?2")
    Double getAverageCompletionByPdf(Long userId, Long pdfId);

    @Query("SELECT COUNT(sp) FROM StudyProgress sp WHERE sp.user.id = ?1 AND sp.nextReviewDate <= ?2")
    long countDueForReview(Long userId, LocalDate date);

    @Query("SELECT sp FROM StudyProgress sp WHERE sp.user.id = ?1 AND sp.nextReviewDate <= ?2 ORDER BY sp.nextReviewDate ASC")
    List<StudyProgress> findDueForReview(Long userId, LocalDate date);

    void deleteByUserId(Long userId);
}
