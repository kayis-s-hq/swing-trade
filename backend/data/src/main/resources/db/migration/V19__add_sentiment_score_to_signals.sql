DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='signals' AND column_name='sentiment_score') THEN
        ALTER TABLE signals ADD COLUMN sentiment_score VARCHAR(12);
    END IF;
END $$;