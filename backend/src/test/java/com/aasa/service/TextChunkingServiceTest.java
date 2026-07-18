package com.aasa.service;

import com.aasa.entity.DocumentChunk;
import com.aasa.entity.PdfDocument;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TextChunkingServiceTest {

    private final TextChunkingService textChunkingService = new TextChunkingService();

    @Test
    void stopsAfterTheFinalOverlappingChunk() {
        PdfDocument pdf = PdfDocument.builder()
                .id(1L)
                .extractedText("a".repeat(9_693))
                .build();

        List<DocumentChunk> chunks = textChunkingService.chunkDocument(pdf);

        assertEquals(6, chunks.size());
        assertEquals(0, chunks.get(0).getChunkIndex());
        assertEquals(5, chunks.get(5).getChunkIndex());
        assertFalse(chunks.get(5).getChunkText().isBlank());
        assertEquals(453, chunks.get(5).getChunkText().length());
    }
}
