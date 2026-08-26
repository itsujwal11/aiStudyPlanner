package com.aasa.service;

import com.aasa.dto.ReportSummaryDto;
import com.aasa.entity.QuizAttempt;
import com.aasa.entity.StudyProgress;
import com.aasa.entity.Topic;
import com.aasa.entity.User;
import com.aasa.repository.QuizAttemptRepository;
import com.aasa.repository.StudyProgressRepository;
import com.aasa.repository.TopicRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Builds the study report consumed by the frontend Reports page
 * (GET /api/reports/study-report). It aggregates quiz attempts, study
 * progress and topic data for the authenticated user into a summary,
 * a per-topic breakdown and data-driven recommendations.
 */
@Service
@Transactional(readOnly = true)
public class ReportService {

    private static final Logger logger = Logger.getLogger(ReportService.class.getName());

    @Autowired
    private QuizAttemptRepository quizAttemptRepository;

    @Autowired
    private StudyProgressRepository studyProgressRepository;

    @Autowired
    private TopicRepository topicRepository;

    public ReportSummaryDto generateStudyReport(User user) {
        logger.info("Generating study report for user: " + user.getId());

        List<QuizAttempt> attempts = quizAttemptRepository.findByUserId(user.getId());
        List<StudyProgress> progressList = studyProgressRepository.findByUserId(user.getId());
        List<Topic> topics = topicRepository.findByUserIdOrderByPriority(user.getId());

        long totalQuizzes = attempts.size();
        long correctAnswers = attempts.stream().filter(a -> Boolean.TRUE.equals(a.getIsCorrect())).count();
        double accuracy = totalQuizzes == 0 ? 0.0 : (correctAnswers * 100.0) / totalQuizzes;
        long totalStudyTimeMinutes = attempts.stream()
                .mapToLong(a -> a.getTimeTakenSeconds() == null ? 0L : a.getTimeTakenSeconds() / 60L)
                .sum();

        List<ReportSummaryDto.TopicBreakdown> breakdown = new ArrayList<>();
        for (Topic topic : topics) {
            long topicAttempts = attempts.stream()
                    .filter(a -> a.getQuiz() != null && a.getQuiz().getTopic() != null
                            && a.getQuiz().getTopic().getId().equals(topic.getId()))
                    .count();
long topicCorrect = attempts.stream()
                    .filter(a -> a.getQuiz() != null && a.getQuiz().getTopic() != null
                            && a.getQuiz().getTopic().getId().equals(topic.getId())
                            && Boolean.TRUE.equals(a.getIsCorrect()))
                    .count();
            double topicAccuracy = topicAttempts == 0 ? 0.0 : (topicCorrect * 100.0) / topicAttempts;

            StudyProgress progress = progressList.stream()
                    .filter(sp -> sp.getTopic() != null && sp.getTopic().getId().equals(topic.getId()))
                    .findFirst().orElse(null);

            double bestScore = progress != null && progress.getBestScore() != null
                    ? progress.getBestScore()
                    : 0.0;
            String weakness = progress != null && progress.getWeaknessLevel() != null
                    ? progress.getWeaknessLevel().name()
                    : "NOT_ATTEMPTED";

            breakdown.add(ReportSummaryDto.TopicBreakdown.builder()
                    .topic(topic.getTitle())
                    .attempts((int) topicAttempts)
                    .correct((int) topicCorrect)
                    .accuracy(Math.round(topicAccuracy * 100.0) / 100.0)
                    .weakness(weakness)
                    .bestScore(Math.round(bestScore * 100.0) / 100.0)
                    .build());
        }

        // Data-driven recommendations derived from real learner evidence.
        List<ReportSummaryDto.Recommendation> recommendations = new ArrayList<>();
        for (Topic topic : topics) {
            StudyProgress progress = progressList.stream()
                    .filter(sp -> sp.getTopic() != null && sp.getTopic().getId().equals(topic.getId()))
                    .findFirst().orElse(null);
            double mastery = progress != null && progress.getMasteryLevel() != null
                    ? progress.getMasteryLevel()
                    : 0.0;
            double currentScore = mastery * 100.0;
            String rec;
            if (currentScore < 40.0) {
                rec = "Needs priority revision — low mastery (" + Math.round(currentScore) + "%).";
            } else if (currentScore < 70.0) {
                rec = "Keep practicing to raise mastery from " + Math.round(currentScore) + "%.";
            } else if (currentScore < 90.0) {
                rec = "Solid foundation — maintain with periodic review.";
            } else {
                rec = "Well mastered — light weekly revision only.";
            }
            recommendations.add(ReportSummaryDto.Recommendation.builder()
                    .topic(topic.getTitle())
                    .currentScore(Math.round(currentScore * 100.0) / 100.0)
                    .recommendation(rec)
                    .build());
        }

        return ReportSummaryDto.builder()
                .generatedAt(LocalDateTime.now().toLocalDate().toString())
                .userName(user.getName())
                .userEmail(user.getEmail())
                .summary(ReportSummaryDto.StudySummary.builder()
                        .totalQuizzes((int) totalQuizzes)
                        .correctAnswers((int) correctAnswers)
                        .accuracy(Math.round(accuracy * 100.0) / 100.0)
                        .totalTopics(topics.size())
                        .totalStudyTimeMinutes(totalStudyTimeMinutes)
                        .build())
                .topicBreakdown(breakdown)
                .recommendations(recommendations)
                .build();
    }
}