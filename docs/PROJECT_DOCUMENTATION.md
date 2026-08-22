# AASA — AI Study Planner: Complete Project Documentation

This document explains **everything**: what the system does, how every algorithm works,
where AI/ML/NLP is used, what Docker does, and how to verify each feature step by step.
Written so you can explain any part of this project in depth during evaluation.

---

## 1. What the project is (elevator pitch)

AASA is an **adaptive AI study assistant**. A student uploads lecture PDFs; the system
extracts the text, splits it into semantic chunks, converts each chunk into a 768-dimension
embedding vector, and stores them in PostgreSQL with the **pgvector** extension. When the
student asks a question, the system retrieves the most semantically similar chunks,
**reranks** them with a hybrid scoring model, and makes **Gemini generate an answer strictly
grounded in those sources with page-level citations**. In parallel, every quiz answer feeds a
**Bayesian Knowledge Tracing** model that estimates the student's mastery of each topic; an
**exponential forgetting curve** estimates how likely the student is to have forgotten it;
and an **adaptive priority formula** combines mastery, forgetting risk, exam urgency, and
topic importance to decide *what to study next*.

> Core contribution: **Adaptive Knowledge-Tracing and RAG Recommendation Algorithm** —
> priorities come from actual learner performance (not manual ratings), and study content is
> generated only from the student's own verified material (not the LLM's imagination).

---

## 2. System architecture

```
                        ┌──────────────────────────────┐
   Student ──browser──▶ │  React frontend (Vite) :3000 │
                        └──────────────┬───────────────┘
                                       │ REST + JWT (axios, /api)
                        ┌──────────────▼───────────────┐
                        │ Spring Boot backend :9096    │
                        │  Auth · PDFs · Quizzes · RAG │
                        └───┬────────────────┬─────────┘
              JDBC/Hibernate│                │ HTTPS
            ┌───────────────▼──────┐   ┌─────▼──────────────────┐
            │ PostgreSQL + pgvector│   │ Google Gemini API      │
            │ users, pdf_documents,│   │  • gemini-embedding-2  │
            │ document_chunks      │   │    (768-dim vectors)   │
            │  (embedding vectors),│   │  • gemini-2.5-flash    │
            │ topics, study_       │   │    (analysis/QA/quiz)  │
            │  progress, quiz_     │   └────────────────────────┘
            │  attempts            │
            └──────────────────────┘
```

- **Frontend** (`frontend/src`) — React pages: Dashboard, Upload/PDF detail, Study, Topics,
  **AI Chat** (`/ai-chat`, the RAG demo), Quick Answers, Quizzes, Planner.
- **Backend** (`backend/src/main/java/com/aasa`) — Spring Boot 3, Java 17. Controllers expose
  REST; services hold all logic; JPA repositories talk to Postgres.
- **Database** — PostgreSQL 17 running inside Docker **with pgvector**, so vectors are stored
  and searched *inside* the database (no separate vector server).
- **Gemini cloud APIs** — one model produces embeddings, another produces analysis/answers/quizzes.

---

## 3. What Docker does (and why)

`docker-compose.yml` starts three containers on one private bridge network (`aasa-network`):

| Container | Image / build | Port | Role |
|---|---|---|---|
| `aasa-postgres` | `pgvector/pgvector:pg17` | 5432 | Database **with the pgvector extension pre-installed**. Data survives restarts via the `postgres_data` volume. On first start it runs `docs/schema.sql` automatically. Has a healthcheck (`pg_isready`). |
| `aasa-backend` | built from `backend/Dockerfile` | 9096 | Spring Boot app. Connects to Postgres **by container name** (`jdbc:postgresql://postgres:5432/aasa_db`). Reads `GEMINI_API_KEY` from env. Uploads persisted via volume mount. |
| `aasa-frontend` | built from `frontend/Dockerfile` | 3000 | React app; `VITE_API_URL=http://localhost:9096/api`. |

**Why Docker here:** (1) the local machine's PostgreSQL did not have the `vector` extension —
the `pgvector/pgvector` image ships with it compiled in; (2) everyone gets the identical
database version/schema; (3) `depends_on: service_healthy` guarantees the backend only boots
after the DB is ready; (4) one command (`docker compose up`) starts the whole stack.

