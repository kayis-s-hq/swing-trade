-- Job runs (one row per orchestration run)
CREATE TABLE IF NOT EXISTS job_runs (
    id              BIGSERIAL PRIMARY KEY,
    run_id          UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    trigger_type    VARCHAR(16) NOT NULL DEFAULT 'MANUAL',  -- MANUAL, SCHEDULED
    status          VARCHAR(16) NOT NULL DEFAULT 'RUNNING', -- RUNNING, COMPLETED, FAILED, CANCELLED
    started_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at    TIMESTAMP,
    symbols_count   INTEGER NOT NULL DEFAULT 0,
    completed_count INTEGER NOT NULL DEFAULT 0,
    failed_count    INTEGER NOT NULL DEFAULT 0,
    error_message   TEXT
);

CREATE INDEX IF NOT EXISTS idx_job_runs_status ON job_runs(status);
CREATE INDEX IF NOT EXISTS idx_job_runs_started_at ON job_runs(started_at DESC);

-- Job run stages (one row per symbol per stage)
CREATE TABLE IF NOT EXISTS job_run_stages (
    id              BIGSERIAL PRIMARY KEY,
    run_id          UUID NOT NULL REFERENCES job_runs(run_id) ON DELETE CASCADE,
    symbol          VARCHAR(16) NOT NULL,
    stage_name      VARCHAR(32) NOT NULL,  -- DATA_FETCH, NEWS, SENTIMENT, SIGNAL, BACKTEST, PAPER_TRADE
    status          VARCHAR(16) NOT NULL DEFAULT 'PENDING', -- PENDING, RUNNING, COMPLETED, SKIPPED, ERROR
    started_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at    TIMESTAMP,
    duration_ms     BIGINT,
    error_message   TEXT,
    log_details     TEXT,          -- JSON string with sub-step details
    result_summary  TEXT           -- Brief result (e.g., "BUY signal generated, confidence 85%")
);

CREATE INDEX IF NOT EXISTS idx_job_run_stages_run ON job_run_stages(run_id);
CREATE INDEX IF NOT EXISTS idx_job_run_stages_symbol_stage ON job_run_stages(symbol, stage_name);
CREATE INDEX IF NOT EXISTS idx_job_run_stages_status ON job_run_stages(status);