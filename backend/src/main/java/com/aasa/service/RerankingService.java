package com.aasa.service;

import com.aasa.service.VectorSearchService.SearchResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hybrid reranker for the RAG pipeline.
 *
 * Raw vector (cosine) retrieval is a strong but imperfect signal: it can rank an
 * embedding-similar chunk above a chunk that literally answers the question. This
 * service re-scores every retrieved candidate with three complementary signals:
 *
 *   rerankScore = 0.70 * vectorSimilarity   (semantic closeness from pgvector)
 *               + 0.20 * keywordOverlap     (question terms present in the chunk)
 *               + 0.10 * titleMatch         (topic/title words shared by question AND chunk)
 *
 * Candidates are returned in descending rerankScore order, trimmed to the top N,
 * and only those are sent to the LLM for grounded generation.
 */
@Service
public class RerankingService {

    private static final double WEIGHT_VECTOR = 0.70;
    private static final double WEIGHT_KEYWORD = 0.20;
    private static final double WEIGHT_TITLE = 0.10;

    private static final Pattern TOKEN_PATTERN = Pattern.compile("[a-z0-9]+");
    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "the", "and", "or", "but", "of", "in", "on", "at", "for", "to", "from",
            "is", "are", "was", "were", "be", "been", "being", "do", "does", "did", "that",
            "this", "these", "those", "it", "its", "as", "by", "not", "you", "your", "please",
            "what", "which", "who", "whom", "whose", "when", "where", "why", "how",
            "explain", "describe", "define", "tell", "about", "with", "give", "me", "can"
    );

    /**
     * Reranks candidates by hybrid score (descending) and returns at most {@code topN}.
     *
     * @param question   the user's question (keyword signal source)
     * @param title      the PDF/topic title (topical anchor signal source)
     * @param candidates raw vector-search results, already ordered by similarity
     * @param topN       how many chunks survive reranking
     */
    public List<RerankedResult> rerank(String question, String title,
                                       List<SearchResult> candidates, int topN) {
        if (candidates == null || candidates.isEmpty() || topN <= 0) {
            return List.of();
        }

        Set<String> queryTokens = tokenize(question);
        Set<String> titleTokens = tokenize(title);

        List<RerankedResult> scored = new ArrayList<>(candidates.size());
        for (int i = 0; i < candidates.size(); i++) {
            SearchResult candidate = candidates.get(i);
            double vectorScore = candidate.similarity != null ? candidate.similarity : 0.0;
            Set<String> chunkTokens = tokenize(candidate.chunkText);
            double keywordScore = keywordOverlap(queryTokens, chunkTokens);
            double titleScore = titleMatch(queryTokens, titleTokens, chunkTokens);

            double score = WEIGHT_VECTOR * vectorScore
                    + WEIGHT_KEYWORD * keywordScore
                    + WEIGHT_TITLE * titleScore;

            scored.add(new RerankedResult(candidate, clamp01(score), i + 1));
        }

        scored.sort((a, b) -> Double.compare(b.rerankScore, a.rerankScore));
        return scored.size() <= topN ? scored : new ArrayList<>(scored.subList(0, topN));
    }

    /** Fraction of question tokens that also appear in the chunk (0..1). */
    double keywordOverlap(Set<String> queryTokens, Set<String> chunkTokens) {
        if (queryTokens.isEmpty()) {
            return 0.0;
        }
        long hits = queryTokens.stream().filter(chunkTokens::contains).count();
        return (double) hits / queryTokens.size();
    }

    /** 1.0 when a title word appears in BOTH the question and the chunk, else 0.0. */
    double titleMatch(Set<String> queryTokens, Set<String> titleTokens, Set<String> chunkTokens) {
        if (titleTokens.isEmpty() || queryTokens.isEmpty()) {
            return 0.0;
        }
        boolean inQuestion = queryTokens.stream().anyMatch(titleTokens::contains);
        boolean inChunk = chunkTokens.stream().anyMatch(titleTokens::contains);
        return inQuestion && inChunk ? 1.0 : 0.0;
    }

    /** Lowercases, extracts alphanumeric tokens, removes stop words. */
    public Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        Set<String> tokens = new HashSet<>();
        Matcher matcher = TOKEN_PATTERN.matcher(text.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            String token = matcher.group();
            if (!STOP_WORDS.contains(token)) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    /** A candidate with its rerank score and its original position in the vector results. */
    public static class RerankedResult {
        public final SearchResult searchResult;
        public final double rerankScore;
        public final int retrievalRank;

        public RerankedResult(SearchResult searchResult, double rerankScore, int retrievalRank) {
            this.searchResult = searchResult;
            this.rerankScore = rerankScore;
            this.retrievalRank = retrievalRank;
        }
    }
}