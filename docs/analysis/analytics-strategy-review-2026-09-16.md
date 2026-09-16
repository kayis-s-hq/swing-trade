# Analytics, LLM and strategy review — 2026-09-16

Scope: signal generation, composite scoring, backtest, candidate scan, LLM sentiment and synthesis,
sentiment accuracy, paper-trading execution and monitoring, and schedulers. The initial findings
were made against `main` (`80f5f7e4`); the status below was re-checked against the working tree on
2026-09-16 after implementation and verification.

Severity: **BUG** gives wrong numbers or behavior today · **GAP** is missing capability or realism · **IMP** is an improvement.

## Implementation update — 2026-09-16

Completed and verified in this pass:

- Composite weights are single-source constants, missing-news composites renormalize over the
  dimensions present, confidence scales news scores, and composite rounding occurs after the
  weighted calculation (`CompositeAnalysisService`).
- Technical 52-week-high scoring uses the latest 252 candles, and the signal pipeline uses the
  TA4J engine ATR rather than recomputing the legacy range average (`TechnicalAnalysisService`,
  `SignalPipeline`). The compatibility `RiskCalculator` ATR API now also uses true range with
  Wilder smoothing.
- Paper monitoring defaults to 16:45 IST, after the 16:30 EOD ingestion, and no longer writes a
  candle it only read. Pending orders settle against the latest EOD candle's session open at
  16:35 IST with configured slippage; paper capacity is checked against the
  calculated quantity (`PaperTradingMonitorService`, `PendingOrderExecutionScheduler`,
  `PaperTradingEngine`).
- Backtest equity is marked to market each bar; exits apply adverse slippage and gap-through
  stops/targets use the bar open when it crosses the trigger. The default backtest now applies an
  explicit Indian delivery cost model for STT, exchange/regulatory fees, stamp duty, GST, and DP
  charges (`BacktestEngine`, `ZerodhaDeliveryCostModel`).
- PAPER_TRADE now queues BUY signals as pending market orders; the next-session scheduler owns the
  actual open-price fill, aligning paper execution with backtest timing (`JobOrchestratorService`,
  `TradingService`, `PaperTradingEngine`).
- Candidate qualification now requires a configurable minimum of 15 completed trades before the
  win-rate/return gates can pass (`CandidateScanService`).
- Backtest results now expose CAGR, Sortino, and Calmar computed from the evaluated period and
  marked-to-market daily equity curve (`BacktestEngine`, `BacktestResult`). Benchmark and excess
  return metrics remain open because the current multi-symbol runner does not yet share capital or
  define a benchmark data contract.
- Sentiment response parsing recognizes the legacy `sentiment` JSON alias instead of accepting a
  converter-created neutral default. Plain-text responses remain neutral when sentiment is
  ambiguous; UNKNOWN is preserved and sentiment provenance is now persisted.
- Historical sentiment analysis now reads the persisted, inclusive symbol/date news window and
  does not fall back to live feeds; current-day analysis continues to use live ingestion
  (`NewsArticleStore`, `NewsIngestionService`, `SentimentService`). Article identity/first-seen
  provenance and sentiment-to-article evidence links remain open.
- Analytical TA paths now scale historical OHLC by `adjClose / close` when valid, preventing
  split/bonus discontinuities from becoming artificial signals while retaining raw persisted
  candles for execution and audit (`OhlcvCandle`, `PriceActionSignalEngine`,
  `TechnicalAnalysisService`). Historical universe snapshots and action provenance remain open.

Additional verified slice — 2026-09-16:

- Sentiment analysis now enforces a seven-day window ending at 15:30 IST on the analysis date,
  formats prompt articles with publication date/source/index metadata, substitutes `{symbol}` in
  the system prompt, and persists `LLM`/`KEYWORD`/`DEFAULT` provenance. Unparseable responses are
  represented as `UNKNOWN` and the gate flags them rather than allowing them. Historical article
  IDs and a complete persisted-news reconstruction path remain follow-ups.
- Pending paper orders are persisted before their source signal is marked processed and pending
  orders are reloaded on startup. Paper stop/target triggers now use the candle open when a bar
  gaps through a trigger, matching the backtest policy.
- Sentiment IC now uses Pearson correlation over average ranks, so repeated categorical scores do
  not use the no-ties shortcut. Excess-return labeling and IC-driven weighting remain open.
