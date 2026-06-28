-- Swing Trade Schema
-- This schema is aligned with the Java Domain Models and Entity classes.

-- 1. Stocks table
CREATE TABLE IF NOT EXISTS stocks (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(10) NOT NULL UNIQUE,
    name TEXT,
    exchange VARCHAR(50),
    sector VARCHAR(100),
    industry VARCHAR(100),
    market_cap BIGINT,
    pe_ratio NUMERIC(10,2),
    isin VARCHAR(13),
    lot_size INTEGER,
    added_on DATE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 2. OHLCV Candles table
CREATE TABLE IF NOT EXISTS ohlcv_candles (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(10) NOT NULL,
    date DATE NOT NULL,
    open_price NUMERIC(15,4),
    high_price NUMERIC(15,4),
    low_price NUMERIC(15,4),
    close_price NUMERIC(15,4),
    volume BIGINT,
    adj_close_price NUMERIC(15,4),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 3. Signals table
CREATE TABLE IF NOT EXISTS signals (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(10) NOT NULL,
    date DATE NOT NULL,
    signal_type VARCHAR(20) NOT NULL,
    confidence_score NUMERIC(5,2),
    reasoning TEXT,
    entry_price NUMERIC(15,4),
    stop_loss NUMERIC(15,4),
    target NUMERIC(15,4),
    risk_reward NUMERIC(5,2),
    indicators TEXT,
    warning_flag VARCHAR(50),
    generated_at DATE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 4. Positions table
CREATE TABLE IF NOT EXISTS positions (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(10) NOT NULL,
    entry_price NUMERIC(15,4) NOT NULL,
    entry_date DATE NOT NULL,
    quantity INTEGER,
    stop_loss NUMERIC(15,4),
    target NUMERIC(15,4),
    status VARCHAR(20),
    entry_reason TEXT,
    current_price NUMERIC(15,4),
    direction VARCHAR(10),
    pnl NUMERIC(15,4),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 5. Trades table
CREATE TABLE IF NOT EXISTS trades (
    id BIGSERIAL PRIMARY KEY,
    position_id BIGINT NOT NULL,
    symbol VARCHAR(10) NOT NULL,
    entry_date DATE NOT NULL,
    exit_date DATE,
    entry_price NUMERIC(15,4) NOT NULL,
    exit_price NUMERIC(15,4),
    quantity INTEGER,
    total_pnl NUMERIC(15,4),
    duration_days INTEGER,
    trade_status VARCHAR(20) NOT NULL,
    entry_reason TEXT,
    exit_reason VARCHAR(50),
    fees NUMERIC(15,4),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 6. Sentiment results table
CREATE TABLE IF NOT EXISTS sentiment_results (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(10) NOT NULL,
    sentiment_score NUMERIC(5,2),
    sentiment_label VARCHAR(20),
    analysis_source VARCHAR(100),
    analysis_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    confidence_score NUMERIC(5,2),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_ohlcv_candles_symbol_date ON ohlcv_candles(symbol, date);
CREATE INDEX IF NOT EXISTS idx_signals_symbol_date ON signals(symbol, date);
CREATE INDEX IF NOT EXISTS idx_positions_symbol ON positions(symbol);
CREATE INDEX IF NOT EXISTS idx_positions_status ON positions(status);
CREATE INDEX IF NOT EXISTS idx_trades_position_id ON trades(position_id);
CREATE INDEX IF NOT EXISTS idx_trades_symbol ON trades(symbol);
CREATE INDEX IF NOT EXISTS idx_sentiment_results_symbol_timestamp ON sentiment_results(symbol, analysis_timestamp DESC);

-- Foreign Key Constraints
ALTER TABLE signals ADD CONSTRAINT fk_signals_stock FOREIGN KEY (symbol) REFERENCES stocks(symbol) ON DELETE CASCADE;
ALTER TABLE positions ADD CONSTRAINT fk_positions_stock FOREIGN KEY (symbol) REFERENCES stocks(symbol) ON DELETE CASCADE;
ALTER TABLE trades ADD CONSTRAINT fk_trades_stock FOREIGN KEY (symbol) REFERENCES stocks(symbol) ON DELETE CASCADE;
ALTER TABLE trades ADD CONSTRAINT fk_trades_position FOREIGN KEY (position_id) REFERENCES positions(id) ON DELETE SET NULL;

-- Update timestamps trigger
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Triggers for updating timestamps
CREATE OR REPLACE TRIGGER update_stocks_updated_at BEFORE UPDATE ON stocks FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE OR REPLACE TRIGGER update_signals_updated_at BEFORE UPDATE ON signals FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE OR REPLACE TRIGGER update_positions_updated_at BEFORE UPDATE ON positions FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE OR REPLACE TRIGGER update_trades_updated_at BEFORE UPDATE ON trades FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
