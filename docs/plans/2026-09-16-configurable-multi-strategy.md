# Configurable Multi-Strategy Framework (A/B/C) — 2026-09-16

Status: **Approved design, ready for implementation**
Supersedes/extends: `2026-09-03-strategy-and-platform-roadmap.md` (Phases 1–3)
Primary goal: **quality and statistical trustworthiness of analytics**, not feature count.

> **Gap found 2026-09-21:** the live orchestrator still resolves configs through the legacy `TradingStrategy` registry, so `BREAKOUT`/`PULLBACK`/`SQUEEZE` variants are skipped (principle 6 below is not yet true for live runs). Remediation, validation plan and follow-ups: `2026-09-21-multi-strategy-orchestration-remediation.md`.

---

## 0. Decisions locked with the owner

| Topic | Decision |
|---|---|
| Live mode | Every enabled variant runs in **SHADOW** with its **own virtual paper portfolio**; one **CHAMPION** flag gates real orders later |
| Strategy families | Breakout (current, loosened), Pullback-in-uptrend, Volatility squeeze, Relative strength vs Nifty |
| Universe | Keep **current full scan** (no universe expansion in this plan) |
| Sentiment | **Toggleable overlay/gate** per variant — measured, not assumed |
| Config storage | **DB (`strategy_config`) + dashboard Strategies page** |
| UI flexibility | **Params only** — strategy logic stays in Java, UI tunes validated params |
| Edits | **Immutable versions** — editing creates `vN+1`; history references exact version |
| Shadow cap | **Max 12** variants in SHADOW/CHAMPION simultaneously |

## 0.1 Guiding principles (analytics quality first)

1. **No result without provenance.** Every signal, paper trade, backtest row carries `strategy_id` + `strategy_version` + params hash.
2. **Out-of-sample or it doesn't count.** Leaderboards default to walk-forward OOS metrics; in-sample shown greyed.
3. **Minimum sample sizes are enforced by code**, not by discipline (see §7).
4. **Costs always on.** Zerodha delivery cost model + slippage in every backtest and paper fill.
5. **Count experiments.** Every variant/version ever backtested is logged so overfitting can be quantified (deflated Sharpe).
6. **Live and backtest share one evaluation path** (`SignalStrategy.evaluate`) — no duplicated rule logic.
7. **Default behaviour unchanged at migration time**: the current strategy becomes `BREAKOUT_STRICT@v1` CHAMPION.

---

## 1. Findings — current state (source-verified 2026-09-16)

| # | Finding | Location | Impact |
|---|---|---|---|
| F1 | `TradingStrategy` interface hard-codes the 4 price-action entry + 3 exit rule methods | `strategy/TradingStrategy.java` | New families can't be expressed without fake methods |
| F2 | `Indicators` is a fixed 7-field record (price, ema20, ema50, rsi, volume, volumeMa, weeklyHigh) | `strategy/Indicators.java` | No BB/Keltner/ADX/RS/index data available to strategies |
| F3 | All thresholds are `static final` constants | `core/domain/StrategyParams.java` | Variants impossible without code changes |
| F4 | `PriceActionSignalEngine` injects concrete `PriceActionStrategy`; reasoning strings hard-code "RSI between 50-65", "3% of 52-week high" | `strategy/PriceActionSignalEngine.java` | Reasoning lies for any other params |
| F5 | `SignalPipeline` hard-codes strategy `"DEFAULT"` on delete/persist | `api/service/SignalPipeline.java:107` | All live signals indistinguishable |
| F6 | `signals.strategy VARCHAR(30) DEFAULT 'DEFAULT'` + index `(symbol,date,strategy)` already exist | `V1__swing_trade_schema.sql` | Reusable; needs version column |
| F7 | `paper_trading_portfolio.portfolio_id` already exists (default `'default'`) | `V1` §20 | Per-variant portfolios feasible with little schema change |
| F8 | `paper_trading_portfolio_snapshots` and `paper_trading_orders` have **no** portfolio_id | `V1` §21–22 | Must add for per-variant equity curves |
| F9 | `StrategyRegistry` exists (name → bean), used by backtest only | `strategy/StrategyRegistry.java` | Replace with config-driven factory |
| F10 | Backtest is single-symbol; `maxConcurrentPositions` unused; equity only updated at trade exit | `BacktestEngine`, `BacktestConfig`, `docs/backtesting.md:79` | Sharpe/MaxDD biased (too smooth); no capital competition |
| F11 | `runBacktestWindow` exists (warm-up retained, metrics in window) and candidate scan has OOS columns (V45) | `BacktestEngine`, `V45` | Good base for walk-forward |
| F12 | ATR14 computed in live engine but unused for stops live | `PriceActionSignalEngine` | Stop/target inconsistency live vs backtest |
| F13 | Roadmap reports **zero BUY signals** across history under 4-of-4 entry | roadmap 2026-09-03 | Nothing downstream validated end-to-end |
| F14 | Job orchestrator stages per symbol: DATA_FETCH → SIGNAL → BACKTEST → NEWS → SENTIMENT → PAPER_TRADE | `api/service/JobOrchestratorService.java:333-342` | Multi-strategy fan-out goes in SIGNAL; gates in SENTIMENT/PAPER_TRADE |
| F15 | `app_settings` + `SettingsController` + `SettingsView.vue` pattern exists | api, dashboard | UI/API conventions to follow |
| F16 | Nifty references exist only in data clients/config, not in `strategy` | grep | Index series ingestion needed for RS and regime |
| F17 | Latest migration is `V45` | `data/src/main/resources/db/migration` | New migrations start at `V46` |

