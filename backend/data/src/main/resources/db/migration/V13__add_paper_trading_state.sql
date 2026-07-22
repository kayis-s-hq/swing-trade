-- Paper trading portfolio state (singleton row)
CREATE TABLE IF NOT EXISTS paper_trading_portfolio (
    id                       BIGSERIAL PRIMARY KEY,
    portfolio_id             VARCHAR(32) NOT NULL DEFAULT 'default',
    initial_capital          NUMERIC(15,2) NOT NULL DEFAULT 10000000,
    current_capital          NUMERIC(15,2) NOT NULL DEFAULT 10000000,
    total_realized_pnl       NUMERIC(15,2) NOT NULL DEFAULT 0,
    total_unrealized_pnl     NUMERIC(15,2) NOT NULL DEFAULT 0,
    open_position_count      INTEGER NOT NULL DEFAULT 0,
    max_concurrent_positions INTEGER NOT NULL DEFAULT 5,
    max_capital_per_position_pct NUMERIC(5,2) NOT NULL DEFAULT 20,
    commission_rate          NUMERIC(6,4) NOT NULL DEFAULT 0.05,
    updated_at               TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO paper_trading_portfolio (id, portfolio_id, initial_capital, current_capital)
VALUES (1, 'default', 10000000, 10000000)
ON CONFLICT (id) DO NOTHING;

-- Open positions
CREATE TABLE IF NOT EXISTS paper_trading_positions (
    id               BIGSERIAL PRIMARY KEY,
    position_id      VARCHAR(32) NOT NULL UNIQUE,
    symbol           VARCHAR(16) NOT NULL,
    direction        VARCHAR(10) NOT NULL DEFAULT 'LONG',
    quantity         INTEGER NOT NULL,
    entry_price      NUMERIC(15,2) NOT NULL,
    average_price    NUMERIC(15,2),
    current_price    NUMERIC(15,2),
    stop_loss        NUMERIC(15,2),
    target_price     NUMERIC(15,2),
    pnl              NUMERIC(15,2) NOT NULL DEFAULT 0,
    unrealized_pnl   NUMERIC(15,2) NOT NULL DEFAULT 0,
    realized_pnl     NUMERIC(15,2) NOT NULL DEFAULT 0,
    status           VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    entry_reason     TEXT,
    entry_time       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    exit_time        TIMESTAMP,
    exit_reason      VARCHAR(64),
    last_updated     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_pt_positions_symbol ON paper_trading_positions(symbol, status);
CREATE INDEX IF NOT EXISTS idx_pt_positions_status ON paper_trading_positions(status);

-- Closed positions (history)
CREATE TABLE IF NOT EXISTS paper_trading_closed_positions (
    id               BIGSERIAL PRIMARY KEY,
    position_id      VARCHAR(32) NOT NULL,
    symbol           VARCHAR(16) NOT NULL,
    direction        VARCHAR(10) NOT NULL DEFAULT 'LONG',
    quantity         INTEGER NOT NULL,
    entry_price      NUMERIC(15,2) NOT NULL,
    exit_price       NUMERIC(15,2) NOT NULL,
    stop_loss        NUMERIC(15,2),
    target_price     NUMERIC(15,2),
    pnl              NUMERIC(15,2) NOT NULL,
    realized_pnl     NUMERIC(15,2) NOT NULL,
    status           VARCHAR(16) NOT NULL,
    entry_reason     TEXT,
    entry_time       TIMESTAMP NOT NULL,
    exit_time        TIMESTAMP NOT NULL,
    exit_reason      VARCHAR(64),
    created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_pt_closed_positions_symbol ON paper_trading_closed_positions(symbol);
CREATE INDEX IF NOT EXISTS idx_pt_closed_positions_exit_time ON paper_trading_closed_positions(exit_time);

-- Orders
CREATE TABLE IF NOT EXISTS paper_trading_orders (
    id            BIGSERIAL PRIMARY KEY,
    order_id      VARCHAR(32) NOT NULL UNIQUE,
    symbol        VARCHAR(16) NOT NULL,
    order_type    VARCHAR(16) NOT NULL DEFAULT 'MARKET',
    direction     VARCHAR(10) NOT NULL,
    quantity      INTEGER NOT NULL,
    price         NUMERIC(15,2) NOT NULL,
    limit_price   NUMERIC(15,2),
    stop_price    NUMERIC(15,2),
    status        VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    commission    NUMERIC(15,4) NOT NULL DEFAULT 0,
    executed_at   TIMESTAMP,
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_pt_orders_symbol ON paper_trading_orders(symbol);
CREATE INDEX IF NOT EXISTS idx_pt_orders_status ON paper_trading_orders(status);
CREATE INDEX IF NOT EXISTS idx_pt_orders_created_at ON paper_trading_orders(created_at);

-- Portfolio history (snapshots for P&L charts)
CREATE TABLE IF NOT EXISTS paper_trading_portfolio_snapshots (
    id              BIGSERIAL PRIMARY KEY,
    snapshot_time   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    total_value     NUMERIC(15,2) NOT NULL,
    cash_balance    NUMERIC(15,2) NOT NULL,
    market_value    NUMERIC(15,2) NOT NULL DEFAULT 0,
    total_pnl       NUMERIC(15,2) NOT NULL DEFAULT 0,
    return_pct      NUMERIC(8,4) NOT NULL DEFAULT 0,
    open_positions  INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_pt_snapshots_time ON paper_trading_portfolio_snapshots(snapshot_time);