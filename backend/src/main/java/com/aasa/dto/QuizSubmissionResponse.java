package com.aasa.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuizSubmissionResponse {
    private Boolean isCorrect;
    private String correctAnswer;
    private String explanation;
    private Double marksObtained;
}
