ALTER TABLE sentiment_accuracy
    ADD COLUMN IF NOT EXISTS excess_return_1d DECIMAL(10,6),
    ADD COLUMN IF NOT EXISTS excess_return_5d DECIMAL(10,6),
    ADD COLUMN IF NOT EXISTS excess_return_21d DECIMAL(10,6),
    ADD COLUMN IF NOT EXISTS ground_truth_basis VARCHAR(20);
