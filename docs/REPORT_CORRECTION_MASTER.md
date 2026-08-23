# REPORT CORRECTION MASTER — Every Edit, Beginning to End

Single source of truth for correcting your final report (.docx). Work top-to-bottom:
front matter first (examiners read it first), then chapters, then back matter.
Every entry gives **WHERE → ACTION → BEFORE → AFTER → WHY**.

**Companion files:** `docs/REPORT_RESTRUCTURE_PLAN.md` (chapter reorganisation +
diagrams), `docs/EVALUATION.md` (RAG metrics), `docs/test-evidence-full-suite.log`
(34-test proof screenshot source).

---

## HOW TO WORK IN WORD

1. `Ctrl+H` = Find & Replace. For quoted titles the document uses curly quotes
   (“ ”). Search WITHOUT the surrounding quote marks to be safe.
2. After every heading move/add/delete: `References → Update Table` on
   TOC, List of Figures, List of Tables.
3. Headings must use Word's Heading 1/2/3 STYLES (not bold manual text) or the
   TOC will not pick them up.
4. Do the global sweeps in §SWEEP near the end, then re-read once fully.

---

# PART A — FRONT MATTER (pages i–viii)

## A1. Cover page
- **WHERE:** Title block, cover page (p. i).
- **ACTION:** REPLACE title.
- **BEFORE:** `STUDY PLANNER WITH PRIORITY ALGORITHM`
- **AFTER:** `STUDY PLANNER USING BAYESIAN KNOWLEDGE TRACING AND RAG`
- **WHY:** The abstract and every algorithm chapter now describe BKT + RAG. An
  examiner who sees the old rejected title ("basic priority algorithm") on page 1
  will assume the work underneath is the same.

## A2. Supervisor's Recommendation page
- **WHERE:** Sentence introducing the project title.
- **ACTION:** REPLACE title inside the sentence.
- **BEFORE:** `…entitled “STUDY PLANNER USING PRIORITY ALGORITHM” in partial fulfilment…`
- **AFTER:** `…entitled “STUDY PLANNER USING BAYESIAN KNOWLEDGE TRACING AND RAG” in partial fulfilment…`

## A3. Letter of Approval page
- **WHERE:** Sentence identifying the student and project.
- **ACTION:** REPLACE title inside the sentence.
- **BEFORE:** `…prepared by UJWOL SHRESTHA (105902086) entitled “STUDY PLANNER USING PRIORITY ALGORITHM”…`
- **AFTER:** same sentence with `“STUDY PLANNER USING BAYESIAN KNOWLEDGE TRACING AND RAG”`.

## A4. Acknowledgement page
- **WHERE:** First paragraph.
- **ACTION:** REPLACE project name.
- **BEFORE:** `"Study Planner Using Priority Algorithm."`
- **AFTER:** `"Study Planner Using Bayesian Knowledge Tracing and RAG."`

## A5. Abstract
- **WHERE:** Abstract page.
- **ACTION:** REPLACE the whole abstract paragraph.
- **BEFORE:** any sentences mentioning *"weighted priority score"*, *"predefined Quick
  Answers only"*, or that retrieval is not semantic.
- **AFTER (paste this):**

> This project presents an Adaptive Knowledge-Tracing and RAG Recommendation System for
> personalized study planning. The system ingests course PDF documents through text
> extraction and structure-aware semantic chunking, generates 768-dimensional Gemini
> embeddings for every chunk, and indexes them in PostgreSQL with the pgvector extension.
> A student's mastery of each topic is estimated with Bayesian Knowledge Tracing (BKT),
> which updates a posterior mastery probability after every quiz attempt using explicit
> guess and slip probabilities and a learning transition. Memory decay is modeled with an
> exponential forgetting curve conditioned on mastery and elapsed time since the last
> review. These evidence-based signals, together with examination urgency and topic
> importance, drive an adaptive priority function that orders daily study tasks instead of
> a static weighted formula. For content support, the system implements a complete
> Retrieval-Augmented Generation (RAG) pipeline: a question is embedded, top-20 candidate
> chunks are retrieved by pgvector cosine similarity, a hybrid reranker combining vector,
> keyword-overlap, and title-match signals selects the top five chunks, and the Gemini
> model generates a strictly grounded answer with inline [Source N] citations and page-level
> source references. The implementation comprises a Spring Boot backend, a React frontend,
> and a PostgreSQL/pgvector database, and is validated by 34 automated unit and integration
> tests, including live-database retrieval tests that demonstrate ownership isolation,
> query-dependent retrieval, and reranking-induced ordering changes.

