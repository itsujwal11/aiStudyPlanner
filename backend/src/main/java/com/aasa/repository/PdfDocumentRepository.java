package com.aasa.repository;

import com.aasa.entity.PdfDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface PdfDocumentRepository extends JpaRepository<PdfDocument, Long> {
    List<PdfDocument> findByUserId(Long userId);
    List<PdfDocument> findByUserIdOrderByUploadDateDesc(Long userId);
    Optional<PdfDocument> findByIdAndUserId(Long id, Long userId);

    @Query("SELECT p FROM PdfDocument p WHERE p.processingStatus IN :statuses OR p.isAnalyzed = false")
    List<PdfDocument> findIncomplete(
            @Param("statuses") List<PdfDocument.ProcessingStatus> statuses
    );
}
