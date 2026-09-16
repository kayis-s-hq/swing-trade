-- Preserve whether a sentiment result came from the LLM, keyword fallback, or default path.
ALTER TABLE sentiment_results
    ADD COLUMN IF NOT EXISTS source VARCHAR(16) NOT NULL DEFAULT 'DEFAULT';

CREATE INDEX IF NOT EXISTS idx_sentiment_results_source ON sentiment_results (source);