- **WHY:** the abstract is the contract with the examiner — every claim here is backed by
  running code verified today.

## A6. Abstract — keywords line
- **BEFORE:** `Keywords: …, Priority Recommendation, Mastery estimation, Quick Answers.`
- **AFTER:** `Keywords: Bayesian Knowledge Tracing, Retrieval-Augmented Generation, pgvector, Forgetting Curve, Adaptive Learning.`

## A7. List of Figures — ghost row
- **WHERE:** List of Figures, row promising `Figure 3.2 Class Diagram … 14`.
- **PROBLEM:** no matching caption exists in the body.
- **ACTION (choose one):** if you add the class diagram (Part D below), keep the row and
  update its page number after inserting the figure; otherwise DELETE the row.
- **WHY:** an LoF entry pointing nowhere is an automatic credibility hit.

## A8. List of Abbreviations
- **ACTION:** ADD missing entries used by the new text:
  `BKT — Bayesian Knowledge Tracing`; `RAG — Retrieval-Augmented Generation`;
  `pgvector — open-source vector similarity search extension for PostgreSQL`;
  `HNSW — Hierarchical Navigable Small World (approximate nearest-neighbour index)`.

# PART B — CHAPTER 1 (Introduction)

## B1. §1.1 — recommendation-engine sentence
- **BEFORE:** `A deterministic recommendation engine ranks topics using complexity, importance, weakness, and exam urgency.`
- **AFTER (paste):**

> An adaptive priority engine orders daily study tasks using four evidence-based
> signals: a Bayesian Knowledge Tracing mastery probability updated by every quiz
> attempt, an exponential forgetting-curve risk computed from the time elapsed since
> the last revision, an examination urgency term derived from the days remaining, and
> an AI-assessed topic importance weight. Because every input changes with real learner
> performance and with the passage of time, the ordering adapts continuously instead of
> combining static document properties through fixed weights.

## B2. §1.1 — RAG-status paragraph
- **BEFORE:** the paragraph from `"The current application provides predefined Quick Answers…"`
  ending `…Full document-grounded RAG therefore remains partially implemented.`
- **AFTER (paste):**

> The application exposes free-form question answering through POST /api/rag/ask and
> the AI Chat page. A question is embedded, the top twenty candidate chunks are
> retrieved by pgvector cosine similarity over automatically indexed chunks, a hybrid
> reranker combining vector, keyword-overlap, and title-match signals selects the five
> most relevant chunks, and the Gemini model generates a strictly grounded answer with
> inline [Source N] citations and page-level source references.

## B3. §1.3 Objectives — two bullets
- Bullet 1 **BEFORE:** `"To provide predefined Quick Answers from stored topic information and to prepare backend services for future document-grounded question answering…"`
  **AFTER:**

> To provide document-grounded question answering through a complete RAG pipeline:
> semantic chunking, embedding generation, pgvector cosine retrieval, hybrid reranking,
> and citation-grounded answer generation.

- Bullet 2 **BEFORE:** `"To implement a priority-based recommendation engine that combines topic complexity, importance, student weakness and examination urgency."`
  **AFTER:**

> To implement an adaptive priority engine driven by Bayesian knowledge-tracing mastery
> estimates, forgetting-curve revision risk, examination urgency, and AI-assessed topic
> importance.

