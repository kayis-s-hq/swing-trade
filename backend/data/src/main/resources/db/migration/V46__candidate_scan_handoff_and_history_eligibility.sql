ALTER TABLE candidate_scan_runs
    ADD COLUMN IF NOT EXISTS orchestration_status VARCHAR(16) NOT NULL DEFAULT 'NOT_REQUIRED',
    ADD COLUMN IF NOT EXISTS orchestration_job_run_id UUID,
    ADD COLUMN IF NOT EXISTS orchestration_error TEXT;

ALTER TABLE job_runs
    ADD COLUMN IF NOT EXISTS candidate_scan_run_id UUID;

CREATE INDEX IF NOT EXISTS ix_job_runs_candidate_scan_run_id ON job_runs(candidate_scan_run_id);

CREATE TABLE IF NOT EXISTS candidate_history_eligibility (
    symbol VARCHAR(32) PRIMARY KEY,
    candle_count INTEGER NOT NULL DEFAULT 0,
    first_available_date DATE,
    last_available_date DATE,
    source_outcome VARCHAR(32) NOT NULL,
    invalid_rows INTEGER NOT NULL DEFAULT 0,
    retry_after DATE,
    error_message TEXT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INTEGER NOT NULL DEFAULT 0
);

ALTER TABLE candidate_scan_results
    ADD COLUMN IF NOT EXISTS source_outcome VARCHAR(32),
    ADD COLUMN IF NOT EXISTS invalid_rows INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS first_available_date DATE,
    ADD COLUMN IF NOT EXISTS last_available_date DATE,
    ADD COLUMN IF NOT EXISTS retry_after DATE;
