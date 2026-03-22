-- =============================================================================
-- Swing Trade Database - Flyway Migration V2
-- =============================================================================
-- Description: Create TimescaleDB hypertables for efficient time-series data
-- Author: Swing Trade Team
-- Date: 2026-03-07
-- =============================================================================

-- Enable TimescaleDB extension if not already enabled
CREATE EXTENSION IF NOT EXISTS timescaledb;

-- =============================================================================
-- OHLCV Candles Hypertable
-- =============================================================================
-- Create a hypertable for OHLCV candle data partitioned by date
-- This provides efficient time-series queries and automatic partitioning

-- Drop existing hypertable if exists (for clean migration)
SELECT drop_chunks('ohlcv_candles', older_than => INTERVAL '5 years')
WHERE EXISTS (SELECT 1 FROM pg_tables WHERE tablename = 'ohlcv_candles');

-- Create the candles table with proper constraints
CREATE TABLE IF NOT EXISTS ohlcv_candles (
    symbol VARCHAR(10) NOT NULL,
    date DATE NOT NULL,
    open_price NUMERIC(15,4) NOT NULL,
    high_price NUMERIC(15,4) NOT NULL,
    low_price NUMERIC(15,4) NOT NULL,
    close_price NUMERIC(15,4) NOT NULL,
    volume BIGINT NOT NULL,
    turnover NUMERIC(20,4),
    trades BIGINT,
    vwap NUMERIC(15,4),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    -- Primary key for unique candle entries
    PRIMARY KEY (symbol, date)
);

-- Create the hypertable with 30-day chunks for optimal performance
SELECT create_hypertable(
    'ohlcv_candles',
    'date',
    if_not_exists => TRUE,
    chunk_time_interval => INTERVAL '30 days',
    if_not_exists_chunks => TRUE
);

-- Add a time partition for symbol to enable efficient symbol-specific queries
SELECT add_range_partition(
    'ohlcv_candles',
    'symbol',
    partitions => ARRAY['BANKNIFTY', 'NIFTY', 'RELIANCE', 'TCS', 'HDFC', 'INFY', 'ICICIBANK', 'HINDUNILVR', 'SBIN', 'ITC']
)
WHERE NOT EXISTS (
    SELECT 1 FROM timescaledb_information.partitions
    WHERE table_name = 'ohlcv_candles' AND column_name = 'symbol'
);

-- Create indexes for the hypertable
CREATE INDEX IF NOT EXISTS idx_ohlcv_candles_symbol_date
    ON ohlcv_candles(symbol, date DESC);

CREATE INDEX IF NOT EXISTS idx_ohlcv_candles_date_symbol
    ON ohlcv_candles(date, symbol);

CREATE INDEX IF NOT EXISTS idx_ohlcv_candles_symbol
    ON ohlcv_candles(symbol);

CREATE INDEX IF NOT EXISTS idx_ohlcv_candles_date
    ON ohlcv_candles(date);

CREATE INDEX IF NOT EXISTS idx_ohlcv_candles_created_at
    ON ohlcv_candles(created_at);

-- Create partial index for latest candle per symbol
CREATE INDEX IF NOT EXISTS idx_ohlcv_candles_latest
    ON ohlcv_candles(symbol)
    WHERE date = (SELECT MAX(date) FROM ohlcv_candles c2 WHERE c2.symbol = ohlcv_candles.symbol);

-- Add comments to table and columns
COMMENT ON TABLE ohlcv_candles IS 'Time-series OHLCV candle data partitioned by date using TimescaleDB';
COMMENT ON COLUMN ohlcv_candles.symbol IS 'Stock/symbol identifier (NSE/BSE format)';
COMMENT ON COLUMN ohlcv_candles.date IS 'Candle date (trading day)';
COMMENT ON COLUMN ohlcv_candles.open_price IS 'Opening price for the period';
COMMENT ON COLUMN ohlcv_candles.high_price IS 'Highest price for the period';
COMMENT ON COLUMN ohlcv_candles.low_price IS 'Lowest price for the period';
COMMENT ON COLUMN ohlcv_candles.close_price IS 'Closing price for the period';
COMMENT ON COLUMN ohlcv_candles.volume IS 'Trading volume for the period';
COMMENT ON COLUMN ohlcv_candles.turnover IS 'Total turnover (volume * price)';
COMMENT ON COLUMN ohlcv_candles.trades IS 'Number of trades in the period';
COMMENT ON COLUMN ohlcv_candles.vwap IS 'Volume-weighted average price';

