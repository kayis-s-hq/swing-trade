# Strategy & Platform Roadmap — 2026-09-03

Source: source-code-verified review session (not doc-based). Scope: trading
strategy effectiveness and platform readiness for live pilot.

## Phase 0 — Fix known bugs (1 day)

- `KillSwitchService.active` (broker/risk/KillSwitchService.java) → make `volatile`.
  Not currently thread-safe; a trade thread can read a stale `false` after activation.
- `SignalService`'s latest-per-symbol lookup still calls `findAll()` + in-memory
  dedupe. Every other query method already has a real DB query — bring this one
  in line before the signals table grows further.

## Phase 1 — Unblock the strategy (highest priority)

- `PriceActionStrategy` requires 4-of-4 rules for BUY (trend, RSI 50-65, volume
  1.5x, within 3% of 52-week high) but only 1-of-3 for SELL. Result: zero BUY
  signals across 14 stocks in the entire signal history.
- Loosen entry to 3-of-4, or widen the RSI/volume/high-proximity bands.
- Backtest each candidate variant against the existing 14-stock, 3-year dataset
  before touching the live engine.
- Goal: produce real BUY signals so the sentiment gate and paper execution path
  get exercised end-to-end for the first time.

## Phase 2 — Risk / sizing upgrade

- Add ATR-based stop-loss and target — ATR14 is already computed in
  `PriceActionSignalEngine` but currently unused.
- Move position sizing from the flat ₹1,00,000 cap to a volatility-scaled size.
- Add a max-positions-per-sector rule to `CapitalTracker` (nothing currently
  stops 5/5 concurrent positions landing in one sector).

## Phase 3 — Market structure filter

- No Nifty/sector regime filter exists anywhere in `strategy` (confirmed:
  zero matches for regime/index-trend logic).
- Add an index-trend gate (e.g. Nifty > 200 EMA) ahead of the per-stock rules.
- Re-run the backtest with the filter on/off and compare win rate and
  max drawdown.

## Phase 4 — Execution realism

- Orders currently fill next-day at 9:15am via `PendingOrderExecutionScheduler`,
  not same-day. Measure signal-day-close vs. next-day-open slippage in
  backtest vs. live before deciding whether same-day limit orders are worth
  adding.

## Phase 5 — Security, before any real money

- No API authentication exists on any endpoint (`backend/api` — confirmed,
  no `SecurityFilterChain` anywhere). Mandatory gate before live broker mode;
  not required for continued paper trading.
- Split the `Position` record (still 23 fields) while auth work touches this
  layer anyway.

## Phase 6 — Pilot go/no-go

Re-run the `docs/status.md` pilot checklist once Phases 1–3 land. Require:

- Win rate > 45% on at least 8 of the active watchlist stocks.
- Max drawdown < 20% on the portfolio equity curve.
- At least one real BUY signal verified sentiment-gated and paper-filled
  end-to-end.

## Sequencing rationale

Phases 0 and 1 unblock everything downstream — no other improvement
(sentiment gating, execution timing, capital rules) is testable while BUY
never fires. Security (Phase 5) only gates live-broker trading, not paper
trading, so it trails the strategy work rather than blocking it.
