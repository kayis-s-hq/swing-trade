ALTER TABLE sentiment_results
    ADD COLUMN IF NOT EXISTS audit_request_id VARCHAR(36);

CREATE INDEX IF NOT EXISTS idx_sentiment_results_audit_request_id
    ON sentiment_results(audit_request_id);
