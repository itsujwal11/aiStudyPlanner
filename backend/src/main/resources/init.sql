-- Create sequences for PostgreSQL
CREATE SEQUENCE user_id_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE pdf_id_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE topic_id_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE quiz_id_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE study_progress_id_seq START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE quiz_attempt_id_seq START WITH 1 INCREMENT BY 1;

-- Seed admin user (password: admin123)
INSERT INTO users (id, email, name, password, role, created_at, updated_at)
SELECT nextval('user_id_seq'), 'admin@aasa.com', 'Admin', '$2a$10$ZhLoXudsw8o58IS.A2Y/8.BzJlKvZV66x.ks56mpOBKccd4tYZvce', 'ADMIN', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = 'admin@aasa.com');
