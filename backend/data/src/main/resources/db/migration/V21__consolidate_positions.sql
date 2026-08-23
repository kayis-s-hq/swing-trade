ALTER TABLE positions ADD COLUMN IF NOT EXISTS broker_type VARCHAR(10) DEFAULT 'PAPER';
ALTER TABLE positions ADD COLUMN IF NOT EXISTS position_id VARCHAR(36);
ALTER TABLE positions ADD COLUMN IF NOT EXISTS exchange VARCHAR(10) DEFAULT 'NSE';
ALTER TABLE positions ADD COLUMN IF NOT EXISTS direction VARCHAR(10) DEFAULT 'LONG';

-- Merge paper_trading_positions (open positions) — INSERT first, then UPDATE NULLs
INSERT INTO positions (broker_type, symbol, entry_price, entry_date, quantity,
    stop_loss, target, status, entry_reason, current_price,
    position_id, exchange, direction, average_price,
    unrealized_pnl, realized_pnl, entry_time, exit_time, exit_reason)
SELECT 'PAPER', p.symbol, p.entry_price, DATE(p.entry_time), p.quantity,
    p.stop_loss, p.target_price, COALESCE(p.status,'OPEN'), p.entry_reason,
    p.current_price, p.position_id, 'NSE', COALESCE(p.direction,'LONG'),
    COALESCE(p.average_price, p.entry_price),
    COALESCE(p.unrealized_pnl, p.pnl, 0),
    COALESCE(p.realized_pnl, 0), p.entry_time, p.exit_time, p.exit_reason
FROM paper_trading_positions p
WHERE NOT EXISTS (SELECT 1 FROM positions pp WHERE pp.position_id = p.position_id);

-- Merge paper_trading_closed_positions (closed positions)
INSERT INTO positions (broker_type, symbol, entry_price, entry_date, quantity,
    stop_loss, target, status, entry_reason, current_price,
    position_id, exchange, direction, average_price,
    unrealized_pnl, realized_pnl, entry_time, exit_time, exit_reason)
SELECT 'PAPER', c.symbol, c.entry_price, DATE(c.entry_time), c.quantity,
    NULL, NULL, COALESCE(c.status,'CLOSED'), c.entry_reason,
    c.exit_price, c.position_id, 'NSE', COALESCE(c.direction,'LONG'),
    NULL,
    COALESCE(c.realized_pnl, c.pnl, 0),
    COALESCE(c.realized_pnl, c.pnl, 0),
    c.entry_time, c.exit_time, c.exit_reason
FROM paper_trading_closed_positions c
WHERE NOT EXISTS (SELECT 1 FROM positions pp WHERE pp.position_id = c.position_id);

-- Fill NULL broker_types AFTER merges (was before — caused UPDATE-before-INSERT bug)
UPDATE positions SET broker_type = 'PAPER' WHERE broker_type IS NULL;

DROP TABLE IF EXISTS paper_trading_closed_positions;
DROP TABLE IF EXISTS paper_trading_positions;