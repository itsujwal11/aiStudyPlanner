package com.aasa.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RagChunkSource {
    private Long chunkId;
    private Long pdfId;
    private Integer chunkIndex;
    private String text;
    private Integer pageNumber;
    private Double similarity;
    private String pdfFileName;
}