# AASA Project Architecture and Complete System Guide

> Current-code guide for the AASA AI Study Planner repository.  
> Reviewed against the source tree on **2026-07-18**.

## 1. Purpose and scope

This document explains how the complete project works: its architecture, source files, database model, API, user workflows, AI integration, RAG pipeline, scoring algorithms, frontend screens, configuration, testing, and current limitations.

The implementation in `backend/src/main` and `frontend/src` is the source of truth. Some older README, SQL, environment, and generated files no longer describe the running application exactly; those differences are called out below.

Generated or runtime-only directories such as `backend/target`, `frontend/dist`, `frontend/node_modules`, uploaded PDF files, and log files are not documented file by file because they are outputs rather than maintained source code.

## 2. What the application does

AASA is an adaptive study-planning application. A user can:

1. Register and sign in.
2. Upload a PDF study document and provide an exam date.
3. Extract the PDF text.
4. Ask Gemini to identify topics and create multiple-choice quizzes.
5. Study those topics and answer quizzes.
6. Track weakness, accuracy, mastery, and review intervals.
7. Receive a dashboard, analytics, recommendations, and a study plan.
8. Ask questions about the uploaded document through a RAG pipeline.
9. Reset all study data while retaining the user account.

The current normal workflow is a **single-active-PDF model per user**. Uploading a new PDF removes the user's earlier PDF-related topics, quizzes, scores, progress, review history, chunks, and stored PDF file. The system does not currently keep several simultaneously active PDFs through the regular upload flow.

## 3. High-level architecture

```text
┌─────────────────────────────────────────────────────────────────────┐
│ Browser                                                             │
│ React + React Router + Axios + Tailwind                             │
└──────────────────────────────┬──────────────────────────────────────┘
                               │ JSON/HTTP, multipart upload, JWT
                               ▼
┌─────────────────────────────────────────────────────────────────────┐
│ Spring Boot API                                                     │
│                                                                     │
│ Security filter                                                     │
│   → REST controllers                                                │
│     → application services                                         │
│       → JPA repositories / EntityManager                            │
│                                                                     │
│ Specialized services: PDF extraction, scoring, mastery, planner,    │
│ topic analysis, embeddings, vector search, and RAG                  │
└───────────────┬────────────────────┬─────────────────────┬──────────┘
                │                    │                     │
                ▼                    ▼                     ▼
      ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐
      │ PostgreSQL       │  │ Local PDF files  │  │ Google Gemini    │
      │ + pgvector       │  │ uploads/pdfs     │  │ REST API         │
      │ app data/vectors │  │ original files   │  │ AI + embeddings  │
      └──────────────────┘  └──────────────────┘  └──────────────────┘
```

The backend follows a conventional layered architecture:

```text
Controller → Service → Repository/EntityManager → PostgreSQL
```

- Controllers translate HTTP requests and authenticated-user details into service calls.
- Services contain business workflows and algorithms.
- Repositories persist JPA entities and execute derived queries.
- `VectorSearchService` uses native SQL because pgvector similarity search is database-specific.
- Security is applied before controller execution through the JWT filter chain.
- Gemini is called with Java's `HttpClient`; the active implementation does not use a Google AI Java SDK.

## 4. Technology stack

| Area | Technology | Role |
|---|---|---|
| Frontend | React 18 | Page and component rendering |
| Routing | React Router | Public/protected routes and navigation |
| HTTP | Axios | Calls the Spring API and attaches JWTs |
| Styling | Tailwind CSS, custom CSS | Layout, responsive UI, colors, glass effects |
| UI effects | Framer Motion, Lucide, canvas-confetti | Animation and icons |
| Charts | Recharts | Dashboard and analytics visualization |
| Frontend build | Vite 5 | Development server and production bundling |
| Backend | Spring Boot 3.2 | REST API and application runtime |
| Language | Java 17 source target | Backend implementation |
| Security | Spring Security, JWT, BCrypt | Authentication and authorization |
| Persistence | Spring Data JPA/Hibernate | Entity mapping and standard database access |
| Database | PostgreSQL | Primary runtime database |
| Vector database feature | pgvector | Stores and searches 768-dimensional embeddings |
| PDF parsing | Apache PDFBox | Extracts text from uploaded PDFs |
| AI | Google Gemini REST endpoints | Topic/quiz generation, embeddings, and RAG answers |
| Backend build | Maven | Dependencies, compilation, and tests |
| Containers | Docker Compose | PostgreSQL, backend, and frontend definitions |

PostgreSQL is the active database. Oracle-related files, dependency entries, and comments are legacy remnants and are not the current runtime design.

## 5. Repository structure

```text
aiStudyPlanner/
├── backend/
│   ├── pom.xml
│   ├── Dockerfile
│   ├── src/main/java/com/aasa/
│   │   ├── AasaBackendApplication.java
│   │   ├── config/
│   │   ├── controller/
│   │   ├── dto/
│   │   ├── entity/
│   │   ├── repository/
│   │   ├── security/
│   │   └── service/
│   ├── src/main/resources/
│   └── src/test/java/com/aasa/service/
├── frontend/
│   ├── package.json
│   ├── Dockerfile
│   ├── index.html
│   ├── vite.config.js
│   ├── tailwind.config.js
│   ├── postcss.config.js
│   ├── vercel.json
│   └── src/
│       ├── main.jsx
│       ├── App.jsx
│       ├── api.js
│       ├── index.css
│       ├── components/
│       ├── context/
│       ├── hooks/
│       └── pages/
├── docs/
│   ├── API_DOCUMENTATION.md
│   ├── schema.sql
│   ├── schema_oracle.sql
│   └── PROJECT_ARCHITECTURE_AND_SYSTEM_GUIDE.md
├── docker-compose.yml
└── README.md
```

## 6. Backend file guide

### 6.1 Application, configuration, and security

| File | Responsibility |
|---|---|
| `AasaBackendApplication.java` | Spring Boot entry point. Starts component scanning and contains development-time admin bootstrap behavior. |
| `config/SecurityConfig.java` | Configures stateless JWT security, route rules, password encoding, authentication manager, CORS, and the authentication filter. |
| `config/GlobalExceptionHandler.java` | Converts validation, business, database, upload, and unexpected exceptions into consistent HTTP error responses. |
| `security/JwtTokenProvider.java` | Creates, parses, and validates signed JWT access tokens. |
| `security/JwtAuthenticationFilter.java` | Reads the `Authorization: Bearer ...` header, validates the token, loads the user, and places authentication in Spring Security's context. |
| `security/CustomUserDetailsService.java` | Loads a database user by email for Spring Security. |

