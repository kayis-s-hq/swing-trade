-- Trade labels table for manual exit reason annotation
-- Created for Phase 7 observability requirement REQ-036
-- Allows manual labelling of trade exit reasons for future analysis

CREATE TABLE IF NOT EXISTS trade_labels (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    position_id INTEGER NOT NULL REFERENCES positions(id) ON DELETE CASCADE,
    exit_reason VARCHAR(50) NOT NULL,
    exit_confidence DECIMAL(5,2),
    notes TEXT,
    labelled_by VARCHAR(100),
    labelled_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Index for quick trade lookup
CREATE INDEX IF NOT EXISTS idx_trade_labels_trade_id ON trade_labels(position_id);

-- Index for time-based queries
CREATE INDEX IF NOT EXISTS idx_trade_labels_labelled_at ON trade_labels(labelled_at);

-- Index for exit reason distribution queries
CREATE INDEX IF NOT EXISTS idx_trade_labels_exit_reason ON trade_labels(exit_reason);

-- Comment
COMMENT ON TABLE trade_labels IS 'Manual exit reason annotations for trade outcomes';
COMMENT ON COLUMN trade_labels.exit_reason IS 'Reason for trade exit: STOP_LOSS, TARGET_HIT, TIME_STOP, TREND_BREAK, MANUAL';
COMMENT ON COLUMN trade_labels.exit_confidence IS 'Confidence level in the labelling decision (0-100)';
COMMENT ON COLUMN trade_labels.labelled_by IS 'User identifier who performed the labelling';
