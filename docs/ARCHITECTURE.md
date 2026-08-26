# Architecture — Adaptive Knowledge-Tracing and RAG Recommendation Algorithm

The system combines two coupled loops:

1. **Content loop (RAG)** — grounded question answering and quizzes.
2. **Learner loop (Knowledge Tracing)** — every answer updates mastery,
   forgetting risk, and topic priority, which reshapes the study plan.

## What the project is

AASA (**A**daptive **A**I **S**tudy **A**rchitect) turns one course PDF into an
adaptive study programme. The student uploads a lecture PDF plus an exam date;
the system extracts topics, generates MCQ quizzes, indexes every passage for
cited question-answering, and rebuilds a personalised plan after every answer.

Core contribution — **Adaptive Knowledge-Tracing and RAG Recommendation**:
study priorities come from measured learner evidence (Bayesian Knowledge
Tracing + an exponential forgetting curve), never from manual ratings; and
answers come only from the student's own document through retrieval-augmented
generation with mandatory `[Source N]` citations.

## Technology stack

| Layer | Technology |
|---|---|
| Backend | Java 17, Spring Boot 3.2 (Web, Data JPA, Security, Validation, Mail), jjwt 0.12.3, Apache PDFBox 2.0.29, Lombok |
| Frontend | React 18, Vite 5, React Router 6, Axios, Tailwind CSS, Framer Motion, Recharts, react-hot-toast, lucide-react |
| Database | PostgreSQL 17 + pgvector (`pgvector/pgvector:pg17` image) |
| AI services | Google Gemini - `gemini-embedding-001` (768-dim vectors); flash-class models for analysis, quiz generation and grounded answers |
| Offline ML | Python 3, pandas, scikit-learn, matplotlib/seaborn, joblib |
| Ports | frontend 3000 - backend 9096 - postgres 5432 |

## Repository layout

```
backend/src/main/java/com/aasa/
    controller/   REST endpoints (13 controllers)
    service/      business logic (~25 services)
    repository/   Spring Data JPA interfaces
    entity/       JPA entities (10 tables)
    dto/          request/response payloads
    security/     JWT provider + filter, CustomUserDetailsService
    config/       SecurityConfig, AsyncConfig, exception handlers
frontend/src/     pages (15) - components - context - hooks - api.js
ml/               train_model.py - data/ - models/ - reports/
docs/             ARCHITECTURE.md - WORKFLOW.md - schema.sql
docker-compose.yml  postgres+pgvector - backend - frontend
```

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
        T5 --> QZ[Quiz generation]
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
        SM2[stored SM-2 nextReviewDate] --> SCHED[Visible revision schedule]
        PLAN --> SCHED
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
| Scheduling consumers | planner tasks/roadmap; visible revision schedule reads the stored SM-2 `nextReviewDate` (heuristic labels only for never-attempted topics) | `PlannerService`, `StudyProgressService`, `AdaptivePriorityService` |
| Account & session security | BCrypt hashes, stateless JWT (24 h), e-mail OTP verification/reset, Google ID-token validation | `AccountAuthService`, `JwtTokenProvider`, `JwtAuthenticationFilter`, `SecurityConfig` |
| Ownership enforcement | every topic/quiz/dashboard/report route verifies resource ↔ caller before use | `TopicController`, `QuizController`, `DashboardService`, `ReportController` |
| Exam-date sync | profile editor persists the exam date so urgency uses real data | `PUT /api/pdfs/{pdfId}/exam-date` → `PdfManagementService.updateExamDate` |
| Study report | aggregates attempts + progress into an exportable JSON/CSV report | `ReportController`, `ReportService` |

## Frontend application map