- Signal confidence is now derived from bounded RSI and EMA rule margins for BUY/SELL/HOLD rather
  than fixed constants. Outcome calibration and confidence-based sizing/ranking remain open.
- The indicator audit found that `TechnicalIndicators` is a thin TA4J adapter rather than a second
  hand-rolled formula set. Technical and fundamental scoring therefore use the same TA4J semantics
  as the live signal path; only indicator object construction remains duplicated.
- Candidate scans now run a configurable 60–1000-day OOS window (252 days by default), persist its
  dates and metrics separately, and require both full-history and OOS gates before a result qualifies.
  Candidate discovery remains read-only; watchlist activation is handled separately by the API/UI.
- Sentiment point-in-time filtering now rejects articles without publication timestamps, since their
  position relative to the decision cutoff cannot be proven. This favors a safe UNKNOWN/neutral result
  over admitting potentially future information.

Verification: `./bin/verify-changes` passed on 2026-09-16. It ran the backend test task selected
from changed paths; the explicit affected-module suite also passed:
`./gradlew :data:test :strategy:test :llm:test :broker:test :api:test --no-daemon`.
GitHub issue state was inspected with `gh-axi`; no existing issue was closed because the remaining
review items are broader than the landed fixes.

---

## Status checklist (by priority)

Legend: `[x]` fixed and verified · `[~]` partially fixed with documented follow-ups · `[ ]` open.

### P1 — Fix wrong numbers (days)
- [x] 1  Composite weights displayed ≠ weights used
- [x] 2  Missing news weight not renormalized
- [x] 3  `Math.round((int) …)` truncation
- [x] 4  Tech score "52W high" uses all history
- [x] 5  Two ATR formulas (range vs true range)
- [x] 6  Two indicator implementations (custom vs TA4J)
- [x] 7  Paper monitor runs before EOD ingestion (1-day SL/TP lag)
- [x] 8  Pending orders fill at previous close, not open
- [x] 9  Orchestrator fills BUY at signal-day close; backtest uses next open
- [x] 10 Capacity check uses fixed 100 qty, not the real size
- [x] 11 Indian transaction costs missing from backtest
- [x] 12 Exit slippage missing
- [x] 13 Gap-through stops fill at the stop price
- [~] 14 Sentiment not point-in-time (look-ahead)
- [~] 15 Plain-text LLM fallback misclassifies

### P2 — Trustworthy evaluation
- [x] 16 No mark-to-market equity curve (Sharpe/DD wrong)
- [ ] 17 No portfolio-level backtest
- [~] 18 Candidate scan qualifies on in-sample backtest
- [x] 19 No minimum trade count
- [~] 20 Survivorship bias and corporate-action checks
- [~] 21 No benchmark / risk-adjusted metrics
- [ ] 22 Circuit limits not modelled
- [~] 23 Accuracy metrics not fed back; IC ignores ties; raw vs excess return
- [ ] 24 No gate-effectiveness or strategy attribution

### P3 — Better LLM inputs and calibration
- [x] 25 Sentiment mapped to fixed ±75, confidence ignored
- [ ] 26 "Fundamentals" is price-only and duplicates Tech
- [x] 27 Headlines lack date/source; `{symbol}` placeholder unfilled
- [ ] 28 No structured Indian-market data in prompt
- [ ] 29 Article truncation before ranking/dedupe
- [ ] 30 No determinism or grounding checks
- [ ] 31 Synthesis LLM adds little decision value
- [~] 32 Hard-coded signal confidence (1.0 / 0.5)
- [~] 33 LLM failure silently becomes NEUTRAL / keyword result

### P4 — Multi-strategy, regime, risk
- [ ] 34 Live fixed to one strategy
- [ ] 35 Add standard NSE swing setups
- [ ] 36 Market-regime filter
- [ ] 37 Relative strength vs Nifty/sector
- [ ] 38 Liquidity / surveillance / event filters
- [ ] 39 Exit management (trailing, partial, breakeven)
- [ ] 40 Sector / correlation exposure limits
- [ ] 41 Parameter optimization with overfit control
- [ ] 42 Entry strictness produces almost no BUYs

---

## A. Scoring and composite analysis

### 1. FIXED — Composite weights displayed ≠ weights used
`CompositeAnalysisService` now defines News 0.30 / Tech 0.40 / Fundamentals 0.30 once and uses
those constants for both source display and calculation.

