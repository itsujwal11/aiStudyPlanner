-- Users table
CREATE TABLE users (
    id NUMBER PRIMARY KEY,
    email VARCHAR2(255) NOT NULL UNIQUE,
    name VARCHAR2(255) NOT NULL,
    password VARCHAR2(255) NOT NULL,
    created_at TIMESTAMP DEFAULT SYSDATE NOT NULL,
    updated_at TIMESTAMP DEFAULT SYSDATE NOT NULL
);

-- Create sequence for users
CREATE SEQUENCE users_seq START WITH 1 INCREMENT BY 1;

-- PDF Documents table
CREATE TABLE pdf_documents (
    id NUMBER PRIMARY KEY,
    user_id NUMBER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    file_name VARCHAR2(255) NOT NULL,
    file_path VARCHAR2(500) NOT NULL,
    upload_date TIMESTAMP DEFAULT SYSDATE NOT NULL,
    exam_date DATE NOT NULL,
    extracted_text CLOB,
    is_analyzed NUMBER(1) DEFAULT 0
);

CREATE SEQUENCE pdf_documents_seq START WITH 1 INCREMENT BY 1;

-- Topics table
CREATE TABLE topics (
    id NUMBER PRIMARY KEY,
    pdf_id NUMBER NOT NULL REFERENCES pdf_documents(id) ON DELETE CASCADE,
    title VARCHAR2(255) NOT NULL,
    description CLOB,
    concept_density NUMBER(10,2),
    keyword_difficulty NUMBER(10,2),
    formula_count NUMBER,
    content_length NUMBER,
    complexity_score NUMBER(10,2),
    importance_score NUMBER(10,2),
    priority_score NUMBER(10,2)
);

CREATE SEQUENCE topics_seq START WITH 1 INCREMENT BY 1;

-- Quizzes table
CREATE TABLE quizzes (
    id NUMBER PRIMARY KEY,
    topic_id NUMBER NOT NULL REFERENCES topics(id) ON DELETE CASCADE,
    question CLOB NOT NULL,
    option_a VARCHAR2(500) NOT NULL,
    option_b VARCHAR2(500) NOT NULL,
    option_c VARCHAR2(500) NOT NULL,
    option_d VARCHAR2(500) NOT NULL,
    correct_answer VARCHAR2(500) NOT NULL,
    difficulty VARCHAR2(50) NOT NULL,
    explanation CLOB
);

CREATE SEQUENCE quizzes_seq START WITH 1 INCREMENT BY 1;

-- Quiz Attempts table
CREATE TABLE quiz_attempts (
    id NUMBER PRIMARY KEY,
    user_id NUMBER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    quiz_id NUMBER NOT NULL REFERENCES quizzes(id) ON DELETE CASCADE,
    selected_answer VARCHAR2(500) NOT NULL,
    is_correct NUMBER(1) NOT NULL,
    marks_obtained NUMBER(10,2),
    attempt_time TIMESTAMP DEFAULT SYSDATE NOT NULL,
    time_taken_seconds NUMBER
);

CREATE SEQUENCE quiz_attempts_seq START WITH 1 INCREMENT BY 1;

-- Study Progress table
CREATE TABLE study_progress (
    id NUMBER PRIMARY KEY,
    user_id NUMBER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    topic_id NUMBER NOT NULL REFERENCES topics(id) ON DELETE CASCADE,
    weakness_level VARCHAR2(50),
    completion_percentage NUMBER(10,2) DEFAULT 0,
    best_score NUMBER(10,2) DEFAULT 0,
    total_attempts NUMBER DEFAULT 0,
    correct_attempts NUMBER DEFAULT 0,
    UNIQUE(user_id, topic_id)
);

CREATE SEQUENCE study_progress_seq START WITH 1 INCREMENT BY 1;

-- Create indexes for performance
CREATE INDEX idx_pdf_user ON pdf_documents(user_id);
CREATE INDEX idx_topic_pdf ON topics(pdf_id);
CREATE INDEX idx_quiz_topic ON quizzes(topic_id);
CREATE INDEX idx_attempt_user ON quiz_attempts(user_id);
CREATE INDEX idx_attempt_quiz ON quiz_attempts(quiz_id);
CREATE INDEX idx_progress_user ON study_progress(user_id);
CREATE INDEX idx_progress_topic ON study_progress(topic_id);
CREATE INDEX idx_user_email ON users(email);

COMMIT;
