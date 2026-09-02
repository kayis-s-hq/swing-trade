CREATE TABLE IF NOT EXISTS candidate_scan_runs (
    id BIGSERIAL PRIMARY KEY,
    run_id UUID NOT NULL UNIQUE,
    status VARCHAR(16) NOT NULL,
    total_symbols INTEGER NOT NULL DEFAULT 0,
    completed_symbols INTEGER NOT NULL DEFAULT 0,
    failed_symbols INTEGER NOT NULL DEFAULT 0,
    qualified_symbols INTEGER NOT NULL DEFAULT 0,
    started_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    error_message TEXT,
    version INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS candidate_scan_results (
    id BIGSERIAL PRIMARY KEY,
    run_id UUID NOT NULL,
    symbol VARCHAR(32) NOT NULL,
    data_status VARCHAR(24) NOT NULL,
    candle_count INTEGER NOT NULL DEFAULT 0,
    signal_type VARCHAR(16),
    total_trades INTEGER,
    win_rate DOUBLE PRECISION,
    total_return DOUBLE PRECISION,
    max_drawdown_pct DOUBLE PRECISION,
    qualified BOOLEAN NOT NULL DEFAULT FALSE,
    activated BOOLEAN NOT NULL DEFAULT FALSE,
    reason TEXT,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT fk_candidate_scan_result_run FOREIGN KEY (run_id) REFERENCES candidate_scan_runs(run_id)
);

CREATE INDEX IF NOT EXISTS ix_candidate_scan_results_run_id ON candidate_scan_results(run_id);
CREATE INDEX IF NOT EXISTS ix_candidate_scan_results_qualified ON candidate_scan_results(run_id, qualified);
