package com.aasa.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.logging.Logger;

@Service
public class PdfExtractionService {

    private static final Logger logger = Logger.getLogger(PdfExtractionService.class.getName());

    public String extractTextFromPdf(MultipartFile file) throws IOException {
        logger.info("Starting PDF extraction from file: " + file.getOriginalFilename());
        PDDocument document = null;
        try {
            document = PDDocument.load(file.getInputStream());
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setLineSeparator("\n");
            String rawText = stripper.getText(document);
            logger.info("Raw text extracted, length: " + rawText.length());
            String cleanedText = cleanExtractedText(rawText);
            logger.info("Text cleaned, final length: " + cleanedText.length());
            return cleanedText;
        } catch (IOException e) {
            logger.severe("Error extracting PDF: " + e.getMessage());
            throw new IOException("Failed to extract text from PDF: " + e.getMessage(), e);
        } finally {
            if (document != null) {
                document.close();
            }
        }
    }

    public String extractTextFromPdf(String filePath) throws IOException {
        logger.info("Starting PDF extraction from file path: " + filePath);
        PDDocument document = null;
        try {
            document = PDDocument.load(new java.io.File(filePath));
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setLineSeparator("\n");
            String rawText = stripper.getText(document);
            logger.info("Raw text extracted, length: " + rawText.length());
            String cleanedText = cleanExtractedText(rawText);
            logger.info("Text cleaned, final length: " + cleanedText.length());
            return cleanedText;
        } catch (IOException e) {
            logger.severe("Error extracting PDF: " + e.getMessage());
            throw new IOException("Failed to extract text from PDF: " + e.getMessage(), e);
        } finally {
            if (document != null) {
                document.close();
            }
        }
    }

    private String cleanExtractedText(String rawText) {
        if (rawText == null || rawText.isEmpty()) {
            return "";
        }

        logger.info("Starting text cleaning process");

        String cleaned = rawText
                .replaceAll("\\r\\n", "\n")
                .replaceAll("\\r", "\n");

        cleaned = cleaned.replaceAll("\n\\s*\n", "\n");

        cleaned = cleaned.replaceAll("[ \\t]+", " ");

        cleaned = cleaned.replaceAll("[^\\x20-\\x7E\\n]", "");

        cleaned = cleaned.replaceAll("\\n\\s+", "\n");
        cleaned = cleaned.replaceAll("\\s+\\n", "\n");

        String[] lines = cleaned.split("\n");
        StringBuilder result = new StringBuilder();
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                result.append(trimmed).append("\n");
            }
        }

        cleaned = result.toString().trim();

        logger.info("Text cleaning completed");
        return cleaned;
    }
}
