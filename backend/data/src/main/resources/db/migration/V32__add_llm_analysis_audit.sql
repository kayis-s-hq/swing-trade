CREATE TABLE IF NOT EXISTS llm_analysis_audit (
    id BIGSERIAL PRIMARY KEY,
    request_id VARCHAR(36) NOT NULL,
    symbol VARCHAR(20) NOT NULL,
    analysis_date DATE NOT NULL,
    provider VARCHAR(32) NOT NULL,
    model_version VARCHAR(255),
    prompt_hash VARCHAR(64),
    system_prompt TEXT,
    user_prompt TEXT,
    raw_response TEXT,
    parsed_score VARCHAR(20),
    parsed_confidence DOUBLE PRECISION,
    status VARCHAR(20) NOT NULL,
    error_message TEXT,
    fallback_used BOOLEAN NOT NULL DEFAULT FALSE,
    max_tokens INTEGER,
    temperature DOUBLE PRECISION,
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    latency_ms BIGINT
);

CREATE INDEX IF NOT EXISTS idx_llm_analysis_audit_symbol_date
    ON llm_analysis_audit(symbol, analysis_date);
CREATE INDEX IF NOT EXISTS idx_llm_analysis_audit_provider_model
    ON llm_analysis_audit(provider, model_version);
CREATE INDEX IF NOT EXISTS idx_llm_analysis_audit_started_at
    ON llm_analysis_audit(started_at);