Security rules at a glance:

- `/api/auth/**` is public.
- `/api/admin/**` requires the admin role.
- Other API routes require a valid JWT.
- The API is stateless; no server-side HTTP session is used.

### 6.2 Controllers and endpoints

Controllers should stay thin. Their job is authentication/context extraction, request validation, status codes, and DTO conversion; the main logic belongs in services.

| Controller | Base path | Responsibility |
|---|---|---|
| `AuthController` | `/api/auth` | Registration, login, and development admin seeding |
| `PdfController` | `/api/pdfs` | PDF upload, listing, detail, deletion, and full study-data reset |
| `TopicController` | `/api/topics` | AI analysis, topic retrieval/ranking, and weakness updates |
| `QuizController` | `/api/quizzes` | Quiz retrieval and answer submission |
| `RagController` | `/api/rag` | Document-grounded questions and RAG reprocessing |
| `DashboardController` | `/api/dashboard` | Overall and PDF-specific dashboard summaries |
| `AnalyticsController` | `/api/analytics` | Performance, topic detail, and comparison calculations |
| `RecommendationController` | `/api/recommendations` | Next-topic recommendations, insights, and a weekly schedule |
| `PlannerController` | `/api/planner` | Database-driven personalized planner |
| `StudyPlanController` | `/api/study-plan` | Separate stateless plan generator using caller-provided topic metrics |
| `AdminController` | `/api/admin` | Database counts, entity browsing, records, and deletion |

### 6.3 Services: business logic and algorithms

| Service | Main responsibility |
|---|---|
| `AuthService` | Validates registration/login data, hashes passwords with BCrypt, loads users, and issues JWT-backed auth responses. |
| `PdfManagementService` | Owns upload persistence, single-PDF replacement, ordered database cleanup, ownership checks, deletion, reset, and physical-file cleanup. |
| `PdfExtractionService` | Uses PDFBox to extract and clean textual PDF content. It does not perform OCR. |
| `GeminiAiService` | Calls Gemini generation endpoints, parses topic/quiz JSON, and produces grounded RAG answers. |
| `TopicAnalysisService` | Turns AI analysis into `Topic` entities, calculates fallback scores and priority, persists topics, and creates validated quizzes. |
| `QuizEngineService` | Validates generated MCQs, normalizes answer representations, retrieves quizzes, checks submitted answers, and stores attempts. |
| `StudyProgressService` | Maintains per-user/per-topic totals, accuracy, best score, weakness, completion value, and triggers adaptive updates. |
| `WeaknessEngineService` | Maps quiz performance to weakness category/value and recalculates topic priorities. |
| `ScoringEngineService` | Calculates complexity, fallback importance, urgency, and the stored weighted topic-priority score. |
| `MasteryService` | Maintains Bayesian mastery, modified SM-2 repetition state, next review date, easiness factor, and review logs. |
| `TextChunkingService` | Splits extracted text into bounded, overlapping RAG chunks while avoiding infinite-loop boundary cases. |
| `EmbeddingService` | Calls Gemini's embedding endpoint in batches, validates returned 768-dimensional vectors, and distinguishes query/document task prefixes. |
| `VectorSearchService` | Executes pgvector cosine similarity searches scoped to PDFs owned by the current user. |
| `RagAugmentedService` | Coordinates RAG indexing, atomic reprocessing, semantic retrieval, context construction, answer generation, and source DTOs. |
| `DashboardService` | Aggregates document, topic, progress, accuracy, and weakness information for dashboard cards. |
| `AnalyticsService` | Calculates overall performance, per-topic analytics, and comparison data from stored progress. |
| `RecommendationEngineService` | Ranks progress records, creates next-topic suggestions, summarizes strengths/weaknesses, and distributes a seven-day schedule. |
| `PlannerService` | Builds the active user planner: weak-topic analysis, today's tasks, roadmap, revision suggestions, and recommendations. |
| `StudyPlanService` | Implements a second request-driven scheduling algorithm independent of the user's persisted planner. |
| `OllamaAiService` | Older local-model experiment. It is currently not wired into an active controller or workflow. |

### 6.4 JPA entities

| Entity | Stored meaning |
|---|---|
| `User` | Account identity, hashed password, name, role, and timestamps |
| `PdfDocument` | Owning user, original/stored filenames, filesystem path, exam date, extracted text, analysis state, and timestamps |
| `Topic` | Topic name/description plus complexity, importance, weakness, priority, and PDF relationship |
| `Quiz` | Topic MCQ, four choices, normalized correct answer, and explanation |
| `QuizAttempt` | User answer, correct/incorrect result, marks, duration, user, quiz, and timestamp |
| `StudyProgress` | Cumulative topic performance, score, best score, weakness, Bayesian parameters, mastery, repetition count, interval, easiness, and next review |
| `ReviewLog` | Historical mastery/review event for a user and topic |
| `DocumentChunk` | PDF chunk text, chunk index, estimated page/token data, and a pgvector embedding |

### 6.5 Repositories

| Repository | Data access role |
|---|---|
| `UserRepository` | User lookup, especially by unique email |
| `PdfDocumentRepository` | User-owned PDF listing, lookup, counts, and deletion support |
| `TopicRepository` | Topics by PDF/user, ordered/ranked topics, and topic counts |
| `QuizRepository` | Quizzes by topic and quiz cleanup |
| `QuizAttemptRepository` | User attempts, counts, performance aggregation, and cleanup |
| `StudyProgressRepository` | Per-user/topic progress, planner/recommendation inputs, and cleanup |
| `ReviewLogRepository` | Review history, due-review queries, and cleanup |
| `DocumentChunkRepository` | Chunk persistence, PDF chunk counts, and replacement/deletion support |

Most repository methods are Spring Data derived queries. Vector similarity itself is intentionally handled by `VectorSearchService` with native pgvector SQL.

### 6.6 DTOs

DTOs keep API JSON separate from database entities.