## 1.1 Assumptions (verify before/while implementing)

- A1. Daily candles only (EOD); no intraday strategies in scope.
- A2. Long-only; no short signals.
- A3. Nifty 50 index daily OHLC can be fetched by the existing market data client (Upstox/Fyers/Yahoo `^NSEI`). If not, add ingestion in Phase 2 before RS/regime.
- A4. Sector mapping exists or can be derived from `stocks` table; if absent, sector cap is deferred (not a blocker).
- A5. Paper fills continue as next-day open via `PendingOrderExecutionScheduler`; backtests must model the same (signal on close T, fill on open T+1).
- A6. Postgres JSONB available (yes, PostgreSQL). H2 tests need JSON-compatible mapping — use `TEXT` + Jackson if H2 incompatibility arises.
- A7. Candle data is split/dividend adjusted via `OhlcvCandle.adjustedForAnalysis()` (in-progress diff); all strategies must use adjusted series.
- A8. Single user, no auth yet (roadmap Phase 5); config endpoints unauthenticated for now but must be listed in the auth work.

---

## 2. Target architecture

```
                ┌──────────────── strategy_config (versioned, JSONB params) ───────────────┐
                │                                                                          │
Daily job ─▶ DATA_FETCH ─▶ SIGNAL stage                                                    │
                              │ MarketContextFactory: builds per-symbol context once       │
                              │   (bar series, lazy+cached indicators, index series)       │
                              │ StrategyFactory ◀── active variants (SHADOW|CHAMPION, ≤12) ┘
                              │   for each variant: SignalStrategy.evaluate(ctx) → StrategyDecision
                              ▼
                        signals (strategy_id, strategy_version, score, rules JSON, stop, target)
                              ▼
                   SENTIMENT stage (only symbols with ≥1 BUY from a variant with sentimentGate; cached per symbol/day)
                              ▼
                   Overlay evaluation (regimeGate, sentimentGate) → final decision per variant
                              ▼
                   PAPER_TRADE stage → portfolio_id = variant id (own capital, own caps)
                              ▼
                   snapshots per portfolio (daily MTM) → analytics / leaderboard
```

Module placement (respect ArchUnit boundaries):
- `core`: `StrategyConfig` domain record, `StrategyMode` enum, `StrategyConfigStore` interface.
- `strategy`: `SignalStrategy`, `MarketContext`, `StrategyDecision`, `ParamSchema`, strategy types, `StrategyFactory`, backtest/portfolio sim, analytics.
- `data`: `StrategyConfigEntity`, repository, `StrategyConfigStoreImpl`, migrations.
- `api`: `StrategyConfigController`, `StrategyAnalyticsController`, orchestrator changes.
- `broker`: portfolio-scoped paper engine changes.
- `dashboard`: `StrategiesView.vue`, store, components.

---

## 3. Phase 1 — Strategy SPI & MarketContext

### 3.1 Contracts

