-- Clear all data while maintaining referential integrity
TRUNCATE TABLE quiz_attempts;
TRUNCATE TABLE quizzes;
TRUNCATE TABLE study_progress;
TRUNCATE TABLE topics;
TRUNCATE TABLE pdf_documents;
TRUNCATE TABLE users;
COMMIT;
EXIT;
