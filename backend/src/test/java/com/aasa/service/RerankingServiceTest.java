package com.aasa.service;

import com.aasa.service.VectorSearchService.SearchResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the hybrid reranker (vector + keyword + title signals).
 */
class RerankingServiceTest {

    private final RerankingService rerankingService = new RerankingService();

    private SearchResult chunk(long id, String text, double similarity) {
        return new SearchResult(id, 1L, (int) id, text, 100, (int) id, null, similarity);
    }

    @Test
    void returnsAtMostTopNFromLargeCandidatePool() {
        List<SearchResult> candidates = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            candidates.add(chunk(i, "generic content number " + i, 0.9 - i * 0.01));
        }
        List<RerankingService.RerankedResult> top = rerankingService.rerank(
                "what is a router", "networking basics", candidates, 5);
        assertEquals(5, top.size(), "reranker must trim the pool to top N");
    }

    @Test
    void rerankingChangesTheOrderOfVectorResults() {
        // Chunk A wins on vector similarity alone but does not answer the question.
        // Chunk B has slightly lower similarity but contains the question keywords.
        List<SearchResult> candidates = new ArrayList<>();
        candidates.add(chunk(1, "Completely unrelated discussion about cooking pasta.", 0.90));
        candidates.add(chunk(2, "A router forwards packets between networks using routing tables.", 0.85));

        List<RerankingService.RerankedResult> top = rerankingService.rerank(
                "how does a router forward packets", "computer networks", candidates, 2);

        assertEquals(2L, top.get(0).searchResult.id,
                "keyword evidence must promote the relevant chunk above the vector-only winner");
        assertEquals(2, top.get(0).retrievalRank,
                "the promoted chunk was originally second in vector order");
        assertTrue(top.get(0).rerankScore > top.get(1).rerankScore, "results sorted by rerank score");
    }

    @Test
    void keywordOverlapBoostsMatchingChunk() {
        Set<String> query = rerankingService.tokenize("osi model layers");
        Set<String> matching = rerankingService.tokenize("The OSI model has seven layers.");
        Set<String> other = rerankingService.tokenize("TCP ports and sockets.");

        double hit = rerankingService.keywordOverlap(query, matching);
        double miss = rerankingService.keywordOverlap(query, other);

        assertTrue(hit > miss, "chunk containing question terms must score higher");
        assertTrue(hit > 0.5 && miss < hit);
    }

    @Test
    void stopWordsAreRemovedFromTokenization() {
        Set<String> tokens = rerankingService.tokenize("What is the OSI model, and why is it important?");
        assertTrue(tokens.contains("osi"));
        assertTrue(tokens.contains("model"));
        assertTrue(!tokens.contains("the"), "stop words must be filtered");
        assertTrue(!tokens.contains("what"));
    }

    @Test
    void emptyCandidatesReturnEmptyList() {
        assertTrue(rerankingService.rerank("any question", "title", List.of(), 5).isEmpty());
        assertTrue(rerankingService.rerank("q", "t", null, 5).isEmpty());
    }

    @Test
    void scoresStayWithinUnitRange() {
        List<SearchResult> candidates = new ArrayList<>();
        candidates.add(chunk(1, "router packet forwarding", 1.0));
        candidates.add(chunk(2, "nothing relevant", 0.0));
        for (RerankingService.RerankedResult r :
                rerankingService.rerank("router", "router", candidates, 2)) {
            assertTrue(r.rerankScore >= 0.0 && r.rerankScore <= 1.0,
                    "rerankScore must stay in [0,1], was " + r.rerankScore);
        }
    }
}
