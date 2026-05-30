-- Add weakness_score column to topics table if it doesn't exist
ALTER TABLE topics ADD COLUMN weakness_score NUMERIC(10,4);

-- Commit the changes
COMMIT;
