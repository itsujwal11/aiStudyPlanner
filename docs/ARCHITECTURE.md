# Architecture — Adaptive Knowledge-Tracing and RAG Recommendation Algorithm

The system combines two coupled loops:

1. **Content loop (RAG)** — grounded question answering, quizzes, flashcards.
2. **Learner loop (Knowledge Tracing)** — every answer updates mastery,
   forgetting risk, and topic priority, which reshapes the study plan.

```mermaid
flowchart TB
    subgraph Ingestion
        A[PDF upload] --> B[Text extraction]
        B --> C[Semantic chunking ~500 tokens, overlap]
        C --> D[Gemini embedding 768-dim]
        D --> E[(document_chunks\nembedding text literal)]
    end

    subgraph ContentLoop[RAG content loop]
        Q[Student question] --> QE[Gemini query embedding]
        E --> V[pgvector cosine search\ntop 20 candidates\n1 - embedding <=> query]
        V --> R[Hybrid reranker\n0.70 vector + 0.20 keyword + 0.10 title]
        R --> T5[top 5 chunks]
        T5 --> G[Gemini generation\nstrictly grounded prompt\ncite [Source N]]
        G --> AN[Answer + Sources panel\nfile / page / relevance / rank]
        T5 --> QZ[Quiz + flashcard generation]
    end

    subgraph LearnerLoop[Adaptive learner loop]
        QA[Quiz attempt stored] --> BKT[Bayesian Knowledge Tracing\nP new = update P prior , correct, guess g, slip s, learn p]
        BKT --> MP[mastery probability P K]
        REV[last review date] --> FC[Forgetting risk = 1 - e^ -lambda days]
        MP --> PRI[Adaptive priority\n0.40 mastery gap + 0.25 forgetting + 0.20 exam urgency + 0.15 importance]
        FC --> PRI
        EXAM[exam date] --> PRI
        IMP[topic importance AI] --> PRI
        PRI --> PLAN[Topic ranking / daily study plan]
        QZ --> QA
        AN --> QA
    end
```

## Component map

| Stage | Implementation | File |
|---|---|---|
| PDF ingestion & chunking | text extraction, semantic chunks | `TextChunkingService`, `PdfManagementService` |
| Embeddings | Gemini `gemini-embedding-001`, 768-dim | `EmbeddingService` |
| Vector store | Postgres + pgvector, cosine distance `<=>` | `VectorSearchService`, `docs/schema.sql` |
| Retrieval | top-20 candidate pool per query | `RagAugmentedService.answerQuestion` |
| Reranking | hybrid vector/keyword/title scoring, top-5 kept | `RerankingService` |
| Grounded generation | citation-enforcing prompt → Gemini | `RagAugmentedService.buildRagPrompt` |
| Mastery estimation | Bayesian Knowledge Tracing update per answer | `BayesianKnowledgeTracingService.updateMastery` |
| Forgetting model | exponential decay `1 − e^(−λ·days)` | `BayesianKnowledgeTracingService.forgettingRisk` |
| Adaptive priority | weighted evidence formula (0.40/0.25/0.20/0.15) | `AdaptivePriorityService.calculatePriority` |
| Scheduling consumers | planner, progress recalculation, topic analysis | `PlannerService`, `StudyProgressService`, `TopicController`, `TopicAnalysisService` |

## Why this is a real algorithmic contribution

- **Mastery is estimated from performance data**, not declared: each quiz answer
  runs a BKT posterior update using explicit guess/slip/learn parameters.
- **Revision scheduling follows a forgetting curve**: time since last review raises
  risk continuously; weak topics decay faster because λ scales with `(1.6 − mastery)`.
- **Prioritization is adaptive and exam-aware**: the four components move independently
  as the learner studies, as time passes, and as exams approach.
- **Generation is grounded in retrieved evidence**: only the top-5 reranked chunks are
  sent to the LLM, citations `[Source N]` are mandatory, and the UI exposes the exact
  source pages with their retrieval and rerank scores — making answers auditable.

## Background processing & user notifications

PDF analysis is intentionally asynchronous so uploads stay fast:

1. `POST /api/pdfs/upload` stores the file, creates a `PENDING` record, and returns immediately.
2. `PdfProcessingService.processAsync` (Spring `@Async("pdfProcessingExecutor")`) runs
   extraction → chunking → embedding → AI topic/quiz analysis, updating
   `processingStatus`: `PENDING → PROCESSING → COMPLETED | FAILED`.
3. The frontend mounts one global watcher, `BackgroundProcessingWatcher`
   (`frontend/src/hooks/useBackgroundProcessingNotifications.js`), inside `AuthProvider`.
   It polls the existing lightweight `GET /api/pdfs` list every 5 s, diffs each poll
   against the previous snapshot, and fires:
   - an in-app toast when any PDF transitions `PROCESSING/PENDING → COMPLETED`
     ("is ready — N topics generated") or `→ FAILED` (with the stored error reason);
   - a desktop notification via the Notification API **only while the tab is hidden**,
     so the user learns about completion even on another tab (no duplicate spam when focused).
4. Polling pauses while the tab is hidden and refreshes instantly on return; the first
   fetch only seeds state, so pre-existing documents never trigger stale notifications.

