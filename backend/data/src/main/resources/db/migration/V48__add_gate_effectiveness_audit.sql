CREATE TABLE IF NOT EXISTS gate_effectiveness_audit (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(20) NOT NULL,
    signal_date DATE NOT NULL,
    gate_name VARCHAR(40) NOT NULL,
    verdict VARCHAR(24) NOT NULL,
    reason TEXT,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_gate_effectiveness_symbol_date_gate UNIQUE (symbol, signal_date, gate_name)
);

CREATE INDEX IF NOT EXISTS idx_gate_effectiveness_gate_date
    ON gate_effectiveness_audit (gate_name, signal_date);
