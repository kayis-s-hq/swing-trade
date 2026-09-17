ALTER TABLE sentiment_accuracy
    ADD COLUMN IF NOT EXISTS sentiment_source VARCHAR(20);
