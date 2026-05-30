package com.aasa.repository;

import com.aasa.entity.Flashcard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface FlashcardRepository extends JpaRepository<Flashcard, Long> {
    List<Flashcard> findByTopicId(Long topicId);
    List<Flashcard> findByTopicPdfDocumentId(Long pdfId);
}
