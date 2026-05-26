package com.aasa.service;

import com.aasa.entity.QuizAttempt;
import com.aasa.entity.StudyProgress;
import com.aasa.entity.Topic;
import com.aasa.entity.User;
import com.aasa.repository.QuizAttemptRepository;
import com.aasa.repository.StudyProgressRepository;
import com.aasa.repository.TopicRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class RecommendationEngineService {

    @Autowired
    private StudyProgressRepository studyProgressRepository;

    @Autowired
    private QuizAttemptRepository quizAttemptRepository;

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private ScoringEngineService scoringEngineService;

    @Autowired
    private WeaknessEngineService weaknessEngineService;

    public List<Topic> getRecommendedTopics(User user, int limit) {
        List<StudyProgress> allProgress = studyProgressRepository.findByUserId(user.getId());

        return allProgress.stream()
                .sorted((a, b) -> {
                    Double scoreA = calculateRecommendationScore(a);
                    Double scoreB = calculateRecommendationScore(b);
                    return scoreB.compareTo(scoreA);
                })
                .limit(limit)
                .map(StudyProgress::getTopic)
                .collect(Collectors.toList());
    }

    private Double calculateRecommendationScore(StudyProgress progress) {
        Topic topic = progress.getTopic();
        Double weaknessScore = weaknessEngineService.getWeaknessScore(progress.getWeaknessLevel());
        Double complexityScore = topic.getComplexityScore() != null ? topic.getComplexityScore() : 0.0;
        Double importanceScore = topic.getImportanceScore() != null ? topic.getImportanceScore() : 0.0;

        long daysUntilExam = ChronoUnit.DAYS.between(LocalDate.now(), topic.getPdfDocument().getExamDate());
        Double urgencyScore = 1.0 / (daysUntilExam + 1.0);

        return (0.35 * complexityScore) +
               (0.30 * importanceScore) +
               (0.20 * weaknessScore) +
               (0.15 * urgencyScore);
    }

    public Map<String, Object> getStudyInsights(User user) {
        List<QuizAttempt> attempts = quizAttemptRepository.findByUserId(user.getId());
        List<StudyProgress> progress = studyProgressRepository.findByUserId(user.getId());

        Map<String, Object> insights = new HashMap<>();

        // Calculate statistics
        long totalAttempts = attempts.size();
        long correctAttempts = attempts.stream().filter(QuizAttempt::getIsCorrect).count();
        double accuracy = totalAttempts > 0 ? (correctAttempts * 100.0) / totalAttempts : 0.0;

        insights.put("totalAttempts", totalAttempts);
        insights.put("correctAttempts", correctAttempts);
        insights.put("accuracy", accuracy);

        // Calculate study time
        long totalTimeSeconds = attempts.stream()
                .mapToLong(a -> a.getTimeTakenSeconds() != null ? a.getTimeTakenSeconds() : 0)
                .sum();
        insights.put("totalTimeSeconds", totalTimeSeconds);
        insights.put("totalTimeMinutes", totalTimeSeconds / 60);

        // Identify strengths and weaknesses
        List<StudyProgress> strengths = progress.stream()
                .filter(p -> p.getWeaknessLevel() == StudyProgress.WeaknessLevel.LOW)
                .sorted((a, b) -> b.getBestScore().compareTo(a.getBestScore()))
                .limit(5)
                .collect(Collectors.toList());

        List<StudyProgress> weaknesses = progress.stream()
                .filter(p -> p.getWeaknessLevel() == StudyProgress.WeaknessLevel.HIGH)
                .sorted((a, b) -> a.getBestScore().compareTo(b.getBestScore()))
                .limit(5)
                .collect(Collectors.toList());

        insights.put("strengths", strengths.stream()
                .map(p -> Map.of(
                        "topic", p.getTopic().getTitle(),
                        "score", p.getBestScore()
                ))
                .collect(Collectors.toList()));

        insights.put("weaknesses", weaknesses.stream()
                .map(p -> Map.of(
                        "topic", p.getTopic().getTitle(),
                        "score", p.getBestScore()
                ))
                .collect(Collectors.toList()));

        return insights;
    }

    public List<Map<String, Object>> getStudySchedule(User user, int daysAhead) {
        List<StudyProgress> progress = studyProgressRepository.findByUserIdOrderByPriority(user.getId());
        List<Map<String, Object>> schedule = new ArrayList<>();

        LocalDate today = LocalDate.now();
        int topicsPerDay = Math.max(1, progress.size() / daysAhead);

        for (int day = 0; day < daysAhead && day * topicsPerDay < progress.size(); day++) {
            LocalDate scheduleDate = today.plusDays(day);
            List<StudyProgress> dayTopics = progress.stream()
                    .skip((long) day * topicsPerDay)
                    .limit(topicsPerDay)
                    .collect(Collectors.toList());

            Map<String, Object> daySchedule = new HashMap<>();
            daySchedule.put("date", scheduleDate);
            daySchedule.put("topics", dayTopics.stream()
                    .map(p -> Map.of(
                            "title", p.getTopic().getTitle(),
                            "priority", p.getTopic().getPriorityScore(),
                            "weakness", p.getWeaknessLevel().toString()
                    ))
                    .collect(Collectors.toList()));

            schedule.add(daySchedule);
        }

        return schedule;
    }
}
