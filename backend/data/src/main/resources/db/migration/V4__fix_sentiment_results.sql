-- V4__fix_sentiment_results.sql
-- Fix sentiment_results table schema + merge signal processed flag

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

-- Add processed flag to signals table (merged from old V4__add_signal_processed_flag.sql)
ALTER TABLE signals ADD COLUMN IF NOT EXISTS processed BOOLEAN DEFAULT FALSE;
CREATE INDEX IF NOT EXISTS idx_signals_processed_date ON signals(processed, date);

-- Update timestamps trigger
CREATE TRIGGER update_sentiment_results_updated_at BEFORE UPDATE ON sentiment_results
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
