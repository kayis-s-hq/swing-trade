-- Honest stage status (plan 2026-09-21 Step 3). All additions are nullable/widening; no backfill.

-- Structured per-stage details (JSON text): source, warnings, per-strategy outcomes.
ALTER TABLE job_run_stages
    ADD COLUMN IF NOT EXISTS details TEXT;

-- COMPLETED_WITH_WARNINGS (23 chars) does not fit the original VARCHAR(16).
ALTER TABLE job_runs
    ALTER COLUMN status TYPE VARCHAR(32);

-- Params-hash provenance for configured-strategy signals (nullable: legacy rows have none).
ALTER TABLE signals
    ADD COLUMN IF NOT EXISTS params_hash VARCHAR(64);
