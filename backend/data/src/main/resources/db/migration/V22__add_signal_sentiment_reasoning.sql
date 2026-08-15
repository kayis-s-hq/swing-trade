-- V22__add_signal_sentiment_reasoning.sql

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='signals' AND column_name='sentiment_reasoning') THEN
        ALTER TABLE signals ADD COLUMN sentiment_reasoning TEXT;
    END IF;
END $$;