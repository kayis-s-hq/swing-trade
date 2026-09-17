ALTER TABLE signals
    ALTER COLUMN strategy TYPE VARCHAR(40);

ALTER TABLE signals
    ADD COLUMN IF NOT EXISTS strategy_version INTEGER;

UPDATE signals
SET strategy_version = 1
WHERE strategy_version IS NULL;

ALTER TABLE signals
    ALTER COLUMN strategy_version SET DEFAULT 1;

ALTER TABLE signals
    ALTER COLUMN strategy_version SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_signals_strategy_version
    ON signals(strategy, strategy_version);
