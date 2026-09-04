package com.aasa.repository;

import com.aasa.entity.QuizAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, Long> {
    List<QuizAttempt> findByUserId(Long userId);
    List<QuizAttempt> findByQuizId(Long quizId);

    @Query("SELECT DISTINCT qa.quiz.id FROM QuizAttempt qa WHERE qa.user.id = ?1")
    List<Long> findAttemptedQuizIdsByUserId(Long userId);

    @Query("SELECT DISTINCT qa.quiz.id FROM QuizAttempt qa WHERE qa.user.id = ?1 AND qa.quiz.topic.pdfDocument.id = ?2")
    List<Long> findAttemptedQuizIdsByUserIdAndPdfId(Long userId, Long pdfId);

    @Query("SELECT qa FROM QuizAttempt qa JOIN FETCH qa.quiz q WHERE qa.user.id = ?1 AND q.topic.id = ?2 ORDER BY qa.attemptTime ASC")
    List<QuizAttempt> findByUserIdAndTopicId(Long userId, Long topicId);

    @Query("SELECT AVG(CASE WHEN qa.isCorrect = true THEN 100.0 ELSE 0.0 END) FROM QuizAttempt qa WHERE qa.user.id = ?1")
    Double getAverageScore(Long userId);

    @Query("SELECT AVG(CASE WHEN qa.isCorrect = true THEN 100.0 ELSE 0.0 END) FROM QuizAttempt qa WHERE qa.user.id = ?1 AND qa.quiz.topic.pdfDocument.id = ?2")
    Double getAverageScoreByPdf(Long userId, Long pdfId);

    @Query("SELECT COUNT(DISTINCT qa.quiz.topic.id) FROM QuizAttempt qa WHERE qa.user.id = ?1 AND qa.isCorrect = true")
    Integer countCorrectTopics(Long userId);

    void deleteByUserId(Long userId);

    @Modifying
    @Query("DELETE FROM QuizAttempt qa WHERE qa.quiz.topic.id = ?1")
    void deleteByQuizTopicId(Long topicId);
}
