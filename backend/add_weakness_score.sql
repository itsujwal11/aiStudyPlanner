-- Add weakness_score column to topics table if it doesn't exist
ALTER TABLE topics ADD (weakness_score NUMBER(10,4));

-- Commit the changes
COMMIT;