| Screen / module | Role |
|---|---|
| `Login` `Register` `VerifyEmail` `ForgotPassword` `ResetPassword` | Full account lifecycle against `/api/auth/**`; OTP codes arrive by e-mail |
| `AuthContext` + `ProtectedRoute` | JWT/user state mirrored to `localStorage`; unauthenticated users bounced to `/login`; `isAdmin` gates `/admin` |
| `api.js` | Single axios instance; request interceptor attaches `Bearer` token, response interceptor force-logs-out on 401 |
| `Dashboard` | Aggregated stats, ranked + weak topics, days-to-exam |
| `UploadPdf` -> `PdfDetail` | Multipart upload with exam date; live processing status, topic list, per-PDF dashboard |
| `Learn` (`/study/:pdfId`) | Topic-wise reading view of descriptions and complexity/importance |
| `Study` (`diagnostic` / `practice`) | Quiz engine UI: timer, instant grading feedback, explanation reveal |
| `AiChat` | Free-form RAG questions; Sources panel shows file/page/relevance/rerank/rank |
| `QuickAnswers` | Predefined overviews built from stored topics - zero Gemini cost |
| `Planner` | Today tasks, roadmap, SM-2-driven revision schedule, recommendations |
| `Analytics` `Reports` | Performance charts; JSON/CSV study-report export |
| `Profile` | Exam-date editor (persisted via `PUT /api/pdfs/{id}/exam-date`), danger-zone reset |
| `Admin` | ADMIN-only entity browser/deleter over an allow-listed set |
| `BackgroundProcessingWatcher` | Global 5 s poll of `GET /api/pdfs`; toasts + desktop notification on COMPLETED/FAILED transitions |

## Backend service catalogue

- **Auth cluster** - `AccountAuthService` (registration, login, OTP verify /
  reset, Google token validation), `OtpService`, `GoogleTokenService`,
  `AuthService` (JWT-subject lookup helper)
- **PDF ingestion** - `PdfManagementService` (store, transactional one-PDF
  replacement, safe delete, exam-date update), `PdfExtractionService`
  (PDFBox), `PdfProcessingService` (@Async pipeline), `GeminiAiService`
  (structured topic/quiz JSON with model fallback chain)
- **RAG cluster** - `TextChunkingService`, `EmbeddingService`,
  `VectorSearchService`, `RerankingService`, `RagAugmentedService`
- **Learner-model cluster** - `MasteryService` (Beta-Binomial + BKT blend,
  SM-2 scheduling, ReviewLog), `BayesianKnowledgeTracingService`,
  `WeaknessEngineService`, `AdaptivePriorityService`
- **Planning cluster** - `PlannerService` (live recompute),
  `RecommendationEngineService`, `StudyPlanService` (stateless experimental)
- **Aggregation** - `DashboardService`, `AnalyticsService`, `ReportService`
- **Administration** - `AdminDeletionService` (FK-safe deletes)

## Development methodology — CRISP-DM

The system was built following **CRISP-DM** (Cross-Industry Standard Process
for Data Mining), which suits a data- and AI-intensive product. The six phases
run twice: once inside the offline model experiment and again across the live
application pipeline.

| CRISP-DM phase | Offline ML experiment (`ml/train_model.py`) | Live application (Spring Boot + React) |
|---|---|---|
| 1. Business Understanding | Goal fixed: predict next-answer correctness → `weakness = 1 − P(correct)` feeding `0.70·evidence + 0.30·ml_weakness` | Replace static study plans with evidence-driven scheduling and grounded document QA |
| 2. Data Understanding | `prepare_data()` profiles `skill_builder_data.csv`: 525,534 raw → 283,105 usable rows, 4,163 students | Uploaded PDFs must expose a real text layer; learner-event schema designed around attempts/progress |
| 3. Data Preparation | Cleaning, dedup by `order_id`, response-time sanitising, leakage-safe features via `shift(1)` | `PdfExtractionService` → `TextChunkingService` → `EmbeddingService` → pgvector persistence |
| 4. Modeling | Logistic Regression vs Random Forest scikit-learn pipelines | BKT posterior, exponential forgetting curve, `AdaptivePriorityService`, `WeaknessEngineService`, modified SM-2, hybrid RAG reranker |
| 5. Evaluation | Student-wise split (no student in two sets); validation selects the winner, held-out test reports F1 0.734 / ROC-AUC 0.705 | 35 automated tests incl. the opt-in live-pgvector retrieval test; RAG Hit@5 / MRR@5 procedure |
| 6. Deployment | Serialized to `models/weakness_model.joblib` + `reports/metrics.json`; live scoring integration is planned future work | Docker Compose: React SPA + Spring Boot API + PostgreSQL/pgvector; Vercel/Render deployment configs |

