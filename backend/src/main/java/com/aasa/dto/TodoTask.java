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
    /**
     * Stable identity ({@code topicId:ACTIVITY_TYPE:sessionIndex}) used to
     * persist a tick. Not the list position: the plan is re-ranked after every
     * answer, so a positional key would mark the wrong topic as done.
     */
    private String taskKey;

    /** Which repeat of this topic+activity this is; 0 unless a topic is split. */
    private int sessionIndex;
}