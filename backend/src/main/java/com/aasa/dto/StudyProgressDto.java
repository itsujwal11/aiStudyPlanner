package com.aasa.dto;

import lombok.*;
import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudyProgressDto {
    private Long topicId;
    private String topicTitle;
    private String weaknessLevel;
    private Double completionPercentage;
    private Double bestScore;
    private Integer totalAttempts;
    private Integer correctAttempts;
    private Double priorityScore;
    private Double masteryLevel;
    private Integer sm2Interval;
    private LocalDate nextReviewDate;
}
