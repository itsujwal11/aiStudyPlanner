-- Fix the correct_answer column size in quizzes table
ALTER TABLE quizzes ALTER COLUMN correct_answer TYPE VARCHAR(500);
COMMIT;
