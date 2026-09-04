package com.aasa.service;

import com.aasa.entity.DocumentChunk;
import com.aasa.entity.PdfDocument;
import com.aasa.entity.User;
import com.aasa.repository.DocumentChunkRepository;
import com.aasa.repository.PdfDocumentRepository;
import com.aasa.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration proof for the real retrieval layer of the RAG pipeline.
 *
 * OPT-IN: requires a running Postgres pointed at by the app's datasource.
 * Enable with:  set RAG_INTEGRATION_TEST=true   (then run `mvn test`)
 *
 * Runs against whichever backend VectorSearchService selects: pgvector SQL when
 * `CREATE EXTENSION vector` has been applied, otherwise the Java cosine
 * fallback. Both must satisfy the same three properties, so this suite is a
 * useful check either way — and on a plain local Postgres it is the only test
 * that exercises the fallback against real rows.
 *
 * Proves three properties the reviewer cares about:
 *  1. semantic ranking with non-zero cosine similarities,
 *  2. different queries return different chunk orderings,
 *  3. per-user scoping never leaks other users' chunks.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RAG_INTEGRATION_TEST", matches = "true")
class RagRetrievalIntegrationTest {

    private static final int DIMENSIONS = 768;

    @Autowired
    private VectorSearchService vectorSearchService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PdfDocumentRepository pdfDocumentRepository;
    @Autowired
    private DocumentChunkRepository documentChunkRepository;

    private User owner;
    private User otherUser;
    private PdfDocument ownerPdf;
    private PdfDocument otherPdf;

    @BeforeEach
    void seedData() {
        owner = userRepository.save(User.builder()
                .email("rag-it-owner-" + UUID.randomUUID() + "@test.local")
                .name("RAG IT Owner").password("test").build());
        otherUser = userRepository.save(User.builder()
                .email("rag-it-other-" + UUID.randomUUID() + "@test.local")
                .name("RAG IT Other").password("test").build());

        ownerPdf = pdfDocumentRepository.save(PdfDocument.builder()
                .user(owner).fileName("networking-notes.pdf").filePath("it/networking-notes.pdf")
                .examDate(LocalDate.now().plusDays(30)).build());
        otherPdf = pdfDocumentRepository.save(PdfDocument.builder()
                .user(otherUser).fileName("other-student.pdf").filePath("it/other-student.pdf")
                .examDate(LocalDate.now().plusDays(30)).build());

        saveChunk(ownerPdf, 0, "The OSI model defines seven network layers.", axis(10));
        saveChunk(ownerPdf, 1, "Routers forward packets between networks.", axis(20));
        saveChunk(ownerPdf, 2, "TCP provides reliable transport.", axis(30));
        // Same direction as owner chunk 0 — would win any cross-user leak.
        saveChunk(otherPdf, 0, "Other student's OSI layers summary.", axis(10));
    }

    @AfterEach
    void cleanUp() {
        documentChunkRepository.deleteAll(
                documentChunkRepository.findByPdfDocumentIdOrderByChunkIndex(ownerPdf.getId()));
        documentChunkRepository.deleteAll(
                documentChunkRepository.findByPdfDocumentIdOrderByChunkIndex(otherPdf.getId()));
        pdfDocumentRepository.deleteById(ownerPdf.getId());
        pdfDocumentRepository.deleteById(otherPdf.getId());
        userRepository.deleteById(owner.getId());
        userRepository.deleteById(otherUser.getId());
    }

    @Test
    void semanticRankingReturnsNonZeroSimilarities() {
        List<VectorSearchService.SearchResult> results =
                vectorSearchService.searchByPdfId(ownerPdf.getId(), axis(10), 3);

        assertEquals(3, results.size());
        assertEquals(0, results.get(0).chunkIndex, "axis-aligned chunk must rank first");
        assertTrue(results.get(0).similarity > 0.99, "perfect match should be ~= 1.0");
        assertTrue(results.get(1).similarity < 0.01, "orthogonal chunks should be ~= 0.0");
    }

    @Test
    void differentQueriesReturnDifferentOrderings() {
        List<VectorSearchService.SearchResult> osiQuery =
                vectorSearchService.searchByPdfId(ownerPdf.getId(), axis(10), 3);
        List<VectorSearchService.SearchResult> routerQuery =
                vectorSearchService.searchByPdfId(ownerPdf.getId(), axis(20), 3);

        assertEquals(0, osiQuery.get(0).chunkIndex);
        assertEquals(1, routerQuery.get(0).chunkIndex,
                "a different query embedding must change which chunk ranks first");
    }

    @Test
    void userIdScopeNeverLeaksOtherUsersChunks() {
        float[] query = axis(10);

        List<VectorSearchService.SearchResult> mine =
                vectorSearchService.searchByUserId(owner.getId(), query, 10);
        assertTrue(mine.stream().allMatch(r -> ownerPdf.getId().equals(r.pdfId)),
                "user-scoped search must only return the owner's chunks");

        List<VectorSearchService.SearchResult> theirs =
                vectorSearchService.searchByUserId(otherUser.getId(), query, 10);
        assertTrue(theirs.stream().allMatch(r -> otherPdf.getId().equals(r.pdfId)));
        assertEquals(1, theirs.size());
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    /** Deterministic unit vector along one axis: cosine(a,b) = dot(a,b) exactly. */
    private static float[] axis(int index) {
        float[] v = new float[DIMENSIONS];
        v[index] = 1.0f;
        return v;
    }

    private static String toLiteral(float[] vector) {
        StringBuilder sb = new StringBuilder(DIMENSIONS * 8);
        sb.append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector[i]);
        }
        return sb.append(']').toString();
    }

    private void saveChunk(PdfDocument pdf, int index, String text, float[] embedding) {
        documentChunkRepository.save(DocumentChunk.builder()
                .pdfDocument(pdf)
                .chunkIndex(index)
                .chunkText(text)
                .embedding(toLiteral(embedding))
                .tokenCount(text.split("\\s+").length)
                .pageNumber(index + 1)
                .build());
    }
}