### 2. FIXED — Missing news weight not renormalized
When `articleCount == 0`, the remaining dimensions are divided by their used weight, so missing
news no longer pulls scores toward HOLD (`weightedSum / usedWeight`).

### 3. FIXED — `Math.round((int) weightedSum)`
The cast truncates before rounding, so the round does nothing (for example, 20.9 becomes 20, which is HOLD).
The cast was removed; `(int) Math.round(weightedSum / usedWeight)` now rounds the final score.

### 4. FIXED — Tech score "52W high" uses the full candle history
`TechnicalAnalysisService` now takes the maximum high from the latest 252 candles, matching the live engine.
**Fix:** Limit to the latest `FIFTY_TWO_WEEK_TRADING_DAYS` candles, or reuse `PriceActionSignalEngine.analyze` output.

### 5. FIXED — Two ATR formulas
`RiskCalculator.calculateATR` now uses true range, including the prior close, and Wilder smoothing;
the signal pipeline passes the engine ATR directly. A regression test covers an overnight gap.

### 6. FIXED — Two indicator implementations
The apparent duplicate is not a formula mismatch: `strategy/TechnicalIndicators` delegates EMA, RSI, SMA,
ATR and volume calculations to TA4J, while the live signal and backtest instantiate the same TA4J indicators
directly. Technical and fundamental scoring therefore share TA4J semantics. A future refactor may share one
snapshot for efficiency, but it is no longer a correctness discrepancy.

### 25. FIXED — Sentiment mapped to fixed ±75
`buildNewsScore` now maps direction to `±100 × confidence`; article-count shrinkage remains a
follow-up calibration choice.

### 26. IMP — "Fundamentals" is price-only
`FundamentalScorer` = ATR%, 30-day momentum, volume trend and SMA50. This overlaps Tech, so price momentum is about 70% of the composite.
**Fix:** Rename it "Price Quality" for now. Add a real fundamentals score (EPS/revenue growth, ROE, D/E, promoter pledge) from filings or a data vendor.

### 32. PARTIALLY FIXED — Hard-coded signal confidence
`SignalPipeline` now derives bounded confidence from RSI location and EMA separation, so BUY/SELL/HOLD
signals no longer all receive fixed conviction values. The mapping is explainable but not yet
calibrated against forward outcomes or used for sizing/ranking.

---

## B. Backtest realism

### 11. FIXED — Indian transaction costs missing
The default `ZerodhaDeliveryCostModel` applies STT on both legs, exchange/regulatory charges,
buy-side stamp duty, GST, DP charge, and configured brokerage in `closeTrade`. A broker-specific
model can replace it later without changing the simulation contract.

### 12. FIXED — Exit slippage missing
`closeTrade` now applies `slippagePct` adversely on stop, target, signal, trend, and time exits.

### 13. FIXED — Gap-through stops fill at the stop price
Backtest and paper stop/target execution now use the bar open when it gaps through a trigger;
otherwise they use the trigger price. Regression tests cover both long stop and target gaps.

### 16. FIXED — No mark-to-market equity curve
`capitalCurve` now includes unrealized P&L at each bar close, so daily returns include open-position risk.
The marked-to-market value is recorded before each daily observation, so Sharpe and drawdown include open-position risk.

### 17. GAP — No portfolio-level backtest
Each symbol is simulated in isolation with full capital. `maxConcurrentPositions` is unused, and there is no capital competition,
sector overlap or combined equity curve.
**Fix:** Add `PortfolioBacktestEngine`: one date-driven loop across symbols, shared cash, position cap, ranking for competing entries.

### 18. PARTIALLY FIXED — Candidate scan qualifies on in-sample backtest
`CandidateScanService` now runs a configurable 60–1000-day out-of-sample window (252 days by default), persists its date range and metrics separately,
and requires both the full-history and OOS trade-count, win-rate and return gates before a result qualifies. Candidate discovery remains read-only;
watchlist activation is handled separately by the API/UI. The windowed backtest retains prior warm-up candles and prevents entries outside the
evaluation boundary. Full multi-fold walk-forward validation and portfolio-level OOS evaluation remain open, and the current OOS window overlaps
the descriptive full-history backtest rather than representing a fitted-model holdout.
**Remaining:** qualify across multiple rolling folds (years 1–2 train, year 3 validate) and report stability across folds.

