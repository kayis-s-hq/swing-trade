-- V11__enhance_sentiment_accuracy.sql
-- Enhance sentiment_accuracy table with doc-spec schema for return-based evaluation

-- Drop old position-based accuracy table and recreate with return-based schema
DROP TABLE IF EXISTS sentiment_accuracy CASCADE;

CREATE TABLE sentiment_accuracy (
    id            BIGSERIAL PRIMARY KEY,
    symbol        VARCHAR(20) NOT NULL,
    analysis_date DATE NOT NULL,
    llm_score     VARCHAR(20) NOT NULL,           -- POSITIVE, NEUTRAL, NEGATIVE
    llm_confidence REAL NOT NULL,                 -- LLM's own confidence (0.0-1.0)
    numeric_score REAL NOT NULL,                  -- Mapped numeric score (-1.0 to +1.0)

    -- Ground truth
    actual_return_1d  DECIMAL(10,6),
    actual_return_5d  DECIMAL(10,6),
    actual_return_21d DECIMAL(10,6),
    ground_truth_label VARCHAR(10),                -- UP, DOWN, FLAT

    was_correct       BOOLEAN,
    pnl_pct           DECIMAL(10,6),

    -- Context
    market_regime     VARCHAR(10),                -- BULL, BEAR, NEUTRAL
    prompt_hash       VARCHAR(64),                -- Hash of prompt used
    model_version     VARCHAR(50),                -- LLM model version
    composite_score   INT,                        -- Composite analysis score (-100 to +100)
    composite_signal  VARCHAR(10),                -- BUY, SELL, HOLD
    composite_id      BIGINT,                     -- Composite analysis FK

    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    evaluated_at      TIMESTAMPTZ                 -- When ground truth was computed
);

-- Hypertable (only if TimescaleDB is installed)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_catalog.pg_extension WHERE extname = 'timescaledb') THEN
        PERFORM create_hypertable('sentiment_accuracy', 'analysis_date', if_not_exists => TRUE);
    END IF;
END
$$;

-- Indexes
CREATE INDEX IF NOT EXISTS idx_sentiment_accuracy_symbol_date ON sentiment_accuracy (symbol, analysis_date);
CREATE INDEX IF NOT EXISTS idx_sentiment_accuracy_label ON sentiment_accuracy (ground_truth_label);
CREATE INDEX IF NOT EXISTS idx_sentiment_accuracy_regime ON sentiment_accuracy (market_regime);
CREATE INDEX IF NOT EXISTS idx_sentiment_accuracy_evaluated ON sentiment_accuracy (evaluated_at) WHERE evaluated_at IS NOT NULL;

-- Continuous aggregate (only if TimescaleDB is installed)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_catalog.pg_extension WHERE extname = 'timescaledb') THEN
        CREATE MATERIALIZED VIEW IF NOT EXISTS sentiment_accuracy_daily
        WITH (timescaledb.continuous) AS
        SELECT
            time_bucket('1 day', analysis_date) AS bucket,
            llm_score,
            COUNT(*) AS total_signals,
            COUNT(*) FILTER (WHERE was_correct = true) AS correct_count,
            AVG(llm_confidence) AS avg_confidence,
            AVG(actual_return_5d) AS avg_return_5d
        FROM sentiment_accuracy
        GROUP BY bucket, llm_score;

        PERFORM refresh_continuous_aggregate('sentiment_accuracy_daily', NULL, NULL);

        PERFORM add_retention_policy('sentiment_accuracy', INTERVAL '1 year', INTERVAL '7 days');
    END IF;
END
$$;