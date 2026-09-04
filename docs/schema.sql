-- ============================================================================
-- AASA — database bootstrap DDL
--
-- Applied once by docker-compose via /docker-entrypoint-initdb.d/schema.sql.
-- Afterwards Hibernate `ddl-auto=update` evolves the schema.
--
-- This file mirrors the JPA entities in
-- backend/src/main/java/com/aasa/entity/. Entities use
-- GenerationType.SEQUENCE with explicit sequence names (allocationSize = 1),
-- so every table declares its sequence rather than using BIGSERIAL — a
-- BIGSERIAL column would create `<table>_id_seq`, which is NOT the name the
-- entities ask for, leaving two independent counters on one table.
-- ============================================================================

-- Enable pgvector (required for semantic vector search; the pgvector/pgvector
-- image ships the extension, any other PostgreSQL needs the package installed).
CREATE EXTENSION IF NOT EXISTS vector;

-- ---------------------------------------------------------------- sequences
CREATE SEQUENCE user_id_seq            START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE otp_challenge_id_seq   START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE pdf_id_seq             START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE topic_id_seq           START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE quiz_id_seq            START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE quiz_attempt_id_seq    START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE study_progress_id_seq  START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE chunk_id_seq           START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE review_log_id_seq      START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE planner_task_completion_id_seq START WITH 1 INCREMENT BY 1;

