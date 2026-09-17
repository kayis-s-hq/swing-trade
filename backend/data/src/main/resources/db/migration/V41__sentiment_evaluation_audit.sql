-- V35 creates this table in a clean database. Keep this later migration
-- idempotent because V41 was introduced by a branch that duplicated V35.
CREATE TABLE IF NOT EXISTS sentiment_evaluation_audits (
    id BIGSERIAL PRIMARY KEY,
    trigger VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    processed_count INTEGER NOT NULL,
    started_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP NOT NULL,
    error_message TEXT
);