```java
public interface SignalStrategy {
    String type();                              // "BREAKOUT", "PULLBACK", ...
    ParamSchema paramSchema();                  // names, types, min/max, defaults, descriptions
    Set<IndicatorKey> requiredIndicators(StrategyParamsView p);
    int warmupBars(StrategyParamsView p);
    StrategyDecision evaluateEntry(MarketContext ctx, int barIndex, StrategyParamsView p);
    ExitDecision evaluateExit(MarketContext ctx, int barIndex, OpenPosition pos, StrategyParamsView p);
}

public record StrategyDecision(
    SignalType type, BigDecimal score /*0..1*/, List<RuleOutcome> rules,
    BigDecimal suggestedStop, BigDecimal suggestedTarget, String reasoning) {}

public record RuleOutcome(String key, boolean passed, BigDecimal weight, String detail) {}
```

- `evaluateEntry` takes `barIndex` so **the same method** is used live (last bar) and in backtest (every bar) — no look-ahead: the context must only expose data `<= barIndex`. Enforce with a `MarketContext.view(barIndex)` wrapper that throws on future access.
- `MarketContext` computes indicators **lazily and caches by (IndicatorKey, params)**; e.g. `ema(20)` computed once even if 12 variants ask.
- Index series (`NIFTY50`) attached to context when any active variant requires `IndicatorKey.INDEX_*`.

### 3.2 Scoring model
- Each strategy returns rule outcomes with weights; `score = Σ(weight·passed)/Σweight`.
- BUY when `score >= params.entryScoreThreshold` **and** all rules flagged `mandatory=true` pass.
- This lets "4-of-4" (threshold 1.0) and "3-of-4" (0.75) be configs of the same type — directly addresses F13.

### 3.3 Exits (uniform across types, params per variant)
- ATR stop: `entry − atrStopMult·ATR14` (fixes F12).
- Target: `entry + rewardRisk·(entry − stop)`.
- Time stop: `maxHoldDays`.
- Optional signal exit (type-specific), optional trailing stop (`trailAtrMult`, 0 = off).
- Exit precedence (document and test): STOP_LOSS → TARGET_HIT → SIGNAL_EXIT → TRAILING → TIME_STOP. Same-bar stop and target both touched → **assume stop first** (conservative).

### 3.4 Backward compatibility
- `LegacyPriceActionAdapter` implements `SignalStrategy` type `BREAKOUT` with params equal to current `StrategyParams` and threshold 1.0.
- Golden test: for the fixture candle set, adapter decisions == current `PriceActionSignalEngine` decisions bar-by-bar. Must pass before removing old path.
- `StrategyParams` constants become defaults inside `BreakoutStrategy.paramSchema()`; delete constants after migration (keep `@Deprecated` one release if referenced elsewhere).

### 3.5 Tests
- Unit tests per rule with hand-built candle fixtures.
- Look-ahead guard test: accessing `barIndex+1` throws.
- Indicator cache test: 12 variants → each indicator computed once.

**Suggestion:** use `DecimalNum` consistently (per 2026-08-07 precision plan); do not mix `TechnicalIndicatorsDouble`.

---

## 4. Phase 2 — Configuration model, persistence, API

### 4.1 Schema (`V46__strategy_config.sql`)

```sql
CREATE TABLE strategy_config (
    id               BIGSERIAL PRIMARY KEY,
    variant_id       VARCHAR(40)  NOT NULL,          -- "PULLBACK_B"
    version          INTEGER      NOT NULL,
    strategy_type    VARCHAR(30)  NOT NULL,          -- "PULLBACK"
    params           JSONB        NOT NULL,
    overlays         JSONB        NOT NULL DEFAULT '{}',
    params_hash      CHAR(64)     NOT NULL,          -- sha256 of canonical JSON(type+params+overlays)
    mode             VARCHAR(16)  NOT NULL DEFAULT 'OFF',   -- OFF|BACKTEST_ONLY|SHADOW|CHAMPION
    paper_capital    NUMERIC(15,2) NOT NULL DEFAULT 500000,
    is_current       BOOLEAN      NOT NULL DEFAULT TRUE,
    notes            TEXT,
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (variant_id, version)
);
CREATE UNIQUE INDEX ux_strategy_config_current ON strategy_config(variant_id) WHERE is_current;
CREATE UNIQUE INDEX ux_strategy_config_champion ON strategy_config((1)) WHERE is_current AND mode = 'CHAMPION';
```

