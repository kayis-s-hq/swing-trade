ALTER TABLE paper_trading_portfolio_snapshots
    ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;