package com.aasa.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuizDto {
    private Long id;
    private Long topicId;
    private String question;
    private String optionA;
    private String optionB;
    private String optionC;
    private String optionD;
    private String difficulty;
}
