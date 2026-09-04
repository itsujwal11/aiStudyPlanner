package com.aasa.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PredefinedAnswerDto {
    private String id;
    private Long topicId;
    private String topicTitle;
    private String question;
    private String answer;
    private String type;
}
