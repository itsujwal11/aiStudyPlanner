package com.aasa.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "pdf_documents")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PdfDocument {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "pdf_seq")
    @SequenceGenerator(name = "pdf_seq", sequenceName = "pdf_id_seq", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "file_path", nullable = false)
    private String filePath;

    @Column(name = "upload_date", nullable = false, updatable = false)
    private LocalDateTime uploadDate;

    @Column(name = "exam_date", nullable = false)
    private LocalDate examDate;

    @Column(name = "extracted_text")
    @Lob
    private String extractedText;

    @Column(name = "is_analyzed")
    private Boolean isAnalyzed = false;

    @OneToMany(mappedBy = "pdfDocument", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Topic> topics;

    @PrePersist
    protected void onCreate() {
        uploadDate = LocalDateTime.now();
    }
}
