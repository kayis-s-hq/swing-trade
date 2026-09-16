-- Widen model_version from VARCHAR(50) to VARCHAR(255) to accommodate
-- longer model identifiers (e.g. full OpenAI/Ollama model strings) without
-- truncation. Safe widening ALTER — no data rewrite or cast required.
ALTER TABLE sentiment_results ALTER COLUMN model_version TYPE VARCHAR(255);
