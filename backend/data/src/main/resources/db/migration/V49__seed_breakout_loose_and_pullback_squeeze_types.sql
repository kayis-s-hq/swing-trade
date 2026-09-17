-- V49: Phase 3 strategy-type seeds (plan §5.1/§5.2/§5.3).
--
-- BREAKOUT_LOOSE addresses finding F13 (BREAKOUT_STRICT produces zero BUYs historically) by
-- seeding a SHADOW variant with the same BREAKOUT rules but a relaxed entryScoreThreshold of
-- 0.75 instead of 1.0 - i.e. 3-of-4 confluence fires a BUY instead of requiring all 4. This is
-- the plan's priority validation target (§9 order 4), following the same direct-SQL seeding
-- approach V46 used for BREAKOUT_STRICT (placeholder params_hash for this seed row only; new
-- versions created through the service still get a real StrategyConfigHasher-computed hash).
--
-- PULLBACK_A and SQUEEZE_A are seeded as BACKTEST_ONLY (not SHADOW/CHAMPION) so they exist as
-- addressable variants for backtesting/walk-forward immediately, without consuming any of the
-- plan's <=12-active-variants shadow cap before they have been validated - promoting them to
-- SHADOW is a later, deliberate step (plan §5.6/§9), not part of this migration.

INSERT INTO strategy_config (variant_id, version, strategy_type, params, overlays, params_hash, mode, is_current)
VALUES (
    'BREAKOUT_LOOSE',
    1,
    'BREAKOUT',
    '{"emaFast":20,"emaSlow":50,"rsiPeriod":14,"rsiMin":50,"rsiMax":65,"volumeMaPeriod":20,"volMult":1.5,"weeklyHighPeriod":252,"highProximity":0.97,"entryScoreThreshold":0.75,"atrPeriod":14,"atrStopMult":2,"rewardRisk":2,"maxHoldDays":20,"trailAtrMult":0}'::jsonb,
    '{"sentimentGate":true}'::jsonb,
    '0000000000000000000000000000000000000000000000000000000000000001',
    'SHADOW',
    TRUE
);

INSERT INTO strategy_config (variant_id, version, strategy_type, params, overlays, params_hash, mode, is_current)
VALUES (
    'PULLBACK_A',
    1,
    'PULLBACK',
    '{"trendEma":50,"pullbackEma":20,"touchPct":0.01,"touchLookback":3,"rsiPeriod":14,"rsiDip":40,"rsiTrigger":45,"slopeLookback":10,"requireConfirmation":false,"entryScoreThreshold":0.75,"atrPeriod":14,"atrStopMult":2,"rewardRisk":2,"maxHoldDays":20,"trailAtrMult":0}'::jsonb,
    '{}'::jsonb,
    '0000000000000000000000000000000000000000000000000000000000000002',
    'BACKTEST_ONLY',
    TRUE
);

INSERT INTO strategy_config (variant_id, version, strategy_type, params, overlays, params_hash, mode, is_current)
VALUES (
    'SQUEEZE_A',
    1,
    'SQUEEZE',
    '{"bbPeriod":20,"trendEma":50,"squeezeDefinition":"BB_PCTILE","squeezeLookback":120,"squeezePctile":20,"squeezeRecency":5,"volumeMaPeriod":20,"volMult":1.5,"entryScoreThreshold":0.75,"atrPeriod":14,"atrStopMult":2,"rewardRisk":2,"maxHoldDays":20,"trailAtrMult":0}'::jsonb,
    '{}'::jsonb,
    '0000000000000000000000000000000000000000000000000000000000000003',
    'BACKTEST_ONLY',
    TRUE
);
