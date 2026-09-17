ALTER TABLE positions
    ADD COLUMN IF NOT EXISTS signal_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_positions_signal_id ON positions(signal_id);
