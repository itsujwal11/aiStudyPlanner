package com.aasa.service;

import com.aasa.entity.PdfDocument;
import com.aasa.repository.PdfDocumentRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.logging.Logger;

@Component
public class PdfProcessingRecovery {
    private static final Logger logger = Logger.getLogger(PdfProcessingRecovery.class.getName());

    private final PdfDocumentRepository pdfDocumentRepository;
    private final PdfProcessingService pdfProcessingService;

    public PdfProcessingRecovery(
            PdfDocumentRepository pdfDocumentRepository,
            PdfProcessingService pdfProcessingService
    ) {
        this.pdfDocumentRepository = pdfDocumentRepository;
        this.pdfProcessingService = pdfProcessingService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void resumeIncompleteJobs() {
        List<PdfDocument> incomplete = pdfDocumentRepository.findIncomplete(List.of(
                PdfDocument.ProcessingStatus.PENDING,
                PdfDocument.ProcessingStatus.PROCESSING
        ));
        if (!incomplete.isEmpty()) {
            logger.info("Resuming " + incomplete.size() + " incomplete PDF processing job(s)");
            incomplete.forEach(pdf -> pdfProcessingService.processAsync(pdf.getId()));
        }
    }
}
