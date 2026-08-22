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
    private Double similarity;    // raw cosine similarity from pgvector retrieval
    private Double rerankScore;   // hybrid reranker score (vector + keyword + title)
    private Integer rank;         // final position after reranking (1 = best)
    private Integer retrievalRank; // position in the raw vector-search results before reranking
    private String pdfFileName;
}