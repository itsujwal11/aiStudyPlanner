package com.aasa.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Study report payload returned by GET /api/reports/study-report and rendered
 * by the frontend Reports page (summary, per-topic breakdown, recommendations).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportSummaryDto {
    private String generatedAt;
    private String userName;
    private String userEmail;
    private StudySummary summary;
    private List<TopicBreakdown> topicBreakdown;
    private List<Recommendation> recommendations;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StudySummary {
        private int totalQuizzes;
        private int correctAnswers;
        private double accuracy;
        private int totalTopics;
        private long totalStudyTimeMinutes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopicBreakdown {
        private String topic;
        private int attempts;
        private int correct;
        private double accuracy;
        private String weakness;
        private double bestScore;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Recommendation {
        private String topic;
        private double currentScore;
        private String recommendation;
    }
}