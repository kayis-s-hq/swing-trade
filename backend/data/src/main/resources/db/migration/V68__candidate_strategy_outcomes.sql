CREATE TABLE IF NOT EXISTS candidate_scan_strategy_outcomes (
    candidate_scan_result_id BIGINT NOT NULL REFERENCES candidate_scan_results(id) ON DELETE CASCADE,
    variant_id VARCHAR(128) NOT NULL,
    signal_type VARCHAR(16) NOT NULL,
    score NUMERIC(18, 8),
    detail TEXT
);

CREATE INDEX IF NOT EXISTS idx_candidate_scan_strategy_outcomes_result
    ON candidate_scan_strategy_outcomes(candidate_scan_result_id);
