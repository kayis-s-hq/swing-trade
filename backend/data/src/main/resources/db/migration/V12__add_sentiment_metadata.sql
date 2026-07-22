-- V12__add_sentiment_metadata.sql
-- Add prompt_hash and model_version to sentiment_results for A/B testing

ALTER TABLE sentiment_results ADD COLUMN IF NOT EXISTS prompt_hash VARCHAR(64);
ALTER TABLE sentiment_results ADD COLUMN IF NOT EXISTS model_version VARCHAR(50);

CREATE INDEX IF NOT EXISTS idx_sentiment_results_model ON sentiment_results (model_version);