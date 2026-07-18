package com.aasa.service;

import com.aasa.dto.RagAnswerDto;
import com.aasa.dto.RagChunkSource;
import com.aasa.entity.DocumentChunk;
import com.aasa.entity.PdfDocument;
import com.aasa.entity.User;
import com.aasa.repository.DocumentChunkRepository;
import com.aasa.repository.PdfDocumentRepository;
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
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Service
public class RagAugmentedService {

    private static final Logger logger = Logger.getLogger(RagAugmentedService.class.getName());

    private static final int TOP_K_CHUNKS = 5;
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
    private DocumentChunkRepository documentChunkRepository;

    @Autowired
    private PdfDocumentRepository pdfDocumentRepository;

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

        if (pdfId != null && getPdfForUser(pdfId, user.getId()) == null) {
            throw new SecurityException("PDF not found");
        }

        // 1. Generate embedding for the question
        float[] queryEmbedding = embeddingService.generateEmbedding(question);
        
        // Check if embeddings are available
        if (queryEmbedding == null) {
            logger.warning("Embeddings not available - RAG search disabled");
            return RagAnswerDto.builder()
                    .answer("I'm sorry, the semantic search feature is currently unavailable. Please try again later or contact support if this issue persists.")
                    .sources(List.of())
                    .build();
        }

        // 2. Search for relevant chunks
        List<VectorSearchService.SearchResult> searchResults;
        if (pdfId != null) {
            searchResults = vectorSearchService.searchByPdfId(pdfId, queryEmbedding, TOP_K_CHUNKS);
        } else {
            searchResults = vectorSearchService.searchByUserId(user.getId(), queryEmbedding, TOP_K_CHUNKS);
        }

        if (searchResults.isEmpty()) {
            logger.warning("No relevant chunks found for query");
            return RagAnswerDto.builder()
                    .answer("I couldn't find any relevant information in your study materials to answer this question. Please make sure you've uploaded and analyzed PDFs first.")
                    .sources(List.of())
                    .build();
        }

        // 3. Build context from chunks
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
        List<VectorSearchService.SearchResult> results = vectorSearchService.searchByPdfId(pdfId, topicEmbedding, 8);

        if (results.isEmpty()) {
            return null;
        }

        return results.stream()
                .map(r -> r.chunkText)
                .collect(Collectors.joining("\n\n---\n\n"));
    }

    private String buildRagPrompt(String question, String context) {
        return "You are an AI study assistant helping a student understand their course material. " +
               "Answer the following question based ONLY on the provided context from their study documents. " +
               "If the context doesn't contain enough information to answer, say so clearly. " +
               "Cite specific sources where possible.\n\n" +
               "CONTEXT:\n" + context + "\n\n" +
               "QUESTION: " + question + "\n\n" +
               "Provide a clear, educational answer based on the context above.";
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
