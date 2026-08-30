package com.aasa.service;

import com.aasa.entity.DocumentChunk;
import com.aasa.repository.DocumentChunkRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;

/**
 * Semantic retrieval over document chunks.
 *
 * <p>Two backends, one ranking. When the database has pgvector, similarity is
 * computed in SQL with the cosine-distance operator ({@code <=>}), which is
 * indexable and keeps the work in the database. When it does not, the same
 * cosine similarity is computed in Java over the candidate chunks. The fallback
 * exists because the extension is only present in the Docker image
 * ({@code pgvector/pgvector:pg17}); on a plain local PostgreSQL every vector
 * cast fails with {@code ERROR: type "vector" does not exist}, which used to
 * take the whole RAG answer down with it. Ranking is identical either way —
 * only the cost changes.</p>
 */
@Service
@Transactional(readOnly = true)
public class VectorSearchService {

    private static final Logger logger = Logger.getLogger(VectorSearchService.class.getName());
    private static final int EMBEDDING_DIMENSION = 768;

    /**
     * Ceiling on rows the Java fallback pulls into memory. A corpus that large
     * means the deployment should be running pgvector anyway; truncating keeps
     * the pathological case slow rather than fatal.
     */
    private static final int MAX_FALLBACK_CHUNKS = 5_000;

    @Autowired
    private PgVectorSupport pgVectorSupport;

    @Autowired
    private DocumentChunkRepository documentChunkRepository;

    /** Probed once on first search; null means "not yet known". */
    private volatile Boolean pgVectorAvailable;

    /**
     * Chunks similar to a query embedding within a single PDF.
     *
     * <p>The stored {@code embedding} column holds pgvector text literals
     * ({@code "[0.12,-0.34,...]"}), which PostgreSQL casts to the vector type at
     * query time and the Java fallback parses directly, so neither path needs a
     * row migration.</p>
     */
    public List<SearchResult> searchByPdfId(Long pdfId, float[] queryEmbedding, int topK) {
        if (queryEmbedding == null) {
            return List.of();
        }

        if (pgVectorAvailable()) {
            // Positional parameters (?1/?2/?3) + explicit CAST(): Hibernate 6
            // mis-parses named parameters when native SQL also contains
            // PostgreSQL "::" casts, sending a raw ":" to the server
            // ("syntax error at or near ':'").
            String sql = """
                    SELECT id, pdf_id, chunk_index, chunk_text, token_count, page_number, created_at,
                           1 - (CAST(embedding AS vector) <=> CAST(?2 AS vector)) AS similarity
                    FROM document_chunks
                    WHERE pdf_id = ?1
                    ORDER BY CAST(embedding AS vector) <=> CAST(?2 AS vector)
                    LIMIT ?3
                    """;
            List<Object[]> rows = pgVectorSupport.query(
                    sql, pdfId, toVectorLiteral(queryEmbedding), topK);
            if (rows != null) {
                return mapResults(rows);
            }
            // The type existed but the query still failed; stop trusting the
            // probe and use the fallback from here on.
            demotePgVector();
        }

        return cosineFallback(
                documentChunkRepository.findByPdfDocumentIdOrderByChunkIndex(pdfId),
                queryEmbedding, topK);
    }

    /**
     * Chunks similar to a query embedding across every PDF owned by a user.
     *
     * <p>Both paths scope by owner — the SQL joins {@code pdf_documents} and the
     * fallback query filters on {@code pdfDocument.user.id} — so a user can
     * never retrieve another user's chunks.</p>
     */
    public List<SearchResult> searchByUserId(Long userId, float[] queryEmbedding, int topK) {
        if (queryEmbedding == null) {
            return List.of();
        }

        if (pgVectorAvailable()) {
            String sql = """
                    SELECT dc.id, dc.pdf_id, dc.chunk_index, dc.chunk_text, dc.token_count, dc.page_number, dc.created_at,
                           1 - (CAST(dc.embedding AS vector) <=> CAST(?2 AS vector)) AS similarity
                    FROM document_chunks dc
                    JOIN pdf_documents pd ON pd.id = dc.pdf_id
                    WHERE pd.user_id = ?1
                    ORDER BY CAST(dc.embedding AS vector) <=> CAST(?2 AS vector)
                    LIMIT ?3
                    """;
            List<Object[]> rows = pgVectorSupport.query(
                    sql, userId, toVectorLiteral(queryEmbedding), topK);
            if (rows != null) {
                return mapResults(rows);
            }
            demotePgVector();
        }

        return cosineFallback(
                documentChunkRepository.findByUserId(userId), queryEmbedding, topK);
    }

