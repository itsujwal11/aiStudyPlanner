package com.aasa.repository;

import com.aasa.entity.ReviewLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ReviewLogRepository extends JpaRepository<ReviewLog, Long> {
    List<ReviewLog> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<ReviewLog> findByUserIdAndTopicIdOrderByCreatedAtDesc(Long userId, Long topicId);
    void deleteByUserId(Long userId);

    @Modifying
    @Query("DELETE FROM ReviewLog reviewLog WHERE reviewLog.topic.id = :topicId")
    int deleteByTopicId(@Param("topicId") Long topicId);
}