| DTO or DTO group | Purpose |
|---|---|
| `AuthRequest`, `AuthResponse` | Login/register input and token/user output |
| `PdfDocumentDto`, `PdfDetailDto` | PDF cards and expanded PDF/topic information |
| `PdfUploadRequest` | Older upload DTO; multipart controller flow does not currently use it |
| `AiAnalysisResponse` | Parsed Gemini analysis containing generated topic and quiz structures |
| `TopicDto`, `WeaknessUpdateRequest` | Topic responses and manual weakness update input |
| `QuizDto` | Quiz question/options without exposing persistence internals |
| `QuizSubmissionRequest`, `QuizSubmissionResponse` | Submitted answer/duration and correctness/progress response |
| `RagQueryDto`, `RagAnswerDto`, `RagChunkSource` | Ask-AI request, grounded answer, and retrieved source information |
| `DashboardDto`, `StudyProgressDto` | Aggregated dashboard and progress output |
| `PlannerDto` | Container for the active planner response |
| `WeakTopicAnalysis`, `TodoTask`, `StudyRoadmapItem`, `RevisionScheduleItem` | Sections within `PlannerDto` |
| `StudyPlanRequest`, `StudyPlanResult` | Input/output for the separate request-driven plan generator |
| `RankedTopic`, `StudyStrategy`, `ScheduleDay`, `ScheduleBlock`, `DroppedTopic` | Detailed structures used by `StudyPlanService` |
| `StudyPlanResponse` | Older/unwired response class; active generation returns `StudyPlanResult` |

### 6.7 Resource and build files

| File | Purpose and current state |
|---|---|
| `backend/pom.xml` | Spring Boot, JPA, security, JWT, PDFBox, PostgreSQL, pgvector-related, Lombok, and test dependencies; Java target is 17. |
| `backend/Dockerfile` | Maven build followed by a Java runtime image. The Docker build currently skips tests. |
| `application.properties` | Default local runtime configuration: port, PostgreSQL, JPA, upload size/path, JWT, Gemini, CORS, and logging. It currently contains development secrets that must not be used as a production pattern. |
| `application-prod.properties` | Production profile using environment variables for database, JWT, Gemini, and CORS settings. |
| `application-local.properties` | Contains stale Oracle-era comments but no meaningful active overrides. |
| `init.sql` | Database/extension initialization support. JPA still evolves mapped tables through `ddl-auto=update`. |
| `seed_admin.sql` | SQL-based admin seed artifact; Java startup seeding is the behavior normally encountered now. |

### 6.8 Backend tests

| Test file | Coverage |
|---|---|
| `PdfManagementServiceTest.java` | Delete ordering, rollback/file retention behavior, and rejection of cross-user PDF deletion |
| `TextChunkingServiceTest.java` | Regression coverage for chunk-boundary termination |

There are four current source test methods in total. They are useful regression tests, not comprehensive integration or security coverage.

## 7. Frontend file guide

### 7.1 Entry, routing, state, and API

| File | Responsibility |
|---|---|
| `index.html` | Vite HTML shell containing the React mount node |
| `src/main.jsx` | Creates the React root, loads global CSS, and mounts `App` |
| `src/App.jsx` | Defines public/protected routes, shared layout, auth provider, toast provider, and sidebar structure |
| `src/api.js` | Creates the Axios client, chooses the API base URL, attaches the bearer token, handles `401`, and exports endpoint helper groups |
| `src/context/AuthContext.jsx` | Stores the authenticated token/user in React state and `localStorage`, exposes login/logout, and restores local auth on reload |
| `src/index.css` | Tailwind layers plus global visual styles and utility classes |
| `src/hooks/useCountUp.js` | Animates numeric dashboard/detail values |

The Axios base URL is `VITE_API_URL` when supplied, otherwise `http://localhost:9096/api`. `VITE_*` variables are compiled into a Vite bundle at build time.

The frontend guard checks whether a local token exists. It does not independently validate the token; the backend remains the authoritative security boundary and redirects/clears state after a `401`.

### 7.2 Shared components

| Component | Responsibility |
|---|---|
| `ProtectedRoute.jsx` | Waits for auth restoration and redirects unauthenticated users to login |
| `Sidebar.jsx` | Responsive navigation, user/admin-specific links, mobile drawer, and logout |
| `StudyTimer.jsx` | Local elapsed-time timer used during study; current use does not persist timer state independently |
| `Navigation.jsx` | Navigation written for `ProgressTimeline`; not part of the active routed layout |
| `GlassCard.jsx` | Reusable glass-styled card experiment; currently has no active imports |

### 7.3 Pages

| Page | Route | What it does |
|---|---|---|
| `Login.jsx` | `/login` | Sends credentials to `/auth/login`, stores auth, and redirects |
| `Register.jsx` | `/register` | Creates an account through `/auth/register` |
| `Dashboard.jsx` | `/dashboard` | Loads aggregate dashboard data and PDFs; admins instead receive admin counts |
| `UploadPdf.jsx` | `/upload` | Uploads a PDF, then calls topic analysis, shows staged progress, and explains replacement behavior |
| `PdfDetail.jsx` | `/pdf/:pdfId` | Shows one document's metadata/topics and links into study |
| `Study.jsx` | `/study`, `/study/:pdfId` | Loads ranked topics/quizzes, times answers, submits attempts, shows feedback, and updates weakness |
| `AiChat.jsx` | `/ai-chat` | Asks questions over all or one PDF, displays sources, and manually reprocesses a PDF for RAG |
| `Analytics.jsx` | `/analytics` | Loads performance and comparison data and draws analytics visualizations |
| `Recommendations.jsx` | `/recommendations` | Shows next topics, insights, and the generated seven-day schedule |
| `Planner.jsx` | `/planner` | Displays today's tasks, weak topics, roadmap, revision schedule, and recommendations |
| `Profile.jsx` | `/profile` | Shows account data, keeps exam date locally, and provides “Reset Everything” |
| `Reports.jsx` | `/reports` | Attempts to load a study-report endpoint and supports browser-side JSON/CSV export; the backend report endpoint is not implemented |
| `Admin.jsx` | `/admin` | Admin counts, entity selection, database record browsing, and deletion |
| `ProgressTimeline.jsx` | not routed | Prototype timeline with randomized/mock information; not part of the live application |

### 7.4 Active routes and navigation

Public routes:

- `/login`
- `/register`

Protected routes:

- `/dashboard`
- `/pdf/:pdfId`
- `/upload`
- `/study`
- `/study/:pdfId`
- `/analytics`
- `/recommendations`
- `/profile`
- `/reports`
- `/planner`
- `/ai-chat`
- `/admin`

`/` redirects to `/dashboard`. There is currently no wildcard 404 route. The admin page adds its own role check as a UI guard; backend role enforcement is still essential.

