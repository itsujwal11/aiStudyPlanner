package com.aasa.service;

import com.aasa.entity.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

@Service
public class AdminDeletionService {
    private static final Logger logger = Logger.getLogger(AdminDeletionService.class.getName());

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public void delete(String entityName, Long id) {
        List<String> filesToDelete = new ArrayList<>();
        switch (entityName) {
            case "User" -> deleteUser(id, filesToDelete);
            case "PdfDocument" -> deletePdf(id, filesToDelete);
            case "Topic" -> deleteTopic(id);
            case "Quiz" -> deleteQuiz(id);
            case "QuizAttempt" -> deleteLeaf(QuizAttempt.class, id);
            case "StudyProgress" -> deleteLeaf(StudyProgress.class, id);
            default -> throw new IllegalArgumentException("Unknown entity: " + entityName);
        }
        entityManager.flush();
        deleteFilesAfterCommit(filesToDelete);
    }

    private void deleteUser(Long userId, List<String> filesToDelete) {
        requireEntity(User.class, userId);
        filesToDelete.addAll(entityManager.createQuery(
                        "SELECT p.filePath FROM PdfDocument p WHERE p.user.id = :id",
                        String.class)
                .setParameter("id", userId)
                .getResultList());

        execute("DELETE FROM OtpChallenge o WHERE o.user.id = :id", userId);
        execute("DELETE FROM ReviewLog r WHERE r.user.id = :id OR r.topic.pdfDocument.user.id = :id", userId);
        execute("DELETE FROM QuizAttempt q WHERE q.user.id = :id OR q.quiz.topic.pdfDocument.user.id = :id", userId);
        execute("DELETE FROM StudyProgress s WHERE s.user.id = :id OR s.topic.pdfDocument.user.id = :id", userId);
        execute("DELETE FROM Quiz q WHERE q.topic.pdfDocument.user.id = :id", userId);
        execute("DELETE FROM DocumentChunk d WHERE d.pdfDocument.user.id = :id", userId);
        execute("DELETE FROM Topic t WHERE t.pdfDocument.user.id = :id", userId);
        execute("DELETE FROM PdfDocument p WHERE p.user.id = :id", userId);
        execute("DELETE FROM User u WHERE u.id = :id", userId);
    }

    private void deletePdf(Long pdfId, List<String> filesToDelete) {
        PdfDocument pdf = requireEntity(PdfDocument.class, pdfId);
        filesToDelete.add(pdf.getFilePath());

        execute("DELETE FROM ReviewLog r WHERE r.topic.pdfDocument.id = :id", pdfId);
        execute("DELETE FROM QuizAttempt q WHERE q.quiz.topic.pdfDocument.id = :id", pdfId);
        execute("DELETE FROM StudyProgress s WHERE s.topic.pdfDocument.id = :id", pdfId);
        execute("DELETE FROM Quiz q WHERE q.topic.pdfDocument.id = :id", pdfId);
        execute("DELETE FROM DocumentChunk d WHERE d.pdfDocument.id = :id", pdfId);
        execute("DELETE FROM Topic t WHERE t.pdfDocument.id = :id", pdfId);
        execute("DELETE FROM PdfDocument p WHERE p.id = :id", pdfId);
    }

    private void deleteTopic(Long topicId) {
        requireEntity(Topic.class, topicId);
        execute("DELETE FROM ReviewLog r WHERE r.topic.id = :id", topicId);
        execute("DELETE FROM QuizAttempt q WHERE q.quiz.topic.id = :id", topicId);
        execute("DELETE FROM StudyProgress s WHERE s.topic.id = :id", topicId);
        execute("DELETE FROM Quiz q WHERE q.topic.id = :id", topicId);
        execute("DELETE FROM Topic t WHERE t.id = :id", topicId);
    }

    private void deleteQuiz(Long quizId) {
        requireEntity(Quiz.class, quizId);
        execute("DELETE FROM QuizAttempt q WHERE q.quiz.id = :id", quizId);
        execute("DELETE FROM Quiz q WHERE q.id = :id", quizId);
    }

    private <T> void deleteLeaf(Class<T> type, Long id) {
        T entity = requireEntity(type, id);
        entityManager.remove(entity);
    }

    private <T> T requireEntity(Class<T> type, Long id) {
        T entity = entityManager.find(type, id);
        if (entity == null) {
            throw new EntityNotFoundException(type.getSimpleName() + " #" + id + " not found");
        }
        return entity;
    }

    private void execute(String jpql, Long id) {
        entityManager.createQuery(jpql).setParameter("id", id).executeUpdate();
    }

    private void deleteFilesAfterCommit(List<String> paths) {
        if (paths.isEmpty()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                for (String path : paths) {
                    if (path == null || path.isBlank()) continue;
                    try {
                        Files.deleteIfExists(Paths.get(path));
                    } catch (Exception exception) {
                        logger.warning("Deleted database data but could not remove PDF file "
                                + path + ": " + exception.getMessage());
                    }
                }
            }
        });
    }
}
