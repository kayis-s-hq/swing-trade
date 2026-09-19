-- V64: Signal tournament record. One row per (symbol, selection_date): which variant's BUY won
-- arbitration, the full candidate slate, and whether the winner has been executed in the
-- dedicated "selected" paper portfolio. status: PENDING (awaiting sentiment/LLM/execution),
-- EXECUTED, BLOCKED (sentiment/LLM/kill switch suppressed it, or it expired).
CREATE TABLE IF NOT EXISTS signal_selection (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(16) NOT NULL,
    selection_date DATE NOT NULL,
    winner_variant_id VARCHAR(40) NOT NULL,
    winner_version INTEGER NOT NULL,
    winner_signal_id BIGINT,
    winner_confidence NUMERIC(8,4) NOT NULL,
    candidates JSONB NOT NULL DEFAULT '[]'::jsonb,
    reason VARCHAR(255),
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    status_detail VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_signal_selection_symbol_date UNIQUE (symbol, selection_date)
);
CREATE INDEX IF NOT EXISTS idx_signal_selection_date ON signal_selection (selection_date);
CREATE INDEX IF NOT EXISTS idx_signal_selection_winner ON signal_selection (winner_variant_id, selection_date);