-- Users
CREATE TABLE users (
    id BIGINT PRIMARY KEY DEFAULT nextval('user_id_seq'),
    email VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(20) DEFAULT 'USER',
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    google_subject VARCHAR(255) UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

-- One-time-password challenges (e-mail verification / password reset)
CREATE TABLE otp_challenges (
    id BIGINT PRIMARY KEY DEFAULT nextval('otp_challenge_id_seq'),
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    purpose VARCHAR(32) NOT NULL
        CHECK (purpose IN ('EMAIL_VERIFICATION', 'LOGIN', 'PASSWORD_RESET')),
    code_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    consumed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- PDF documents
-- processing_status default mirrors the entity's columnDefinition: rows that
-- predate background processing read as COMPLETED. The application always sets
-- the value explicitly (new uploads start PENDING).
CREATE TABLE pdf_documents (
    id BIGINT PRIMARY KEY DEFAULT nextval('pdf_id_seq'),
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    file_name VARCHAR(255) NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    upload_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    exam_date DATE NOT NULL,
    extracted_text TEXT,
    is_analyzed BOOLEAN DEFAULT FALSE,
    processing_status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED'
        CHECK (processing_status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')),
    processing_error VARCHAR(1000)
);

-- Topics
CREATE TABLE topics (
    id BIGINT PRIMARY KEY DEFAULT nextval('topic_id_seq'),
    pdf_id BIGINT NOT NULL REFERENCES pdf_documents(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    concept_density DOUBLE PRECISION,
    keyword_difficulty DOUBLE PRECISION,
    formula_count INTEGER,
    content_length INTEGER,
    complexity_score DOUBLE PRECISION,
    importance_score DOUBLE PRECISION,
    priority_score DOUBLE PRECISION,
    weakness_score DOUBLE PRECISION
);

-- Quizzes
CREATE TABLE quizzes (
    id BIGINT PRIMARY KEY DEFAULT nextval('quiz_id_seq'),
    topic_id BIGINT NOT NULL REFERENCES topics(id) ON DELETE CASCADE,
    question TEXT NOT NULL,
    option_a VARCHAR(255) NOT NULL,
    option_b VARCHAR(255) NOT NULL,
    option_c VARCHAR(255) NOT NULL,
    option_d VARCHAR(255) NOT NULL,
    correct_answer VARCHAR(500) NOT NULL,
    difficulty VARCHAR(50) NOT NULL CHECK (difficulty IN ('EASY', 'MEDIUM', 'HARD')),
    explanation TEXT
);

-- Quiz attempts
CREATE TABLE quiz_attempts (
    id BIGINT PRIMARY KEY DEFAULT nextval('quiz_attempt_id_seq'),
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    quiz_id BIGINT NOT NULL REFERENCES quizzes(id) ON DELETE CASCADE,
    selected_answer VARCHAR(500) NOT NULL,
    is_correct BOOLEAN NOT NULL,
    marks_obtained DOUBLE PRECISION,
    attempt_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    time_taken_seconds BIGINT
);

-- Study progress — one row per (user, topic); carries the learner model:
-- Beta-Binomial counts (alpha_param/beta_param), BKT-blended mastery_level,
-- and the SM-2 scheduling state.
CREATE TABLE study_progress (
    id BIGINT PRIMARY KEY DEFAULT nextval('study_progress_id_seq'),
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    topic_id BIGINT NOT NULL REFERENCES topics(id) ON DELETE CASCADE,
    weakness_level VARCHAR(50)
        CHECK (weakness_level IN ('LOW', 'MEDIUM', 'HIGH', 'INSUFFICIENT_DATA', 'NOT_ATTEMPTED')),
    completion_percentage DOUBLE PRECISION DEFAULT 0.0,
    best_score DOUBLE PRECISION DEFAULT 0.0,
    total_attempts INTEGER DEFAULT 0,
    correct_attempts INTEGER DEFAULT 0,
    mastery_level DOUBLE PRECISION DEFAULT 0.0,
    alpha_param DOUBLE PRECISION DEFAULT 2.0,
    beta_param DOUBLE PRECISION DEFAULT 8.0,
    sm2_interval INTEGER DEFAULT 0,
    sm2_efactor DOUBLE PRECISION DEFAULT 2.5,
    sm2_repetitions INTEGER DEFAULT 0,
    last_study_date DATE,
    next_review_date DATE,
    UNIQUE (user_id, topic_id)
);

-- Review log — one row per graded attempt, for mastery-history analytics
CREATE TABLE review_log (
    id BIGINT PRIMARY KEY DEFAULT nextval('review_log_id_seq'),
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    topic_id BIGINT NOT NULL REFERENCES topics(id) ON DELETE CASCADE,
    review_type VARCHAR(255),
    rating INTEGER,
    response_time_ms INTEGER,
    scheduled_days INTEGER,
    actual_interval INTEGER,
    mastery_before DOUBLE PRECISION,
    mastery_after DOUBLE PRECISION,
    created_at TIMESTAMP
);

-- Document chunks (RAG index; 'embedding' stores pgvector text literals
-- "[v1,v2,...]" which are cast to the vector type at query time via
-- CAST(embedding AS vector))
CREATE TABLE document_chunks (
    id BIGINT PRIMARY KEY DEFAULT nextval('chunk_id_seq'),
    pdf_id BIGINT NOT NULL REFERENCES pdf_documents(id) ON DELETE CASCADE,
    chunk_index INTEGER NOT NULL,
    chunk_text TEXT NOT NULL,
    embedding TEXT,
    token_count INTEGER,
    page_number INTEGER,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Planner task ticks — one row per task the student marks done, per calendar
-- day. Keyed by topic + activity + session rather than list position, because
-- the plan is re-ranked after every quiz answer.
CREATE TABLE planner_task_completions (
    id BIGINT PRIMARY KEY DEFAULT nextval('planner_task_completion_id_seq'),
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    topic_id BIGINT NOT NULL,
    activity_type VARCHAR(20) NOT NULL,
    completion_date DATE NOT NULL,
    session_index INTEGER NOT NULL DEFAULT 0,
    completed BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT uk_planner_task_completion
        UNIQUE (user_id, topic_id, activity_type, completion_date, session_index)
);

-- Indexes
CREATE INDEX idx_planner_completion_user_date ON planner_task_completions(user_id, completion_date);
CREATE INDEX idx_pdf_user ON pdf_documents(user_id);
CREATE INDEX idx_topic_pdf ON topics(pdf_id);
CREATE INDEX idx_quiz_topic ON quizzes(topic_id);
CREATE INDEX idx_attempt_user ON quiz_attempts(user_id);
CREATE INDEX idx_attempt_quiz ON quiz_attempts(quiz_id);
CREATE INDEX idx_progress_user ON study_progress(user_id);
CREATE INDEX idx_progress_topic ON study_progress(topic_id);
CREATE INDEX idx_user_email ON users(email);
CREATE INDEX idx_chunk_pdf ON document_chunks(pdf_id);
CREATE INDEX idx_review_log_user_topic ON review_log(user_id, topic_id);
CREATE INDEX idx_otp_user_purpose_created ON otp_challenges(user_id, purpose, created_at);
CREATE UNIQUE INDEX uq_document_chunks_pdf_chunk_index
    ON document_chunks(pdf_id, chunk_index);

-- Optional ANN speed-up once the column is migrated to a native vector type:
-- ALTER TABLE document_chunks ALTER COLUMN embedding TYPE vector(768) USING embedding::vector;
-- CREATE INDEX idx_chunk_embedding ON document_chunks USING hnsw (embedding vector_cosine_ops);
