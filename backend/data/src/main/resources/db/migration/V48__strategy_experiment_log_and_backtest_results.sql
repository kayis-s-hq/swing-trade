-- V48: Overfitting controls + compare-endpoint persistence (plan §6.4/§6.8).
--
-- strategy_experiment_log: append-only record of every backtest/walk-forward run for a variant
-- version. Never updated - one row per run. This is the source for the Deflated Sharpe Ratio's
-- N = "distinct params_hash tested per strategy type" (DeflatedSharpeRatio, plan §6.4, after
-- Bailey & Lopez de Prado 2014).
CREATE TABLE strategy_experiment_log (
    id               BIGSERIAL PRIMARY KEY,
    variant_id       VARCHAR(40)  NOT NULL,
    version          INTEGER      NOT NULL,
    strategy_type    VARCHAR(30)  NOT NULL,
    params_hash      CHAR(64)     NOT NULL,
    window_start     DATE         NOT NULL,
    window_end       DATE         NOT NULL,
    metrics          JSONB        NOT NULL,
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_strategy_experiment_log_type ON strategy_experiment_log(strategy_type);
CREATE INDEX idx_strategy_experiment_log_variant ON strategy_experiment_log(variant_id, version);

-- strategy_backtest_results: persisted output of POST /api/backtest/compare (plan §6.8), one row
-- per (variant, version, fold) - fold=0 for a plain (non-walk-forward) run, fold=1..N for
-- walk-forward folds. The equity curve is stored as plain JSON (date/equity pairs); the plan's
-- "compressed" suggestion is not implemented in this phase - runs are infrequent, operator-
-- triggered comparisons, not a high-volume table, so gzip wasn't judged worth the complexity yet.
CREATE TABLE strategy_backtest_results (
    id               BIGSERIAL PRIMARY KEY,
    variant_id       VARCHAR(40)  NOT NULL,
    version          INTEGER      NOT NULL,
    fold             INTEGER      NOT NULL,
    window_start     DATE         NOT NULL,
    window_end       DATE         NOT NULL,
    metrics          JSONB        NOT NULL,
    equity_curve     JSONB        NOT NULL,
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_strategy_backtest_results_variant ON strategy_backtest_results(variant_id, version);
