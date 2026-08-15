-- Trade labels for exit reason tracking
CREATE TABLE IF NOT EXISTS trade_labels (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trade_id BIGINT NOT NULL,
    position_id BIGINT NOT NULL,
    exit_reason VARCHAR(20) NOT NULL,
    exit_confidence NUMERIC(15,4),
    notes TEXT,
    labelled_by VARCHAR(100),
    labelled_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_trade_labels_trade_id ON trade_labels(trade_id);
CREATE INDEX IF NOT EXISTS idx_trade_labels_position_id ON trade_labels(position_id);

DROP TRIGGER IF EXISTS update_trade_labels_updated_at ON trade_labels;
CREATE TRIGGER update_trade_labels_updated_at BEFORE UPDATE ON trade_labels
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
