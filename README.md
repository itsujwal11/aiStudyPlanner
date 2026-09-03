# AASA - Adaptive AI Study Architect

> Turn one lecture PDF into an adaptive study programme. Upload PDF + exam date → get topics, MCQ quizzes, grounded Q&A, and a personalised plan that re-ranks itself after every answer.

[![Java](https://img.shields.io/badge/Java-17-blue.svg)](https://openjdk.org/projects/jdk/17/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-18-61dafb.svg)](https://react.dev/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-17-4169e1.svg)](https://www.postgresql.org/)
[![pgvector](https://img.shields.io/badge/pgvector-enabled-orange.svg)](https://github.com/pgvector/pgvector)
[![Python](https://img.shields.io/badge/Python-3.11-3776ab.svg)](https://www.python.org/)
[![FastAPI](https://img.shields.io/badge/FastAPI-0.109-009688.svg)](https://fastapi.tiangolo.com/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

---

## System Workflow

```
┌─────────────┐     HTTPS/JSON/JWT     ┌──────────────────┐
│   Browser   │ ──────────────────────▶ │  Spring Boot API │
│  React 18   │                         │   Java 17, 9096  │
│   + Vite    │ ◀────────────────────── │  (35 services)   │
└─────────────┘                         └────────┬─────────┘
                                                  │
                    ┌─────────────────────────────┼─────────────────────────────┐
                    ▼                             ▼                             ▼
            ┌───────────────┐            ┌─────────────────┐            ┌──────────────┐
            │  PostgreSQL 17 │            │  ml-service     │            │  Google      │
            │  + pgvector    │            │  FastAPI :8000  │            │  Gemini API  │
            │  (relational   │            │  Random Forest  │            │  (embeddings,│
            │   + vectors)   │            │  (283k rows)    │            │   generation)│
            └───────────────┘            └─────────────────┘            └──────────────┘
```

### Request Flow

1. **Upload** → `POST /api/pdfs` → immediate `PENDING` response → background processing
2. **Processing** → text extraction → chunking (~512 tokens) → Gemini embeddings (768-dim) → pgvector storage
3. **Polling** → Frontend polls `GET /api/pdfs` every 10s until `COMPLETED`
4. **Study** → Diagnostic quiz → mastery update → adaptive planner re-ranks
5. **RAG** → Question → query embedding → cosine search top-20 → hybrid rerank → top-5 → Gemini answer with citations

---

## Code Organization — File-to-Function Map

### Authentication & Security

| File | Purpose |
|---|---|
| `backend/src/main/java/com/aasa/security/SecurityConfig.java` | CORS, security filter chain, password encoder |
| `backend/src/main/java/com/aasa/security/JwtTokenProvider.java` | JWT generation & validation |
| `backend/src/main/java/com/aasa/security/JwtAuthenticationFilter.java` | JWT extraction from requests |
| `backend/src/main/java/com/aasa/controller/AuthController.java` | Register, login, OTP, Google Sign-In |
| `backend/src/main/java/com/aasa/service/UserService.java` | User management |
| `frontend/src/context/AuthContext.jsx` | Auth state management |
| `frontend/src/pages/Login.jsx` | Login page |
| `frontend/src/pages/Register.jsx` | Registration page |

### PDF Upload & Processing

| File | Purpose |
|---|---|
| `backend/src/main/java/com/aasa/controller/PdfController.java` | PDF CRUD endpoints |
| `backend/src/main/java/com/aasa/service/PdfManagementService.java` | PDF lifecycle management |
| `backend/src/main/java/com/aasa/service/PdfProcessingService.java` | Async processing orchestration |
| `backend/src/main/java/com/aasa/service/PdfExtractionService.java` | Text extraction from PDF |
| `backend/src/main/java/com/aasa/service/DocumentChunkingService.java` | Semantic chunking |
| `backend/src/main/java/com/aasa/entity/PdfDocument.java` | PDF entity |
| `backend/src/main/java/com/aasa/entity/DocumentChunk.java` | Chunk entity |
| `frontend/src/pages/UploadPdf.jsx` | Upload page |
| `frontend/src/pages/PdfDetail.jsx` | PDF detail & topics page |

### RAG / Q&A / Quiz

| File | Purpose |
|---|---|
| `backend/src/main/java/com/aasa/controller/RagController.java` | RAG query endpoints |
| `backend/src/main/java/com/aasa/service/RagAugmentedService.java` | RAG orchestration |
| `backend/src/main/java/com/aasa/service/VectorSearchService.java` | pgvector similarity search |
| `backend/src/main/java/com/aasa/service/EmbeddingService.java` | Gemini embedding generation |
| `backend/src/main/java/com/aasa/service/RerankingService.java` | Hybrid reranking (vector + keyword + title) |
| `backend/src/main/java/com/aasa/controller/QuizController.java` | Quiz endpoints |
| `backend/src/main/java/com/aasa/service/QuizEngineService.java` | Quiz generation & management |
| `backend/src/main/java/com/aasa/controller/TopicController.java` | Topic endpoints |
| `frontend/src/pages/AiChat.jsx` | RAG chat interface |
| `frontend/src/pages/QuickAnswers.jsx` | Quick answers page |
| `frontend/src/pages/Study.jsx` | Study & quiz page |

### Knowledge Tracing & Mastery

| File | Purpose |
|---|---|
| `backend/src/main/java/com/aasa/service/BayesianKnowledgeTracingService.java` | BKT mastery update |
| `backend/src/main/java/com/aasa/service/MasteryService.java` | Mastery tracking |
| `backend/src/main/java/com/aasa/service/WeaknessEngineService.java` | Evidence-based weakness scoring |
| `backend/src/main/java/com/aasa/service/AdaptivePriorityService.java` | Priority calculation |
| `backend/src/main/java/com/aasa/service/PlannerService.java` | Study plan generation |
| `backend/src/main/java/com/aasa/controller/PlannerController.java` | Planner endpoints |

### ML Weakness Model

| File | Purpose |
|---|---|
| `backend/src/main/java/com/aasa/service/MlWeaknessClient.java` | ML service HTTP client |
| `backend/src/main/java/com/aasa/service/LearnerFeatureService.java` | Feature extraction for ML |
| `ml/train_model.py` | Trains Random Forest on ASSISTments dataset |
| `ml/serve.py` | FastAPI inference server |
| `ml/requirements.txt` | Training dependencies |
| `ml/requirements-serve.txt` | Serving dependencies |
| `ml/models/weakness_model.joblib` | Trained model (gitignored) |

### Frontend Polling & Notifications

| File | Purpose |
|---|---|
| `frontend/src/hooks/useBackgroundProcessingNotifications.js` | Polling hook (10s interval) |
| `frontend/src/hooks/backgroundProcessingNotifications.js` | Toast & notification logic |

### Database

| File | Purpose |
|---|---|
| `docs/schema.sql` | Bootstrap DDL (sequences, tables, pgvector) |
| `backend/src/main/java/com/aasa/entity/` | 10 JPA entities |
| `backend/src/main/java/com/aasa/repository/` | 10 Spring Data JPA repos |

---

## Repository Layout

```
aiStudyPlanner/
├── backend/                          # Spring Boot API
│   └── src/main/java/com/aasa/
│       ├── controller/              # 13 REST controllers
│       ├── service/                 # 36 business-logic services
│       ├── repository/              # 10 Spring Data JPA repos
│       ├── entity/                  # 10 JPA entities
│       ├── dto/                     # 37 request/response DTOs
│       ├── security/                # JWT provider + filter
│       └── config/                  # Async, CORS, exception handlers
├── frontend/                         # React + Vite SPA
│   └── src/
│       ├── pages/                   # 17 pages
│       ├── components/              # 7 reusable components
│       ├── hooks/                   # Custom hooks
│       ├── context/                 # AuthContext
│       └── api.js                   # Axios instance + interceptors
├── ml/                               # ML training & inference
│   ├── train_model.py               # Random Forest training
│   ├── serve.py                     # FastAPI inference server
│   ├── data/                        # Training data (gitignored)
│   ├── models/                      # Trained model (gitignored)
│   └── reports/                     # Metrics & confusion matrix
├── docs/
│   ├── ARCHITECTURE.md              # Full architecture & algorithms
│   ├── WORKFLOW.md                  # Step-by-step workflows (A-E)
│   └── schema.sql                   # Database DDL
├── docker-compose.yml               # Postgres + ml-service + backend + frontend
└── .env.example                     # Environment variable template
```

---

## Quick Start

### Prerequisites
- JDK 17+
- Maven 3.9+
- Node 18+
- Docker (for PostgreSQL + pgvector)

### 1. Database
```bash
# Option A: Docker (recommended)
docker compose up -d postgres

# Option B: Local PostgreSQL with pgvector
# CREATE EXTENSION IF NOT EXISTS vector;
# Run docs/schema.sql manually
```

### 2. Backend
```bash
cp .env.example backend/.env
# Edit backend/.env — set GEMINI_API_KEY, JWT_SECRET, DB credentials
cd backend
mvn spring-boot:run
# Runs on http://localhost:9096
# Health check: curl http://localhost:9096/api/health
```

### 3. Frontend
```bash
cd frontend
npm install
npm run dev
# Runs on http://localhost:3000
```

### 4. ML Service (Optional)
```bash
cd ml
python -m venv .venv
# Windows: .venv\Scripts\activate
# Linux/macOS: source .venv/bin/activate
pip install -r requirements.txt
python train_model.py                  # Generates models/weakness_model.joblib
uvicorn serve:app --port 8000          # Starts inference on :8000
```

### 5. Full Stack (Docker)
```bash
# Set GEMINI_API_KEY and JWT_SECRET in environment
docker compose up --build
# Frontend: http://localhost:3000
# Backend:  http://localhost:9096
# ML:       http://localhost:8000
# Postgres: localhost:5432
```

---

## Key Configuration Files

| File | Purpose |
|---|---|
| `.env.example` | Template for all environment variables |
| `backend/.env` | Backend secrets (DB, JWT, Gemini, ML) |
| `frontend/.env` | Frontend API URL (`VITE_API_URL`) |
| `backend/src/main/resources/application.properties` | CORS, upload limits, JWT expiry, logging |
| `docker-compose.yml` | Service definitions & networking |

### Required Environment Variables

| Key | Description |
|---|---|
| `JWT_SECRET` | HS512 signing key (min 32 chars) |
| `GEMINI_API_KEY` | Google Gemini API key |
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` | PostgreSQL credentials |
| `ML_WEAKNESS_URL` | ML service URL (default: `http://localhost:8000`) |
| `VITE_API_URL` | Frontend API base (default: `http://localhost:9096/api`) |

---

## Ports

| Service | Port |
|---|---|
| Frontend | 3000 |
| Backend | 9096 |
| ML Service | 8000 |
| PostgreSQL | 5432 |

---

## Testing

```bash
# Backend unit tests (76 tests, 14 suites)
cd backend && mvn test

# Integration tests (opt-in, require running services)
# RAG retrieval (needs Postgres + pgvector):
RAG_INTEGRATION_TEST=true mvn test -Dtest=RagRetrievalIntegrationTest

# ML weakness client (needs ml-service on :8000):
ML_INTEGRATION_TEST=true mvn test -Dtest=MlWeaknessClientIntegrationTest
```

---

## Documentation

| Document | Description |
|---|---|
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | Complete architecture, algorithms, data model, configuration reference |
| [`docs/WORKFLOW.md`](docs/WORKFLOW.md) | Step-by-step workflows (A–E) with Mermaid diagrams |
| [`docs/schema.sql`](docs/schema.sql) | Bootstrap DDL — sequences, tables, pgvector |

---

## ML Model

| Metric | Value |
|---|---|
| Algorithm | Random Forest (scikit-learn) |
| Training Data | ASSISTments Skill-Builder (283k rows, 4,163 students) |
| Features | previous_attempts, previous_accuracy, average_response_time, recent_accuracy, opportunity |
| Validation | Student-wise split |
| Test F1 | 0.734 |
| Test ROC-AUC | 0.705 |
| Serving | FastAPI + uvicorn on port 8000 |

---

## Contact

**Author:** Ujwol Shrestha 

**Project Link:** https://github.com/itsujwal11/aiStudyPlanner
