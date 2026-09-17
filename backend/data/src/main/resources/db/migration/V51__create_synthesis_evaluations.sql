CREATE TABLE IF NOT EXISTS synthesis_evaluations (
    id BIGSERIAL PRIMARY KEY,
    version INTEGER NOT NULL DEFAULT 0,
    symbol VARCHAR(20) NOT NULL,
    analysis_date DATE NOT NULL,
    recommendation VARCHAR(10),
    confidence DOUBLE PRECISION NOT NULL,
    conflict_detected BOOLEAN NOT NULL DEFAULT FALSE,
    event_risk_detected BOOLEAN NOT NULL DEFAULT FALSE,
    event_risk_reason VARCHAR(500),
    recorded_at TIMESTAMPTZ NOT NULL,
    outcome_horizon_days INTEGER,
    outcome_forward_return_pct NUMERIC(12,6),
    outcome_correct BOOLEAN,
    outcome_measured_at TIMESTAMPTZ,
    CONSTRAINT uq_synthesis_evaluation_symbol_date UNIQUE (symbol, analysis_date)
);

CREATE INDEX IF NOT EXISTS idx_synthesis_evaluation_date
    ON synthesis_evaluations (analysis_date);
CREATE INDEX IF NOT EXISTS idx_synthesis_evaluation_outcome
    ON synthesis_evaluations (outcome_measured_at);
