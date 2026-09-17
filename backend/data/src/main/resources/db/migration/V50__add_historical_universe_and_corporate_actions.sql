CREATE TABLE IF NOT EXISTS universe_snapshots (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(20) NOT NULL,
    snapshot_date DATE NOT NULL,
    exchange VARCHAR(12),
    isin VARCHAR(12),
    included BOOLEAN NOT NULL,
    source VARCHAR(80) NOT NULL,
    captured_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_universe_snapshot_symbol_date UNIQUE (symbol, snapshot_date)
);
CREATE INDEX IF NOT EXISTS idx_universe_snapshots_date ON universe_snapshots (snapshot_date, symbol);

CREATE TABLE IF NOT EXISTS corporate_actions (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(20) NOT NULL,
    effective_date DATE NOT NULL,
    action_type VARCHAR(40) NOT NULL,
    adjustment_factor NUMERIC(20,8),
    cash_amount NUMERIC(20,8),
    source VARCHAR(80) NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_corporate_action_symbol_date_type UNIQUE (symbol, effective_date, action_type),
    CONSTRAINT ck_corporate_action_value CHECK (adjustment_factor IS NOT NULL OR cash_amount IS NOT NULL),
    CONSTRAINT ck_corporate_action_factor_positive CHECK (adjustment_factor IS NULL OR adjustment_factor > 0),
    CONSTRAINT ck_corporate_action_cash_nonnegative CHECK (cash_amount IS NULL OR cash_amount >= 0)
);
CREATE INDEX IF NOT EXISTS idx_corporate_actions_symbol_date ON corporate_actions (symbol, effective_date);
