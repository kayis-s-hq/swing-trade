DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='paper_trading_portfolio_snapshots' AND column_name='created_at') THEN
        ALTER TABLE paper_trading_portfolio_snapshots
            ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
    END IF;
END $$;