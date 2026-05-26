package com.aasa.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuizSubmissionRequest {
    private Long quizId;
    private String selectedAnswer;
    private Long timeTakenSeconds;
}