> **Honest status:** the trained classifier is currently an *offline* artifact —
> topic ranking runs on the deterministic evidence formulas above, not on the
> joblib model. Wiring it into live scoring is declared future work instead of
> being claimed as live behaviour.

## Data model (PostgreSQL)

Schema bootstrap lives in `docs/schema.sql` (used by docker-compose on first
run); afterwards Hibernate `ddl-auto=update` evolves it. Embeddings are stored
as pgvector **text literals** in a TEXT column and cast to `vector` at query
time.

| Entity (table) | Key fields | Relationships |
|---|---|---|
| `users` | email (unique), name, password (BCrypt), role USER/ADMIN, email_verified, google_subject (unique, nullable) | owns all rows below |
| `otp_challenges` | code_hash, purpose EMAIL_VERIFICATION/PASSWORD_RESET, expires_at, attempts, consumed_at | many -> 1 user |
| `pdf_documents` | file_name, file_path, exam_date, extracted_text, is_analyzed, processing_status PENDING/PROCESSING/COMPLETED/FAILED, processing_error, upload_date | 1/user; owns topics + chunks |
| `topics` | title, description, complexity_score, importance_score, priority_score, weakness_score | 1/pdf; owns quizzes |
| `quizzes` | question, option_a..d, correct_answer, difficulty EASY/MEDIUM/HARD, explanation | 1/topic; owns attempts |
| `quiz_attempts` | selected_answer, is_correct, marks_obtained, time_taken_seconds, attempt_time | user x quiz |
| `study_progress` | weakness_level LOW/MEDIUM/HIGH/INSUFFICIENT_DATA/NOT_ATTEMPTED, completion_percentage, best_score, total/correct_attempts, mastery_level, alpha (2.0), beta (8.0), sm2_interval, sm2_efactor (2.5), sm2_repetitions, last_study_date, next_review_date | unique (user, topic) |
| `document_chunks` | chunk_index, chunk_text, estimated page, embedding (TEXT vector literal) | 1/pdf; forms the RAG index |
| `review_log` | review_type, rating 1-4, response_time_ms, scheduled_days, actual_interval, mastery_before/after, created_at (@PrePersist) | user x topic history |

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

## HTTP API surface

All routes are JWT-protected except `/api/auth/**` and `/api/health`. Every
identifier route is ownership-checked against the JWT caller.

| Group | Endpoints |
|---|---|
| Auth (public) | POST `/auth/register` - `/auth/login` - `/auth/verify-email` - `/auth/resend-verification` - `/auth/forgot-password` - `/auth/reset-password` - `/auth/google` |
| PDFs | POST `/pdfs/upload` (multipart file+examDate) - GET `/pdfs` - GET `/pdfs/{id}` - GET `/pdfs/{id}/detail` - PUT `/pdfs/{id}/exam-date` - DELETE `/pdfs/{id}` - DELETE `/pdfs/reset` |
| Topics | POST `/topics/analyze/{pdfId}` - GET `/topics/pdf/{pdfId}` - GET `/topics/ranked` - GET `/topics/ranked/pdf/{pdfId}` - GET `/topics/{id}` - POST `/topics/{id}/update-weakness` |
| Quizzes | GET `/quizzes/topic/{topicId}` - GET `/quizzes/{quizId}` - POST `/quizzes/{quizId}/submit` - GET `/quizzes/progress?pdfId=` |
| Dashboard | GET `/dashboard` - GET `/dashboard/pdf/{pdfId}` |
| Analytics | GET `/analytics/performance` - `/analytics/topic/{topicId}` - `/analytics/comparison` |
| Planner / plans | GET `/planner` - GET `/recommendations/next-topics?limit` - `/recommendations/insights` - `/recommendations/schedule?daysAhead` - POST `/study-plan/generate` |
| RAG | POST `/rag/ask {question, pdfId?}` - GET `/rag/predefined?pdfId=` (Quick Answers) |
| Reports | GET `/reports/study-report` |
| Admin (ADMIN) | GET `/admin/dashboard` - `/admin/entities` - `/admin/entities/{name}` - `/admin/entities/{name}/{id}` - DELETE `/admin/entities/{name}/{id}` |
| Health | GET `/health` |

