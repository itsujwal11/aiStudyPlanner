package com.aasa.service;

import com.aasa.entity.DocumentChunk;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

@Service
@Transactional(readOnly = true)
public class VectorSearchService {

    private static final Logger logger = Logger.getLogger(VectorSearchService.class.getName());
    private static final int EMBEDDING_DIMENSION = 768;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Search for chunks similar to a query embedding within a specific PDF.
     * NOTE: pgvector extension is not installed; returns chunks ordered by chunk_index as fallback.
     */
    public List<SearchResult> searchByPdfId(Long pdfId, float[] queryEmbedding, int topK) {
        logger.info("Vector search in PDF " + pdfId + " for top " + topK + " results (pgvector not available - returning sequential chunks)");

        try {
            // Fallback: return recent chunks when pgvector is not available
            String sql = """
                SELECT id, pdf_id, chunk_index, chunk_text, token_count, page_number, created_at,
                       0.0 AS similarity
                FROM document_chunks
                WHERE pdf_id = :pdfId
                ORDER BY chunk_index
                LIMIT :topK
                """;

            var query = entityManager.createNativeQuery(sql, Object[].class);
            query.setParameter("pdfId", pdfId);
            query.setParameter("topK", topK);

            @SuppressWarnings("unchecked")
            List<Object[]> rows = query.getResultList();
            return mapResults(rows);
        } catch (Exception e) {
            logger.warning("Vector search failed (pgvector not installed): " + e.getMessage());
            return List.of();
        }
    }

    /**
     * Search for chunks similar to a query embedding across all PDFs for a user.
     * NOTE: pgvector extension is not installed; returns chunks ordered by chunk_index as fallback.
     */
    public List<SearchResult> searchByUserId(Long userId, float[] queryEmbedding, int topK) {
        logger.info("Vector search for user " + userId + " for top " + topK + " results (pgvector not available - returning sequential chunks)");

        try {
            String sql = """
                SELECT dc.id, dc.pdf_id, dc.chunk_index, dc.chunk_text, dc.token_count, dc.page_number, dc.created_at,
                       0.0 AS similarity
                FROM document_chunks dc
                JOIN pdf_documents pd ON pd.id = dc.pdf_id
                WHERE pd.user_id = :userId
                ORDER BY dc.pdf_id, dc.chunk_index
                LIMIT :topK
                """;

            var query = entityManager.createNativeQuery(sql, Object[].class);
            query.setParameter("userId", userId);
            query.setParameter("topK", topK);

            @SuppressWarnings("unchecked")
            List<Object[]> rows = query.getResultList();
            return mapResults(rows);
        } catch (Exception e) {
            logger.warning("Vector search failed (pgvector not installed): " + e.getMessage());
            return List.of();
        }
    }

    private List<SearchResult> mapResults(List<Object[]> rows) {
        List<SearchResult> results = new ArrayList<>();
        for (Object[] row : rows) {
            SearchResult result = new SearchResult(
                    ((Number) row[0]).longValue(),   // id
                    ((Number) row[1]).longValue(),   // pdf_id
                    ((Number) row[2]).intValue(),    // chunk_index
                    (String) row[3],                 // chunk_text
                    row[4] != null ? ((Number) row[4]).intValue() : 0,  // token_count
                    row[5] != null ? ((Number) row[5]).intValue() : 0,  // page_number
                    row[6] != null ? ((java.sql.Timestamp) row[6]).toLocalDateTime() : null, // created_at
                    ((Number) row[7]).doubleValue()  // similarity
            );
            results.add(result);
        }
        return results;
    }

    private String toVectorLiteral(float[] vector) {
        if (vector == null || vector.length != EMBEDDING_DIMENSION) {
            throw new IllegalArgumentException(
                    "Expected a " + EMBEDDING_DIMENSION + "-dimension query embedding"
            );
        }

        StringBuilder literal = new StringBuilder(EMBEDDING_DIMENSION * 12);
        literal.append('[');
        for (int i = 0; i < vector.length; i++) {
            float value = vector[i];
            if (!Float.isFinite(value)) {
                throw new IllegalArgumentException("Query embedding contains a non-finite value");
            }
            if (i > 0) {
                literal.append(',');
            }
            literal.append(Float.toString(value));
        }
        return literal.append(']').toString();
    }

    public static class SearchResult {
        public final Long id;
        public final Long pdfId;
        public final Integer chunkIndex;
        public final String chunkText;
        public final Integer tokenCount;
        public final Integer pageNumber;
        public final java.time.LocalDateTime createdAt;
        public final Double similarity;

        public SearchResult(Long id, Long pdfId, Integer chunkIndex, String chunkText,
                           Integer tokenCount, Integer pageNumber,
                           java.time.LocalDateTime createdAt, Double similarity) {
            this.id = id;
            this.pdfId = pdfId;
            this.chunkIndex = chunkIndex;
            this.chunkText = chunkText;
            this.tokenCount = tokenCount;
            this.pageNumber = pageNumber;
            this.createdAt = createdAt;
            this.similarity = similarity;
        }
    }
}
