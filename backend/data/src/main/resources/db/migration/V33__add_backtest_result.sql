CREATE TABLE IF NOT EXISTS backtest_result (
    id BIGSERIAL PRIMARY KEY, symbol VARCHAR(20) NOT NULL, run_date DATE NOT NULL,
    total_trades INTEGER NOT NULL, winning_trades INTEGER NOT NULL, losing_trades INTEGER NOT NULL,
    win_rate DOUBLE PRECISION NOT NULL, avg_gain_pct DOUBLE PRECISION NOT NULL,
    avg_loss_pct DOUBLE PRECISION NOT NULL, max_drawdown_pct DOUBLE PRECISION NOT NULL,
    sharpe_ratio DOUBLE PRECISION NOT NULL, total_return DOUBLE PRECISION NOT NULL,
    expectancy DOUBLE PRECISION NOT NULL, profit_factor DOUBLE PRECISION NOT NULL,
    has_enough_data BOOLEAN NOT NULL DEFAULT TRUE, created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_backtest_result_symbol_date UNIQUE (symbol, run_date)
);
CREATE INDEX IF NOT EXISTS idx_backtest_result_symbol_date ON backtest_result(symbol, run_date);
