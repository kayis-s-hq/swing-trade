-- Test schema: plain PostgreSQL, no TimescaleDB dependencies

CREATE TABLE IF NOT EXISTS stocks (
    id SERIAL PRIMARY KEY,
    symbol VARCHAR(10) NOT NULL UNIQUE,
    name VARCHAR(200),
    exchange VARCHAR(50),
    segment VARCHAR(50),
    sector VARCHAR(100),
    industry VARCHAR(100),
    is_active BOOLEAN DEFAULT TRUE,
    is_tradable BOOLEAN DEFAULT TRUE,
    is_derivatives BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ohlcv_candles (
    id SERIAL PRIMARY KEY,
    symbol VARCHAR(10) NOT NULL,
    date DATE NOT NULL,
    open_price NUMERIC(15,4) NOT NULL,
    high_price NUMERIC(15,4) NOT NULL,
    low_price NUMERIC(15,4) NOT NULL,
    close_price NUMERIC(15,4) NOT NULL,
    volume BIGINT NOT NULL,
    adj_close_price NUMERIC(15,4),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ohlcv_symbol ON ohlcv_candles(symbol);
CREATE INDEX IF NOT EXISTS idx_ohlcv_symbol_date ON ohlcv_candles(symbol, date DESC);
