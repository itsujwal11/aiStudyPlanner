package com.aasa.dto;

import lombok.*;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FlashcardDto {
    private Long id;
    private Long topicId;
    private String frontText;
    private String backText;
    private Double difficultyEst;
}