The normal user sidebar exposes Dashboard, Upload, Study, Ask AI, Analytics, Recommend, Planner, and Profile. The admin sidebar exposes Dashboard and Database. Reports has a route but no current sidebar link.

### 7.5 Frontend configuration files

| File | Role |
|---|---|
| `package.json` | React/Vite dependencies and development/build/lint scripts |
| `vite.config.js` | Vite/React configuration and a legacy development proxy target |
| `tailwind.config.js` | Tailwind content paths, theme, and plugins |
| `postcss.config.js` | Tailwind and Autoprefixer processing |
| `frontend/Dockerfile` | Builds the frontend and starts Vite preview |
| `vercel.json` | Rewrites routes to the SPA entry page |
| `.env` / `.env.production` | Build-time API base URL selection; values must match the intended deployment |

Each page owns its own loading, error, and request state. The project does not use Redux, Zustand, or a shared React Query-style server cache.

## 8. Database architecture

### 8.1 Relationship model

```text
User
├──< PdfDocument
│   ├──< Topic
│   │   ├──< Quiz ──< QuizAttempt >── User
│   │   ├──< StudyProgress >───────── User
│   │   └──< ReviewLog >───────────── User
│   └──< DocumentChunk
```

`<` means “one parent has many child rows.”

Important constraints and design points:

- Email identifies a user uniquely.
- Study progress is conceptually unique for a user/topic pair.
- Document chunks are unique by `(pdf_id, chunk_index)`.
- Each vector contains 768 floating-point dimensions in PostgreSQL's `vector` type.
- Planner, recommendation, schedule, and dashboard responses are computed dynamically; they are not stored as separate plan tables.
- The physical PDF is stored on disk, while metadata and extracted text are stored in PostgreSQL.

### 8.2 Why pgvector is used

Normal JPA queries find exact relational records. Ask AI needs semantic similarity: a question and a chunk can use different words while expressing the same meaning. Gemini converts both into numeric vectors, and pgvector ranks chunks using cosine distance.

`DocumentChunk` represents its embedding as a string at the Java boundary and uses a Hibernate `@ColumnTransformer` to cast it to/from PostgreSQL `vector`. This avoids problematic generic JDBC result handling while keeping the database column vector-native.

### 8.3 Schema authority

The JPA entities are the current authoritative application schema because Hibernate runs with `spring.jpa.hibernate.ddl-auto=update`. `docs/schema.sql` is useful context but does not contain the complete current model. `docs/schema_oracle.sql` describes the abandoned Oracle direction and must not be used to understand the active database.

For a production system, versioned migrations through Flyway or Liquibase would be safer than relying on `ddl-auto=update`.

## 9. End-to-end system workflows

### 9.1 Startup

```text
Spring Boot starts
→ configuration and beans load
→ database connection/Hibernate schema update
→ security filter chain becomes active
→ development admin bootstrap may run
→ REST API listens on port 9096 by default
```

The backend requires PostgreSQL and the pgvector extension for full RAG functionality.

### 9.2 Registration and login

```text
React form
→ POST /api/auth/register or /api/auth/login
→ AuthController
→ AuthService
→ UserRepository
→ BCrypt password verification
→ JwtTokenProvider creates access token
→ frontend stores token/user in localStorage
→ Axios adds Bearer token to later requests
```

Passwords are never stored in plain text; only BCrypt hashes are persisted.

### 9.3 PDF upload and replacement

```text
UploadPdf page
→ multipart POST /api/pdfs/upload
→ write new UUID-named PDF file
→ PDFBox extracts and cleans text
→ database transaction:
     delete all earlier study data for this user
     save the new PdfDocument
→ commit
→ delete old physical files
→ chunk new document and request embeddings
→ persist DocumentChunk rows
→ return uploaded PDF data
→ frontend calls POST /api/topics/analyze/{pdfId}
```

Critical behavior:

- A successful replacement resets old scores/progress by design.
- Old files are removed after the database commit, reducing inconsistency if the transaction rolls back.
- RAG indexing currently runs synchronously even though an old code comment calls it asynchronous.
- RAG indexing failure is caught so the basic upload can still succeed. In that state, topic study may work while Ask AI remains unavailable until reprocessing succeeds.
- Topic generation is a separate request initiated by the frontend after upload.

### 9.4 Text extraction

`PdfExtractionService` opens the document with PDFBox and uses `PDFTextStripper`. Cleanup normalizes line endings and whitespace and removes non-ASCII characters.

Consequences:

- It works for PDFs that contain an actual text layer.
- It does not OCR scanned/image-only pages.
- Non-English or special characters may be lost because non-ASCII cleanup is aggressive.
- A scanned PDF can appear empty and be rejected even though a human can see text in the page image.

### 9.5 Topic and quiz generation

```text
POST /api/topics/analyze/{pdfId}
→ verify/load PDF
→ GeminiAiService sends up to 100,000 extracted characters
→ Gemini returns structured topic/quiz JSON
→ TopicAnalysisService validates and maps topics
→ ScoringEngineService calculates fallback values/priority
→ save up to 20 topics
→ QuizEngineService validates and saves 3 MCQs per topic
→ mark document analyzed
```

Quiz validation requires four unique, nonblank options. Gemini may return the correct answer as full option text or A/B/C/D; the service normalizes it to the stored option value.

The external Gemini generation call currently occurs inside the transactional topic-analysis method. That can keep a database transaction open during a slow network request and is a future refactoring opportunity.

### 9.6 Studying and adaptive updates

```text
Study page loads ranked topics
→ loads each topic's quizzes
→ user answers a quiz while timer runs
→ POST /api/quizzes/{quizId}/submit
→ save QuizAttempt
→ update StudyProgress totals and accuracy
→ WeaknessEngineService updates weakness
→ MasteryService updates Bayesian mastery + SM-2 state
→ save ReviewLog
→ recalculate topic priorities
→ return feedback and updated progress
```

The stored `completionPercentage` currently represents cumulative quiz accuracy, not the percentage of all available questions or content completed.

The Study page also calculates one aggregate session weakness in JavaScript and sends that same value to every loaded topic. This can conflict with the more precise per-topic weakness calculated by the backend and is an important improvement area.

### 9.7 Ask AI / RAG

```text
Question
→ create query embedding
→ pgvector cosine search over the user's chunks
→ select top 5 chunks
→ build a context-only prompt
→ Gemini generates an answer from that context
→ return answer plus source chunks
```

The user can restrict the search to one owned PDF or search all currently owned PDFs. The normal upload design usually leaves one PDF, but the query supports both scopes.

