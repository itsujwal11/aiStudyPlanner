-- Enable pgvector extension (required for semantic vector search; install the pgvector package once)
CREATE EXTENSION IF NOT EXISTS vector;

-- Users table
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- PDF Documents table
CREATE TABLE pdf_documents (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    file_name VARCHAR(255) NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    upload_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    exam_date DATE NOT NULL,
    extracted_text TEXT,
    is_analyzed BOOLEAN DEFAULT FALSE
);

-- Topics table
CREATE TABLE topics (
    id BIGSERIAL PRIMARY KEY,
    pdf_id BIGINT NOT NULL REFERENCES pdf_documents(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    concept_density DOUBLE PRECISION,
    keyword_difficulty DOUBLE PRECISION,
    formula_count INTEGER,
    content_length INTEGER,
    complexity_score DOUBLE PRECISION,
    importance_score DOUBLE PRECISION,
    priority_score DOUBLE PRECISION
);

-- Quizzes table
CREATE TABLE quizzes (
    id BIGSERIAL PRIMARY KEY,
    topic_id BIGINT NOT NULL REFERENCES topics(id) ON DELETE CASCADE,
    question TEXT NOT NULL,
    option_a VARCHAR(500) NOT NULL,
    option_b VARCHAR(500) NOT NULL,
    option_c VARCHAR(500) NOT NULL,
    option_d VARCHAR(500) NOT NULL,
    correct_answer VARCHAR(500) NOT NULL,
    difficulty VARCHAR(50) NOT NULL,
    explanation TEXT
);

-- Quiz Attempts table
CREATE TABLE quiz_attempts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    quiz_id BIGINT NOT NULL REFERENCES quizzes(id) ON DELETE CASCADE,
    selected_answer VARCHAR(500) NOT NULL,
    is_correct BOOLEAN NOT NULL,
    marks_obtained DOUBLE PRECISION,
    attempt_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    time_taken_seconds BIGINT
);

-- Study Progress table
CREATE TABLE study_progress (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    topic_id BIGINT NOT NULL REFERENCES topics(id) ON DELETE CASCADE,
    weakness_level VARCHAR(50) CHECK (weakness_level IN ('LOW', 'MEDIUM', 'HIGH', 'NOT_ATTEMPTED', 'INSUFFICIENT_DATA')),
    completion_percentage DOUBLE PRECISION DEFAULT 0.0,
    best_score DOUBLE PRECISION DEFAULT 0.0,
    total_attempts INTEGER DEFAULT 0,
    correct_attempts INTEGER DEFAULT 0,
    mastery_level DOUBLE PRECISION DEFAULT 0.0,
    alpha DOUBLE PRECISION DEFAULT 1.0,
    beta DOUBLE PRECISION DEFAULT 1.0,
    last_study_date DATE,
    next_review_date DATE,
    interval_days INTEGER DEFAULT 0,
    ease_factor DOUBLE PRECISION DEFAULT 2.5,
    UNIQUE(user_id, topic_id)
);

-- Document Chunks table (RAG index; 'embedding' stores pgvector text literals "[v1,v2,...]"
-- which are cast to the vector type at query time via embedding::vector)
CREATE TABLE document_chunks (
    id BIGSERIAL PRIMARY KEY,
    pdf_id BIGINT NOT NULL REFERENCES pdf_documents(id) ON DELETE CASCADE,
    chunk_index INTEGER NOT NULL,
    chunk_text TEXT NOT NULL,
    embedding TEXT,
    token_count INTEGER,
    page_number INTEGER,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for better query performance
CREATE INDEX idx_pdf_user ON pdf_documents(user_id);
CREATE INDEX idx_topic_pdf ON topics(pdf_id);
CREATE INDEX idx_quiz_topic ON quizzes(topic_id);
CREATE INDEX idx_attempt_user ON quiz_attempts(user_id);
CREATE INDEX idx_attempt_quiz ON quiz_attempts(quiz_id);
CREATE INDEX idx_progress_user ON study_progress(user_id);
CREATE INDEX idx_progress_topic ON study_progress(topic_id);
CREATE INDEX idx_user_email ON users(email);
CREATE INDEX idx_chunk_pdf ON document_chunks(pdf_id);
-- Optional ANN speed-up once the column is migrated to a native vector type:
-- ALTER TABLE document_chunks ALTER COLUMN embedding TYPE vector(768) USING embedding::vector;
-- CREATE INDEX idx_chunk_embedding ON document_chunks USING hnsw (embedding vector_cosine_ops);
CREATE UNIQUE INDEX uq_document_chunks_pdf_chunk_index
    ON document_chunks(pdf_id, chunk_index);