You currently run **Postgres in Docker** (container `aasa-postgres`, port 5432) and the
backend/frontend natively — `backend/.env` points at `DB_PORT=5432`, `SERVER_PORT=9096`.
That is fully supported: the backend just needs *a* reachable Postgres with pgvector.

---

## 4. End-to-end data flow

**Ingestion (once per PDF):**
```
upload PDF (≤50 MB) → saved to uploads/pdfs
  → PdfProcessingService: extract raw text (PDFBox)
  → GeminiAiService.analyzeContent(): LLM extracts topics w/ importance & complexity (JSON)
  → TopicAnalysisService: creates Topic rows (initial adaptive priority computed)
  → TextChunkingService.chunkDocument(): semantic chunking (~512 tokens, overlap 200)
  → EmbeddingService.generateEmbeddings(): batches of 20 → 768-dim vectors
  → DocumentChunkRepository: rows in document_chunks (embedding as pgvector text literal)
```

**Question answering (every question):**
```
question → EmbeddingService.generateEmbedding()          [query prefix]
  → VectorSearchService.searchByPdfId/searchByUserId     [pgvector cosine, top 20]
  → RerankingService.rerank()                            [hybrid score, keep top 5]
  → buildRagPrompt(): [Source 1..5] blocks + strict rules
  → GeminiAiService.generateContent()                    [grounded answer]
  → RagAnswerDto { answer, sources[] }                   [page numbers + scores]
  → AiChat.jsx renders answer + Sources panel
```

**Learning loop (every quiz attempt):**
```
answer recorded (correct?, response time) → QuizAttempt row
  → MasteryService.updateAfterAttempt(): Beta-Binomial posterior + SM-2 schedule
                                          + Bayesian Knowledge Tracing update
  → StudyProgressService.recalculatePriorities():
        AdaptivePriorityService.calculatePriority(mastery, forgettingRisk, urgency, importance)
  → Topic.priority_score updated → Dashboard/Planner re-rank what to study next
```

---

## 5. Chunking — how a PDF becomes retrievable pieces

**File:** `service/TextChunkingService.java`

Raw extracted text is cut into overlapping windows so that each piece is small enough to
embed and retrieve precisely, but complete enough to be understandable on its own:

| Constant | Value | Meaning |
|---|---|---|
| `CHUNK_SIZE_CHARS` | 2048 | target ≈ **512 tokens** per chunk (1 token ≈ 4 chars in English) |
| `CHUNK_OVERLAP_CHARS` | 200 | consecutive chunks share 200 chars so sentences spanning a boundary are never lost |
| `MAX_CHUNKS` | 500 | hard cap per PDF (≈ 1 MB of text) |

This is *semantic* chunking because the cutter prefers natural boundaries: inside each
window it looks for the last **paragraph break** (`\n\n`); if none, the last **sentence end**
(`". "`); only as a last resort does it hard-cut. Each chunk stores metadata used later:
`chunk_index` (order), `token_count` (estimated), and `page_number`
(estimated at ~3000 chars/page — this is what powers "page 14" in citations).

*Why chunking matters:* embeddings describe one idea well when the text is paragraph-sized;
a whole lecture is too coarse to retrieve precisely, and single sentences are too thin to
generate from.

---

## 6. Embeddings — the neural text-representation model (ML part 1)

**File:** `service/EmbeddingService.java`

An **embedding** maps text to a point in a high-dimensional vector space where *semantic
similarity becomes geometric closeness*: texts about the same concept land near each other
even if they share no words ("OSI layer for physical transmission" ≈ "cables and signals").

Implementation facts you should quote:

- Model: **`gemini-embedding-2`**, endpoint `models/gemini-embedding-2:batchEmbedContents`.
- Output: **768 float dimensions** per text (`EMBEDDING_DIMENSION = 768`, validated on every response).
- **Asymmetric retrieval prefixes** (instruction-tuned embedding): queries are sent as
  `"task: question answering | query: <question>"`; documents as `"title: none | text: <chunk>"`.
  This matches question-style searches against document-style content — a known retrieval trick.
- Documents are embedded in **batches of 20** (one network call per 20 chunks), with up to
  3 attempts, exponential backoff, and honoring of HTTP `Retry-After`.
- Vectors are validated (dimension count, no NaN/Inf, non-zero magnitude) then stored in
  `document_chunks.embedding` as a pgvector **text literal** `[0.12,-0.34,…]`.

