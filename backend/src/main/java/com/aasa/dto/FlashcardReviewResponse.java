package com.aasa.dto;

import lombok.*;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FlashcardReviewResponse {
    private LocalDate nextReviewAt;
    private Integer box;
    private Integer intervalDays;
}
