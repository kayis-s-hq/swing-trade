-- Widen sentiment_results.model_version from VARCHAR(50) to VARCHAR(255) to
-- match llm_analysis_audit's model_version column. Local llama.cpp backends
-- report the full model file path as the model identifier (e.g.
-- "/home/dietpi/.synapse/models/Qwen3-4B-Instruct-2507-UD-Q4_K_XL.gguf"),
-- which exceeds 50 characters and was failing every sentiment persist with
-- "value too long for type character varying(50)".
-- Safe widening ALTER — no data rewrite or cast required in Postgres.
ALTER TABLE sentiment_results ALTER COLUMN model_version TYPE VARCHAR(255);
