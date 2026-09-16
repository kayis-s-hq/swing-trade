ALTER TABLE paper_trading_orders
    ADD COLUMN IF NOT EXISTS signal_id VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_pt_orders_signal_id ON paper_trading_orders(signal_id);
