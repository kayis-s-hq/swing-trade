ALTER TABLE candidate_scan_results
    ADD COLUMN IF NOT EXISTS oos_start_date DATE,
    ADD COLUMN IF NOT EXISTS oos_end_date DATE,
    ADD COLUMN IF NOT EXISTS oos_total_trades INTEGER,
    ADD COLUMN IF NOT EXISTS oos_win_rate DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS oos_total_return DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS oos_max_drawdown_pct DOUBLE PRECISION;
