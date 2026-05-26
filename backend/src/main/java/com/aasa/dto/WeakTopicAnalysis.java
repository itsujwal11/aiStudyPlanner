package com.aasa.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WeakTopicAnalysis {
    private Long topicId;
    private String topicTitle;
    private String description;
    private double weaknessScore;
    private double importanceScore;
    private double complexityScore;
    private double masteryLevel;
    private double priorityScore;
    private int daysUntilExam;
    private String whyImportant;
    private String recommendedDuration;
    private int totalAttempts;
    private int correctAttempts;
}