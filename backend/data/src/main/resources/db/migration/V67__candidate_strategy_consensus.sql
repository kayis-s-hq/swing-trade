ALTER TABLE candidate_scan_results
    ADD COLUMN IF NOT EXISTS strategy_buy_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS strategy_evaluation_count INTEGER NOT NULL DEFAULT 0;
