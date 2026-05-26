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
public class RevisionScheduleItem {
    private String topicTitle;
    private LocalDate revisionDate;
    private String frequency;
    private int daysSinceLastPractice;
    private double weaknessScore;
}