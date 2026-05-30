-- Fix column sizes in quiz_attempts table
ALTER TABLE quiz_attempts ALTER COLUMN selected_answer TYPE VARCHAR(500);
COMMIT;
