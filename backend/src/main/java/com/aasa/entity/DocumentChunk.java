package com.aasa.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnTransformer;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "document_chunks",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_document_chunks_pdf_chunk_index",
                columnNames = {"pdf_id", "chunk_index"}
        )
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentChunk {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "chunk_seq")
    @SequenceGenerator(name = "chunk_seq", sequenceName = "chunk_id_seq", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pdf_id", nullable = false)
    private PdfDocument pdfDocument;

    @Column(name = "chunk_index", nullable = false)
    private Integer chunkIndex;

    @Column(name = "chunk_text", columnDefinition = "TEXT", nullable = false)
    private String chunkText;

    @Column(name = "embedding", columnDefinition = "TEXT")
    private String embedding;

    @Column(name = "token_count")
    private Integer tokenCount;

    @Column(name = "page_number")
    private Integer pageNumber;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
