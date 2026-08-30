package com.aasa.repository;

import com.aasa.entity.DocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Long> {

    List<DocumentChunk> findByPdfDocumentIdOrderByChunkIndex(Long pdfId);

    /**
     * Every chunk owned by a user, for the Java cosine fallback in
     * {@code VectorSearchService} when pgvector is unavailable. Scoped through
     * the PDF's owner so cross-user retrieval is impossible.
     */
    @Query("SELECT dc FROM DocumentChunk dc WHERE dc.pdfDocument.user.id = :userId")
    List<DocumentChunk> findByUserId(@Param("userId") Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM DocumentChunk dc WHERE dc.pdfDocument.id = :pdfId")
    int deleteByPdfDocumentId(@Param("pdfId") Long pdfId);

    long countByPdfDocumentId(Long pdfId);
}