If query embedding fails—for example because of Gemini quota—the API returns a friendly “semantic search unavailable” answer rather than crashing the entire controller. This message means the embedding/retrieval stage failed; it does not necessarily mean login, PDF study, or the whole backend failed.

### 9.8 Dashboard, analytics, recommendations, and planner

These features are deterministic Java/database features, not LLM-generated output.

- Dashboard aggregates totals, scores, weak topics, and recent activity.
- Analytics calculates accuracy and topic comparisons from progress rows.
- Recommendations rank persisted progress records and produce insights/schedule data.
- Planner ranks all user topics and builds today's tasks, roadmap, and revision suggestions.

### 9.9 Reset everything

```text
Profile confirmation
→ DELETE /api/pdfs/reset
→ validate authenticated user
→ transaction deletes in dependency-safe order:
     review logs
     quiz attempts
     study progress
     quizzes
     document chunks
     topics/PDF records
→ commit database transaction
→ remove physical PDF files
→ clear local exam-date setting
→ return to dashboard
```

The account and authentication identity remain. Reset removes the user's study material and derived study data, not the user record.

## 10. Algorithms and formulas

There are several distinct priority/mastery calculations. They solve different problems and must not be treated as one formula.

### 10.1 Topic complexity fallback

If Gemini does not supply a usable normalized complexity value:

```text
complexity =
    0.40 × (conceptDensity / 10)
  + 0.30 × (keywordDifficulty / 10)
  + 0.20 × min(formulaCount / 10, 1)
  + 0.10 × min(contentLength / 10000, 1)
```

This combines conceptual density, hard vocabulary, formulas, and content length. Gemini-supplied 0–1 values take precedence when present.

### 10.2 Topic importance fallback

```text
importance =
    0.60 × (conceptDensity / 10)
  + 0.40 × (keywordDifficulty / 10)
```

Again, a valid Gemini-supplied importance value takes precedence.

### 10.3 Persisted topic priority

```text
urgency = 1 / (daysUntilExam + 1)

priority =
    0.35 × complexity
  + 0.25 × importance
  + 0.25 × weakness
  + 0.15 × urgency
```

Initial weakness is `1.0`, so new/unattempted topics start with high need. The urgency contribution rises as the exam date approaches.

### 10.4 Weakness levels

```text
No score        → NOT_ATTEMPTED → weakness 1.0
Score ≥ 75      → LOW           → weakness 0.2
Score ≥ 50      → MEDIUM        → weakness 0.5
Score below 50  → HIGH          → weakness 0.9
```

After progress changes, priorities are recalculated so weak topics rise in the study order.

### 10.5 Quiz scoring

The submitted option text is compared case-insensitively with the normalized correct option.

```text
correct answer   → marks = 1
incorrect answer → marks = 0

cumulative score = correct attempts / total attempts × 100
best score       = maximum historical cumulative score
```

### 10.6 Bayesian mastery

The model starts conservatively:

```text
alpha = 2
beta  = 8
mastery = alpha / (alpha + beta)
```

For each new response:

```text
correct   → alpha += 1
incorrect → beta  += 1
correct in under 3 seconds → beta += 0.3 guess penalty
```

The fast-answer penalty reduces overconfidence from answers that may be guesses.

### 10.7 Modified SM-2 review scheduling

Answer quality is inferred from correctness and duration:

```text
correct under 5 seconds  → quality 4
correct under 15 seconds → quality 3
slower correct           → quality 2
incorrect                → quality 1
```

- Quality below 3 resets repetitions and schedules a one-day interval.
- Otherwise intervals progress approximately `1 → 6 → ceil(previous interval × easiness factor)`.
- Easiness is updated using an SM-2-style formula and is bounded to at least `1.3`.
- The calculated next review date and state are stored in `StudyProgress` and a `ReviewLog` is added.

The active planner does not yet directly use `nextReviewDate`, so the scheduler is stored but only partly connected to the visible planning experience.

### 10.8 Active planner ranking

`PlannerService` uses:

```text
planner priority =
  (weakness × importance × complexity)
  / (bestScore / 100 + 0.1)
```

It treats `bestScore` as its mastery proxy, selects up to five tasks for today, creates a 7–14 day roadmap, and produces heuristic revision and recommendation sections. It does not currently use Bayesian `masteryLevel` or the SM-2 due date.

### 10.9 Separate request-driven study plan

`StudyPlanService` uses caller-provided metrics rather than database progress:

```text
request-plan priority =
  (weakness × importance × difficulty)
  / (mastery + 0.1)
```

It ranks topics, keeps up to eight, allocates available study time, creates learning/revision blocks, and reports dropped topics. The current frontend does not call this endpoint.

### 10.10 Recommendation ranking

`RecommendationEngineService` uses a weighted combination similar in purpose to topic priority, then derives insights and distributes suggested study over seven days. It begins from existing `StudyProgress` rows, so completely unattempted topics with no progress row may be missing from recommendations.

### 10.11 Frontend session weakness

The Study page separately calculates:

```text
session weakness = (1 - session accuracy) × attempt multiplier

attempts ≤ 3 → multiplier 1.8
attempts ≤ 8 → multiplier 1.2
otherwise    → multiplier 1.0
```

This is a UI-side heuristic and is not the same as the backend weakness thresholds. Applying one aggregate value to every topic is a known design mismatch.

## 11. RAG architecture in detail

RAG means **retrieval-augmented generation**. Instead of asking Gemini to answer from general knowledge, the backend first retrieves sections of the user's PDF and instructs Gemini to answer only from those sections.

### 11.1 Index construction

`TextChunkingService` uses these limits:

- Target chunk size: 2,048 characters.
- Overlap: 200 characters.
- Maximum chunks per document: 500.
- Prefer paragraph or sentence boundaries where practical.
- Estimated token count: approximately `characters / 4`.
- Estimated page: approximately `character position / 3000 + 1`.

The stored page number is therefore an estimate, not the exact PDF page from PDFBox.

`EmbeddingService` then:

1. Prefixes document input with `title: none | text:`.
2. Calls the `gemini-embedding-2` batch endpoint.
3. Sends at most 20 chunks per batch.
4. Requests/validates 768-dimensional vectors.
5. Rejects missing, malformed, non-finite, or zero vectors.
6. Retries transient quota/service/network failures within its retry policy.
7. Stops document indexing when a batch cannot be completed safely.

### 11.2 Question retrieval

