-- Swing Trade Consolidated Schema (V1)
-- All 24 migrations merged into a single idempotent schema definition.
-- Safe to run on a fresh database or as a one-time re-initialization.

-- ============================================================================
-- Utility: Update-timestamp trigger function
-- ============================================================================

CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- ============================================================================
-- 1. Stocks table
-- ============================================================================

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

CREATE OR REPLACE TRIGGER update_stocks_updated_at BEFORE UPDATE ON stocks
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ============================================================================
-- 2. OHLCV Candles table (V1 base + V2 adj_close column)
-- ============================================================================

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

-- ============================================================================
-- 3. Signals table (V1 base + V7 strategy + V19 sentiment_score + V22 sentiment_reasoning)
-- ============================================================================

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
    processed BOOLEAN DEFAULT FALSE,
    strategy VARCHAR(30) NOT NULL DEFAULT 'DEFAULT',
    sentiment_score VARCHAR(12),
    sentiment_reasoning TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE OR REPLACE TRIGGER update_signals_updated_at BEFORE UPDATE ON signals
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ============================================================================
-- 4. Positions table (V1 base + V16 broker columns + V21 broker_type/direction/exchange)
-- ============================================================================

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
    direction VARCHAR(10) DEFAULT 'LONG',
    pnl NUMERIC(15,4),
    broker_type VARCHAR(10) DEFAULT 'PAPER',
    position_id VARCHAR(36),
    exchange VARCHAR(10) DEFAULT 'NSE',
    average_price NUMERIC(15,2),
    unrealized_pnl NUMERIC(15,2),
    realized_pnl NUMERIC(15,2),
    margin_utilized NUMERIC(15,2),
    entry_time TIMESTAMP,
    exit_time TIMESTAMP,
    exit_reason VARCHAR(64),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE OR REPLACE TRIGGER update_positions_updated_at BEFORE UPDATE ON positions
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ============================================================================
-- 5. Trades table (V1 base + V23 direction)
-- ============================================================================

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
    direction VARCHAR(10),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE OR REPLACE TRIGGER update_trades_updated_at BEFORE UPDATE ON trades
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ============================================================================
-- 6. Sentiment results table (V4 DROP+RECREATE + V12 metadata + V17 article_count + V24 expand model_version)
-- ============================================================================

