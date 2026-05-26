package com.aasa.dto;

import lombok.*;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PdfUploadRequest {
    private LocalDate examDate;
}