For a question, `EmbeddingService` prefixes the text for asymmetric question answering:

```text
task: question answering | query: <question>
```

`VectorSearchService` uses pgvector's cosine-distance operator:

```text
distance   = stored_embedding <=> query_embedding
similarity = 1 - distance
```

It returns the top five chunks. Search is scoped either to a specific PDF whose ownership was verified or to all PDFs owned by the user. There is currently no minimum similarity cutoff, so even weakly related top results can be passed to generation.

### 11.3 Answer generation

`RagAugmentedService` builds a prompt containing:

- The user's question.
- The retrieved chunk text.
- Source identifiers/page estimates.
- An instruction to rely on the provided context and admit when the answer is absent.

Answer generation tries the configured current Gemini fallback sequence beginning with Gemini 3.5 Flash and then Gemini 2.5 Flash. Questions are capped at 2,000 characters. The response includes both the generated answer and structured chunk-source data.

### 11.4 Atomic reprocessing

Manual reprocessing does not delete the working index first. It:

1. Reads and chunks the document.
2. Generates and validates every replacement embedding.
3. Only after successful generation, opens the database mutation.
4. Replaces the old chunk rows transactionally.

This preserves the old usable index if external embedding generation fails midway.

### 11.5 AI calls and quota use

Different workflows consume different Gemini calls:

| Workflow | Gemini usage |
|---|---|
| Topic analysis | One or more text-generation attempts depending on model fallback |
| PDF RAG indexing | One embedding per chunk, sent in batches of 20 |
| Ask AI | One query embedding plus one answer-generation call |
| Manual reprocess | Re-embeds all chunks and replaces the index |

A large document can hit rate/quota limits. Existing workspace logs show a 253-chunk document reaching HTTP 429 during embedding around chunks 100–119. The upload and topic analysis could still succeed while RAG remained incomplete. Therefore a successful PDF upload does not guarantee Ask AI readiness.

Quiz generation is not currently RAG-backed. `RagAugmentedService.generateQuizContext()` exists but is unused; quizzes are produced by the full-text topic-analysis flow.

## 12. Complete API reference

Unless marked public, endpoints require `Authorization: Bearer <JWT>`. Admin endpoints additionally require the admin role.

### 12.1 Authentication

| Method | Endpoint | Purpose |
|---|---|---|
| POST | `/api/auth/register` | Create a user and return auth data; public |
| POST | `/api/auth/login` | Verify credentials and return JWT/user data; public |
| POST | `/api/auth/seed-admin` | Development admin seed path; currently public because all auth routes are permitted |

### 12.2 PDFs

| Method | Endpoint | Purpose |
|---|---|---|
| POST | `/api/pdfs/upload` | Upload multipart PDF and exam date; replaces existing user study material |
| GET | `/api/pdfs` | List current user's PDF records |
| GET | `/api/pdfs/{pdfId}` | Get one PDF summary |
| GET | `/api/pdfs/{pdfId}/detail` | Get expanded PDF/topic detail |
| DELETE | `/api/pdfs/{pdfId}` | Delete one owned PDF and dependent data |
| DELETE | `/api/pdfs/reset` | Delete all study data/files for current user while keeping account |

### 12.3 Topics

| Method | Endpoint | Purpose |
|---|---|---|
| POST | `/api/topics/analyze/{pdfId}` | Run Gemini topic/quiz analysis for a PDF |
| GET | `/api/topics/pdf/{pdfId}` | List topics for one PDF |
| GET | `/api/topics/ranked/pdf/{pdfId}` | Get ranked topics for one PDF |
| GET | `/api/topics/ranked` | Get current user's ranked topics |
| GET | `/api/topics/{topicId}` | Get a topic |
| POST | `/api/topics/{topicId}/update-weakness` | Apply a weakness value/update |

### 12.4 Quizzes

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/api/quizzes/topic/{topicId}` | List quizzes for a topic |
| GET | `/api/quizzes/{quizId}` | Get one quiz |
| POST | `/api/quizzes/{quizId}/submit` | Submit answer/duration and update adaptive state |

### 12.5 RAG

| Method | Endpoint | Purpose |
|---|---|---|
| POST | `/api/rag/ask` | Ask a document-grounded question over one/all owned PDFs |
| POST | `/api/rag/reprocess/{pdfId}` | Atomically rebuild an owned PDF's vector index |

### 12.6 Dashboard and analytics

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/api/dashboard` | Overall dashboard for current user |
| GET | `/api/dashboard/pdf/{pdfId}` | PDF-specific dashboard |
| GET | `/api/analytics/performance` | Overall performance metrics |
| GET | `/api/analytics/topic/{topicId}` | Per-topic analytics |
| GET | `/api/analytics/comparison` | Topic/performance comparison data |

### 12.7 Recommendations and planning

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/api/recommendations/next-topics` | Ranked next-topic suggestions |
| GET | `/api/recommendations/insights` | Strength/weakness/performance insights |
| GET | `/api/recommendations/schedule` | Suggested seven-day schedule |
| GET | `/api/planner` | Active database-driven personalized planner |
| POST | `/api/study-plan/generate` | Generate a stateless plan from supplied topic/time data |

### 12.8 Administration

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/api/admin/dashboard` | Entity/system counts |
| GET | `/api/admin/entities` | Supported entity metadata |
| GET | `/api/admin/entities/{entityName}` | Browse records for a supported entity |
| DELETE | `/api/admin/entities/{entityName}/{id}` | Delete an allowed record |

There is no current backend `/api/reports/study-report`, flashcard API, or `/api/health` controller. Frontend helpers or old documentation referring to them do not make those APIs operational.

## 13. Authentication and security design

### 13.1 Normal security flow

1. BCrypt hashes passwords.
2. Login issues a signed JWT.
3. The browser stores the JWT in `localStorage`.
4. Axios attaches it to API requests.
5. `JwtAuthenticationFilter` validates it and establishes the current principal.
6. Services/controllers use that principal to identify the user.

### 13.2 Current security risks to fix before production

- Development Gemini and JWT secrets are present in committed/default configuration. Rotate them and use environment/secret management; do not merely hide the existing values in the UI.
- A fixed development admin account is created at startup, and the seed-admin endpoint falls under public `/api/auth/**` access.
- CORS configuration includes a broad wildcard source in the current code.
- The app has no application-level rate limiting, including for expensive AI endpoints.
- JWT storage in `localStorage` increases the impact of any XSS vulnerability.
- Ownership enforcement is good in the corrected PDF delete/reset and RAG paths, but it is not consistently established for every topic, quiz, and PDF-dashboard identifier path.
- Admin record deletion needs strict allow-listing and dependency-aware behavior.
- Uploaded content and filenames require continued validation; PDF parsing does not make arbitrary uploads harmless.