### 19. FIXED — No minimum trade count
Candidate scans now require at least 15 trades by default, configurable through
`candidate-scan.min-trades`, before applying the win-rate and return gates. The no-loss profit-factor
cap remains separately documented as a reporting limitation.

### 20. PARTIALLY FIXED — Survivorship bias and corporate actions
Technical-analysis and backtest TA4J inputs now use an analytical candle view that scales OHLC by
`adjClose / close` when valid, while raw OHLC remains persisted and is not rewritten for execution.
This removes a major split/bonus discontinuity from indicators, ATR, breakouts and analytical exits.
The universe is still the current symbol master/watchlist, delisted and merged names have no
date-effective eligibility snapshot, and Yahoo adjusted close is total-return adjusted rather than
an event-specific corporate-action feed.
**Remaining:** Add historical universe snapshots/action provenance and a warning/quarantine policy for unexplained large gaps.

### 21. PARTIALLY FIXED — No benchmark or risk-adjusted metrics
Single-symbol backtest results now expose CAGR, Sortino and Calmar. CAGR uses the evaluated
calendar dates; Sortino uses the daily marked-to-market equity curve and downside deviation; Calmar
uses CAGR divided by maximum drawdown. Benchmark/buy-and-hold comparison, exposure, average R,
and alpha/beta remain open until a benchmark data contract and shared-capital portfolio semantics
are defined.
**Remaining:** Add aligned NIFTY 50 / NIFTY 500 TRI benchmark returns and portfolio-level risk metrics.

### 22. GAP — Circuit limits not modelled
A stock closing at the upper circuit can't be bought at next open; at the lower circuit it can't be exited.
**Fix:** Store the price band per symbol. Skip entry when the next open is at the upper limit and defer exit while locked at the lower limit.

---

## C. Paper trading and execution (new in this pass)

### 7. FIXED — Paper monitor runs before EOD ingestion
`PaperTradingMonitorService` now runs at 16:45, after `EodIngestionScheduler` loads today's candle at 16:30; the redundant candle write was also removed.

### 8. FIXED — Pending orders fill at the previous close
`PendingOrderExecutionScheduler` now runs at 16:35, after EOD ingestion, reads the latest candle
open, and applies configured adverse slippage before filling. This settles the queued order using
the next session's open represented by the newly ingested EOD candle rather than the prior close.

### 9. FIXED — Orchestrator fills BUY at signal-day close
`JobOrchestratorService` now calls `TradingService.queueSignal`; the scheduler fills that pending
market order at the next session open with adverse slippage, matching the backtest timing model.

### 10. FIXED — Capacity check uses fixed quantity 100
`PaperTradingEngine.executeSignal` now calculates size first and validates capacity with the actual
quantity, eliminating the fixed-100 pre-check. Quantity clamping remains a separate risk-policy decision.

### 24. GAP — No gate-effectiveness or strategy attribution
Every BUY is stored and the SUPPRESS verdict is recorded, but nobody measures forward returns of blocked vs allowed BUYs, or P&L by strategy/regime.
**Fix:** Nightly job: 5/10/20-day forward returns for each BUY tagged by verdict, strategy and regime. Show it on the dashboard.

---

## D. LLM and sentiment

### 14. PARTIALLY FIXED — Sentiment not point-in-time
The previous implementation fetched live news without a date-bounded source, so backfills and accuracy
evaluation could use news published after the decision date (look-ahead). `SentimentService` now
filters fetched articles to `publishedAt ≤ date 15:30 IST` and ≥ date − 7d
before cleaning, truncation, and prompting. For past dates, `NewsIngestionService` reads the
persisted inclusive symbol/date window and does not call live feeds. Articles without timestamps
are rejected because their point-in-time position cannot be proven. Persisted article IDs and
first-seen provenance for exact evidence reconstruction remain open.

### 15. PARTIALLY FIXED — Plain-text fallback misclassifies
Malformed/empty responses now return `UNKNOWN` with zero confidence, and the gate flags UNKNOWN.
The keyword fallback remains explicitly marked `KEYWORD` and now ignores locally negated terms and
peer/competitor-scoped phrases such as “not positive” and “positive for peers”. Broader contextual
language, sarcasm, and domain-specific polarity remain open.

