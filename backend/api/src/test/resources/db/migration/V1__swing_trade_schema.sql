-- Swing Trade Schema with TimescaleDB hypertables

-- Enable TimescaleDB extension
CREATE EXTENSION IF NOT EXISTS timescaledb;

-- Stocks table
CREATE TABLE IF NOT EXISTS stocks (
    id SERIAL PRIMARY KEY,
    symbol VARCHAR(10) NOT NULL UNIQUE,
    name TEXT,
    exchange VARCHAR(50),
    sector VARCHAR(100),
    industry VARCHAR(100),
    market_cap BIGINT,
    pe_ratio DECIMAL(10,2),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- OHLCV Candles table (TimescaleDB hypertable)
CREATE TABLE IF NOT EXISTS ohlcv_candles (
    symbol VARCHAR(10) NOT NULL,
    date DATE NOT NULL,
    open_price NUMERIC(15,4),
    high_price NUMERIC(15,4),
    low_price NUMERIC(15,4),
    close_price NUMERIC(15,4),
    volume BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Make it a hypertable partitioned by date
SELECT create_hypertable('ohlcv_candles', 'date', chunk_time_interval => INTERVAL '30 days');

-- Create indexes for fast lookups
CREATE INDEX IF NOT EXISTS idx_ohlcv_candles_symbol_date ON ohlcv_candles(symbol, date);
CREATE INDEX IF NOT EXISTS idx_ohlcv_candles_date ON ohlcv_candles(date);

-- Signals table
CREATE TABLE IF NOT EXISTS signals (
    id SERIAL PRIMARY KEY,
    symbol VARCHAR(10) NOT NULL,
    signal_type VARCHAR(20) NOT NULL, -- BUY, SELL, HOLD
    signal_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    price NUMERIC(15,4),
    confidence_score DECIMAL(5,2),
    strategy_name VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create index on signals for fast lookups
CREATE INDEX IF NOT EXISTS idx_signals_symbol_timestamp ON signals(symbol, signal_timestamp DESC);

-- Positions table
CREATE TABLE IF NOT EXISTS positions (
    id SERIAL PRIMARY KEY,
    symbol VARCHAR(10) NOT NULL,
    position_type VARCHAR(10) NOT NULL, -- LONG, SHORT
    quantity NUMERIC(15,4),
    entry_price NUMERIC(15,4),
    exit_price NUMERIC(15,4),
    entry_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    exit_timestamp TIMESTAMP,
    status VARCHAR(20) DEFAULT 'OPEN', -- OPEN, CLOSED, STOPPED
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Trades table
CREATE TABLE IF NOT EXISTS trades (
    id SERIAL PRIMARY KEY,
    symbol VARCHAR(10) NOT NULL,
    trade_type VARCHAR(10) NOT NULL, -- BUY, SELL
    quantity NUMERIC(15,4),
    price NUMERIC(15,4),
    fees NUMERIC(15,4) DEFAULT 0,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    position_id INTEGER REFERENCES positions(id) ON DELETE SET NULL,
    signal_id INTEGER REFERENCES signals(id) ON DELETE SET NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Sentiment results table
CREATE TABLE IF NOT EXISTS sentiment_results (
    id SERIAL PRIMARY KEY,
    symbol VARCHAR(10) NOT NULL,
    sentiment_score DECIMAL(5,2),
    sentiment_label VARCHAR(20), -- POSITIVE, NEGATIVE, NEUTRAL
    analysis_source VARCHAR(100),
    analysis_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    confidence_score DECIMAL(5,2),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for fast lookups on sentiment_results
CREATE INDEX IF NOT EXISTS idx_sentiment_results_symbol_timestamp ON sentiment_results(symbol, analysis_timestamp DESC);

-- Foreign key constraints
ALTER TABLE signals 
ADD CONSTRAINT fk_signals_stock 
FOREIGN KEY (symbol) REFERENCES stocks(symbol) ON DELETE CASCADE;

ALTER TABLE positions 
ADD CONSTRAINT fk_positions_stock 
FOREIGN KEY (symbol) REFERENCES stocks(symbol) ON DELETE CASCADE;

ALTER TABLE trades 
ADD CONSTRAINT fk_trades_stock 
FOREIGN KEY (symbol) REFERENCES stocks(symbol) ON DELETE CASCADE;

ALTER TABLE trades 
ADD CONSTRAINT fk_trades_position 
FOREIGN KEY (position_id) REFERENCES positions(id) ON DELETE SET NULL;

ALTER TABLE trades 
ADD CONSTRAINT fk_trades_signal 
FOREIGN KEY (signal_id) REFERENCES signals(id) ON DELETE SET NULL;

-- Update timestamps trigger
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Triggers for updating timestamps
CREATE OR REPLACE TRIGGER update_stocks_updated_at 
    BEFORE UPDATE ON stocks 
    FOR EACH ROW 
    EXECUTE FUNCTION update_updated_at_column();

CREATE OR REPLACE TRIGGER update_signals_updated_at 
    BEFORE UPDATE ON signals 
    FOR EACH ROW 
    EXECUTE FUNCTION update_updated_at_column();

CREATE OR REPLACE TRIGGER update_positions_updated_at 
    BEFORE UPDATE ON positions 
    FOR EACH ROW 
    EXECUTE FUNCTION update_updated_at_column();