Cosine similarity (what retrieval uses):
```
cos(q, c) = (q · c) / (‖q‖ × ‖c‖)      ∈ [-1, 1];  1 = same direction/topic
```

---

## 7. Vector search — pgvector cosine retrieval

**File:** `service/VectorSearchService.java`

```sql
SELECT id, pdf_id, chunk_index, chunk_text, token_count, page_number, created_at,
       1 - (embedding::vector <=> CAST($2 AS vector)) AS similarity
FROM document_chunks
WHERE pdf_id = $1
ORDER BY embedding::vector <=> CAST($2 AS vector)
LIMIT $3;
```

- `<=>` is pgvector's **cosine distance** operator; `similarity = 1 − distance` converts it to
  an intuitive 0..1 relevance score (this is the number shown in the UI).
- The stored text literal is cast with `::vector` **at query time**, so existing rows never
  needed migration.
- Two scopes: per-PDF (`WHERE pdf_id = $1`) and user-wide (`JOIN pdf_documents … WHERE
  user_id = $1`) — the join makes cross-user leakage impossible: retrieval is ownership-aware.
- Parameters are positional (`$1…`) because Hibernate 6's native-query parser mis-handles
  named `:param` markers when the SQL also contains PostgreSQL `::` casts (a real bug we hit
  and fixed — good war story for the viva).

---

## 8. Hybrid reranking (NLP part) — deciding what Gemini actually reads

**File:** `service/RerankingService.java`

Vector similarity alone can rank a *topically similar* chunk above the chunk that *literally
answers* the question. So we retrieve generously (**top 20**) and re-score each candidate:

```
rerankScore = 0.70 × vectorSimilarity      ← semantic closeness (pgvector)
            + 0.20 × keywordOverlap        ← exact-term evidence
            + 0.10 × titleMatch            ← topical anchor bonus
```

- `tokenize()` = classical NLP preprocessing: lowercase, extract `[a-z0-9]+` tokens,
  remove ~40 English stop words (*what, is, the, explain…*).
- `keywordOverlap = |queryTokens ∩ chunkTokens| / |queryTokens|` — recall of question terms.
- `titleMatch` = 1.0 when a word from the PDF title appears in *both* question and chunk.
- Output sorted descending; top **5** go to the LLM. Each result keeps its original
  `retrievalRank`, so you can *show the order changed* — demonstrable reranking.

*Why not just top-5 by vector?* Keyword evidence rescues exact names/formulas embeddings may
blur; the weights favor the strong learned signal while leaving room for lexical evidence.
Upgrade path: replace with a cross-encoder reranker — interface stays identical.

---

## 9. Grounded generation & citations

**Files:** `service/RagAugmentedService.java`, `service/GeminiAiService.java`,
`controller/RagController.java`, `frontend/src/pages/AiChat.jsx`

- Endpoint **`POST /api/rag/ask`** `{ question, pdfId? }` → `RagAnswerDto { answer, sources[] }`.
- Prompt rules enforced in `buildRagPrompt()`: use ONLY the `[Source N]` blocks; invent
  nothing; if evidence is insufficient say exactly that; cite every factual statement inline
  as `[Source N]`; never append a Sources section (the UI renders it from structured data).
- Generation model chain with automatic fallback: `gemini-2.5-flash → 2.0-flash →
  2.5-flash-lite → 3.1-flash-lite`; temperature **0.2** (factual, low randomness);
  retries on 429/503 with backoff.
- Every source returned to the UI carries: file name, page number, `similarity` (vector),
  `rerankScore`, final `rank`. The AI Chat page lists them under the answer:
  `Lecture_notes.pdf — page 14 — relevance 0.91 · rerank 0.87 · rank #1`.

This is what makes answers **verifiable and hallucination-resistant**: the LLM is a
*reader* of your material, not an oracle.

---

## 10. THE main algorithm — Adaptive Knowledge-Tracing and RAG Recommendation

This replaced the old fixed weighted score
(`0.35·complexity + 0.25·importance + 0.25·manualWeakness + 0.15·urgency`, in the legacy
`ScoringEngineService.calculatePriorityScore`, now an unused helper). The new pipeline is:

```
answer history → BKT mastery → forgetting risk → adaptive priority → RAG content selection
```

### 10.1 Bayesian Knowledge Tracing (mastery from performance)

**File:** `service/BayesianKnowledgeTracingService.java`

