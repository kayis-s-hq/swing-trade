-- Widens paper_trading_portfolio.portfolio_id from VARCHAR(32) to VARCHAR(40) to match
-- strategy_config.variant_id's length (V49__create_strategy_config.sql), and gives it a
-- uniqueness guarantee since each strategy variant now gets its own portfolio row (this
-- table is no longer a fixed "default" singleton). Also adds a portfolio_id column to
-- paper_trading_orders and positions so per-variant orders/positions can be attributed
-- and queried back to the variant that produced them.

ALTER TABLE paper_trading_portfolio ALTER COLUMN portfolio_id TYPE VARCHAR(40);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uq_paper_trading_portfolio_portfolio_id'
    ) THEN
        ALTER TABLE paper_trading_portfolio
            ADD CONSTRAINT uq_paper_trading_portfolio_portfolio_id UNIQUE (portfolio_id);
    END IF;
END $$;

ALTER TABLE paper_trading_orders ADD COLUMN IF NOT EXISTS portfolio_id VARCHAR(40);
CREATE INDEX IF NOT EXISTS idx_paper_trading_orders_portfolio_id ON paper_trading_orders (portfolio_id);

ALTER TABLE positions ADD COLUMN IF NOT EXISTS portfolio_id VARCHAR(40);
CREATE INDEX IF NOT EXISTS idx_positions_portfolio_id ON positions (portfolio_id);
