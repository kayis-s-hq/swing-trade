-- V51: Open/closed shadow-variant position tracking (plan §7.4 gap-fill).
--
-- SHADOW variants' BUY trades (executeVariantBuy, V50/commit caa37b63) never exited, so the
-- champion/challenger PromotionEligibilityChecker had no closed trades to score. This table
-- persists per-portfolio open-position state (entry price/stop/target/high-water-mark) across
-- process restarts so the daily job pipeline can evaluate exits and, once closed, gives a
-- direct query surface for closed round-trip trades per portfolio.
CREATE TABLE paper_shadow_positions (
    id BIGSERIAL PRIMARY KEY,
    version INTEGER NOT NULL DEFAULT 0,
    portfolio_id VARCHAR(32) NOT NULL,
    symbol VARCHAR(16) NOT NULL,
    entry_date DATE NOT NULL,
    entry_price NUMERIC(15,2),
    stop_loss NUMERIC(15,2),
    target NUMERIC(15,2),
    quantity INTEGER NOT NULL,
    high_water_mark NUMERIC(15,2),
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    exit_date DATE,
    exit_price NUMERIC(15,2),
    exit_reason VARCHAR(32),
    pnl NUMERIC(15,2),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

-- Exit evaluation looks up the (at most one expected) OPEN row per portfolio+symbol every run.
CREATE INDEX idx_shadow_positions_open ON paper_shadow_positions (portfolio_id, symbol, status);

-- Closed-trade query for a portfolio, newest first.
CREATE INDEX idx_shadow_positions_closed ON paper_shadow_positions (portfolio_id, status, exit_date);
