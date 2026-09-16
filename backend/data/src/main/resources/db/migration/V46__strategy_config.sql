-- V46: Configurable multi-strategy framework - config model (plan docs/plans/2026-09-16-configurable-multi-strategy.md §4.1)
--
-- strategy_config rows are append-only: params/overlays/strategy_type never change after insert.
-- Only mode/is_current/notes may change on a row, and mode changes are additionally audited in
-- strategy_config_audit. Editing a variant creates a new version row (service layer), never an
-- UPDATE of an existing version's params/overlays.
--
-- Column naming note: `version` here is the *business* version number of a variant
-- (v1, v2, ...; part of the UNIQUE(variant_id, version) key), not Hibernate's optimistic-lock
-- column. Per this codebase's convention (V25__add_version_columns.sql /
-- V27__backfill_null_version_columns.sql - every JPA-managed entity carries an @Version column
-- after a prior live-blocking NPE from a missing one), a *separate* optimistic-lock column
-- `lock_version` is added below so the two concerns don't collide on one column name.
CREATE TABLE strategy_config (
    id               BIGSERIAL PRIMARY KEY,
    variant_id       VARCHAR(40)  NOT NULL,          -- "PULLBACK_B"
    version          INTEGER      NOT NULL,          -- business version number (v1, v2, ...)
    strategy_type    VARCHAR(30)  NOT NULL,          -- "PULLBACK"
    params           JSONB        NOT NULL,
    overlays         JSONB        NOT NULL DEFAULT '{}',
    params_hash      CHAR(64)     NOT NULL,          -- sha256 of canonical JSON(type+params+overlays)
    mode             VARCHAR(16)  NOT NULL DEFAULT 'OFF',   -- OFF|BACKTEST_ONLY|SHADOW|CHAMPION
    paper_capital    NUMERIC(15,2) NOT NULL DEFAULT 500000,
    is_current       BOOLEAN      NOT NULL DEFAULT TRUE,
    portfolio_action VARCHAR(16),                    -- CONTINUE|RESET; recorded intent for this version's activation, null for v1
    notes            TEXT,
    lock_version     INTEGER      NOT NULL DEFAULT 0, -- Hibernate @Version optimistic lock (distinct from business `version` above)
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (variant_id, version)
);

-- One "current" row per variant.
CREATE UNIQUE INDEX ux_strategy_config_current ON strategy_config(variant_id) WHERE is_current;
-- At most one current CHAMPION across all variants.
CREATE UNIQUE INDEX ux_strategy_config_champion ON strategy_config((1)) WHERE is_current AND mode = 'CHAMPION';

CREATE INDEX idx_strategy_config_mode ON strategy_config(mode) WHERE is_current;
CREATE INDEX idx_strategy_config_variant ON strategy_config(variant_id);

-- NOTE on enforcement scope: the two partial unique indexes above are real Postgres-level
-- invariants, but this codebase's default unit-test profile runs on H2 with Flyway disabled and
-- Hibernate ddl-auto=create-drop (see backend/data/src/test/java/.../TestProfileResolver.java),
-- so H2 does not exercise these partial indexes at all. The <=12-active-variants and
-- single-CHAMPION rules are therefore ALSO enforced at the service layer
-- (StrategyConfigService, api module) as belt-and-suspenders - that's what the H2-profile tests
-- actually prove; a Postgres-profile test (see backend/data/src/test/resources/db/test-migration)
-- is required to prove the DB-level constraint itself.

-- Audit trail for mode changes (params/overlays/type are immutable and need no audit table).
CREATE TABLE strategy_config_audit (
    id               BIGSERIAL PRIMARY KEY,
    variant_id       VARCHAR(40)  NOT NULL,
    version          INTEGER      NOT NULL,
    old_mode         VARCHAR(16),
    new_mode         VARCHAR(16)  NOT NULL,
    changed_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    notes            TEXT
);
CREATE INDEX idx_strategy_config_audit_variant ON strategy_config_audit(variant_id);

-- Seed BREAKOUT_STRICT v1 as CHAMPION, reproducing today's only live path
-- (LegacyPriceActionAdapter / PriceActionSignalEngine, "4-of-4" confluence, entryScoreThreshold=1.0).
--
-- sentimentGate verification (plan §4.1 open item, "verify current gate behaviour before
-- seeding"): today's live pipeline (SignalPipeline.generatePrimarySignal +
-- JobOrchestratorService's SENTIMENT/PAPER_TRADE stages + SentimentGate) persists every
-- technical BUY unconditionally, then blocks the *paper trade* (not the signal row) when
-- sentiment is NEGATIVE and flags it when NEUTRAL/unknown - i.e. sentiment already gates
-- whether a BUY signal results in a trade for the (only) live strategy today. `overlays:
-- {sentimentGate: true}` below matches that actual behaviour: BREAKOUT_STRICT trades are
-- gated by sentiment exactly as they are today. (Nothing changes yet: overlay evaluation
-- against this column is orchestrator wiring, deferred to a later phase per this migration's
-- task scope - see StrategyConfigStore Javadoc.)
INSERT INTO strategy_config (variant_id, version, strategy_type, params, overlays, params_hash, mode, is_current)
VALUES (
    'BREAKOUT_STRICT',
    1,
    'BREAKOUT',
    '{"emaFast":20,"emaSlow":50,"rsiPeriod":14,"rsiMin":50,"rsiMax":65,"volumeMaPeriod":20,"volMult":1.5,"weeklyHighPeriod":252,"highProximity":0.97,"entryScoreThreshold":1.0,"atrPeriod":14,"atrStopMult":2,"rewardRisk":2,"maxHoldDays":20,"trailAtrMult":0}'::jsonb,
    '{"sentimentGate":true}'::jsonb,
    -- placeholder hash; the real value is deterministic sha256(canonical JSON) computed by
    -- StrategyConfigHasher and is content-addressed only for *newly created* versions going
    -- forward (this seed row is inserted directly by SQL, not through the service, so its hash
    -- is fixed here rather than computed at migration time; it never needs to match another
    -- row's hash because it's the very first row for this variant).
    '0000000000000000000000000000000000000000000000000000000000000000',
    'CHAMPION',
    TRUE
);