## B4. §1.4.1 Scope — Quick Answers bullet
- **BEFORE:** `"Predefined Quick Answers generated from the analyzed topic descriptions and saved quiz information of the authenticated user's active PDF."`
- **AFTER:**

> Predefined Quick Answers together with free-form AI Chat question answering grounded
> in retrieved chunks of the authenticated user's active PDF, each answer accompanied by
> page-level source references.

## B5. §1.4.2 Limitations — obsolete claim
- **BEFORE:** `"…Free-form RAG and pgvector-based semantic retrieval are not active in the submitted version."`
- **AFTER:**

> Topic and quiz generation depend on Gemini API availability, internet access, model
> availability and quota. Semantic retrieval additionally requires the pgvector extension
> to be enabled in the PostgreSQL instance; when it is unavailable the retrieval service
> degrades gracefully and reports that no relevant material could be retrieved rather than
> producing ungrounded answers.

---

# PART C — CHAPTER 2 (Literature Review)

## C1. Research-gap placement (only if adopting the final-report structure)
- **ACTION:** delete heading `2.3 Research Gap and Justification`; move its paragraphs to
  the end of §2.2 as a closing sub-part starting with text like:
  `"From this review, three gaps emerge…"`.
- **UPDATE** inside those paragraphs: any claim such as *"no reviewed study combines
  mastery modelling with grounded document retrieval"* should become your contribution
  statement — your system now implements exactly that combination.
- **ADD (optional but strong):** one short paragraph citing BKT (Corbett & Anderson,
  1995) and the forgetting curve (Ebbinghaus) as the theoretical basis for §3.3's
  equations, so Chapter 3 does not introduce them cold.

---

# PART D — CHAPTER 3 (System Analysis and Design)

## D1. Heading moves (do these FIRST, text follows)

| Old location | New location |
|---|---|
| 3.3.1 System Architecture | **3.2.1** System Architecture |
| 3.3.2 Activity Diagrams | **3.1.5** Process Modelling using Activity Diagrams |
| 3.3.4 Database Design | **3.2.4** Database Design |
| 3.3.5 Security & Ownership | **3.2.5** Security and Ownership Design |
| 4.4 Algorithms | **3.3** Algorithm Details (see D2) |

**ADD three new subsections with diagrams** (draw.io, one evening; caption each as
`Figure 3.x` and add to List of Figures):
- **3.1.3** Object Modelling using Class and Object Diagrams
- **3.1.4** Dynamic Modelling using State and Sequence Diagrams (state: PDF lifecycle
  UPLOADED→PROCESSING→READY→FAILED; sequence: ask-question RAG flow)
- **3.2.2** Component Diagram · **3.2.3** Deployment Diagram
  (React/Vite → Spring Boot API → PostgreSQL+pgvector; Gemini API external)

## D2. NEW §3.3 Algorithm Details (moved from old 4.4 — paste-ready)

