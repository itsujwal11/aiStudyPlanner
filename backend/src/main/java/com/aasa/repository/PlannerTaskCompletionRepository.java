package com.aasa.repository;

import com.aasa.entity.PlannerTaskCompletion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PlannerTaskCompletionRepository extends JpaRepository<PlannerTaskCompletion, Long> {
    List<PlannerTaskCompletion> findByUserIdAndCompletionDate(Long userId, LocalDate completionDate);

    // ptc.user.id, not ptc.userId: the entity maps a @ManyToOne User, so there
    // is no userId attribute to navigate. Spring Data validates @Query at
    // bootstrap, so the wrong path stopped the whole application from starting.
    @Query("SELECT ptc FROM PlannerTaskCompletion ptc WHERE ptc.user.id = :userId AND ptc.completionDate = :date AND ptc.topicId = :topicId AND ptc.activityType = :activityType AND ptc.sessionIndex = :sessionIndex")
    Optional<PlannerTaskCompletion> findByUserIdAndCompletionDateAndTopicIdAndActivityTypeAndSessionIndex(
            @Param("userId") Long userId,
            @Param("date") LocalDate date,
            @Param("topicId") Long topicId,
            @Param("activityType") String activityType,
            @Param("sessionIndex") Integer sessionIndex
    );

    void deleteByUserId(Long userId);

    void deleteByUserIdAndCompletionDateBefore(Long userId, LocalDate date);
}