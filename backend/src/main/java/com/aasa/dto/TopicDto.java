package com.aasa.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TopicDto {
    private Long id;
    private String title;
    private String description;
    private Double complexityScore;
    private Double importanceScore;
    private Double priorityScore;
    private Double weaknessScore;
    private Integer quizCount;
}
