CREATE TABLE IF NOT EXISTS fyers_symbol_master (
    id             BIGSERIAL PRIMARY KEY,
    fy_token       VARCHAR(32)  NOT NULL UNIQUE,
    fyers_symbol   VARCHAR(64)  NOT NULL,          -- e.g. NSE:RELIANCE-EQ
    trading_symbol VARCHAR(32)  NOT NULL,          -- e.g. RELIANCE
    name           TEXT,
    exchange       VARCHAR(16)  NOT NULL DEFAULT 'NSE',
    segment        VARCHAR(16),
    lot_size       INTEGER,
    tick_size      NUMERIC(10,4),
    isin           VARCHAR(13),
    updated_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_fyers_symbol_master_symbol ON fyers_symbol_master (fyers_symbol);
CREATE INDEX IF NOT EXISTS ix_fyers_symbol_master_trading ON fyers_symbol_master (trading_symbol);
CREATE INDEX IF NOT EXISTS ix_fyers_symbol_master_name ON fyers_symbol_master (LOWER(name) text_pattern_ops);