BKT maintains P(K): the probability the student has *mastered* the skill behind a topic.
Each answer updates it by Bayes' rule, correcting for lucky guesses and unlucky slips:

```
correct   P(obs) = (1−s)·P / [ (1−s)·P + g·(1−P) ]
incorrect P(obs) = s·P     / [ s·P     + (1−g)·(1−P) ]
learning  P(new) = P(obs) + (1−P(obs))·ℓ
```

Parameters: guess `g = 0.20` (unprepared student still right), slip `s = 0.10`
(prepared student still wrong), learn `ℓ = 0.40` after correct practice, `0.15` after
reviewing a mistake's feedback.

**Worked example** (quote this in the viva): mastery P = 0.40, student answers correctly:
- numerator `(1−0.10)·0.40 = 0.36`; denominator `0.36 + 0.20·0.60 = 0.48`
- posterior `0.36/0.48 = 0.75` → after learning step `0.75 + 0.25·0.40 = 0.85`

One correct answer moved belief from 40% → 85% — because guessing can't explain it well.
An incorrect answer at P = 0.85 drops to ≈ 0.55 (a slip is plausible but so is over-rating).

### 10.2 Forgetting risk (memory decay)

Same file. Exponential forgetting curve fitted to mastery strength:

```
forgettingRisk = 1 − e^(−λ · daysSinceLastReview),   λ = 0.15 × (1.6 − mastery)
```

λ grows as mastery falls, so fragile knowledge decays faster (spacing-effect aware).
Example: mastery 0.5 after 7 days → λ = 0.165, risk = 1 − e^(-1.155) ≈ **0.68**.
Reviewed today → risk exactly 0.

### 10.3 Adaptive priority (the decision formula)

**File:** `service/AdaptivePriorityService.java`

```
priority = 0.40 × (1 − masteryProbability)      ← knowledge gap (BKT)
         + 0.25 × forgettingRisk                ← memory decay (curve above)
         + 0.20 × examUrgency = 1/(days+1)      ← deadline pressure
         + 0.15 × topicImportance               ← AI-assessed weight (Gemini analysis)
```

Where each input comes from — **all evidence-based, none manual**:

| Input | Source |
|---|---|
| `masteryProbability` | BKT update on every quiz attempt (`MasteryService.updateAfterAttempt`) |
| `daysSinceLastReview` | `StudyProgress.lastStudyDate` (set when the student studies/is quizzed) |
| `examDate` | `PdfDocument.examDate`; urgency = 1/(daysUntilExam+1): 1.0 on exam day, halves daily |
| `topicImportance` | Gemini structured topic analysis at ingestion (fallback 0.5) |

Worked example: mastery 0.3, last studied 6 days ago (risk ≈ 0.62), exam in 9 days
(urgency 0.1), importance 0.8 →
`0.40·0.70 + 0.25·0.62 + 0.20·0.10 + 0.15·0.8 = 0.28+0.155+0.02+0.12 = 0.575`.
Study that tonight; another topic at mastery 0.9, reviewed today, no exam scores ≈ 0.07.

Call sites (all swapped from the old formula): `StudyProgressService.recalculatePriorities`
(after every attempt), `PlannerService`, `TopicController.updateWeakness`,
`TopicAnalysisService.createTopicFromAnalysis` (initial priors for brand-new topics).

### 10.4 Supporting algorithms kept underneath

- **Beta-Binomial mastery** (`MasteryService`) — per-attempt Bayesian posterior stored in
  `study_progress.alpha/beta`; feeds SM-2.
- **SM-2 spaced repetition** — next review interval/easiness from recall quality.
- **Evidence-based weakness** (`WeaknessEngineService`) — weighted error rate, mastery gap,
  response time, overdue state; used where a weakness scalar is needed.

---

## 11. Where AI / ML / NLP is used — the one-slide answer