> **3.3.1 Bayesian Knowledge Tracing Mastery Estimation.** After every quiz attempt the
> posterior mastery probability P is updated from the observed correctness using explicit
> guess (g = 0.20) and slip (s = 0.10) parameters. A correct answer yields
> P_obs = (1−s)·P / [(1−s)·P + g·(1−P)]; an incorrect answer yields
> P_obs = s·P / [s·P + (1−g)·(1−P)]. A learning transition then models skill acquisition
> during the practice opportunity: P_new = P_obs + (1−P_obs)·L, with L = 0.40 after a
> correct answer and L = 0.15 after feedback on an error.
>
> **3.3.2 Forgetting-Curve Revision Risk.** Memory decay follows an exponential
> forgetting curve conditioned on mastery: risk = 1 − e^(−λ·d), where d is whole days
> since the last review and λ = 0.15 × (1.6 − P). Weaker knowledge therefore decays
> faster than well-consolidated knowledge, so revision scheduling is sensitive to both
> time and ability.
>
> **3.3.3 Adaptive Priority Computation.** Daily topic ordering combines four
> evidence-based signals: priority = 0.40·(1 − P) + 0.25·risk + 0.20·U + 0.15·I, where U
> = 1/(daysUntilExam + 1) is examination urgency (neutral 0.5 when unscheduled) and I is
> the AI-assessed topic importance. Every term changes with learner performance and the
> passage of time, replacing the earlier static weighted combination of document
> properties.
>
> **3.3.4 Hybrid Retrieval and Reranking (RAG).** The pipeline runs five stages:
> (1) semantic chunking of ingested PDFs with page tracking; (2) 768-dimensional Gemini
> embeddings persisted per chunk; (3) pgvector cosine retrieval of the top-20 candidates,
> similarity = 1 − (embedding ⇔ query distance); (4) hybrid reranking with
> rerankScore = 0.70·similarity + 0.20·keywordOverlap + 0.10·titleMatch, keeping the top
> five — this corrects pure vector retrieval, where an embedding-similar but
> non-answer-bearing chunk can outrank the chunk that literally answers the question;
> (5) grounded generation — Gemini receives only those five chunks under instructions to
> answer exclusively from context, refuse when evidence is insufficient, and cite every
> factual statement as [Source N]; the UI lists file, page, relevance, rerank score and
> rank for each source.

---

# PART E — CHAPTER 4 (Implementation and Testing)

## E1. Entity table — DocumentChunk row
- **BEFORE:** `Belongs to a PDF and supports the partially implemented document-chunking prototype. Its embedding is currently stored as text.`
- **AFTER:**
> Belongs to a PDF and forms the RAG index. The embedding is persisted as a pgvector text
> literal and cast to the vector type at query time for cosine similarity search.

## E2. §4.7 Implementation Constraints — pgvector paragraph
- **BEFORE:** `The current entity and SQL schema store embeddings as TEXT, and VectorSearchService uses sequential chunk retrieval. pgvector is therefore not active.`
- **AFTER:**
> Embeddings are stored as pgvector-compatible text literals and cast to the vector type
> at query time; the pgvector extension is enabled in the runtime database and powers all
> semantic retrieval. A future optimisation is a native vector column with an HNSW index
> for approximate nearest-neighbour search at larger corpus sizes.
- **ALSO:** delete any sentence about port/build configuration drift if docker-compose and
  application.properties are now aligned (verify before claiming).

## E3. §4.1 Tools Used
- Rename `4.1 Technology Stack` → short intro sentence + bullet list:
> Java 17, Spring Boot 3, PostgreSQL 16 + pgvector, Gemini API (embeddings + generation),
> React 18 + Vite + Tailwind, Maven, JUnit 5, Mockito, draw.io (diagrams), Docker (database).

## E4. NEW §4.2 Testing with test-case tables (build from the 34 real tests)

**Table 4.1 Unit Test Cases** (8 representative rows — all verified passing):

| ID | Module | Description | Input | Expected | Result |
|---|---|---|---|---|---|
| UT-01 | BKT | Correct answer raises mastery | prior 0.20, correct | posterior > 0.20 | Pass |
| UT-02 | BKT | Incorrect answer lowers mastery | prior 0.80, incorrect | posterior < 0.80 | Pass |
| UT-03 | BKT | Probability bounded | priors −0.5 / 2.0 | clamped to [0,1] | Pass |
| UT-04 | Forgetting | Risk grows with days | 1 vs 7 vs 30 days | monotonic increase | Pass |
| UT-05 | Priority | Weights match formula | mastery 0, exam +9d | 0.40+0.20·0.1+0.15·0.5 | Pass |
| UT-06 | Reranker | Keyword evidence reorders chunks | rank-2 chunk answers question | rank 1 after rerank | Pass |
| UT-07 | Reranker | Trims pool to top-N | 20 candidates | exactly 5 returned | Pass |
| UT-08 | Weakness | Evidence classification | mixed correctness/latency | expected level | Pass |

