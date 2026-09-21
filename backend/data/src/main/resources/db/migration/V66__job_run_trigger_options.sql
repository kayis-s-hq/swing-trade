-- Run scoping provenance (plan 2026-09-21 Step 4): the JSON request body of a scoped manual run
-- (symbols, variantIds, stages, skipLlm, dryRun). Nullable; NULL means a full, unscoped run.
ALTER TABLE job_runs
    ADD COLUMN IF NOT EXISTS trigger_options TEXT;