| # | Capability | Technique (say this) | Code |
|---|---|---|---|
| 1 | Topic extraction from PDFs | **LLM** structured-output JSON (≤20 topics, importance, complexity), temp 0.2, model fallback chain | `GeminiAiService.analyzeContent`, `TopicAnalysisService` |
| 2 | Text representation | **Neural embeddings**, gemini-embedding-2, 768-dim, asymmetric query/doc prefixes, batching + retries | `EmbeddingService` |
| 3 | Finding relevant content | **Vector similarity search**, pgvector cosine `<=>`, ownership-scoped SQL | `VectorSearchService` |
| 4 | Ordering what the LLM reads | **Hybrid reranking**: 0.70 vector + 0.20 keyword + 0.10 title; top-20 → top-5 | `RerankingService` |
| 5 | Tokenization/stop-words for scoring | **Classical NLP** preprocessing (`[a-z0-9]+`, stop-word sets, set overlap) | `RerankingService.tokenize` |
| 6 | Grounded answering with citations | **RAG prompt engineering** — only-source context, forced `[Source N]` cites, refuse-if-insufficient | `RagAugmentedService.buildRagPrompt` |
| 7 | Quiz & flashcard generation | **LLM generation over reranked RAG context** (8 chunks) as JSON | `generateQuizContext` → quiz services |
| 8 | Mastery estimation | **Bayesian Knowledge Tracing** (probabilistic user model: guess/slip/learn) | `BayesianKnowledgeTracingService` |
| 9 | Memory decay | **Exponential forgetting curve**, mastery-scaled λ | same file |
| 10 | What-to-study-next decision | **Adaptive weighted priority** from learner evidence | `AdaptivePriorityService` |
| 11 | Review scheduling | **SM-2 spaced repetition** + Beta-Binomial posterior | `MasteryService` |
| 12 | Weakness measurement | Weighted evidence stats (error rate, response time, overdue) | `WeaknessEngineService` |
| 13 | Study-plan ranking | Recommendation scoring over adaptive priorities | `RecommendationEngineService`, `StudyPlanService` |

**ML vs AI vs NLP in one sentence:** the embedding model is *machine learning*; BKT and the
forgetting curve are *probabilistic ML models of the learner*; Gemini analysis/QA/quiz are
*LLM usage*; tokenization + keyword overlap are *NLP*; retrieval + reranking + grounded
generation together form the *RAG pipeline*.

---

## 12. Quiz and study-plan flows

**Quiz:** topic chosen → `generateQuizContext(pdfId, title)` embeds the title → vector
search top-20 → rerank to 8 chunks → Gemini generates questions (with correct answers and
explanations) strictly from that context → student answers recorded in `quiz_attempts`
(correct flag + response time) → BKT/mastery/priority update (Section 10).

**Study plan:** `RecommendationEngineService` ranks topics by the new adaptive priority;
`StudyPlanService` composes sessions respecting exam dates and SM-2 due reviews. The plan
therefore changes automatically as quiz performance and time pass — no manual settings.

---

## 13. API reference (main endpoints)

| Method & path | Purpose |
|---|---|
| `POST /api/auth/register` / `login` | account + JWT |
| `POST /api/pdfs/upload` | upload PDF → full ingestion pipeline |
| `GET /api/pdfs` | list user's PDFs |
| `POST /api/rag/ask` | **the RAG pipeline** `{question, pdfId?}` → `{answer, sources[]}` |
| `GET /api/rag/predefined` | quick answers (same RAG internals) |
| quiz/topic/planner endpoints | generate quizzes, record attempts, get ranked topics/plan |

## 14. How to verify everything works — the detailed checklist

### 14.1 Start the stack (exact commands)

```bat
:: 1. Database (pgvector) — must be running before the backend starts
docker compose up -d postgres
docker ps                      :: expect: aasa-postgres … Up (healthy), 0.0.0.0:5432->5432

:: 2. Backend (native, reads backend/.env)
cd backend && mvn spring-boot:run
:: wait for "Started AasaApplication" ; listens on SERVER_PORT=9096

:: 3. Frontend
cd frontend && npm run dev     :: http://localhost:3000
```

### 14.2 Infrastructure health checks

| Check | Command | Expected |
|---|---|---|
| pgvector installed | `docker exec aasa-postgres psql -U aasa_user -d aasa_db -c "SELECT extname FROM pg_extension WHERE extname='vector';"` | 1 row: `vector` |
| Tables exist | same pattern, `SELECT table_name FROM information_schema.tables WHERE table_schema='public';` | users, pdf_documents, document_chunks, topics, study_progress, quiz_attempts |
| Embeddings stored | `SELECT count(*), min(length(embedding)) FROM document_chunks;` | count > 0, length ≈ 768 floats |
| Backend up | open `http://localhost:9096/api/pdfs` (no token) | 401/403 — security active |