## Security & multi-tenancy model

- Passwords are BCrypt-hashed; sessions are stateless JWTs (24 h expiry)
  resolved by `JwtAuthenticationFilter`.
- Accounts confirm their e-mail via hashed OTP challenges; password resets
  reuse the same mechanism; Google sign-in validates issuer/audience/expiry.
- **One CORS authority:** `SecurityConfig` builds the allowlist from
  `cors.allowed-origins`; per-controller `@CrossOrigin` annotations were
  removed so no endpoint can bypass it.
- **No default accounts:** the former startup hook that auto-seeded
  `admin@aasa.com` was removed — administrators are provisioned deliberately.
- **Tenant isolation:** every identifier route (topics, quizzes, dashboard,
  reports, Quick Answers) verifies resource ↔ owner before use, and the vector
  search SQL itself joins `pdf_documents` on the owner, so retrieval cannot
  cross accounts.

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

## Configuration reference

Environment keys are loaded from `backend/.env` (or repo-root `.env`) via
`spring.config.import`; OS variables win. Everything not listed below is a
plain property in `application.properties` (CORS origins, upload dir, JWT
expiry, multipart 50 MB limits, logging levels).

| Key | Feeds |
|---|---|
| `PORT` | `server.port` (default 9096) |
| `DB_HOST` `DB_PORT` `DB_NAME` `DB_USERNAME` `DB_PASSWORD` | datasource |
| `JWT_SECRET` | HS512 signing key (24 h expiry is fixed in properties) |
| `GEMINI_API_KEY` | embeddings + generation |
| `GOOGLE_CLIENT_ID` / frontend `VITE_GOOGLE_CLIENT_ID` | Google sign-in |
| `SMTP_ENABLED` `SMTP_FROM` `SMTP_HOST` `SMTP_PORT` `SMTP_USERNAME` `SMTP_PASSWORD` (+ auth/starttls/timeout props) | OTP e-mail delivery; when disabled codes are logged server-side if `OTP_LOG_CODES=true` |
| `OTP_EXPIRATION_MINUTES` `OTP_RESEND_COOLDOWN_SECONDS` | challenge lifetime / resend throttle |
| `VITE_API_URL` | frontend API base |

## Running the project from scratch

Prerequisites: JDK 17, Maven, Node 18+, Docker (or any PostgreSQL with the
pgvector extension).

1. Database: `docker compose up -d postgres` (image already contains the
   `vector` extension and applies `docs/schema.sql`), or point at your own
   PostgreSQL after running `CREATE EXTENSION IF NOT EXISTS vector`.
2. Backend config: create `backend/.env` from `.env.example`;
   `GEMINI_API_KEY` is required for analysis/embeddings/RAG.
3. Start the API: `cd backend && mvn spring-boot:run` -> port 9096.
4. Start the UI: `cd frontend && npm install && npm run dev` -> port 3000
   (dev proxy forwards `/api` to 9096). Production bundle: `npm run build`.
5. Tests: `mvn test` (default suite; the live-database retrieval class skips
   itself). Opt-in integration: with Postgres up, set `RAG_INTEGRATION_TEST=true`
   and run `mvn test -Dtest=RagRetrievalIntegrationTest`.
6. Full stack in containers: `docker compose up --build`.

## Known limitations (by design, today)

- One active PDF per user; a new upload transactionally replaces the previous
  study set (multi-subject support is future work).
- Text-layer PDFs only - no OCR for scanned notes.
- Gemini availability/quota gates topic, quiz and RAG generation; graceful
  failure states are returned instead of ungrounded answers.
- Embeddings live as TEXT literals cast per query; a native `vector(768)`
  column plus an HNSW index is the planned scale-up.
- Revision-schedule labels fall back to weakness/mastery heuristics only for
  topics that have never been attempted.
- The trained joblib classifier remains an offline experiment.
- No rate limiting, refresh tokens or frontend test suite yet; local-disk
  uploads need a mounted volume in cloud deployments.

