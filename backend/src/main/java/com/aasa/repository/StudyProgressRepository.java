package com.aasa.repository;

import com.aasa.entity.StudyProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
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

    void deleteByUserId(Long userId);
}