**Table 4.2 System Test Cases** (fill Result by clicking through once):

| ID | Scenario | Steps | Expected | Result |
|---|---|---|---|---|
| ST-01 | Register/Login | valid credentials | JWT issued, dashboard loads | |
| ST-02 | Upload PDF | upload lecture PDF | status READY, topics generated | |
| ST-03 | Ask question | AI Chat: "Explain OSI model" | answer cites [Source N]; sources list file/page/relevance/rerank | |
| ST-04 | Grounded refusal | ask something absent from PDF | assistant states insufficient material | |
| ST-05 | Take quiz | answer 5 questions | attempts stored; mastery changes | |
| ST-06 | Priority adapts | fail a previously-strong topic | topic priority rises on dashboard | |
| ST-07 | Ownership | user B requests user A's PDF id | 404, no chunks leaked | |

## E5. §4.3 Result Analysis (half a page)
> Report Hit@5 / MRR from `docs/EVALUATION.md`, note on how many of the evaluated
> questions reranking changed the retrieved ordering, and state that all 34 automated
> tests pass. One honest limitation sentence: text-literal embeddings cast at query time;
> native vector column planned.

## E6. Evidence figure
- Insert a screenshot of `docs/test-evidence-full-suite.log` (the summary block reading
  `Tests run: 34, Failures: 0, Skipped: 0`) into Chapter 4 as test evidence.

---

# PART F — CHAPTERS 5 & 6 → single final chapter + status table

## F1. Collapse the two mid-term chapters
- Move all testing content into Chapter 4 (per Part E), then merge what remains into one
  **CHAPTER 5: CONCLUSIONS AND FUTURE ENHANCEMENTS** — §5.1 Conclusion, §5.2 Limitations,
  §5.3 Future Enhancements. Update `1.6 Report Organization` to describe 5 chapters.

## F2. Table 5.1 status rows — exact replacements

**Row "Quick Answers and RAG prototype"**
- **BEFORE:** `Partial — Predefined Quick Answers are active. Chunking and embedding services are present, but pgvector similarity retrieval, an exposed free-form answer endpoint and automatic RAG indexing are not active.`
- **AFTER:**
> Completed
> Predefined Quick Answers remain available, and free-form question answering is active
> through POST /api/rag/ask and the AI Chat page. The pipeline performs pgvector cosine
> retrieval over automatically indexed chunks, hybrid reranking, and citation-grounded
> generation with page-level sources.

**Row "Automated backend tests"**
- **BEFORE:** `Partial — Four maintained test classes contain ten test methods…`
- **AFTER:**
> Completed for current scope
> Eight test classes with 34 test methods cover authentication, PDF deletion and
> ownership, chunk termination, evidence-based weakness classification, Bayesian knowledge
> tracing, adaptive priority computation, hybrid reranking, and live-database RAG
> retrieval (ownership isolation, query-dependent results, and reranking reordering).

**Row "Bayesian mastery and modified SM-2"** — replace the comment with:
> Mastery, SM-2 state, and review logs are stored and updated on every attempt. BKT
> mastery now directly drives the adaptive priority function, and forgetting-curve risk
> from the last-review date modulates daily topic ordering.

**Row "Reports"** — verify `/api/reports/study-report` works before submission; write this
row to match reality (do not copy text blindly).

---

# PART G — BACK MATTER

## G1. List of Figures vs body mismatch
- **PROBLEM:** LoF promises `Figure 3.2 Class Diagram … 14` but no matching caption exists
  in the body.
- **FIX:** you are adding the class diagram anyway (D1, §3.1.3) — insert it with caption
  `Figure 3.2: Class Diagram …` so the LoF entry becomes true; then
  References → Update Table on TOC, List of Figures, List of Tables.

## G2. References — add what Chapter 2/3 now cite
- ADD: Corbett, A. T., & Anderson, J. R. (1995). *Knowledge tracing: Modeling the
  acquisition of procedural knowledge.* User Modeling and User-Adapted Interaction, 4(4).