### 27. FIXED — Headlines lack date/source; `{symbol}` never substituted
Sentiment prompts now format each item with an index, publication date and source, substitute `{symbol}`
in the system prompt, and filter against the actual seven-day decision window. Historical article IDs and
persisted-news reconstruction remain part of item 14.

### 28. GAP — No structured Indian-market data in prompt
The prompt names FII/DII activity and promoter actions but provides no data for them.
**Fix:** Add a structured block: last-quarter results YoY, promoter holding and pledge change, FII/DII holding change, bulk/block deals,
delivery %, ASM/GSM flag and next results date. `EarningsData` and `StructuredFiling` already exist.

### 29. IMP — Truncation before ranking and dedupe
`MAX_ARTICLES_FOR_LLM = 10` keeps the first 10 in fetch order. The same story from 5 outlets can use up the budget.
**Fix:** Run `NewsFilterService` ranking, dedupe by normalized-title similarity, prioritize NSE/BSE filings, then take the top N.

### 30. IMP — No determinism or grounding
A single sample at non-zero temperature. Red flags and catalysts can be invented.
**Fix:** Temperature 0; optional 3-sample majority vote (disagreement lowers confidence). Require each flag or catalyst to cite a headline index and drop uncited items.

### 31. IMP — Synthesis LLM adds little decision value
`SynthesisService` restates numbers already computed. Its recommendation isn't measured.
**Fix:** Narrow the job to conflict detection and event risk (results or ex-date within holding window). Track its recommendation accuracy like sentiment.

### 33. PARTIALLY FIXED — LLM failures hidden
Top-level exceptions return a default NEUTRAL; LLM outages fall back to keyword sentiment stored as if it were an LLM result.
Sentiment rows now persist `source = LLM | KEYWORD | DEFAULT`, and UNKNOWN is preserved. Excluding
non-LLM rows from accuracy statistics and correlating rows to audit request IDs remain open.

### 23. PARTIALLY FIXED — Accuracy metrics not fed back; IC formula
`SentimentAccuracyService` computes IC, ECE and regime accuracy, but nothing uses them. Scores are
nearly all tied (3 categories), so the prior no-ties shortcut distorted IC. "UP/DOWN/FLAT" still
uses raw returns, so a bull market can make POSITIVE look accurate.
The IC calculation now uses Pearson correlation over average ranks, correcting the tied-score
distortion. Accuracy is still based on raw returns, no Nifty excess-return input exists, and no
gate/composite weight consumes trailing IC.

---

## E. Strategy, regime and risk

### 34. GAP — Live fixed to one strategy
`StrategyRegistry` supports many strategies but live signals and the orchestrator use only `defaultStrategy()`. The signal
strategy label is the string `"DEFAULT"`.
**Fix:** Add `strategy_config` (enabled, capital %, params). Loop enabled strategies in SIGNAL stage and persist `strategy` on signals and positions.

### 35. GAP — Add standard NSE swing setups
**Fix:** Add `TradingStrategy` beans, backtest each, and enable only those passing out-of-sample:
- Pullback to EMA20 in an uptrend (price > EMA50 > EMA200, RSI 40–50 bounce)
- Base or volatility-contraction breakout (NR7 / Bollinger squeeze + 2× volume close above range)
- 52-week-high breakout (close above prior 252-bar high, not "within 3%")
- RSI(2) < 10 mean reversion above the 200-day moving average, exit on close > SMA5
- Sector rotation: buy RS leaders in top-3 sectors by 3-month return

### 36. GAP — Market-regime filter
No index-trend or volatility gate exists; momentum breakouts lose heavily in falling markets.
**Fix:** `RegimeService`: Nifty vs 200-day average, India VIX band, and breadth (% of NIFTY 500 above 50-day average). Each strategy declares allowed regimes.

### 37. GAP — Relative strength
Entry rules ignore performance vs the index and sector.
**Fix:** Add an RS line (stock/Nifty) with a rising 63-day RS rank filter (for example, top 30% of universe).

### 38. GAP — Liquidity, surveillance and event filters
**Fix:** Reject if 20-day average traded value < ₹5 Cr, the stock is in ASM/GSM or F&O ban, it has a 5%/10% price band, or results or a board meeting falls within 5 trading days.

