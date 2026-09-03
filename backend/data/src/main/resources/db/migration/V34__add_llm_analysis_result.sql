CREATE TABLE IF NOT EXISTS llm_analysis_result (
    id BIGSERIAL PRIMARY KEY, run_id UUID NOT NULL, symbol VARCHAR(20) NOT NULL,
    analysis_date DATE NOT NULL, recommendation VARCHAR(20), confidence DOUBLE PRECISION,
    narrative TEXT, key_drivers TEXT[], bullish_factors TEXT[], bearish_factors TEXT[],
    composite_score INTEGER, composite_signal VARCHAR(20), success BOOLEAN NOT NULL,
    fallback_used BOOLEAN NOT NULL DEFAULT FALSE, error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_llm_analysis_result_symbol_date UNIQUE (symbol, analysis_date)
);
CREATE INDEX IF NOT EXISTS idx_llm_analysis_result_run_id ON llm_analysis_result(run_id);
