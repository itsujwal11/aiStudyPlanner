package com.aasa.repository;

import com.aasa.entity.ReviewLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ReviewLogRepository extends JpaRepository<ReviewLog, Long> {
    List<ReviewLog> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<ReviewLog> findByUserIdAndTopicIdOrderByCreatedAtDesc(Long userId, Long topicId);
}
