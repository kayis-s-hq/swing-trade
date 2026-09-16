-- Widen model_version from VARCHAR(50) to VARCHAR(255), matching the same
-- widening applied to sentiment_results in V39, for the same reason: longer
-- model identifiers must not be truncated. Safe widening ALTER — no data
-- rewrite or cast required.
ALTER TABLE sentiment_accuracy ALTER COLUMN model_version TYPE VARCHAR(255);
