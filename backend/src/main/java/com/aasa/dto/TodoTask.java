package com.aasa.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TodoTask {
    private String topicTitle;
    private String activityType;        // LEARN, REVISION, PRACTICE, TEST
    private int estimatedDurationMinutes;
    private String complexityLevel;     // EASY, MEDIUM, HARD
    private String priorityLevel;       // HIGH, MEDIUM, LOW
    private boolean completed;
    private Long topicId;
    private double weaknessScore;
}