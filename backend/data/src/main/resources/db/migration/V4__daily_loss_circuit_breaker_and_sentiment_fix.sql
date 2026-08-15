-- V4: daily loss circuit breaker + sentiment_results schema fix + signal processed flag

-- Circuit breaker state
CREATE TABLE IF NOT EXISTS daily_loss_circuit_breaker_state (
    id              BIGSERIAL PRIMARY KEY,
    circuit_open    BOOLEAN NOT NULL DEFAULT FALSE,
    circuit_opened_at TIMESTAMP,
    loss_at_open    NUMERIC(15,2),
    last_reset_date DATE NOT NULL,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE daily_loss_circuit_breaker_state
    DROP CONSTRAINT IF EXISTS uq_single_row;
ALTER TABLE daily_loss_circuit_breaker_state
    ADD CONSTRAINT uq_single_row CHECK (id = 1);

-- Fix sentiment_results table schema
DROP TABLE IF EXISTS sentiment_results;

CREATE TABLE sentiment_results (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(10) NOT NULL,
    date DATE NOT NULL,
    sentiment_score VARCHAR(20) NOT NULL,
    summary TEXT,
    raw_content TEXT,
    confidence REAL,
    analyzed_at DATE,
    red_flags TEXT[],
    catalysts TEXT[],
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_sentiment_results_symbol_date ON sentiment_results(symbol, date);

-- Add processed flag to signals table
ALTER TABLE signals ADD COLUMN IF NOT EXISTS processed BOOLEAN DEFAULT FALSE;
CREATE INDEX IF NOT EXISTS idx_signals_processed_date ON signals(processed, date);

-- Update timestamps trigger
DROP TRIGGER IF EXISTS update_sentiment_results_updated_at ON sentiment_results;
CREATE TRIGGER update_sentiment_results_updated_at BEFORE UPDATE ON sentiment_results
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();