### 14.3 Automated tests (proof of correctness)

```bat
cd backend
mvn test                                    :: 34+ unit tests (BKT, priority, reranker, services)
set RAG_INTEGRATION_TEST=true&& mvn test -Dtest=RagRetrievalIntegrationTest
                                            :: 3 tests against the LIVE Docker DB:
                                            ::  • ownership isolation
                                            ::  • different questions → different retrieved pages
                                            ::  • reranking changes retrieval order
```
All green = retrieval, reranking, BKT, and adaptive priority are mathematically verified.

### 14.4 Feature-by-feature walkthrough (do these in order)

| # | Action | Where | What proves it works |
|---|---|---|---|
| 1 | Register + login | `/register`, `/login` | JWT stored; redirected to Dashboard |
| 2 | Upload a lecture PDF | `/upload` | Progress shown; on the PDF page: **topics with importance/complexity** appear (LLM analysis) and **chunks were created** |
| 3 | Verify ingestion in DB | health-check 14.2 row 3 | `document_chunks` has rows for your pdf_id |
| 4 | Ask a question | **AI Chat** (`/ai-chat`) | Answer text contains `[Source N]` markers; below it a **Sources** panel lists *file — page — relevance — rerank — rank* |
| 5 | Ask a *different* question about the same PDF | AI Chat | Different pages/sources returned → retrieval is real, not sequential chunks |
| 6 | Compare relevance vs rank | Sources panel | Order ≠ pure similarity order sometimes → **reranking changed the order** (e.g. relevance 0.88 ranked below 0.86 because of keyword overlap) |
| 7 | Generate a quiz for a topic | Topic → Quiz | Questions reference only content from your PDF (context = reranked top-8 chunks) |
| 8 | Answer the quiz (some wrong) | Quiz page | Attempts stored; explanations come from the source chunks |
| 9 | Watch priorities adapt | Dashboard/Topics before vs after | The topic you failed re-orders upward — its mastery dropped ⇒ `(1 − mastery)` term rose (BKT at work) |
| 10 | Wait / backdate a review, refresh planner | Planner | Priority rises again over time — **forgetting curve** term grows with days since last study |
| 11 | Set an exam date near-term | PDF settings | That PDF's topics jump in priority — exam urgency `1/(days+1)` dominates |

If all 11 pass, every subsystem (ingestion, RAG, BKT, adaptive scheduling) is demonstrably live.

---

## 15. The 5-minute demo script (for the evaluator)

1. *"I upload a PDF"* → show topics auto-extracted with importance scores.
2. *"The system chunks and embeds it into pgvector"* → run the DB count query.
3. *"I ask anything — answers come only from my material"* → AI Chat: question with
   `[Source N]` citations and the sources panel with page numbers.
4. *"Retrieval is two-stage: cosine search top-20, hybrid rerank to top-5"* → point at
   relevance vs rerank/rank numbers in the panel.
5. *"I take a quiz; a Bayesian Knowledge Tracing model updates my mastery"*
   → answer some questions.
6. *"Priority = 0.40·(1−mastery) + 0.25·forgettingRisk + 0.20·examUrgency + 0.15·importance"*
   → show the topic re-ranked after the quiz; mention the forgetting-curve term.
7. Close with: *"34 unit tests plus a live-database integration test verify each stage."*

---

## 16. Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| Backend log: "Vector search failed … operator does not exist" or "type vector does not exist" | pgvector extension not created on this DB | `docker exec aasa-postgres psql -U aasa_user -d aasa_db -c "CREATE EXTENSION IF NOT EXISTS vector;"` |
| Searches return empty but no error | PDF uploaded before embeddings existed, or wrong pdfId | Re-upload; check `SELECT count(*) FROM document_chunks WHERE pdf_id=<id>;` |
| Frontend gets network errors | Port mismatch frontend↔backend | `VITE_API_URL` must match `SERVER_PORT` (both default 9096); restart `npm run dev` after changes |
| 401 on every call | JWT expired | Log out/in |
| Gemini 429/503 during upload | API quota/rate limit | Retry; EmbeddingService already retries with backoff and falls back across models |
| Integration test silently skipped | `RAG_INTEGRATION_TEST` not set to `true` (by design, so CI passes without a DB) | `set RAG_INTEGRATION_TEST=true&& mvn test -Dtest=RagRetrievalIntegrationTest` |


