package com.aasa.repository;

import com.aasa.entity.Topic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface TopicRepository extends JpaRepository<Topic, Long> {
    List<Topic> findByPdfDocumentId(Long pdfId);

    @Query("SELECT t FROM Topic t WHERE t.pdfDocument.user.id = ?1 ORDER BY t.priorityScore DESC")
    List<Topic> findByUserIdOrderByPriority(Long userId);
}
