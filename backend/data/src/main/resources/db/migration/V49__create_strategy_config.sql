CREATE TABLE IF NOT EXISTS strategy_config (
    id BIGSERIAL PRIMARY KEY,
    variant_id VARCHAR(40) NOT NULL,
    version INTEGER NOT NULL,
    strategy_type VARCHAR(30) NOT NULL,
    params JSONB NOT NULL,
    overlays JSONB NOT NULL DEFAULT '{}',
    params_hash CHAR(64) NOT NULL,
    mode VARCHAR(16) NOT NULL DEFAULT 'OFF',
    paper_capital NUMERIC(15,2) NOT NULL DEFAULT 500000,
    is_current BOOLEAN NOT NULL DEFAULT TRUE,
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_strategy_config_variant_version UNIQUE (variant_id, version),
    CONSTRAINT ck_strategy_config_version_positive CHECK (version > 0),
    CONSTRAINT ck_strategy_config_mode CHECK (mode IN ('OFF', 'BACKTEST_ONLY', 'SHADOW', 'CHAMPION')),
    CONSTRAINT ck_strategy_config_paper_capital_nonnegative CHECK (paper_capital >= 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_strategy_config_current
    ON strategy_config (variant_id) WHERE is_current;

CREATE UNIQUE INDEX IF NOT EXISTS ux_strategy_config_champion
    ON strategy_config ((1)) WHERE is_current AND mode = 'CHAMPION';
