DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='signals' AND column_name='strategy') THEN
        ALTER TABLE signals ADD COLUMN strategy VARCHAR(30) NOT NULL DEFAULT 'DEFAULT';
    END IF;
END $$;

DROP INDEX IF EXISTS idx_signals_symbol_date_strategy;
CREATE INDEX idx_signals_symbol_date_strategy ON signals(symbol, date, strategy);
