package com.aasa.service;

import com.aasa.entity.DocumentChunk;
import com.aasa.entity.PdfDocument;
import com.aasa.repository.DocumentChunkRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Retrieval must still work on a database without pgvector.
 *
 * <p>The integration test that exercises the SQL path is gated behind
 * {@code RAG_INTEGRATION_TEST}, so before this suite the "no extension"
 * branch — the one that actually runs on a plain local PostgreSQL — had no
 * coverage at all.</p>
 */
@ExtendWith(MockitoExtension.class)
class VectorSearchFallbackTest {

    private static final int DIMENSION = 768;

    @Mock
    private PgVectorSupport pgVectorSupport;

    @Mock
    private DocumentChunkRepository documentChunkRepository;

    @InjectMocks
    private VectorSearchService service;

    @Test
    @DisplayName("without pgvector, ranks chunks by cosine similarity in Java")
    void ranksByCosineWhenExtensionMissing() {
        when(pgVectorSupport.isVectorTypeAvailable()).thenReturn(false);
        when(documentChunkRepository.findByPdfDocumentIdOrderByChunkIndex(7L))
                .thenReturn(List.of(
                        chunk(1L, 0, "orthogonal", literal(axis(500))),
                        chunk(2L, 1, "exact match", literal(axis(10))),
                        chunk(3L, 2, "partial match", literal(mix(10, 20)))));

        List<VectorSearchService.SearchResult> results =
                service.searchByPdfId(7L, axis(10), 10);

        assertEquals(List.of(2L, 3L, 1L), results.stream().map(r -> r.id).toList());
        assertEquals(1.0, results.get(0).similarity, 1e-6);
        assertEquals(1.0 / Math.sqrt(2.0), results.get(1).similarity, 1e-6);
        assertEquals(0.0, results.get(2).similarity, 1e-6);
    }

    @Test
    @DisplayName("no SQL is issued once the extension is known to be missing")
    void neverQueriesWhenExtensionMissing() {
        when(pgVectorSupport.isVectorTypeAvailable()).thenReturn(false);
        when(documentChunkRepository.findByPdfDocumentIdOrderByChunkIndex(anyLong()))
                .thenReturn(List.of(chunk(1L, 0, "a", literal(axis(1)))));

        service.searchByPdfId(7L, axis(1), 5);
        service.searchByPdfId(7L, axis(1), 5);

        verify(pgVectorSupport, never()).query(anyString(), anyLong(), anyString(), anyInt());
        // Probed once, then cached: the answer cannot change at runtime.
        verify(pgVectorSupport).isVectorTypeAvailable();
    }

    @Test
    @DisplayName("topK caps the fallback result set")
    void respectsTopK() {
        when(pgVectorSupport.isVectorTypeAvailable()).thenReturn(false);
        when(documentChunkRepository.findByPdfDocumentIdOrderByChunkIndex(7L))
                .thenReturn(List.of(
                        chunk(1L, 0, "a", literal(axis(1))),
                        chunk(2L, 1, "b", literal(axis(2))),
                        chunk(3L, 2, "c", literal(axis(3)))));

        assertEquals(2, service.searchByPdfId(7L, axis(1), 2).size());
    }

    @Test
    @DisplayName("unusable embeddings are skipped, not scored")
    void skipsUnusableEmbeddings() {
        when(pgVectorSupport.isVectorTypeAvailable()).thenReturn(false);
        when(documentChunkRepository.findByPdfDocumentIdOrderByChunkIndex(7L))
                .thenReturn(List.of(
                        chunk(1L, 0, "not embedded yet", null),
                        chunk(2L, 1, "malformed", "0.1,0.2,0.3"),
                        chunk(3L, 2, "wrong dimension", "[0.1,0.2]"),
                        chunk(4L, 3, "zero magnitude", literal(new float[DIMENSION])),
                        chunk(5L, 4, "usable", literal(axis(10)))));

        List<VectorSearchService.SearchResult> results =
                service.searchByPdfId(7L, axis(10), 10);

        assertEquals(List.of(5L), results.stream().map(r -> r.id).toList());
    }

    @Test
    @DisplayName("a failed pgvector query falls back instead of returning nothing")
    void failedQueryFallsBackToJava() {
        when(pgVectorSupport.isVectorTypeAvailable()).thenReturn(true);
        // null = "the query failed", distinct from an empty list meaning "no matches".
        when(pgVectorSupport.query(anyString(), anyLong(), anyString(), anyInt()))
                .thenReturn(null);
        when(documentChunkRepository.findByPdfDocumentIdOrderByChunkIndex(7L))
                .thenReturn(List.of(chunk(1L, 0, "usable", literal(axis(10)))));

        List<VectorSearchService.SearchResult> results =
                service.searchByPdfId(7L, axis(10), 10);

        assertEquals(List.of(1L), results.stream().map(r -> r.id).toList());
    }

    @Test
    @DisplayName("user-scoped fallback reads through the owner-scoped query")
    void userScopedFallbackIsOwnerScoped() {
        when(pgVectorSupport.isVectorTypeAvailable()).thenReturn(false);
        when(documentChunkRepository.findByUserId(42L))
                .thenReturn(List.of(chunk(1L, 0, "owned", literal(axis(10)))));

        assertEquals(1, service.searchByUserId(42L, axis(10), 10).size());
        verify(documentChunkRepository).findByUserId(42L);
    }

    @Test
    @DisplayName("a null query embedding searches nothing")
    void nullQueryEmbedding() {
        assertTrue(service.searchByPdfId(7L, null, 10).isEmpty());
        verify(documentChunkRepository, never())
                .findByPdfDocumentIdOrderByChunkIndex(anyLong());
    }

    /** Unit basis vector: cosine against another axis is 0, against itself 1. */
    private static float[] axis(int index) {
        float[] vector = new float[DIMENSION];
        vector[index] = 1.0f;
        return vector;
    }

    /** Two axes at equal weight — cosine against either axis is 1/sqrt(2). */
    private static float[] mix(int first, int second) {
        float[] vector = new float[DIMENSION];
        vector[first] = 1.0f;
        vector[second] = 1.0f;
        return vector;
    }

    private static String literal(float[] vector) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(vector[i]);
        }
        return builder.append(']').toString();
    }

    private static DocumentChunk chunk(long id, int index, String text, String embedding) {
        return DocumentChunk.builder()
                .id(id)
                .pdfDocument(PdfDocument.builder().id(7L).build())
                .chunkIndex(index)
                .chunkText(text)
                .embedding(embedding)
                .tokenCount(12)
                .pageNumber(1)
                .build();
    }
}