    private boolean pgVectorAvailable() {
        Boolean cached = pgVectorAvailable;
        if (cached != null) {
            return cached;
        }
        boolean available = pgVectorSupport.isVectorTypeAvailable();
        pgVectorAvailable = available;
        if (!available) {
            logger.warning("pgvector is not installed on this database - semantic search "
                    + "will run in Java. Install the extension (or use the pgvector Docker "
                    + "image) for indexed retrieval.");
        }
        return available;
    }

    private void demotePgVector() {
        pgVectorAvailable = Boolean.FALSE;
    }

    /**
     * Cosine similarity in Java, ranked exactly as pgvector would rank it.
     *
     * <p>Chunks with a missing, malformed, zero-magnitude, or wrong-dimension
     * embedding are skipped rather than scored, so a partially embedded document
     * degrades to its usable chunks instead of failing.</p>
     */
    private List<SearchResult> cosineFallback(List<DocumentChunk> chunks,
                                              float[] queryEmbedding, int topK) {
        if (chunks == null || chunks.isEmpty() || topK <= 0) {
            return List.of();
        }
        if (chunks.size() > MAX_FALLBACK_CHUNKS) {
            logger.warning("Ranking only the first " + MAX_FALLBACK_CHUNKS + " of "
                    + chunks.size() + " chunks in the Java fallback");
            chunks = chunks.subList(0, MAX_FALLBACK_CHUNKS);
        }

        double queryMagnitude = magnitude(queryEmbedding);
        if (queryEmbedding.length != EMBEDDING_DIMENSION || queryMagnitude == 0.0) {
            return List.of();
        }

        List<SearchResult> scored = new ArrayList<>();
        for (DocumentChunk chunk : chunks) {
            float[] embedding = parseEmbedding(chunk.getEmbedding());
            if (embedding == null) {
                continue;
            }
            double embeddingMagnitude = magnitude(embedding);
            if (embeddingMagnitude == 0.0) {
                continue;
            }
            double similarity = dot(queryEmbedding, embedding)
                    / (queryMagnitude * embeddingMagnitude);
            scored.add(new SearchResult(
                    chunk.getId(),
                    chunk.getPdfDocument() != null ? chunk.getPdfDocument().getId() : null,
                    chunk.getChunkIndex(),
                    chunk.getChunkText(),
                    chunk.getTokenCount() != null ? chunk.getTokenCount() : 0,
                    chunk.getPageNumber() != null ? chunk.getPageNumber() : 0,
                    chunk.getCreatedAt(),
                    similarity));
        }

        scored.sort(Comparator.comparingDouble((SearchResult r) -> r.similarity).reversed());
        return scored.size() > topK ? new ArrayList<>(scored.subList(0, topK)) : scored;
    }

    /** Parses a stored {@code "[0.12,-0.34,...]"} literal, or null if unusable. */
    private float[] parseEmbedding(String literal) {
        if (literal == null) {
            return null;
        }
        String body = literal.trim();
        if (body.length() < 2 || body.charAt(0) != '['
                || body.charAt(body.length() - 1) != ']') {
            return null;
        }
        body = body.substring(1, body.length() - 1);
        if (body.isBlank()) {
            return null;
        }

        String[] parts = body.split(",");
        if (parts.length != EMBEDDING_DIMENSION) {
            return null;
        }
        float[] vector = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                float value = Float.parseFloat(parts[i].trim());
                if (!Float.isFinite(value)) {
                    return null;
                }
                vector[i] = value;
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return vector;
    }

    private double dot(float[] a, float[] b) {
        double sum = 0.0;
        for (int i = 0; i < a.length; i++) {
            sum += (double) a[i] * b[i];
        }
        return sum;
    }

    private double magnitude(float[] vector) {
        double sum = 0.0;
        for (float value : vector) {
            sum += (double) value * value;
        }
        return Math.sqrt(sum);
    }

    private List<SearchResult> mapResults(List<Object[]> rows) {
        List<SearchResult> results = new ArrayList<>();
        for (Object[] row : rows) {
            results.add(new SearchResult(
                    ((Number) row[0]).longValue(),   // id
                    ((Number) row[1]).longValue(),   // pdf_id
                    ((Number) row[2]).intValue(),    // chunk_index
                    (String) row[3],                 // chunk_text
                    row[4] != null ? ((Number) row[4]).intValue() : 0,  // token_count
                    row[5] != null ? ((Number) row[5]).intValue() : 0,  // page_number
                    row[6] != null ? ((java.sql.Timestamp) row[6]).toLocalDateTime() : null,
                    ((Number) row[7]).doubleValue()  // similarity
            ));
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
