package com.aasa.service;

import com.aasa.entity.PdfDocument;
import com.aasa.entity.Topic;
import com.aasa.repository.DocumentChunkRepository;
import com.aasa.repository.PdfDocumentRepository;
import com.aasa.repository.QuizAttemptRepository;
import com.aasa.repository.QuizRepository;
import com.aasa.repository.ReviewLogRepository;
import com.aasa.repository.StudyProgressRepository;
import com.aasa.repository.TopicRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PdfManagementServiceTest {

    @Mock private PlatformTransactionManager transactionManager;
    @Mock private PdfDocumentRepository pdfDocumentRepository;
    @Mock private TopicRepository topicRepository;
    @Mock private QuizRepository quizRepository;
    @Mock private QuizAttemptRepository quizAttemptRepository;
    @Mock private StudyProgressRepository studyProgressRepository;
    @Mock private DocumentChunkRepository documentChunkRepository;
    @Mock private ReviewLogRepository reviewLogRepository;

    private PdfManagementService service;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any()))
                .thenReturn(new SimpleTransactionStatus());

        service = new PdfManagementService(transactionManager);
        ReflectionTestUtils.setField(service, "pdfDocumentRepository", pdfDocumentRepository);
        ReflectionTestUtils.setField(service, "topicRepository", topicRepository);
        ReflectionTestUtils.setField(service, "quizRepository", quizRepository);
        ReflectionTestUtils.setField(service, "quizAttemptRepository", quizAttemptRepository);
        ReflectionTestUtils.setField(service, "studyProgressRepository", studyProgressRepository);
        ReflectionTestUtils.setField(service, "documentChunkRepository", documentChunkRepository);
        ReflectionTestUtils.setField(service, "reviewLogRepository", reviewLogRepository);
    }

    @Test
    void deletePdfDeletesDatabaseInFkOrderThenDeletesFile(@TempDir Path tempDir) throws Exception {
        Long pdfId = 11L;
        Long userId = 7L;
        Long topicId = 19L;
        Path pdfFile = Files.writeString(tempDir.resolve("owned.pdf"), "test");

        PdfDocument pdf = mock(PdfDocument.class);
        when(pdf.getFilePath()).thenReturn(pdfFile.toString());
        Topic topic = mock(Topic.class);
        when(topic.getId()).thenReturn(topicId);

        when(pdfDocumentRepository.findByIdAndUserId(pdfId, userId))
                .thenReturn(Optional.of(pdf));
        when(topicRepository.findByPdfDocumentId(pdfId)).thenReturn(List.of(topic));
        doAnswer(invocation -> {
            assertTrue(Files.exists(pdfFile), "The file must still exist before commit");
            return null;
        }).when(transactionManager).commit(any(TransactionStatus.class));

        service.deletePdf(pdfId, userId);

        assertFalse(Files.exists(pdfFile));
        InOrder deletionOrder = inOrder(
                reviewLogRepository,
                quizAttemptRepository,
                studyProgressRepository,
                quizRepository,
                topicRepository,
                pdfDocumentRepository);
        deletionOrder.verify(reviewLogRepository).deleteByTopicId(topicId);
        deletionOrder.verify(quizAttemptRepository).deleteByQuizTopicId(topicId);
        deletionOrder.verify(studyProgressRepository).deleteByTopicId(topicId);
        deletionOrder.verify(quizRepository).deleteByTopicId(topicId);
        deletionOrder.verify(topicRepository).deleteByPdfDocumentId(pdfId);
        deletionOrder.verify(pdfDocumentRepository).deleteById(pdfId);
        verify(transactionManager).commit(any(TransactionStatus.class));
    }

    @Test
    void deletePdfKeepsFileWhenDatabaseTransactionFails(@TempDir Path tempDir) throws Exception {
        Long pdfId = 12L;
        Long userId = 8L;
        Long topicId = 20L;
        Path pdfFile = Files.writeString(tempDir.resolve("rollback.pdf"), "test");

        PdfDocument pdf = mock(PdfDocument.class);
        when(pdf.getFilePath()).thenReturn(pdfFile.toString());
        Topic topic = mock(Topic.class);
        when(topic.getId()).thenReturn(topicId);

        when(pdfDocumentRepository.findByIdAndUserId(pdfId, userId))
                .thenReturn(Optional.of(pdf));
        when(topicRepository.findByPdfDocumentId(pdfId)).thenReturn(List.of(topic));
        doThrow(new DataIntegrityViolationException("review log FK failure"))
                .when(reviewLogRepository).deleteByTopicId(topicId);

        assertThrows(
                DataIntegrityViolationException.class,
                () -> service.deletePdf(pdfId, userId));

        assertTrue(Files.exists(pdfFile));
        verify(transactionManager).rollback(any(TransactionStatus.class));
        verify(pdfDocumentRepository, never()).deleteById(pdfId);
    }

    @Test
    void deletePdfRejectsPdfOwnedByAnotherUser() {
        Long pdfId = 13L;
        Long userId = 9L;
        when(pdfDocumentRepository.findByIdAndUserId(pdfId, userId))
                .thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> service.deletePdf(pdfId, userId));

        verify(documentChunkRepository, never()).deleteByPdfDocumentId(pdfId);
        verify(pdfDocumentRepository, never()).deleteById(pdfId);
    }
}
