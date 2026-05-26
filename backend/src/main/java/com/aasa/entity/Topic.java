package com.aasa.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.List;

@Entity
@Table(name = "topics")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Topic {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "topic_seq")
    @SequenceGenerator(name = "topic_seq", sequenceName = "topic_id_seq", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pdf_id", nullable = false)
    private PdfDocument pdfDocument;

    @Column(nullable = false)
    private String title;

    @Column
    @Lob
    private String description;

    @Column(name = "concept_density")
    private Double conceptDensity;

    @Column(name = "keyword_difficulty")
    private Double keywordDifficulty;

    @Column(name = "formula_count")
    private Integer formulaCount;

    @Column(name = "content_length")
    private Integer contentLength;

    @Column(name = "complexity_score")
    private Double complexityScore;

    @Column(name = "importance_score")
    private Double importanceScore;

    @Column(name = "priority_score")
    private Double priorityScore;

    @Column(name = "weakness_score")
    private Double weaknessScore;

    @OneToMany(mappedBy = "topic", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Quiz> quizzes;

    @OneToMany(mappedBy = "topic", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<StudyProgress> studyProgress;
}
