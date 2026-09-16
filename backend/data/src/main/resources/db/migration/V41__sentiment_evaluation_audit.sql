CREATE TABLE sentiment_evaluation_audits (
    id BIGSERIAL PRIMARY KEY,
    trigger VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    processed_count INTEGER NOT NULL,
    started_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP NOT NULL,
    error_message TEXT
);