- ADD: Ebbinghaus, H. (1885/1913). *Memory: A contribution to experimental psychology.*
  (cite where the forgetting curve is introduced in §3.3.2 / Ch2 closing).
- CHECK: renumber if your list is numbered by order of appearance; ensure every in-text
  `[n]` still resolves after deletions.

## G3. List of Abbreviations — add these rows
| Abbrev | Full form |
|---|---|
| BKT | Bayesian Knowledge Tracing |
| RAG | Retrieval-Augmented Generation |
| HNSW | Hierarchical Navigable Small World |
| SM-2 | SuperMemo-2 spaced-repetition algorithm |
| MRR | Mean Reciprocal Rank |
| ANN | Approximate Nearest Neighbour |

## G4. Appendix (recommended) — API contract sample
> POST /api/rag/ask  ·  Request: `{ "question": "Explain the OSI model", "pdfId": 1 }`
> Response: `{ "answer": "…[Source 1]…", "sources": [ { "pdfFileName": "Lecture_notes.pdf",
> "pageNumber": 14, "similarity": 0.91, "rerankScore": 0.88, "rank": 1 }, … ] }`

---

# PART H — GLOBAL SWEEPS (Ctrl+H, do near the end)

| Search for | Replace with | Notes |
|---|---|---|
| `PRIORITY ALGORITHM` / `Priority Algorithm` | (title per A1–A4) | must reach **0 hits** |
| `Kathamndu` | `Kathmandu` | 3 signature blocks |
| `Humanities and Social Science` (no trailing s) | `Humanities and Social Sciences` | unify with cover |
| `Bachelor in Computer Application` vs `Bachelor of …` | pick the letterhead's official form | make cover + approval identical |
| `Keywords: … Priority Recommendation …` | `Keywords: Bayesian Knowledge Tracing, Retrieval-Augmented Generation, pgvector, Forgetting Curve, Adaptive Learning.` | abstract page |
| `not active` · `partially implemented` · `sequential chunk retrieval` | update or delete | all such claims are now false — search each phrase |
| `predefined Quick Answers` (as the ONLY answering mode) | reword per B4 | QA still exists; it is no longer the only mode |

After sweeping: References → **Update Table** on TOC, List of Figures, List of Tables;
re-read §1.6 Report Organization so the chapter descriptions match the new 5-chapter
structure; check every remaining cross-reference ("see Section 4.4" etc.).

---

# PART I — FINAL PRE-SUBMISSION CHECKLIST

- [ ] Title replaced on all 4 pages (A1–A4); Ctrl+F "Priority Algorithm" = 0 hits
- [ ] Abstract replaced (A5) + new keywords line
- [ ] Objectives, Scope, Limitations updated (B3–B5)
- [ ] Research gap merged into end of §2.2 (C1)
- [ ] Headings moved per D1; three diagram subsections added with figures
- [ ] §3.3 Algorithm Details present with all equations (D2)
- [ ] Chapter 4: entity row, constraints paragraph, Tools Used (E1–E3)
- [ ] Tables 4.1/4.2 inserted; ST rows filled by a real click-through (E4)
- [ ] Test-evidence screenshot inserted (E6) — log shows 34/34 passing
- [ ] Chapters collapsed to five; Table 5.1 rows updated (F1–F2)
- [ ] Figure 3.2 class diagram inserted → LoF entry true (G1)
- [ ] Corbett & Anderson + Ebbinghaus references added and numbering intact (G2)
- [ ] Abbreviations rows added (G3); API appendix optional (G4)
- [ ] All Part H sweeps done; Kathmandu/faculty/degree consistent
- [ ] TOC / LoF / LoT refreshed; page numbers correct; spell-check passed
- [ ] Export final PDF and flip through once end-to-end

**Done = submission-ready.** Every technical claim then matches running, tested code.

