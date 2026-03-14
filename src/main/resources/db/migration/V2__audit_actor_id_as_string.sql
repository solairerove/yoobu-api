ALTER TABLE audit_log
    ALTER COLUMN actor_id TYPE VARCHAR(255)
    USING actor_id::VARCHAR;
