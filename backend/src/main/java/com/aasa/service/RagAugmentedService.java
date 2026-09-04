package com.aasa.service;

import com.aasa.dto.RagAnswerDto;
import com.aasa.dto.RagChunkSource;
import com.aasa.dto.PredefinedAnswerDto;
import com.aasa.entity.DocumentChunk;
import com.aasa.entity.PdfDocument;
import com.aasa.entity.Topic;
import com.aasa.entity.User;
import com.aasa.repository.DocumentChunkRepository;
import com.aasa.repository.PdfDocumentRepository;
import com.aasa.repository.TopicRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Service
public class RagAugmentedService {

    private static final Logger logger = Logger.getLogger(RagAugmentedService.class.getName());

    private static final int TOP_K_CHUNKS = 5;
    private static final int TOP_K_CANDIDATES = 20;   // wide retrieval pool before reranking
    private static final int QUIZ_CONTEXT_CHUNKS = 8; // reranked context size for quiz generation
    private static final int MAX_OVERVIEW_CHUNKS = 12;
    private static final int MAX_PREDEFINED_TOPICS = 20;
    private static final int TIMEOUT_SECONDS = 120;
    private static final int MAX_ATTEMPTS = 3;
    private static final int EMBEDDING_DIMENSION = 768;
    private static final String[][] GENERATION_ENDPOINTS = {
            {"v1beta", "gemini-3.5-flash"},
            {"v1", "gemini-2.5-flash"}
    };

    @Value("${gemini.api.key}")
    private String apiKey;

    @Autowired
    private EmbeddingService embeddingService;

    @Autowired
    private VectorSearchService vectorSearchService;

    @Autowired
    private RerankingService rerankingService;

    @Autowired
    private DocumentChunkRepository documentChunkRepository;

    @Autowired
    private PdfDocumentRepository pdfDocumentRepository;

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private TextChunkingService textChunkingService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    private final TransactionTemplate transactionTemplate;

