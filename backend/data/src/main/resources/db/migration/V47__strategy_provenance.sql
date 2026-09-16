-- V47: Provenance columns for multi-strategy signals/positions/paper-trading (plan §4.2)
--
-- Deploy-ordering note (plan finding F5 / task item 7): this migration's one-time backfill
-- (`UPDATE signals SET strategy='BREAKOUT_STRICT' ... WHERE strategy='DEFAULT'`) only makes
-- sense if new rows stop arriving as literal "DEFAULT" going forward. This migration ships in
-- the SAME change as: SignalEntity.STRATEGY_DEFAULT's value being changed from "DEFAULT" to
-- "BREAKOUT_STRICT" (its default field value, used for every persisted signal today) and
-- SignalPipeline.generatePrimarySignal's hard-coded "DEFAULT" literal being replaced -
-- see backend/data/src/main/java/com/swingtrade/data/entity/SignalEntity.java and
-- backend/api/src/main/java/com/swingtrade/api/service/SignalPipeline.java. Both are updated in
-- this same commit, so this migration is not inert.

ALTER TABLE signals ALTER COLUMN strategy TYPE VARCHAR(40);
ALTER TABLE signals ADD COLUMN strategy_version INTEGER;
ALTER TABLE signals ADD COLUMN strategy_score NUMERIC(5,4);
ALTER TABLE signals ADD COLUMN rule_outcomes JSONB;
ALTER TABLE signals ADD COLUMN gate_outcomes JSONB;

UPDATE signals SET strategy = 'BREAKOUT_STRICT', strategy_version = 1 WHERE strategy = 'DEFAULT';

-- Explicit index the plan's prose calls for but whose SQL snippet omitted (task item 4).
CREATE INDEX idx_signals_strategy_version_date ON signals(strategy, strategy_version, date);

-- No unique constraint exists today on signals around (symbol, date, strategy) - only the
-- non-unique idx_signals_symbol_date_strategy index (V1) - so the future multi-strategy-rows-
-- per-symbol/date design needs no constraint change here (task item 6).
-- No FK from signals.strategy to strategy_config.variant_id is added: deliberate deferral per
-- the plan (task item 8) - live orchestrator wiring that produces these rows per-variant is a
-- later phase, and an FK today would only constrain ad-hoc/manual strategy values with no
-- corresponding real behaviour change yet.

ALTER TABLE positions ADD COLUMN strategy_id VARCHAR(40);
ALTER TABLE positions ADD COLUMN strategy_version INTEGER;

ALTER TABLE paper_trading_orders ADD COLUMN portfolio_id VARCHAR(40) NOT NULL DEFAULT 'default';
ALTER TABLE paper_trading_portfolio_snapshots ADD COLUMN portfolio_id VARCHAR(40) NOT NULL DEFAULT 'default';
ALTER TABLE paper_trading_portfolio ALTER COLUMN portfolio_id TYPE VARCHAR(40);
