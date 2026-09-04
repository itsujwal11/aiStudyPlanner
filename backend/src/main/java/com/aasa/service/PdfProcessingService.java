package com.aasa.service;

import com.aasa.entity.PdfDocument;
import com.aasa.entity.Topic;
import com.aasa.repository.PdfDocumentRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.logging.Logger;

@Service
public class PdfProcessingService {
    private static final Logger logger = Logger.getLogger(PdfProcessingService.class.getName());

    private final PdfDocumentRepository pdfDocumentRepository;
    private final PdfProcessingStateService stateService;
    private final TopicAnalysisService topicAnalysisService;
    private final RagAugmentedService ragAugmentedService;

    public PdfProcessingService(
            PdfDocumentRepository pdfDocumentRepository,
            PdfProcessingStateService stateService,
            TopicAnalysisService topicAnalysisService,
            RagAugmentedService ragAugmentedService
    ) {
        this.pdfDocumentRepository = pdfDocumentRepository;
        this.stateService = stateService;
        this.topicAnalysisService = topicAnalysisService;
        this.ragAugmentedService = ragAugmentedService;
    }

    @Async("pdfProcessingExecutor")
    public void processAsync(Long pdfId) {
        if (!stateService.markProcessing(pdfId)) return;

        try {
            PdfDocument pdf = pdfDocumentRepository.findById(pdfId)
                    .orElseThrow(() -> new IllegalStateException("PDF was deleted"));

            // RAG pipeline first (chunking + embedding) so the AI Chat / Quick Answers
            // index exists as soon as processing completes, matching the documented
            // extraction -> chunking -> embedding -> AI topic/quiz analysis order.
            ragAugmentedService.reprocessPdfForRag(pdf);

            List<Topic> topics = topicAnalysisService.analyzeAndCreateTopics(pdf);
            if (topics.isEmpty()) throw new IllegalStateException("AI analysis returned no topics");

            stateService.markCompleted(pdfId);
            logger.info("Background processing completed for PDF " + pdfId);
        } catch (Exception exception) {
            logger.severe("Background processing failed for PDF " + pdfId
                    + ": " + exception.getMessage());
            stateService.markFailed(pdfId, rootMessage(exception));
        }
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage() == null ? throwable.getClass().getSimpleName() : current.getMessage();
    }
}
