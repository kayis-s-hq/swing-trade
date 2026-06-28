-- Fix sentiment_results table to match SentimentResultEntity
-- Drop and recreate with correct schema

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
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_sentiment_results_symbol_date ON sentiment_results(symbol, date);

-- Update timestamps trigger
CREATE TRIGGER update_sentiment_results_updated_at BEFORE UPDATE ON sentiment_results
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
