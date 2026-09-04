package com.aasa.service;

import com.aasa.entity.DocumentChunk;
import com.aasa.entity.PdfDocument;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

@Service
public class TextChunkingService {

    private static final Logger logger = Logger.getLogger(TextChunkingService.class.getName());

    // Target ~512 tokens per chunk. Rough estimate: 1 token ≈ 4 characters for English text
    private static final int CHUNK_SIZE_CHARS = 2048;
    private static final int CHUNK_OVERLAP_CHARS = 200;
    private static final int MAX_CHUNKS = 500;

    public List<DocumentChunk> chunkDocument(PdfDocument pdfDocument) {
        String text = pdfDocument.getExtractedText();
        if (text == null || text.isBlank()) {
            logger.warning("No text to chunk for PDF: " + pdfDocument.getId());
            return List.of();
        }

        logger.info("Chunking document " + pdfDocument.getId() + " with " + text.length() + " characters");
        List<DocumentChunk> chunks = new ArrayList<>();
        int start = 0;
        int chunkIndex = 0;

        while (start < text.length() && chunkIndex < MAX_CHUNKS) {
            int end = Math.min(start + CHUNK_SIZE_CHARS, text.length());

            // Try to break at a paragraph or sentence boundary
            if (end < text.length()) {
                int paragraphBreak = text.lastIndexOf("\n\n", end);
                if (paragraphBreak > start + CHUNK_SIZE_CHARS / 2) {
                    end = paragraphBreak;
                } else {
                    int sentenceBreak = text.lastIndexOf(". ", end);
                    if (sentenceBreak > start + CHUNK_SIZE_CHARS / 2) {
                        end = sentenceBreak + 1;
                    }
                }
            }

            String chunkText = text.substring(start, end).trim();
            if (!chunkText.isEmpty()) {
                DocumentChunk chunk = DocumentChunk.builder()
                        .pdfDocument(pdfDocument)
                        .chunkIndex(chunkIndex)
                        .chunkText(chunkText)
                        .tokenCount(estimateTokenCount(chunkText))
                        .pageNumber(estimatePageNumber(start, text.length()))
                        .build();
                chunks.add(chunk);
                chunkIndex++;
            }

            // The final chunk is complete. Without this break, subtracting the
            // overlap repeats the last 200 characters until MAX_CHUNKS.
            if (end >= text.length()) {
                break;
            }

            // Move start forward with overlap and defensively guarantee progress.
            int nextStart = Math.max(0, end - CHUNK_OVERLAP_CHARS);
            if (nextStart <= start) {
                nextStart = end;
            }
            start = nextStart;
        }

        logger.info("Created " + chunks.size() + " chunks for PDF " + pdfDocument.getId());
        return chunks;
    }

    private int estimateTokenCount(String text) {
        // Rough estimate: 1 token ≈ 4 characters
        return text.length() / 4;
    }

    private int estimatePageNumber(int charPosition, int totalLength) {
        // Rough estimate: assume ~3000 characters per page
        return (charPosition / 3000) + 1;
    }
}