The literal secrets and fixed development password are intentionally not copied into this guide.

## 14. Transactions, deletion, and failure behavior

### 14.1 Why delete order matters

Relational foreign keys prevent deleting a parent while children still reference it. Reset therefore removes deepest dependent rows first:

```text
ReviewLog / QuizAttempt / StudyProgress
→ Quiz / DocumentChunk
→ Topic
→ PdfDocument
```

This avoids constraint errors and `UnexpectedRollbackException` caused by a database operation marking a transaction rollback-only before later code attempts to commit it.

### 14.2 Database and filesystem consistency

The database transaction cannot automatically roll back filesystem deletion. The implementation therefore records which stored files must be removed and deletes them only after the database commit. If the database work rolls back, the original physical file remains.

### 14.3 External AI calls

- Upload catches RAG indexing failure so it does not automatically undo the base PDF upload.
- Reprocessing builds a complete replacement index before touching the old one.
- Ask AI converts embedding failure into a user-friendly unavailable response.
- Topic analysis still performs a potentially slow generation request within a transaction.

### 14.4 Interpreting common errors

| Symptom | Likely subsystem | Checks |
|---|---|---|
| `UnexpectedRollbackException` | Transaction already marked rollback-only | Find the first database exception earlier in the backend log; do not focus only on the final commit message |
| `JpaSystemException: No results were returned by the query` | Native/modifying query declared or invoked with the wrong result expectation | Check repository/EntityManager query annotations, return type, and whether it is an update/delete |
| “Semantic search feature is currently unavailable” | Query embedding/RAG retrieval | Check Gemini key/quota, chunk count, pgvector, and reprocess status |
| Upload works but Ask AI fails | Partial/failed RAG indexing | Reprocess after quota is available and inspect embedding batch logs |
| PDF has no extracted text | PDF contains images instead of a text layer | Add OCR or upload a text-based PDF |
| New upload makes old scores disappear | Expected single-PDF replacement | This is current product behavior, not a random score calculation failure |

## 15. AI versus deterministic logic

It is useful to be precise about what “AI-powered” means in this project.

| Gemini-dependent | Deterministic Java/PostgreSQL |
|---|---|
| Topic extraction | Authentication and ownership checks |
| Quiz question generation | Quiz correctness and cumulative score |
| Document/query embeddings | Weakness thresholds |
| Final RAG answer wording | Topic-priority formulas |
|  | Bayesian mastery and modified SM-2 |
|  | Dashboard and analytics aggregation |
|  | Planner construction |
|  | Recommendations and schedule distribution |

If Gemini is unavailable, login and previously stored deterministic calculations can still work. New topic generation, new embeddings, and generated RAG answers cannot.

## 16. Local configuration and running the project

### 16.1 Prerequisites

- Java 17 or newer compatible JDK.
- Maven.
- Node.js/npm.
- PostgreSQL with pgvector, or Docker.
- A valid Gemini API key supplied securely.

### 16.2 Typical Windows development startup

From the repository root:

```powershell
docker compose up -d postgres
```

In a backend terminal:

```powershell
cd backend
mvn.cmd spring-boot:run
```

In a frontend terminal:

```powershell
cd frontend
npm.cmd install
npm.cmd run dev
```

Expected local addresses:

- Frontend: `http://localhost:3000`
- Backend API: `http://localhost:9096/api`
- PostgreSQL: normally `localhost:5432`, database `aasa_db`

When the backend starts from `backend`, uploaded PDFs normally appear under `backend/uploads/pdfs`.

### 16.3 Production property names

The production profile expects environment-backed values for:

```text
SPRING_DATASOURCE_URL
SPRING_DATASOURCE_USERNAME
SPRING_DATASOURCE_PASSWORD
JWT_SECRET
JWT_EXPIRATION
GEMINI_API_KEY
CORS_ALLOWED_ORIGINS
```

Activate the intended Spring profile explicitly. A `backend/.env` file is not automatically imported by Spring Boot simply because it exists.

## 17. Build and test commands

Backend tests:

```powershell
cd backend
mvn.cmd test
```

Frontend production build:

```powershell
cd frontend
npm.cmd run build
```

The latest inspected workspace artifacts report:

- All four current backend source tests passing.
- A successful frontend production bundle.
- No frontend test suite.
- A frontend bundle-size warning for a large JavaScript chunk.

Generated `target` reports and historical logs can provide evidence, but only tests still present under `src/test` are reproducible source coverage. A previous real-PostgreSQL RAG integration report remains in generated artifacts, while its temporary test source is absent.

## 18. Deployment configuration and known drift

The deployment files are useful starting points but are not currently verified as a working end-to-end deployment.

### 18.1 Docker Compose

- PostgreSQL uses a pgvector-enabled PostgreSQL 17 image.
- The backend defaults to port 9096, while Compose currently maps 8080 to 8080. It should map the chosen host port to 9096 or explicitly set `PORT=8080`.
- Vite preview normally uses port 4173, while Compose exposes port 3000 without aligning the preview command.
- Preview is not explicitly bound to `0.0.0.0`.
- `VITE_API_URL` is supplied at container runtime, but Vite normally needs it during the production build.
- Compose does not explicitly activate the production Spring profile.
- Its fallback JWT value may be unsuitable for the configured HS512 signing requirements.
- A fresh PostgreSQL volume may conflict because the database environment creates `aasa_db` before mounted SQL tries to create the same database again.

### 18.2 Vercel/frontend

- `vercel.json` handles single-page application routing.
- The production API URL is compiled from frontend production environment configuration.
- The existing Vite proxy points at an older backend port, but normal Axios calls use an absolute configured URL and bypass that proxy.
- There is no backend Render deployment manifest in this repository.

### 18.3 File storage

PDFs are stored on the backend filesystem. This is acceptable locally but is not durable on many ephemeral cloud hosts. Production should use a persistent volume or object storage and store only durable object references in PostgreSQL.

## 19. Active, legacy, incomplete, and unused areas

### 19.1 Active core

