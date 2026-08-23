-- V8__create_intelligence_tables.sql

-- news_items: fetched headlines
CREATE TABLE IF NOT EXISTS news_items (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(10) NOT NULL,
    headline TEXT NOT NULL,
    source VARCHAR(50) NOT NULL,
    published_at TIMESTAMP NOT NULL,
    url TEXT,
    raw_content TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- pdf_extractions: extracted earnings data
CREATE TABLE IF NOT EXISTS pdf_extractions (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(10) NOT NULL,
    document_type VARCHAR(20) NOT NULL,
    extracted_json JSONB NOT NULL,
    source_url TEXT,
    extraction_date DATE NOT NULL,
    model_used VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- sentiment_accuracy: LLM prediction accuracy tracking
CREATE TABLE IF NOT EXISTS sentiment_accuracy (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(10) NOT NULL,
    signal_date DATE NOT NULL,
    sentiment_score VARCHAR(20) NOT NULL,
    actual_outcome VARCHAR(20) NOT NULL,
    was_correct BOOLEAN,
    pnl_pct NUMERIC(10,2),
    recorded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Indexes
DROP INDEX IF EXISTS idx_news_symbol_date;
DROP INDEX IF EXISTS idx_news_headline;
DROP INDEX IF EXISTS idx_pdf_symbol_date;
-- Note: idx_sentiment_accuracy is created by V11 with analysis_date column
CREATE INDEX idx_news_symbol_date ON news_items(symbol, published_at);
CREATE INDEX idx_news_headline ON news_items(LOWER(headline));
CREATE INDEX idx_pdf_symbol_date ON pdf_extractions(symbol, extraction_date);