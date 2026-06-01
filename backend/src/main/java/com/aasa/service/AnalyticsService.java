package com.aasa.service;

import com.aasa.entity.QuizAttempt;
import com.aasa.entity.Topic;
import com.aasa.entity.User;
import com.aasa.repository.QuizAttemptRepository;
import com.aasa.repository.TopicRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class AnalyticsService {

    @Autowired
    private QuizAttemptRepository quizAttemptRepository;

    @Autowired
    private TopicRepository topicRepository;

    public AnalyticsService() {
    }

    public Map<String, Object> getPerformanceAnalytics(User user) {
        List<QuizAttempt> attempts = quizAttemptRepository.findByUserId(user.getId());

        Map<String, Object> analytics = new HashMap<>();

        // Overall statistics
        long totalQuizzes = attempts.size();
        long correctAnswers = attempts.stream().filter(QuizAttempt::getIsCorrect).count();
        double overallAccuracy = totalQuizzes > 0 ? (correctAnswers * 100.0) / totalQuizzes : 0.0;

        analytics.put("totalQuizzes", totalQuizzes);
        analytics.put("correctAnswers", correctAnswers);
        analytics.put("overallAccuracy", (double) Math.round(overallAccuracy * 100.0) / 100.0);


        // Performance by difficulty
        Map<String, Object> difficultyStats = new HashMap<>();
        for (String difficulty : Arrays.asList("EASY", "MEDIUM", "HARD")) {
            long difficultyAttempts = attempts.stream()
                    .filter(a -> a.getQuiz().getDifficulty().toString().equals(difficulty))
                    .count();
            long difficultyCorrect = attempts.stream()
                    .filter(a -> a.getQuiz().getDifficulty().toString().equals(difficulty) && a.getIsCorrect())
                    .count();
            double accuracy = difficultyAttempts > 0 ? (difficultyCorrect * 100.0) / difficultyAttempts : 0.0;

            difficultyStats.put(difficulty, Map.of(
                    "attempts", difficultyAttempts,
                    "correct", difficultyCorrect,
                    "accuracy", Math.round(accuracy * 100.0) / 100.0
            ));
        }
        analytics.put("byDifficulty", difficultyStats);

        // Performance trend (last 7 days)
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        List<QuizAttempt> recentAttempts = attempts.stream()
                .filter(a -> a.getAttemptTime().isAfter(sevenDaysAgo))
                .sorted(Comparator.comparing(QuizAttempt::getAttemptTime))
                .collect(Collectors.toList());

        List<Map<String, Object>> trend = new ArrayList<>();
        for (int day = 0; day < 7; day++) {
            LocalDateTime dayStart = LocalDateTime.now().minusDays(7 - day).withHour(0).withMinute(0).withSecond(0);
            LocalDateTime dayEnd = dayStart.plusDays(1);

            List<QuizAttempt> dayAttempts = recentAttempts.stream()
                    .filter(a -> a.getAttemptTime().isAfter(dayStart) && a.getAttemptTime().isBefore(dayEnd))
                    .collect(Collectors.toList());

            long dayCorrect = dayAttempts.stream().filter(QuizAttempt::getIsCorrect).count();
            double dayAccuracy = dayAttempts.size() > 0 ? (dayCorrect * 100.0) / dayAttempts.size() : 0.0;

            trend.add(Map.of(
                    "date", dayStart.toLocalDate(),
                    "attempts", dayAttempts.size(),
                    "accuracy", Math.round(dayAccuracy * 100.0) / 100.0
            ));
        }
        analytics.put("trend", trend);

        // Average time per quiz
        double avgTime = attempts.stream()
                .mapToLong(a -> a.getTimeTakenSeconds() != null ? a.getTimeTakenSeconds() : 0)
                .average()
                .orElse(0.0);
        analytics.put("averageTimeSeconds", (long) Math.round(avgTime));

        return analytics;
    }

    public Map<String, Object> getTopicAnalytics(User user, Long topicId) {
        List<QuizAttempt> topicAttempts = quizAttemptRepository.findByUserId(user.getId()).stream()
                .filter(a -> a.getQuiz().getTopic().getId().equals(topicId))
                .collect(Collectors.toList());

        Map<String, Object> analytics = new HashMap<>();

        long totalAttempts = topicAttempts.size();
        long correctAttempts = topicAttempts.stream().filter(QuizAttempt::getIsCorrect).count();
        double accuracy = totalAttempts > 0 ? (correctAttempts * 100.0) / totalAttempts : 0.0;

        analytics.put("totalAttempts", totalAttempts);
        analytics.put("correctAttempts", correctAttempts);
        analytics.put("accuracy", Math.round(accuracy * 100.0) / 100.0);

        // Improvement over time
        List<Map<String, Object>> improvement = topicAttempts.stream()
                .sorted(Comparator.comparing(QuizAttempt::getAttemptTime))
                .map(attempt -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("date", attempt.getAttemptTime().toLocalDate());
                    map.put("correct", attempt.getIsCorrect() ? 1 : 0);
                    map.put("time", attempt.getTimeTakenSeconds() != null ? attempt.getTimeTakenSeconds() : 0);
                    return map;
                })
                .collect(Collectors.toList());

        analytics.put("improvement", improvement);

        return analytics;
    }

    public List<Map<String, Object>> getComparisonAnalytics(User user) {
        List<Topic> topics = topicRepository.findByUserIdOrderByPriority(user.getId());

        return topics.stream()
                .map(topic -> {
                    List<QuizAttempt> topicAttempts = quizAttemptRepository.findByUserId(user.getId()).stream()
                            .filter(a -> a.getQuiz().getTopic().getId().equals(topic.getId()))
                            .collect(Collectors.toList());

                    long correct = topicAttempts.stream().filter(QuizAttempt::getIsCorrect).count();
                    double accuracy = topicAttempts.size() > 0 ? (correct * 100.0) / topicAttempts.size() : 0.0;

                    Map<String, Object> map = new HashMap<>();
                    map.put("topic", topic.getTitle());
                    map.put("attempts", topicAttempts.size());
                    map.put("accuracy", Math.round(accuracy * 100.0) / 100.0);
                    map.put("complexity", topic.getComplexityScore());
                    map.put("priority", topic.getPriorityScore());
                    return map;
                })
                .sorted((a, b) -> Double.compare(((Number) b.get("accuracy")).doubleValue(), ((Number) a.get("accuracy")).doubleValue()))
                .collect(Collectors.toList());
    }
}
