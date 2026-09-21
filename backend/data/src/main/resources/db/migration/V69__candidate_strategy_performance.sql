ALTER TABLE candidate_scan_strategy_outcomes
    ADD COLUMN IF NOT EXISTS backtest_total_trades INTEGER,
    ADD COLUMN IF NOT EXISTS backtest_win_rate DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS backtest_total_return DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS oos_total_trades INTEGER,
    ADD COLUMN IF NOT EXISTS oos_win_rate DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS oos_total_return DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS performance_status VARCHAR(16) NOT NULL DEFAULT 'NOT_RUN';
