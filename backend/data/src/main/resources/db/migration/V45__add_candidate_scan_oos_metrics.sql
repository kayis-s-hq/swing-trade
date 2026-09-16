ALTER TABLE candidate_scan_results
    ADD COLUMN oos_start_date DATE,
    ADD COLUMN oos_end_date DATE,
    ADD COLUMN oos_total_trades INTEGER,
    ADD COLUMN oos_win_rate DOUBLE PRECISION,
    ADD COLUMN oos_total_return DOUBLE PRECISION,
    ADD COLUMN oos_max_drawdown_pct DOUBLE PRECISION;
