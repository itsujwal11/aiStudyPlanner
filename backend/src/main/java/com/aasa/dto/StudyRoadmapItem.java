package com.aasa.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudyRoadmapItem {
    private String topicTitle;
    private String activityType;
    private int day;
    private LocalDate scheduledDate;
    private int estimatedDurationMinutes;
    private String complexityLevel;
    private String priorityLevel;
    private boolean completed;
}