CREATE TABLE IF NOT EXISTS sentiment_results (
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
    prompt_hash VARCHAR(64),
    model_version VARCHAR(255),
    article_count INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE OR REPLACE TRIGGER update_sentiment_results_updated_at BEFORE UPDATE ON sentiment_results
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ============================================================================
-- 7. Watchlist table (V3)
-- ============================================================================

CREATE TABLE IF NOT EXISTS watchlist (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(10) NOT NULL UNIQUE,
    name TEXT,
    exchange VARCHAR(5) DEFAULT 'NSE',
    is_active BOOLEAN DEFAULT TRUE,
    added_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_synced_at TIMESTAMP,
    candle_count INTEGER DEFAULT 0
);

-- ============================================================================
-- 8. Daily loss circuit breaker state (V4)
-- ============================================================================

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

-- ============================================================================
-- 9. Trade labels table (V5)
-- ============================================================================

CREATE TABLE IF NOT EXISTS trade_labels (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trade_id BIGINT NOT NULL,
    position_id BIGINT NOT NULL,
    exit_reason VARCHAR(20) NOT NULL,
    exit_confidence NUMERIC(15,4),
    notes TEXT,
    labelled_by VARCHAR(100),
    labelled_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE OR REPLACE TRIGGER update_trade_labels_updated_at BEFORE UPDATE ON trade_labels
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- ============================================================================
-- 10. Fyers symbol master table (V6)
-- ============================================================================

CREATE TABLE IF NOT EXISTS fyers_symbol_master (
    id             BIGSERIAL PRIMARY KEY,
    fy_token       VARCHAR(32)  NOT NULL UNIQUE,
    fyers_symbol   VARCHAR(64)  NOT NULL,
    trading_symbol VARCHAR(32)  NOT NULL,
    name           TEXT,
    exchange       VARCHAR(16)  NOT NULL DEFAULT 'NSE',
    segment        VARCHAR(16),
    lot_size       INTEGER,
    tick_size      NUMERIC(10,4),
    isin           VARCHAR(13),
    updated_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================================
-- 11. News items table (V8)
-- ============================================================================

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

-- ============================================================================
-- 12. PDF extractions table (V8)
-- ============================================================================

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

-- ============================================================================
-- 13. Sentiment accuracy table (V11 — DROP+RECREATE of V8 schema)
-- ============================================================================

CREATE TABLE IF NOT EXISTS sentiment_accuracy (
    id            BIGSERIAL PRIMARY KEY,
    symbol        VARCHAR(20) NOT NULL,
    analysis_date DATE NOT NULL,
    llm_score     VARCHAR(20) NOT NULL,
    llm_confidence REAL NOT NULL,
    numeric_score REAL NOT NULL,
    actual_return_1d  DECIMAL(10,6),
    actual_return_5d  DECIMAL(10,6),
    actual_return_21d DECIMAL(10,6),
    ground_truth_label VARCHAR(10),
    was_correct       BOOLEAN,
    pnl_pct           DECIMAL(10,6),
    market_regime     VARCHAR(10),
    prompt_hash       VARCHAR(64),
    model_version     VARCHAR(50),
    composite_score   INT,
    composite_signal  VARCHAR(10),
    composite_id      BIGINT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    evaluated_at      TIMESTAMPTZ
);

-- ============================================================================
-- 14. App settings table (V9)
-- ============================================================================

CREATE TABLE IF NOT EXISTS app_settings (
    id BIGSERIAL PRIMARY KEY,
    key VARCHAR(64) NOT NULL UNIQUE,
    value TEXT,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================================
-- 15. Kill switch table (V10)
-- ============================================================================

CREATE TABLE IF NOT EXISTS kill_switch (
    id          BIGINT PRIMARY KEY DEFAULT 1,
    active      BOOLEAN NOT NULL DEFAULT FALSE,
    enabled_at  TIMESTAMP,
    reason      TEXT,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ============================================================================
-- 16. NSE holidays table (V14)
-- ============================================================================

CREATE TABLE IF NOT EXISTS nse_holidays (
    id            BIGSERIAL PRIMARY KEY,
    holiday_date  DATE NOT NULL,
    occasion      VARCHAR(128) NOT NULL,
    holiday_type  VARCHAR(32) NOT NULL DEFAULT 'FULL' CHECK (holiday_type IN ('FULL', 'PARTIAL')),
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_nse_holiday_date UNIQUE (holiday_date)
);

-- ============================================================================
-- 17. News articles table (V18)
-- ============================================================================

CREATE TABLE IF NOT EXISTS news_articles (
    id          BIGSERIAL PRIMARY KEY,
    symbol      VARCHAR(20) NOT NULL,
    source      VARCHAR(50) NOT NULL,
    title       TEXT NOT NULL,
    link        TEXT,
    summary     TEXT,
    published_at TIMESTAMPTZ,
    raw_content TEXT,
    created_at  TIMESTAMPTZ DEFAULT NOW()
);

-- ============================================================================
-- 18. Job runs table (V20)
-- ============================================================================

CREATE TABLE IF NOT EXISTS job_runs (
    id              BIGSERIAL PRIMARY KEY,
    run_id          UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    trigger_type    VARCHAR(16) NOT NULL DEFAULT 'MANUAL',
    status          VARCHAR(16) NOT NULL DEFAULT 'RUNNING',
    started_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at    TIMESTAMP,
    symbols_count   INTEGER NOT NULL DEFAULT 0,
    completed_count INTEGER NOT NULL DEFAULT 0,
    failed_count    INTEGER NOT NULL DEFAULT 0,
    error_message   TEXT
);

-- ============================================================================
-- 19. Job run stages table (V20)
-- ============================================================================

CREATE TABLE IF NOT EXISTS job_run_stages (
    id              BIGSERIAL PRIMARY KEY,
    run_id          UUID NOT NULL REFERENCES job_runs(run_id) ON DELETE CASCADE,
    symbol          VARCHAR(16) NOT NULL,
    stage_name      VARCHAR(32) NOT NULL,
    status          VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    started_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at    TIMESTAMP,
    duration_ms     BIGINT,
    error_message   TEXT,
    log_details     TEXT,
    result_summary  TEXT
);

-- ============================================================================
-- 20. Paper trading portfolio (V13 — kept, not dropped by V21)
-- ============================================================================

CREATE TABLE IF NOT EXISTS paper_trading_portfolio (
    id                       BIGSERIAL PRIMARY KEY,
    portfolio_id             VARCHAR(32) NOT NULL DEFAULT 'default',
    initial_capital          NUMERIC(15,2) NOT NULL DEFAULT 10000000,
    current_capital          NUMERIC(15,2) NOT NULL DEFAULT 10000000,
    total_realized_pnl       NUMERIC(15,2) NOT NULL DEFAULT 0,
    total_unrealized_pnl     NUMERIC(15,2) NOT NULL DEFAULT 0,
    open_position_count      INTEGER NOT NULL DEFAULT 0,
    max_concurrent_positions INTEGER NOT NULL DEFAULT 5,
    max_capital_per_position_pct NUMERIC(5,2) NOT NULL DEFAULT 20,
    commission_rate          NUMERIC(6,4) NOT NULL DEFAULT 0.05,
    updated_at               TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================================
-- 21. Paper trading portfolio snapshots (V13 + V15 created_at — kept, not dropped by V21)
-- ============================================================================

CREATE TABLE IF NOT EXISTS paper_trading_portfolio_snapshots (
    id              BIGSERIAL PRIMARY KEY,
    snapshot_time   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    total_value     NUMERIC(15,2) NOT NULL,
    cash_balance    NUMERIC(15,2) NOT NULL,
    market_value    NUMERIC(15,2) NOT NULL DEFAULT 0,
    total_pnl       NUMERIC(15,2) NOT NULL DEFAULT 0,
    return_pct      NUMERIC(8,4) NOT NULL DEFAULT 0,
    open_positions  INTEGER NOT NULL DEFAULT 0,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================================
-- 22. Paper trading orders (V13 — kept, not dropped by V21)
-- ============================================================================

CREATE TABLE IF NOT EXISTS paper_trading_orders (
    id            BIGSERIAL PRIMARY KEY,
    order_id      VARCHAR(32) NOT NULL UNIQUE,
    symbol        VARCHAR(16) NOT NULL,
    order_type    VARCHAR(16) NOT NULL DEFAULT 'MARKET',
    direction     VARCHAR(10) NOT NULL,
    quantity      INTEGER NOT NULL,
    price         NUMERIC(15,2) NOT NULL,
    limit_price   NUMERIC(15,2),
    stop_price    NUMERIC(15,2),
    status        VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    commission    NUMERIC(15,4) NOT NULL DEFAULT 0,
    executed_at   TIMESTAMP,
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================================
-- Indexes
-- ============================================================================

-- OHLCV
CREATE INDEX IF NOT EXISTS idx_ohlcv_candles_symbol_date ON ohlcv_candles(symbol, date);

-- Signals
CREATE INDEX IF NOT EXISTS idx_signals_symbol_date ON signals(symbol, date);
CREATE INDEX IF NOT EXISTS idx_signals_processed_date ON signals(processed, date);
CREATE INDEX IF NOT EXISTS idx_signals_symbol_date_strategy ON signals(symbol, date, strategy);

-- Positions
CREATE INDEX IF NOT EXISTS idx_positions_symbol ON positions(symbol);
CREATE INDEX IF NOT EXISTS idx_positions_status ON positions(status);

-- Trades
CREATE INDEX IF NOT EXISTS idx_trades_position_id ON trades(position_id);
CREATE INDEX IF NOT EXISTS idx_trades_symbol ON trades(symbol);

-- Sentiment results
CREATE INDEX IF NOT EXISTS idx_sentiment_results_symbol_date ON sentiment_results(symbol, date);
CREATE INDEX IF NOT EXISTS idx_sentiment_results_model ON sentiment_results (model_version);

-- Watchlist
CREATE INDEX IF NOT EXISTS idx_watchlist_active ON watchlist(is_active);

-- Trade labels
CREATE INDEX IF NOT EXISTS idx_trade_labels_trade_id ON trade_labels(trade_id);
CREATE INDEX IF NOT EXISTS idx_trade_labels_position_id ON trade_labels(position_id);

-- Fyers symbol master
CREATE UNIQUE INDEX IF NOT EXISTS ux_fyers_symbol_master_symbol ON fyers_symbol_master (fyers_symbol);
CREATE INDEX IF NOT EXISTS ix_fyers_symbol_master_trading ON fyers_symbol_master (trading_symbol);
CREATE INDEX IF NOT EXISTS ix_fyers_symbol_master_name ON fyers_symbol_master (LOWER(name) text_pattern_ops);

-- News items
CREATE INDEX IF NOT EXISTS idx_news_symbol_date ON news_items(symbol, published_at);
CREATE INDEX IF NOT EXISTS idx_news_headline ON news_items(LOWER(headline));

-- PDF extractions
CREATE INDEX IF NOT EXISTS idx_pdf_symbol_date ON pdf_extractions(symbol, extraction_date);

-- Sentiment accuracy
CREATE INDEX IF NOT EXISTS idx_sentiment_accuracy_symbol_date ON sentiment_accuracy (symbol, analysis_date);
CREATE INDEX IF NOT EXISTS idx_sentiment_accuracy_label ON sentiment_accuracy (ground_truth_label);
CREATE INDEX IF NOT EXISTS idx_sentiment_accuracy_regime ON sentiment_accuracy (market_regime);
CREATE INDEX IF NOT EXISTS idx_sentiment_accuracy_evaluated ON sentiment_accuracy (evaluated_at) WHERE evaluated_at IS NOT NULL;

-- App settings
CREATE INDEX IF NOT EXISTS idx_app_settings_key ON app_settings(key);

-- NSE holidays
CREATE INDEX IF NOT EXISTS idx_nse_holidays_date ON nse_holidays(holiday_date);

-- News articles
CREATE INDEX IF NOT EXISTS idx_news_articles_symbol ON news_articles(symbol);
CREATE INDEX IF NOT EXISTS idx_news_articles_source ON news_articles(source);
CREATE INDEX IF NOT EXISTS idx_news_articles_published ON news_articles(published_at);

-- Job runs
CREATE INDEX IF NOT EXISTS idx_job_runs_status ON job_runs(status);
CREATE INDEX IF NOT EXISTS idx_job_runs_started_at ON job_runs(started_at DESC);

-- Job run stages
CREATE INDEX IF NOT EXISTS idx_job_run_stages_run ON job_run_stages(run_id);
CREATE INDEX IF NOT EXISTS idx_job_run_stages_symbol_stage ON job_run_stages(symbol, stage_name);
CREATE INDEX IF NOT EXISTS idx_job_run_stages_status ON job_run_stages(status);

-- Paper trading orders indexes
CREATE INDEX IF NOT EXISTS idx_pt_orders_symbol ON paper_trading_orders(symbol);
CREATE INDEX IF NOT EXISTS idx_pt_orders_status ON paper_trading_orders(status);
CREATE INDEX IF NOT EXISTS idx_pt_orders_created_at ON paper_trading_orders(created_at);

-- Paper trading portfolio snapshots
CREATE INDEX IF NOT EXISTS idx_pt_snapshots_time ON paper_trading_portfolio_snapshots(snapshot_time);

-- paper_trading_positions and paper_trading_closed_positions are dropped by V21 migration.
-- They are intentionally excluded from this consolidated schema.

-- ============================================================================
-- Foreign Key Constraints
-- ============================================================================

ALTER TABLE signals ADD CONSTRAINT fk_signals_stock FOREIGN KEY (symbol) REFERENCES stocks(symbol) ON DELETE CASCADE;
ALTER TABLE positions ADD CONSTRAINT fk_positions_stock FOREIGN KEY (symbol) REFERENCES stocks(symbol) ON DELETE CASCADE;
ALTER TABLE trades ADD CONSTRAINT fk_trades_stock FOREIGN KEY (symbol) REFERENCES stocks(symbol) ON DELETE CASCADE;
ALTER TABLE trades ADD CONSTRAINT fk_trades_position FOREIGN KEY (position_id) REFERENCES positions(id) ON DELETE SET NULL;

-- ============================================================================
-- Seeds
-- ============================================================================

-- Watchlist: active universe (HDFC is retained as an inactive historical row)
INSERT INTO watchlist (symbol, name, exchange) VALUES
    ('RELIANCE', 'Reliance Industries Limited', 'NSE'),
    ('TCS', 'Tata Consultancy Services Limited', 'NSE'),
    ('INFY', 'Infosys Limited', 'NSE'),
    ('HDFCBANK', 'HDFC Bank Limited', 'NSE'),
    ('ICICIBANK', 'ICICI Bank Limited', 'NSE'),
    ('HDFC', 'Housing Development Finance Corporation Limited', 'NSE', FALSE),
    ('SBIN', 'State Bank of India', 'NSE'),
    ('BHARTIARTL', 'Bharti Airtel Limited', 'NSE'),
    ('ITC', 'ITC Limited', 'NSE'),
    ('KOTAKBANK', 'Kotak Mahindra Bank Limited', 'NSE'),
    ('LT', 'Larsen & Toubro Limited', 'NSE'),
    ('AXISBANK', 'Axis Bank Limited', 'NSE'),
    ('MARUTI', 'Maruti Suzuki India Limited', 'NSE'),
    ('SUNPHARMA', 'Sun Pharmaceutical Industries Limited', 'NSE'),
    ('WIPRO', 'Wipro Limited', 'NSE')
ON CONFLICT (symbol) DO NOTHING;

-- App settings defaults (V9)
INSERT INTO app_settings (key, value) VALUES
    ('llm.vllm.base_url', 'https://u425-84cf-d540ae09.singapore-b.gpuhub.com:8443/v1'),
    ('llm.vllm.model', 'Qwen3-30B-AWQ'),
    ('llm.pdf.base_url', ''),
    ('llm.pdf.model', 'gemma-4-E2B'),
    ('discord.webhook.url', ''),
    ('discord.webhook.enabled', 'false')
ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value;

-- Kill switch default (V10)
INSERT INTO kill_switch (id, active, enabled_at, reason, created_at, updated_at)
VALUES (1, FALSE, NULL, NULL, NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- NSE holidays FY2026-27 (V14)
INSERT INTO nse_holidays (holiday_date, occasion, holiday_type) VALUES
-- 2026
('2026-01-26', 'Republic Day', 'FULL'),
('2026-03-17', 'Holi', 'FULL'),
('2026-03-30', 'Good Friday', 'FULL'),
('2026-04-02', 'Mahavir Jayanti', 'FULL'),
('2026-04-09', 'Eid ul-Adha', 'FULL'),
('2026-04-06', 'Eid ul-Fitr', 'FULL'),
('2026-05-01', 'Maharashtra Day', 'FULL'),
('2026-06-05', 'Shri Krishna Janmashtami', 'FULL'),
('2026-06-20', 'Muharram', 'FULL'),
('2026-07-15', 'Ganesh Chaturthi', 'FULL'),
('2026-08-14', 'Veer Savarkar Jayanti', 'PARTIAL'),
('2026-08-17', 'Id-e-Milad', 'FULL'),
('2026-10-02', 'Gandhi Jayanti', 'FULL'),
('2026-10-20', 'Diwali (Lakshmi Pujan)', 'FULL'),
('2026-10-21', 'Diwali Padva / Fair Monday', 'PARTIAL'),
('2026-11-05', 'Guru Nanak Jayanti', 'FULL'),
('2026-12-25', 'Christmas', 'FULL'),
-- 2027
('2027-01-26', 'Republic Day', 'FULL'),
('2027-03-29', 'Good Friday', 'FULL'),
('2027-03-22', 'Holi', 'FULL'),
('2027-01-01', 'New Year', 'FULL'),
('2027-02-26', 'Muharram', 'FULL'),
('2027-03-12', 'Maha Shivaratri', 'FULL'),
('2027-03-20', 'Eid ul-Fitr', 'FULL')
ON CONFLICT (holiday_date) DO UPDATE SET
    occasion = EXCLUDED.occasion,
    holiday_type = EXCLUDED.holiday_type;

-- Paper trading portfolio default (V13)
INSERT INTO paper_trading_portfolio (id, portfolio_id, initial_capital, current_capital)
VALUES (1, 'default', 10000000, 10000000)
ON CONFLICT (id) DO NOTHING;

-- ============================================================================
-- Data migration notes (for existing deployments only — no-ops on fresh DB)
-- ============================================================================

-- V21: Merge paper_trading_positions into positions table
-- INSERT INTO positions (broker_type, symbol, entry_price, entry_date, quantity,
--     stop_loss, target, status, entry_reason, current_price,
--     position_id, exchange, direction, average_price,
--     unrealized_pnl, realized_pnl, entry_time, exit_time, exit_reason)
-- SELECT 'PAPER', p.symbol, p.entry_price, DATE(p.entry_time), p.quantity,
--     p.stop_loss, p.target_price, COALESCE(p.status,'OPEN'), p.entry_reason,
--     p.current_price, p.position_id, 'NSE', COALESCE(p.direction,'LONG'),
--     COALESCE(p.average_price, p.entry_price),
--     COALESCE(p.unrealized_pnl, p.pnl, 0),
--     COALESCE(p.realized_pnl, 0), p.entry_time, p.exit_time, p.exit_reason
-- FROM paper_trading_positions p
-- WHERE NOT EXISTS (SELECT 1 FROM positions pp WHERE pp.position_id = p.position_id);

-- V21: Merge paper_trading_closed_positions into positions table
-- INSERT INTO positions (broker_type, symbol, entry_price, entry_date, quantity,
--     stop_loss, target, status, entry_reason, current_price,
--     position_id, exchange, direction, average_price,
--     unrealized_pnl, realized_pnl, entry_time, exit_time, exit_reason)
-- SELECT 'PAPER', c.symbol, c.entry_price, DATE(c.entry_time), c.quantity,
--     NULL, NULL, COALESCE(c.status,'CLOSED'), c.entry_reason,
--     c.exit_price, c.position_id, 'NSE', COALESCE(c.direction,'LONG'),
--     NULL,
--     COALESCE(c.realized_pnl, c.pnl, 0),
--     COALESCE(c.realized_pnl, c.pnl, 0),
--     c.entry_time, c.exit_time, c.exit_reason
-- FROM paper_trading_closed_positions c
-- WHERE NOT EXISTS (SELECT 1 FROM positions pp WHERE pp.position_id = c.position_id);
