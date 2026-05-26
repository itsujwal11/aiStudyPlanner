package com.aasa.dto;

import lombok.*;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardDto {
    private Integer totalPdfs;
    private Integer totalTopics;
    private Integer totalQuizzes;
    private Double averageScore;
    private Integer daysUntilExam;
    private List<StudyProgressDto> rankedTopics;
    private List<StudyProgressDto> weakTopics;
    private Double overallCompletionPercentage;
}