    public RagAugmentedService(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * Answer a question using RAG: embed query → search chunks → augment prompt → Gemini.
     * If embeddings are not available, returns a message indicating the feature is disabled.
     */
    public RagAnswerDto answerQuestion(User user, String question, Long pdfId) {
        logger.info(
                "RAG query from user " + user.getId()
                        + " with " + question.length() + " characters"
        );

        PdfDocument selectedPdf = pdfId == null ? null : getPdfForUser(pdfId, user.getId());
        if (pdfId != null && selectedPdf == null) {
            throw new SecurityException("PDF not found");
        }

        List<VectorSearchService.SearchResult> searchResults;
        if (isOverviewQuestion(question)) {
            List<PdfDocument> targetPdfs = selectedPdf == null
                    ? pdfDocumentRepository.findByUserId(user.getId())
                    : List.of(selectedPdf);
            searchResults = getRepresentativeChunks(targetPdfs);
        } else {
            float[] queryEmbedding = embeddingService.generateEmbedding(question);
            if (queryEmbedding == null) {
                logger.warning("Embeddings not available - RAG search disabled");
                return RagAnswerDto.builder()
                        .answer("I'm sorry, the semantic search feature is currently unavailable. Please try again later or contact support if this issue persists.")
                        .sources(List.of())
                        .build();
            }

            // Retrieve a generous candidate pool; hybrid reranking selects the final context below.
            searchResults = pdfId != null
                    ? vectorSearchService.searchByPdfId(pdfId, queryEmbedding, TOP_K_CANDIDATES)
                    : vectorSearchService.searchByUserId(user.getId(), queryEmbedding, TOP_K_CANDIDATES);
        }

        if (searchResults.isEmpty()) {
            logger.warning("No relevant chunks found for query");
            return RagAnswerDto.builder()
                    .answer("I couldn't find any relevant information in your study materials to answer this question. Please make sure you've uploaded and analyzed PDFs first.")
                    .sources(List.of())
                    .build();
        }

        // 3. Hybrid reranking: vector similarity alone is not enough — combine it with keyword
        //    overlap and title match, then keep only the top chunks for grounded generation.
        Map<Long, Double> rerankScores = new HashMap<>();
        Map<Long, Integer> retrievalRanks = new HashMap<>();
        if (!isOverviewQuestion(question)) {
            String title = selectedPdf != null && selectedPdf.getFileName() != null
                    ? selectedPdf.getFileName()
                    : "";
            List<RerankingService.RerankedResult> reranked = rerankingService.rerank(
                    question, title, searchResults, TOP_K_CHUNKS);
            if (!reranked.isEmpty()) {
                searchResults = reranked.stream()
                        .map(r -> r.searchResult)
                        .collect(Collectors.toList());
                for (RerankingService.RerankedResult r : reranked) {
                    rerankScores.put(r.searchResult.id, r.rerankScore);
                    retrievalRanks.put(r.searchResult.id, r.retrievalRank);
                }
                logger.info("Reranking kept " + reranked.size() + " of "
                        + reranked.get(0).retrievalRank + "+ retrieved chunks");
            }
        }

        // 4. Build context from chunks
        StringBuilder context = new StringBuilder();
        List<RagChunkSource> sources = new ArrayList<>();

        for (int i = 0; i < searchResults.size(); i++) {
            VectorSearchService.SearchResult sr = searchResults.get(i);
            context.append("[Source ").append(i + 1).append("]\n")
                   .append(sr.chunkText).append("\n\n");

            // Get PDF file name
            String pdfFileName = "";
            try {
                PdfDocument pdf = pdfDocumentRepository.findById(sr.pdfId).orElse(null);
                if (pdf != null) {
                    pdfFileName = pdf.getFileName();
                }
            } catch (Exception e) {
                logger.warning("Could not fetch PDF name for ID " + sr.pdfId);
            }

            sources.add(RagChunkSource.builder()
                    .chunkId(sr.id)
                    .pdfId(sr.pdfId)
                    .chunkIndex(sr.chunkIndex)
                    .text(sr.chunkText.length() > 200 ? sr.chunkText.substring(0, 200) + "..." : sr.chunkText)
                    .pageNumber(sr.pageNumber)
                    .similarity(sr.similarity)
                    .rerankScore(rerankScores.get(sr.id))
                    .rank(i + 1)
                    .retrievalRank(retrievalRanks.get(sr.id))
                    .pdfFileName(pdfFileName)
                    .build());
        }

        // 4. Build augmented prompt
        String prompt = buildRagPrompt(question, context.toString());

        // 5. Call Gemini with context
        String answer = callGeminiWithContext(prompt);

        return RagAnswerDto.builder()
                .answer(answer)
                .sources(sources)
                .build();
    }

    @Transactional(readOnly = true)
    public List<PredefinedAnswerDto> getPredefinedAnswers(User user, Long pdfId) {
        List<Topic> topics;
        if (pdfId != null) {
            if (getPdfForUser(pdfId, user.getId()) == null) {
                throw new SecurityException("PDF not found");
            }
            topics = topicRepository.findByPdfDocumentId(pdfId);
        } else {
            topics = topicRepository.findByUserIdOrderByPriority(user.getId());
        }

        if (topics.isEmpty()) {
            return List.of();
        }

        List<Topic> limitedTopics = topics.stream()
                .limit(MAX_PREDEFINED_TOPICS)
                .toList();
        List<PredefinedAnswerDto> answers = new ArrayList<>();

        StringBuilder overview = new StringBuilder(
                "Your study material contains these main topics:\n\n"
        );
        for (int i = 0; i < limitedTopics.size(); i++) {
            Topic topic = limitedTopics.get(i);
            overview.append(i + 1)
                    .append(". **")
                    .append(topic.getTitle())
                    .append("**");
            if (topic.getDescription() != null && !topic.getDescription().isBlank()) {
                overview.append(" — ").append(topic.getDescription());
            }
            overview.append("\n");
        }

        answers.add(PredefinedAnswerDto.builder()
                .id("overview")
                .question("What topics are covered in my study material?")
                .answer(overview.toString().trim())
                .type("OVERVIEW")
                .build());

        for (Topic topic : limitedTopics) {
            String description = topic.getDescription() == null
                    || topic.getDescription().isBlank()
                    ? "This topic was identified in your uploaded study material."
                    : topic.getDescription().trim();

            answers.add(PredefinedAnswerDto.builder()
                    .id("topic-" + topic.getId() + "-what")
                    .topicId(topic.getId())
                    .topicTitle(topic.getTitle())
                    .question("What is " + topic.getTitle() + "?")
                    .answer(description)
                    .type("TOPIC_OVERVIEW")
                    .build());

            int quizCount = topic.getQuizzes() == null ? 0 : topic.getQuizzes().size();
            String studyAnswer = buildStudyGuidance(topic, description, quizCount);
            answers.add(PredefinedAnswerDto.builder()
                    .id("topic-" + topic.getId() + "-study")
                    .topicId(topic.getId())
                    .topicTitle(topic.getTitle())
                    .question("How should I study " + topic.getTitle() + "?")
                    .answer(studyAnswer)
                    .type("STUDY_GUIDANCE")
                    .build());
        }

        return answers;
    }

    private String buildStudyGuidance(Topic topic, String description, int quizCount) {
        String complexity = describeScore(topic.getComplexityScore());
        String importance = describeScore(topic.getImportanceScore());

        StringBuilder guidance = new StringBuilder();
        guidance.append("**Study profile:** ")
                .append(complexity)
                .append(" complexity and ")
                .append(importance)
                .append(" importance.\n\n")
                .append("1. Read the topic overview and identify its main terms.\n")
                .append("2. Break the concept into smaller parts and explain each part in your own words.\n");

        if (quizCount > 0) {
            guidance.append("3. Complete the ")
                    .append(quizCount)
                    .append(" saved practice question")
                    .append(quizCount == 1 ? "" : "s")
                    .append(" for this topic and review every explanation.\n");
        } else {
            guidance.append("3. Create a short example and test whether you can explain it without notes.\n");
        }

        guidance.append("\n**Topic overview:** ").append(description);
        return guidance.toString();
    }

    private String describeScore(Double score) {
        if (score == null) {
            return "moderate";
        }
        if (score >= 0.7) {
            return "high";
        }
        if (score >= 0.4) {
            return "moderate";
        }
        return "low";
    }

    /**
     * Generate quizzes for a topic using RAG-augmented context instead of full text.
     */
    public String generateQuizContext(Long pdfId, String topicTitle) {
        // Search for chunks related to this topic
        float[] topicEmbedding = embeddingService.generateEmbedding(topicTitle);
        if (topicEmbedding == null) {
            logger.warning("Could not generate an embedding for quiz context");
            return null;
        }
        // Retrieve a wide candidate pool, then hybrid-rerank down to the best quiz-context chunks.
        List<VectorSearchService.SearchResult> candidates =
                vectorSearchService.searchByPdfId(pdfId, topicEmbedding, TOP_K_CANDIDATES);

        if (candidates.isEmpty()) {
            return null;
        }

        List<VectorSearchService.SearchResult> results = rerankingService.rerank(
                        topicTitle, topicTitle, candidates, QUIZ_CONTEXT_CHUNKS).stream()
                .map(r -> r.searchResult)
                .collect(Collectors.toList());

        if (results.isEmpty()) {
            return null;
        }

        return results.stream()
                .map(r -> r.chunkText)
                .collect(Collectors.joining("\n\n---\n\n"));
    }

    private boolean isOverviewQuestion(String question) {
        String normalized = question == null ? "" : question.toLowerCase();
        return normalized.contains("summarize")
                || normalized.contains("summary")
                || normalized.contains("overview")
                || normalized.contains("whole pdf")
                || normalized.contains("entire pdf")
                || normalized.contains("explain the pdf")
                || normalized.contains("all topic")
                || normalized.contains("main topic");
    }

    private List<VectorSearchService.SearchResult> getRepresentativeChunks(
            List<PdfDocument> pdfs
    ) {
        if (pdfs == null || pdfs.isEmpty()) {
            return List.of();
        }

        List<VectorSearchService.SearchResult> results = new ArrayList<>();
        int chunksPerPdf = Math.max(1, MAX_OVERVIEW_CHUNKS / pdfs.size());

        for (PdfDocument pdf : pdfs) {
            List<DocumentChunk> chunks =
                    documentChunkRepository.findByPdfDocumentIdOrderByChunkIndex(pdf.getId());
            int sampleCount = Math.min(chunksPerPdf, chunks.size());

            for (int i = 0; i < sampleCount; i++) {
                int position = sampleCount == 1
                        ? 0
                        : (int) Math.round(i * (chunks.size() - 1.0) / (sampleCount - 1.0));
                DocumentChunk chunk = chunks.get(position);
                results.add(new VectorSearchService.SearchResult(
                        chunk.getId(),
                        pdf.getId(),
                        chunk.getChunkIndex(),
                        chunk.getChunkText(),
                        chunk.getTokenCount(),
                        chunk.getPageNumber(),
                        chunk.getCreatedAt(),
                        null
                ));
            }

            if (results.size() >= MAX_OVERVIEW_CHUNKS) {
                break;
            }
        }

        return results.size() <= MAX_OVERVIEW_CHUNKS
                ? results
                : new ArrayList<>(results.subList(0, MAX_OVERVIEW_CHUNKS));
    }

    private String buildRagPrompt(String question, String context) {
        return "You are an AI study assistant helping a student understand their course material.\n\n" +
               "RULES:\n" +
               "1. Answer using ONLY the [Source 1] ... [Source N] blocks provided in CONTEXT below.\n" +
               "2. Do NOT invent facts, examples, numbers, or terms that are not present in CONTEXT.\n" +
               "3. If CONTEXT does not contain enough information to answer, reply exactly: 'The provided study material does not contain enough information to answer this.'\n" +
               "4. Cite EVERY factual statement with an inline marker such as [Source 1] or [Source 2].\n" +
               "5. Do NOT add a Sources section at the end; the interface displays sources separately.\n" +
               "6. For summaries or overviews, organize the answer by the major topics visible across CONTEXT.\n\n" +
               "CONTEXT:\n" + context + "\n\n" +
               "QUESTION: " + question + "\n\n" +
               "Provide a clear, educational answer following all rules above.";
    }

    private String callGeminiWithContext(String prompt) {
        List<String> errors = new ArrayList<>();
        for (String[] endpoint : GENERATION_ENDPOINTS) {
            String apiVersion = endpoint[0];
            String model = endpoint[1];
            try {
                return callGeminiModel(prompt, apiVersion, model);
            } catch (Exception e) {
                logger.warning("RAG generation with " + model + " failed: " + e.getMessage());
                errors.add(model + ": " + e.getMessage());
            }
        }

        logger.severe("All RAG generation models failed: " + String.join(" | ", errors));
        return "I'm sorry, the AI answer service is temporarily unavailable. Please try again later.";
    }

    private String callGeminiModel(String prompt, String apiVersion, String model)
            throws Exception {
        if (apiKey == null || apiKey.isBlank() || apiKey.contains("YOUR_")) {
            throw new IllegalStateException("Gemini API key is not configured");
        }

        String url = "https://generativelanguage.googleapis.com/" + apiVersion
                + "/models/" + model + ":generateContent";

        var root = objectMapper.createObjectNode();
        var contents = root.putArray("contents");
        var content = contents.addObject();
        var parts = content.putArray("parts");
        parts.addObject().put("text", prompt);

        var genConfig = root.putObject("generationConfig");
        genConfig.put("temperature", 0.3);
        genConfig.put("maxOutputTokens", 1024);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(
                        objectMapper.writeValueAsString(root)
                ))
                .build();

        Exception lastError = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                HttpResponse<String> response = httpClient.send(
                        request,
                        HttpResponse.BodyHandlers.ofString()
                );
                int status = response.statusCode();

                if (status == 200) {
                    return extractTextFromResponse(response.body());
                }

                if ((status == 429 || status == 503) && attempt < MAX_ATTEMPTS) {
                    long waitMs = (long) Math.pow(2, attempt) * 2_000L;
                    logger.warning(
                            model + " returned HTTP " + status + ", retry "
                                    + attempt + "/" + MAX_ATTEMPTS
                    );
                    Thread.sleep(waitMs);
                    continue;
                }

                throw new IllegalStateException(
                        "HTTP " + status + ": " + parseGeminiError(response.body())
                );
            } catch (java.io.IOException e) {
                lastError = e;
                if (attempt >= MAX_ATTEMPTS) {
                    break;
                }
                Thread.sleep((long) Math.pow(2, attempt) * 1_000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Gemini request was interrupted", e);
            }
        }

