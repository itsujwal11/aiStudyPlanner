package com.aasa.dto;

import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PdfDetailDto {
    private Long id;
    private String fileName;
    private LocalDateTime uploadDate;
    private LocalDate examDate;
    private Boolean isAnalyzed;
    private int daysUntilExam;
    private int totalTopics;
    private int totalQuizzes;
    private double averageScore;
    private double overallCompletionPercentage;
    private List<TopicDetail> topics;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TopicDetail {
        private Long id;
        private String title;
        private String description;
        private Double complexityScore;
        private Double importanceScore;
        private Double priorityScore;
        private Double weaknessScore;
        private int quizCount;
        private int totalAttempts;
        private int correctAttempts;
        private Double bestScore;
        private Double completionPercentage;
        private String weaknessLevel;
    }
}
