package com.aasa.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PdfDocumentDto {
    private Long id;
    private String fileName;
    private LocalDateTime uploadDate;
    private LocalDate examDate;
    private Boolean isAnalyzed;
    private Integer topicCount;
}