### 39. GAP — Exit management
Exits use a fixed 2×ATR stop, 2.5R target, EMA trend-break and time stop. Partial exit exists in `PaperTradingEngine` but the strategy never uses it.
**Fix:** Take 50% off at 1.5–2R, move the stop to breakeven, and trail the rest with a chandelier (3×ATR) stop. Backtest against the current exits.

### 40. GAP — Sector and correlation limits
Nothing stops all positions landing in one sector (already noted in `docs/plans/2026-09-03-strategy-and-platform-roadmap.md`).
**Fix:** Max N positions / X% capital per sector in `CapitalTracker`. Optionally reject entries with > 0.7 60-day correlation to an existing holding.

### 41. IMP — Parameter optimization with overfit control
Thresholds are compile-time constants in `StrategyParams`.
**Fix:** Move params into `strategy_config`. Add a grid or walk-forward optimizer reporting a stability heatmap and deflated Sharpe; reject spiky optima.

### 42. BUG (behavioral) — Entry confluence produces almost no BUYs
4-of-4 entry vs 1-of-3 exit. The 2026-09-03 roadmap records zero BUYs across 14 stocks. RSI 50–65 plus "within 3% of 52w high"
plus a 1.5× volume surge rarely line up on the same bar, because a strong stock near its highs usually has RSI > 65.
**Fix:** Backtest 3-of-4 and RSI 55–75 variants on the portfolio engine (#17) with costs (#11) before changing live rules.

---

## Special mentions

- **Fix measurement before strategy.** Items 7–13 and 16 mean the backtest and paper results don't reflect real outcomes.
  Tuning a strategy against them makes the strategy fit the bugs, not the market.
- **Paper and backtest must use the same execution model.** Put one `ExecutionModel` (fill price, slippage, costs, gaps, circuits) in `core`
  and use it in both `BacktestEngine` and `PaperTradingEngine`, so the two numbers can be compared directly.
- **Security before live money.** No API authentication exists (roadmap Phase 5). `GpuHubController` was re-disabled for RCE risk. Keep broker live mode off until auth lands.
- **Data source risk.** Yahoo Finance is unofficial and rate-limited, and NSE symbols sometimes return stale or unadjusted bars. Fyers/Upstox historical APIs
  are already integrated; make them primary for OHLC and keep Yahoo as fallback with a cross-check.
- **Regulatory.** SEBI's 2025 algo-trading framework for retail requires broker-registered algos (algo ID tagging) above order-rate thresholds and
  API access via static IP. Check before automating live orders through Fyers/Upstox/Kite.
- **Taxes.** Short-term capital gains (≤ 12 months) are taxed at 20% since July 2024. Report post-tax returns for a realistic comparison with buying and holding an index fund.
- **T+1 settlement.** Sell proceeds are available next day; a portfolio engine should not recycle cash from a same-day exit into a new entry in live mode.
- **Holidays / special sessions.** `NseHolidayService` exists; make sure schedulers (EOD 16:30, pending 16:35, orchestrator 18:00) skip holidays and handle Muhurat trading.
- **Stale repo artifacts.** `OhlcvCandleRepository.java.bak2` in `backend/data` and several `.claude/worktrees/*` copies of the backend can confuse search and review. Clean up.
- **Previous roadmap overlap.** Items 36, 40 and 42 overlap `docs/plans/2026-09-03-strategy-and-platform-roadmap.md`. Merge them there when planning.

## Future ideas

- **Feature store + ML ranker:** after #24 produces labeled forward returns, train a gradient-boosted ranker on technical + sentiment + RS + regime features, with purged time-series CV.
- **Options-derived signals:** PCR, OI build-up and IV rank for F&O stocks as sentiment confirmation.
- **Delivery % and bulk/block deals** from NSE bhavcopy as accumulation signals.
- **Earnings-call / annual-report RAG:** embed filings and let the LLM answer targeted questions (guidance change, capex, pledge) instead of summarizing headlines.
- **Local-model evaluation harness:** fixed labeled headline set to compare llama.cpp / MLX / GPUHub models on accuracy, latency and JSON validity before switching backends (PR #94 adds MLX).
- **Monte Carlo on trade sequence:** bootstrap trade order to estimate drawdown distribution and risk of ruin at the chosen risk-per-trade.
- **Kelly-fraction / volatility-target sizing** per strategy once per-strategy expectancy is stable.
