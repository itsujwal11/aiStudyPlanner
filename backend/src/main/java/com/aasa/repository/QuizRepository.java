package com.aasa.repository;

import com.aasa.entity.Quiz;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface QuizRepository extends JpaRepository<Quiz, Long> {
    List<Quiz> findByTopicId(Long topicId);
    long countByTopicId(Long topicId);
    void deleteByTopicId(Long topicId);
}