- JWT authentication and role-aware UI.
- Single-PDF upload/replacement/reset.
- PDFBox extraction.
- Gemini topic and quiz generation.
- Quiz attempts, weakness, priority, Bayesian mastery, and SM-2 state.
- Dashboard, analytics, recommendations, and planner.
- pgvector-backed RAG Ask AI and atomic reprocessing.
- Admin database browsing.

### 19.2 Unwired or legacy backend code

- `OllamaAiService` is unused.
- `RagAugmentedService.generateQuizContext()` is unused.
- Flashcard code/API ideas are inactive and have no backend controller.
- `PdfUploadRequest` is unused by multipart upload.
- `StudyPlanResponse` is unused.
- `ScoringEngineService.calculateImportanceScore(Topic)` is an unused placeholder.
- Due-review and predicted-retention helpers are not connected to the active planner/controllers.
- Oracle configuration/schema references are legacy.
- The Google AI SDK dependency is commented; direct REST calls are active.
- SQL admin seed artifacts are largely superseded by Java startup behavior.

### 19.3 Unwired or incomplete frontend code

- `ProgressTimeline.jsx` is unrouted and contains mock/random values.
- `Navigation.jsx` supports only that unrouted prototype.
- `GlassCard.jsx` has no active imports.
- Reports calls a backend endpoint that does not exist.
- The reports route is not exposed in the sidebar.
- Planner task-completion state is local-only and disappears on reload.
- Ask AI chat history is local-only and disappears on reload.
- Ask AI can render source information in more than one place.
- There is no visible per-PDF delete button even though an API helper exists.
- Several API helpers are currently unused, including individual resource, study-plan, flashcard, and admin-detail helpers.
- Supabase is installed as a frontend dependency but is not used by the active architecture.

## 20. Current limitations and improvement priorities

### Highest priority

1. Rotate all committed Gemini/JWT secrets and use environment/secret storage.
2. Remove or protect public admin seeding and eliminate fixed startup credentials in production.
3. Add consistent ownership checks to every topic, quiz, analytics, and dashboard ID endpoint.
4. Add Gemini quota/rate handling, index readiness state, and clearer user feedback.
5. Add a real migration tool and make pgvector/schema setup reproducible.
6. Correct Docker ports, Vite build-time environment, profile activation, and persistent PDF storage.

### Product/data consistency

1. Decide whether the product is permanently single-PDF or should truly support multiple PDFs.
2. Remove the frontend session-wide weakness overwrite or make it per-topic.
3. Connect SM-2 due dates and Bayesian mastery to the active planner.
4. Include never-attempted topics in recommendations.
5. Define `completionPercentage` as actual coverage if that is the intended UI meaning.
6. Persist planner completion and chat history if users should retain them.
7. Add an explicit RAG state such as `NOT_INDEXED`, `INDEXING`, `READY`, or `FAILED`.

### Quality and maintainability

1. Move slow Gemini topic analysis outside long-running database transactions.
2. Add real PostgreSQL/pgvector integration tests to source control.
3. Add controller security/ownership tests and reset/upload concurrency tests.
4. Add frontend component and end-to-end tests.
5. Split the large frontend bundle and add working ESLint configuration.
6. Remove or finish unused routes, components, DTOs, and API helpers.
7. Update README/API/schema documentation to match port 9096, PostgreSQL, current endpoints, and the single-PDF model.

## 21. Practical debugging map

| Problem | Start with these files |
|---|---|
| Login/JWT failure | `AuthController`, `AuthService`, `JwtTokenProvider`, `JwtAuthenticationFilter`, `SecurityConfig`, `AuthContext.jsx`, `api.js` |
| Upload/reset/delete failure | `PdfController`, `PdfManagementService`, PDF/topic/quiz/progress repositories, `Profile.jsx`, `UploadPdf.jsx` |
| Empty/scanned PDF | `PdfExtractionService` |
| Bad AI topics or malformed quizzes | `GeminiAiService`, `TopicAnalysisService`, `QuizEngineService` |
| Incorrect score/weakness | `QuizEngineService`, `StudyProgressService`, `WeaknessEngineService`, `Study.jsx` |
| Mastery/review dates | `MasteryService`, `StudyProgress`, `ReviewLog` |
| Planner order | `PlannerService`, `ScoringEngineService`, `Planner.jsx` |
| Recommendations | `RecommendationEngineService`, `Recommendations.jsx` |
| Ask AI unavailable | `RagController`, `RagAugmentedService`, `EmbeddingService`, `VectorSearchService`, `DocumentChunk`, PostgreSQL pgvector setup |
| Frontend unauthorized redirect | `api.js`, `AuthContext.jsx`, `ProtectedRoute.jsx` |
| Admin database view | `AdminController`, `Admin.jsx` |
| Port/deployment mismatch | Spring properties, frontend env files, `vite.config.js`, both Dockerfiles, `docker-compose.yml` |

## 22. A concise mental model

The simplest accurate way to understand the system is:

```text
PDF content
  ├─ Gemini topic analysis → Topics + Quizzes
  └─ chunking + embeddings → DocumentChunks for RAG

Quiz attempts
  → StudyProgress
  → weakness + priority
  → Bayesian mastery + SM-2
  → dashboard + analytics + recommendations + planner

User question
  → query embedding
  → nearest PDF chunks
  → grounded Gemini answer + sources
```

Gemini supplies content understanding, generated questions, embeddings, and answer language. The adaptive study behavior—the scoring, weakness, mastery, scheduling, analytics, and planning—is mostly deterministic application logic in the Java services.

## 23. Glossary

| Term | Meaning in this project |
|---|---|
| JWT | Signed token proving the user's authenticated identity |
| DTO | API request/response object kept separate from database entities |
| JPA/Hibernate | Java persistence layer mapping entities to relational tables |
| pgvector | PostgreSQL extension that stores vectors and calculates similarity |
| Embedding | Numeric representation of text meaning; 768 values here |
| RAG | Retrieve PDF chunks first, then generate an answer grounded in them |
| Cosine similarity | Measure used to rank query/chunk semantic closeness |
| Bayesian mastery | Probability-like knowledge estimate updated from answers |
| SM-2 | Spaced-repetition scheduling algorithm adapted here using answer speed |
| Weakness | Need-for-study value derived from performance |
| Priority | Ranking score used to decide which topic should be studied sooner |
| Transaction | Database operation group that commits completely or rolls back completely |

---

When behavior and older documentation disagree, inspect the controller → service → repository path in the current source, then verify relevant Spring/Vite environment configuration. This guide intentionally labels observed limitations instead of presenting unfinished code as a completed feature.