        throw new IllegalStateException(
                "Gemini request failed after " + MAX_ATTEMPTS + " attempts",
                lastError
        );
    }

    private String parseGeminiError(String responseBody) {
        try {
            String message = objectMapper.readTree(responseBody)
                    .path("error")
                    .path("message")
                    .asText("");
            return message.isBlank() ? "Unknown API error" : message;
        } catch (Exception ignored) {
            return "Unknown API error";
        }
    }

    private String extractTextFromResponse(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode candidates = root.path("candidates");
        if (candidates.isEmpty()) {
            return "I couldn't generate an answer. The model returned no response.";
        }

        return candidates.get(0)
                .path("content")
                .path("parts")
                .get(0)
                .path("text")
                .asText("No answer generated.");
    }

    /**
     * Recreate and store a PDF's text chunks.
     * Runs in its own transaction after the PDF upload transaction commits.
     */
    @Transactional
    public void processPdfForRag(PdfDocument pdfDocument) {
        logger.info("Processing PDF " + pdfDocument.getId() + " for RAG pipeline");

        // Delete existing chunks for this PDF
        documentChunkRepository.deleteByPdfDocumentId(pdfDocument.getId());

        // Chunk the document
        List<DocumentChunk> chunks = chunkDocument(pdfDocument);
        if (chunks.isEmpty()) {
            logger.warning("No chunks generated for PDF " + pdfDocument.getId());
            return;
        }

        // Save chunks first to get IDs (within transaction)
        List<DocumentChunk> savedChunks = documentChunkRepository.saveAll(chunks);
        logger.info("Saved " + savedChunks.size() + " chunks for PDF " + pdfDocument.getId());
        
        // Flush to ensure chunks are persisted before generating embeddings
        documentChunkRepository.flush();
    }
    
    /**
     * Generate embeddings for saved chunks (called outside transaction).
     */
    public void generateEmbeddingsForChunks(PdfDocument pdfDocument) {
        logger.info("Generating embeddings for PDF " + pdfDocument.getId());
        
        List<DocumentChunk> savedChunks = documentChunkRepository.findByPdfDocumentIdOrderByChunkIndex(pdfDocument.getId());
        if (savedChunks.isEmpty()) {
            logger.warning("No chunks found for PDF " + pdfDocument.getId());
            return;
        }

        generateAndAttachEmbeddings(savedChunks, pdfDocument.getId());
        documentChunkRepository.saveAllAndFlush(savedChunks);
        logger.info("Embedded " + savedChunks.size() + " chunks for PDF " + pdfDocument.getId());
    }

    /**
     * Rebuild a PDF's RAG index without deleting the working index until all
     * replacement embeddings have been generated successfully.
     */
    public void reprocessPdfForRag(PdfDocument pdfDocument) {
        logger.info("Safely reprocessing PDF " + pdfDocument.getId() + " for RAG");

        List<DocumentChunk> replacementChunks = chunkDocument(pdfDocument);
        if (replacementChunks.isEmpty()) {
            throw new IllegalStateException(
                    "No text chunks could be generated for this PDF"
            );
        }

        // External API call happens before any database mutation.
        generateAndAttachEmbeddings(replacementChunks, pdfDocument.getId());

        transactionTemplate.executeWithoutResult(status -> {
            documentChunkRepository.deleteByPdfDocumentId(pdfDocument.getId());
            documentChunkRepository.flush();
            documentChunkRepository.saveAllAndFlush(replacementChunks);
        });

        logger.info(
                "Atomically replaced RAG index with " + replacementChunks.size()
                        + " chunks for PDF " + pdfDocument.getId()
        );
    }

    private void generateAndAttachEmbeddings(
            List<DocumentChunk> chunks,
            Long pdfId
    ) {
        List<String> texts = chunks.stream()
                .map(DocumentChunk::getChunkText)
                .collect(Collectors.toList());

        List<float[]> embeddings = embeddingService.generateEmbeddings(texts);
        
        if (embeddings == null || embeddings.size() != chunks.size()) {
            throw new IllegalStateException(
                    "Embedding count did not match the saved PDF chunk count"
            );
        }

        for (int i = 0; i < chunks.size(); i++) {
            float[] embedding = embeddings.get(i);
            if (embedding == null) {
                throw new IllegalStateException(
                        "Embedding generation failed for PDF " + pdfId
                                + " chunk " + i
                );
            }
            chunks.get(i).setEmbedding(toVectorLiteral(embedding));
        }
    }

    private List<DocumentChunk> chunkDocument(PdfDocument pdfDocument) {
        return textChunkingService.chunkDocument(pdfDocument);
    }

    private String toVectorLiteral(float[] vector) {
        if (vector == null || vector.length != EMBEDDING_DIMENSION) {
            throw new IllegalArgumentException(
                    "Expected a " + EMBEDDING_DIMENSION + "-dimension document embedding"
            );
        }

        StringBuilder literal = new StringBuilder(EMBEDDING_DIMENSION * 12);
        literal.append('[');
        for (int i = 0; i < vector.length; i++) {
            float value = vector[i];
            if (!Float.isFinite(value)) {
                throw new IllegalArgumentException(
                        "Document embedding contains a non-finite value"
                );
            }
            if (i > 0) {
                literal.append(',');
            }
            literal.append(Float.toString(value));
        }
        return literal.append(']').toString();
    }

    /**
     * Get a PDF document if it belongs to the specified user.
     */
    public PdfDocument getPdfForUser(Long pdfId, Long userId) {
        PdfDocument pdf = pdfDocumentRepository.findById(pdfId).orElse(null);
        if (pdf != null && pdf.getUser().getId().equals(userId)) {
            return pdf;
        }
        return null;
    }
}
