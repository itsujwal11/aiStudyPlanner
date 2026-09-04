package com.aasa.service;

import com.aasa.entity.PdfDocument;
import com.aasa.repository.PdfDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PdfProcessingStateService {
    private final PdfDocumentRepository pdfDocumentRepository;

    public PdfProcessingStateService(PdfDocumentRepository pdfDocumentRepository) {
        this.pdfDocumentRepository = pdfDocumentRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markProcessing(Long pdfId) {
        return pdfDocumentRepository.findById(pdfId).map(pdf -> {
            pdf.setProcessingStatus(PdfDocument.ProcessingStatus.PROCESSING);
            pdf.setProcessingError(null);
            pdf.setIsAnalyzed(false);
            return true;
        }).orElse(false);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markCompleted(Long pdfId) {
        pdfDocumentRepository.findById(pdfId).ifPresent(pdf -> {
            pdf.setProcessingStatus(PdfDocument.ProcessingStatus.COMPLETED);
            pdf.setProcessingError(null);
            pdf.setIsAnalyzed(true);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long pdfId, String error) {
        pdfDocumentRepository.findById(pdfId).ifPresent(pdf -> {
            pdf.setProcessingStatus(PdfDocument.ProcessingStatus.FAILED);
            pdf.setIsAnalyzed(false);
            String message = error == null || error.isBlank() ? "PDF processing failed" : error;
            pdf.setProcessingError(message.substring(0, Math.min(message.length(), 1000)));
        });
    }
}