-- =============================================================================
-- Intraday Candles Hypertable (Optional - for minute-level data)
-- =============================================================================
-- Create hypertable for intraday/candle data if needed

CREATE TABLE IF NOT EXISTS ohlcv_candles_intraday (
    symbol VARCHAR(10) NOT NULL,
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    interval_seconds INTEGER NOT NULL, -- 60 for minute, 300 for 5min, etc.
    open_price NUMERIC(15,4) NOT NULL,
    high_price NUMERIC(15,4) NOT NULL,
    low_price NUMERIC(15,4) NOT NULL,
    close_price NUMERIC(15,4) NOT NULL,
    volume BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (symbol, timestamp, interval_seconds)
);

-- Create hypertable for intraday data
SELECT create_hypertable(
    'ohlcv_candles_intraday',
    'timestamp',
    if_not_exists => TRUE,
    chunk_time_interval => INTERVAL '1 day',
    if_not_exists_chunks => TRUE
);

-- Add interval partition for different time frames
SELECT add_range_partition(
    'ohlcv_candles_intraday',
    'interval_seconds',
    partitions => ARRAY['60', '300', '900', '3600']
)
WHERE NOT EXISTS (
    SELECT 1 FROM timescaledb_information.partitions
    WHERE table_name = 'ohlcv_candles_intraday' AND column_name = 'interval_seconds'
);

-- Create indexes for intraday table
CREATE INDEX IF NOT EXISTS idx_ohlcv_intraday_symbol_timestamp
    ON ohlcv_candles_intraday(symbol, timestamp DESC);

CREATE INDEX IF NOT EXISTS idx_ohlcv_intraday_timestamp
    ON ohlcv_candles_intraday(timestamp);

COMMENT ON TABLE ohlcv_candles_intraday IS 'Intraday OHLCV data with configurable intervals';

-- =============================================================================
-- Signals Hypertable
-- =============================================================================
-- Create hypertable for trading signals

CREATE TABLE IF NOT EXISTS signals (
    id SERIAL NOT NULL,
    symbol VARCHAR(10) NOT NULL,
    signal_type VARCHAR(20) NOT NULL, -- BUY, SELL, HOLD, STRONG_BUY, STRONG_SELL
    signal_timestamp TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    price NUMERIC(15,4),
    target_price NUMERIC(15,4),
    stop_loss_price NUMERIC(15,4),
    confidence_score DECIMAL(5,2),
    strategy_name VARCHAR(100),
    signal_source VARCHAR(50), -- TECHNICAL, FUNDAMENTAL, SENTIMENT, COMBINED
    indicators JSONB, -- JSON for storing indicator values
    notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id)
);

-- Create hypertable for signals
SELECT create_hypertable(
    'signals',
    'signal_timestamp',
    if_not_exists => TRUE,
    chunk_time_interval => INTERVAL '7 days',
    if_not_exists_chunks => TRUE
);

-- Add symbol partition
SELECT add_range_partition(
    'signals',
    'symbol',
    partitions => ARRAY['BANKNIFTY', 'NIFTY', 'RELIANCE', 'TCS', 'HDFC', 'INFY', 'ICICIBANK', 'HINDUNILVR', 'SBIN', 'ITC']
)
WHERE NOT EXISTS (
    SELECT 1 FROM timescaledb_information.partitions
    WHERE table_name = 'signals' AND column_name = 'symbol'
);

-- Create indexes
CREATE UNIQUE INDEX IF NOT EXISTS idx_signals_symbol_timestamp ON signals(symbol, signal_timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_signals_type ON signals(signal_type);
CREATE INDEX IF NOT EXISTS idx_signals_strategy ON signals(strategy_name);
CREATE INDEX IF NOT EXISTS idx_signals_source ON signals(signal_source);
CREATE INDEX IF NOT EXISTS idx_signals_confidence ON signals(confidence_score DESC)
    WHERE signal_type IN ('BUY', 'SELL');
CREATE INDEX IF NOT EXISTS idx_signals_created_at ON signals(created_at);

-- Index on JSONB field for indicator searches
CREATE INDEX IF NOT EXISTS idx_signals_indicators_gin ON signals USING GIN(indicators);

COMMENT ON TABLE signals IS 'Trading signals generated by various strategies';
COMMENT ON COLUMN signals.signal_type IS 'Type of signal: BUY, SELL, HOLD, STRONG_BUY, STRONG_SELL';
COMMENT ON COLUMN signals.confidence_score IS 'Confidence percentage (0-100)';
COMMENT ON COLUMN signals.signal_source IS 'Source of signal: TECHNICAL, FUNDAMENTAL, SENTIMENT, COMBINED';
COMMENT ON COLUMN signals.indicators IS 'JSON object containing indicator values that triggered the signal';

-- =============================================================================
-- Position History Hypertable
-- =============================================================================

CREATE TABLE IF NOT EXISTS position_history (
    id SERIAL NOT NULL,
    position_id INTEGER,
    symbol VARCHAR(10) NOT NULL,
    position_type VARCHAR(10) NOT NULL, -- LONG, SHORT
    entry_price NUMERIC(15,4) NOT NULL,
    current_price NUMERIC(15,4),
    quantity NUMERIC(15,4) NOT NULL,
    pnl NUMERIC(15,4),
    pnl_percentage DECIMAL(10,4),
    status VARCHAR(20) DEFAULT 'OPEN', -- OPEN, CLOSED, STOPPED, ADJUSTED
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id)
);