- Rows are **append-only**: params/overlays/type never updated. Only `mode`, `is_current`, `notes` may change (mode changes audited in `strategy_config_audit`).
- Seed: `BREAKOUT_STRICT` v1, type BREAKOUT, threshold 1.0, mode CHAMPION, overlays `{sentimentGate: true}` (matches today's sentiment-gated flow — **verify** current gate behaviour before seeding).

### 4.2 Provenance columns (`V47__strategy_provenance.sql`)

```sql
ALTER TABLE signals ADD COLUMN strategy_version INTEGER, ADD COLUMN strategy_score NUMERIC(5,4),
                    ADD COLUMN rule_outcomes JSONB, ADD COLUMN gate_outcomes JSONB;
UPDATE signals SET strategy = 'BREAKOUT_STRICT', strategy_version = 1 WHERE strategy = 'DEFAULT';
-- index: (strategy, strategy_version, date)
ALTER TABLE positions                        ADD COLUMN strategy_id VARCHAR(40), ADD COLUMN strategy_version INTEGER;
ALTER TABLE paper_trading_orders             ADD COLUMN portfolio_id VARCHAR(40) NOT NULL DEFAULT 'default';
ALTER TABLE paper_trading_portfolio_snapshots ADD COLUMN portfolio_id VARCHAR(40) NOT NULL DEFAULT 'default';
ALTER TABLE paper_trading_portfolio          ALTER COLUMN portfolio_id TYPE VARCHAR(40);
```
- Widen `signals.strategy` to `VARCHAR(40)`. Check unique constraints on `signals` still allow same symbol/date for different strategies.
- Backfill existing `default` portfolio → `BREAKOUT_STRICT`. Route through `migration-reviewer`.

### 4.3 Param validation
- `ParamSchema` entries: `name, type(INT|DECIMAL|BOOL|ENUM), min, max, default, step, description, group`.
- Cross-field validators per type (e.g. `rsiMin < rsiMax`, `emaFast < emaSlow`).
- Unknown keys rejected; missing keys filled with defaults **at create time** and stored explicitly (so version is self-contained even if defaults change later).
- `params_hash` dedupes: creating a version identical to an existing one returns the existing one.

### 4.4 API (`StrategyConfigController`)
| Method | Path | Purpose |
|---|---|---|
| GET | `/api/strategy-types` | Types + ParamSchema (drives UI forms) |
| GET | `/api/strategies` | Current version of every variant + mode + headline OOS metrics |
| GET | `/api/strategies/{variantId}/versions` | Version history |
| POST | `/api/strategies` | Create variant (v1) |
| POST | `/api/strategies/{variantId}/versions` | New version; body includes `portfolioAction: CONTINUE|RESET` |
| POST | `/api/strategies/{variantId}/clone` | Clone into new variant id |
| PUT | `/api/strategies/{variantId}/mode` | Change mode; enforces ≤12 active and single CHAMPION (409 otherwise) |
| POST | `/api/strategies/validate` | Validate params without saving |

- Mode/param changes take effect **at next job run**; the orchestrator snapshots the active config list at job start and records it in `job_runs` (add `strategy_snapshot JSONB`).
- CHAMPION change requires `confirm=true` and cannot happen while a job is running.

### 4.5 Portfolio action on new version
- `CONTINUE`: open positions keep old version's exits; new entries use new version. Analytics split by version.
- `RESET`: close virtual positions at next open (marked `exit_reason=CONFIG_RESET`, excluded from strategy metrics), restore `paper_capital`.
- **Suggestion:** default to `RESET` for param changes affecting entries — cleaner attribution.

---

## 5. Phase 3 — Strategy types (initial param sets)

All types share exit params (§3.3) and `entryScoreThreshold`. Values are **starting defaults**, not tuned — tuning happens only via walk-forward (§6).

### 5.1 BREAKOUT (current, generalised)
Rules: trend (close>EMAfast>EMAslow, mandatory), RSI in [rsiMin,rsiMax], volume > volMult·VolMA, close ≥ proximity·52wHigh.
| Param | Default | Range |
|---|---|---|
| emaFast/emaSlow | 20/50 | 5–100 / 20–250 |
| rsiMin/rsiMax | 50/65 | 30–80 |
| volMult | 1.5 | 1.0–4.0 |
| highProximity | 0.97 | 0.85–1.0 |
| entryScoreThreshold | 1.0 (STRICT) / 0.75 (LOOSE) | 0.5–1.0 |
Seed variants: `BREAKOUT_STRICT` (CHAMPION), `BREAKOUT_LOOSE` (SHADOW).
**Finding-driven note:** STRICT produced zero BUYs (F13); LOOSE is the priority validation target.

### 5.2 PULLBACK (buy dip in uptrend)
Rules: close > EMA(trendEma=50) & EMA50 slope>0 over `slopeLookback` (mandatory); low touched within `touchPct` of EMA20 in last `touchLookback` bars; RSI crossed back above `rsiTrigger` (e.g. 45) from below `rsiDip` (40); today close > prior high (confirmation, optional).
Defaults: trendEma 50, pullbackEma 20, touchPct 0.01, touchLookback 3, rsiDip 40, rsiTrigger 45, slopeLookback 10.
**Suggestion:** stop = min(swing low of last `touchLookback` bars, entry − atrStopMult·ATR).

### 5.3 SQUEEZE (volatility contraction → expansion)
Rules: BB(20,2) width percentile over `squeezeLookback`(120) ≤ `squeezePctile`(20) within last `squeezeRecency`(5) bars; close > upper BB or > Keltner(20,1.5) upper; volume > volMult(1.5)·VolMA; close > EMA50 (mandatory).
**Suggestion:** "BB inside Keltner" is an alternative squeeze definition — expose as ENUM `squeezeDefinition: BB_PCTILE|BB_IN_KC`.

### 5.4 RS_NIFTY (relative strength momentum)
Rules: RS = stock return / Nifty return over `rsLookback` (63 and 126 blend, weights param); RS percentile within **scan universe on that date** ≥ `rsPctile`(80); close > EMA50 (mandatory); 52wHigh proximity ≥ 0.90 (optional); regime gate recommended on.
**Constraint:** needs cross-sectional context → `MarketContext` needs universe access. Implement `UniverseContext` computed once per job/backtest date. Must use only symbols that existed in the universe on that date (**survivorship bias** — record this limitation if historical membership unavailable).
Rebalance semantics: entries only on signals; no forced monthly rebalance (keeps it comparable with other swing types).

### 5.5 Overlays (apply to any type)
| Overlay | Params | Logic |
|---|---|---|
| regimeGate | indexEma 200, mode `CLOSE_ABOVE|EMA_SLOPE_UP` | Block BUY when Nifty below its EMA |
| sentimentGate | minScore (e.g. NEUTRAL), maxAgeDays 3 | Block BUY if sentiment negative/stale |
Record every gate outcome in `signals.gate_outcomes` **including blocked signals**, so "what gate prevented" is measurable (key analytics requirement).

### 5.6 Suggested initial 12-slot allocation
1 BREAKOUT_STRICT (champion) · 2 BREAKOUT_LOOSE · 3 BREAKOUT_LOOSE+REGIME · 4 PULLBACK_A · 5 PULLBACK_A+REGIME · 6 PULLBACK_A+SENT · 7 SQUEEZE_A · 8 SQUEEZE_A+REGIME · 9 RS_A · 10 RS_A+REGIME · 11 BREAKOUT_LOOSE+SENT · 12 reserved.
This forms a factorial design (type × regime, type × sentiment) so overlay value is estimated per type.

---

## 6. Phase 4 — Backtest quality upgrades (highest analytics priority)

### 6.1 Portfolio simulator (`PortfolioBacktestEngine`)
- Iterate **by date** across all symbols; shared cash; `maxConcurrentPositions`, `maxCapitalPerPositionPct` mirror `paper_trading_portfolio` settings (single source of truth).
- Candidate ranking when capacity limited: by `strategy_score` desc, tiebreak by lower ATR% (document it).
- Signal on close T, fill on **open T+1** + slippage (matches live, A5). Gap-through stops fill at open, not stop price.
- Position sizing: risk-based `riskPct·equity / (entry − stop)`, capped by `maxCapitalPerPositionPct`.
- **Daily mark-to-market equity** (fixes F10) → Sharpe/Sortino/MaxDD on daily returns.
- Keep single-symbol engine for diagnostics; leaderboard uses portfolio engine only.

### 6.2 Metrics (per variant/version, per window)
Required: CAGR, total return, annualised vol, Sharpe (daily, rf configurable, default 6.5% INR), Sortino, MaxDD & duration, Calmar, trade count, win rate **with Wilson 95% CI**, avg win/loss, payoff ratio, **expectancy per trade (R and ₹) after costs with bootstrap CI**, profit factor, exposure %, avg holding days, turnover, cost drag %.
Diagnostics: MAE/MFE per trade, exit-reason breakdown, per-symbol contribution (flag if one symbol > 40% of P&L), per-year returns, benchmark (Nifty buy&hold) alpha/beta.

### 6.3 Walk-forward
- Rolling: train 24m / test 6m / step 6m (params). Report concatenated OOS equity.
- Since UI is params-only, "train" here means **selection among saved versions** (optional grid search endpoint in later phase, capped trials); default is fixed params evaluated OOS per fold for stability.
- Stability metric: std-dev of fold Sharpe; flag variant if any fold MaxDD > 2× median.
- **Hold-out:** last 6 months globally reserved; not shown in leaderboard until explicitly "unlocked" (logged).

### 6.4 Overfitting controls
- `strategy_experiment_log` table: every backtest run (variant, version, params_hash, window, metrics, timestamp).
- Deflated Sharpe Ratio (Bailey & López de Prado) using N = distinct params_hash tested per strategy type.
- Leaderboard shows DSR p-value; badge "likely noise" when p > 0.1.
- Probability of Backtest Overfitting (CSCV) — **suggestion, later phase**.

### 6.5 Signal overlap / independence
- Pairwise Jaccard of entry (symbol,date) sets and correlation of daily returns between variants. Needed to judge whether A vs B is really different.

### 6.6 Data quality gates (block run, don't warn)
- Missing bars > 2% in window, zero/negative prices, unadjusted splits (jump > 40% without corporate action) → symbol excluded, logged in result `excludedSymbols`.

### 6.7 Tests
- Deterministic fixture with known trades/equity → metrics exactly asserted.
- No-look-ahead property test: truncating future data does not change past decisions.
- Parity test: live `SIGNAL` stage decision on date D == backtest decision at bar D for same version.

### 6.8 API
`POST /api/backtest/compare { variants:[{id,version}], start, end, walkForward:{trainM,testM,stepM}, costsOn:true }` → async job (reuse job_runs pattern), results persisted in `strategy_backtest_results` (variant, version, fold, metrics JSONB, equity curve compressed).

---

## 7. Phase 5 — Live shadow execution & per-variant paper portfolios

### 7.1 Orchestrator
- At job start: load active configs (SHADOW/CHAMPION, ≤12), snapshot into `job_runs.strategy_snapshot`.
- SIGNAL stage: build context once per symbol; evaluate all variants; persist one `signals` row per (symbol,date,variant) — **including HOLD only for CHAMPION** (suggestion: persist non-HOLD for shadows to limit table growth; store daily per-variant counts in a summary table).
- Idempotency: delete/replace by `(symbol, date, strategy, strategy_version)` (replaces F5).
- SENTIMENT stage: run only if any variant with `sentimentGate` has BUY on that symbol; result cached per symbol/day and shared.
- PAPER_TRADE stage: per variant portfolio; CHAMPION additionally eligible for real broker later (not in scope).
- Per-variant failure isolation: one strategy throwing must not fail the symbol stage; record error per variant.

### 7.2 Portfolios
- `paper_trading_portfolio` row per variant (`portfolio_id = variant_id`), created on first SHADOW activation with `paper_capital`.
- Daily snapshot per portfolio after EOD MTM (F8 fix).
- Kill switch / daily loss breaker: apply per portfolio for shadows (suggestion), global for champion.

### 7.3 Live-vs-backtest tracking
- Nightly: re-run backtest for yesterday for each active variant; compare decisions vs live signals → `parity_mismatch` metric; alert on Discord if > 0.

### 7.4 Promotion rules (champion/challenger) — enforced in code, advisory UI
Challenger eligible when **all**:
- ≥ 60 calendar days in SHADOW on current version **and** ≥ 30 closed paper trades.
- Paper expectancy (after costs) > champion's, and bootstrap 90% CI of difference excludes 0 — or, if trade count too low, consistent with walk-forward OOS result sign.
- Paper MaxDD ≤ 1.2 × champion MaxDD over same period.
- Walk-forward OOS Sharpe > 0 and DSR p < 0.1.
UI shows checklist; promotion requires manual confirm.

**Finding/assumption:** with current scan size, reaching 30 trades may take months per variant. This is accepted; the UI must display "insufficient sample" rather than a ranking.

---

## 8. Phase 6 — Dashboard (Strategies page)

Route `/strategies`, Pinia store `strategies`, follow `SettingsView.vue` & API client conventions.

Views/components:
1. **Variant list**: id, type, version, mode toggle, active count `n/12`, OOS Sharpe, trades, sample-size badge.
2. **Editor**: form generated from `/strategy-types` schema (grouped, min/max, tooltips), overlay toggles, diff vs current version, portfolioAction choice, Validate → Save as vN+1.
3. **Clone** action.
4. **Compare**: select variants → run compare → table (§6.2 metrics with CIs) + overlaid equity curves (OOS solid, IS dashed) + drawdown chart + overlap matrix.
5. **Live shadow leaderboard**: per-portfolio equity, open positions, gate-blocked signal counts, promotion checklist.
6. **Signals page**: add strategy filter + version column.

UI rules: never rank variants below minimum sample; always show CIs; colour neutral (no green "winner") until promotion criteria met.
Verify with Playwright per repo rules.

---

## 9. Priority & sequencing (quality first)

| Order | Phase | Why this order |
|---|---|---|
| 1 | §3 SPI + golden parity test | Everything depends; zero behaviour change |
| 2 | §4.1–4.3 config + provenance migrations | Provenance before generating any new data |
| 3 | §6.1–6.2 portfolio backtest + daily MTM + metrics w/ CIs | Trustworthy measurement before new strategies |
| 4 | §5.1 BREAKOUT_LOOSE + §6.3 walk-forward | Unblock BUYs (F13), measured OOS |
| 5 | §6.4–6.6 experiment log, DSR, data quality gates | Guard before experimentation scales |
| 6 | §5.2–5.4 PULLBACK, SQUEEZE, RS_NIFTY (+ index ingestion) | New families on sound tooling |
| 7 | §5.5 overlays with gate_outcomes | Measure sentiment/regime value |
| 8 | §4.4 API + §8 dashboard | Operability |
| 9 | §7 shadow execution, per-variant portfolios, parity check | Live OOS evidence |
| 10 | §7.4 promotion workflow | Only after evidence accrues |

Agent routing: migrations → `migration-reviewer`; strategy/backtest/api → `backend-dev` with `test-writer` (TDD); dashboard → `frontend-dev`; module boundaries → `arch-auditor`; final diff → `code-reviewer`.

## 10. Acceptance criteria

- [ ] Golden parity: `BREAKOUT_STRICT@v1` reproduces historical signals exactly.
- [ ] Every signal/order/snapshot/backtest row has variant + version.
- [ ] Config versions immutable (DB test proves update of params fails at service layer).
- [ ] ≤12 active and single CHAMPION enforced by DB + API.
- [ ] Portfolio backtest uses T+1 open fills, costs, daily MTM; metrics fixture test passes.
- [ ] Walk-forward OOS report and DSR shown for each compared variant.
- [ ] Look-ahead property test and live/backtest parity test pass.
- [ ] Gate-blocked signals persisted with reasons.
- [ ] UI hides ranking when sample < threshold; shows CIs.
- [ ] ArchUnit, Checkstyle/PMD, unit + H2 integration, Playwright tests green.

## 11. Risks & open items

| Risk | Mitigation |
|---|---|
| Low trade counts → noisy comparisons | Enforced minimums, CIs, DSR, pooled factorial analysis across overlays |
| Overfitting via UI param tweaking | Experiment log, DSR by trial count, hold-out lock |
| Survivorship bias (RS universe) | Record limitation; add historical membership later |
| Job runtime ×12 variants | Shared indicator cache, sentiment dedupe; measure in stage before enabling 12 |
| Table growth (signals) | Persist non-HOLD for shadows; daily summary counts |
| H2 lacks JSONB | TEXT + Jackson converter in entity |
| Nifty data unavailable from provider | Verify A3 first; fallback Yahoo `^NSEI` |

Open questions to resolve during implementation (not blockers):
- Confirm current sentiment gate semantics before seeding BREAKOUT_STRICT overlays.
- Confirm `signals` unique constraints permit multi-strategy rows.
- Sector mapping availability (A4) for future sector cap.