-- Create hypertable for position history
SELECT create_hypertable(
    'position_history',
    'timestamp',
    if_not_exists => TRUE,
    chunk_time_interval => INTERVAL '1 day',
    if_not_exists_chunks => TRUE
);

-- Create indexes
CREATE INDEX IF NOT EXISTS idx_position_history_symbol_timestamp
    ON position_history(symbol, timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_position_history_position_id
    ON position_history(position_id);
CREATE INDEX IF NOT EXISTS idx_position_history_status
    ON position_history(status);
CREATE INDEX IF NOT EXISTS idx_position_history_created_at
    ON position_history(created_at);

COMMENT ON TABLE position_history IS 'Historical tracking of position state changes';

-- =============================================================================
-- Query Optimization: Continuous Aggregates
-- =============================================================================
-- Create continuous aggregates for common time-series queries

-- Daily aggregates for OHLCV data
CREATE MATERIALIZED VIEW IF NOT EXISTS ohlcv_candles_daily_agg
WITH (timescaledb.continuous) AS
SELECT
    symbol,
    time_bucket('1 day', date) AS bucket,
    first(open_price, date) AS open,
    max(high_price) AS high,
    min(low_price) AS low,
    last(close_price, date) AS close,
    sum(volume) AS volume,
    sum(coalesce(turnover, 0)) AS turnover,
    sum(coalesce(trades, 0)) AS trades
FROM ohlcv_candles
GROUP BY symbol, bucket;

-- Weekly aggregates
CREATE MATERIALIZED VIEW IF NOT EXISTS ohlcv_candles_weekly_agg
WITH (timescaledb.continuous) AS
SELECT
    symbol,
    time_bucket('1 week', date) AS bucket,
    first(open_price, date) AS open,
    max(high_price) AS high,
    min(low_price) AS low,
    last(close_price, date) AS close,
    sum(volume) AS volume
FROM ohlcv_candles
GROUP BY symbol, bucket;

-- Monthly aggregates
CREATE MATERIALIZED VIEW IF NOT EXISTS ohlcv_candles_monthly_agg
WITH (timescaledb.continuous) AS
SELECT
    symbol,
    time_bucket('1 month', date) AS bucket,
    first(open_price, date) AS open,
    max(high_price) AS high,
    min(low_price) AS low,
    last(close_price, date) AS close,
    sum(volume) AS volume
FROM ohlcv_candles
GROUP BY symbol, bucket;

-- =============================================================================
-- Cleanup and Maintenance Functions
-- =============================================================================

-- Function to clean old data (useful for maintenance)
CREATE OR REPLACE FUNCTION cleanup_old_data(
    retention_days INTEGER DEFAULT 365
)
RETURNS VOID AS $$
DECLARE
    cutoff_date DATE;
BEGIN
    cutoff_date := CURRENT_DATE - interval '1 day' * retention_days;

    -- Clean up old signal data
    DELETE FROM signals
    WHERE signal_timestamp < (cutoff_date - interval '30 days');

    -- Drop old chunks
    PERFORM drop_chunks('ohlcv_candles', older_than => interval '5 years');

    RAISE NOTICE 'Data cleanup completed. Retained data from %', cutoff_date;
END;
$$ LANGUAGE plpgsql;

-- =============================================================================
-- Permissions (adjust for your user setup)
-- =============================================================================
-- Grant necessary permissions to the application user
-- GRANT SELECT, INSERT, UPDATE ON ALL TABLES IN SCHEMA public TO swingtrade_user;
-- GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO swingtrade_user;

-- =============================================================================
-- Migration Complete
-- =============================================================================
-- This migration creates TimescaleDB hypertables for efficient time-series
-- data storage. All time-based tables are now partitioned for optimal
-- performance on time-range queries.
