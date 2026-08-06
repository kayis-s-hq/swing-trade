# Backtest Dashboard View

## Context

Dev stack was started (`./dev-stack.sh start`): backend API on `:8080` (health UP,
db + redis UP), dashboard on `:3003`, infra containers (postgres/redis) running on
pi-node. The backend already exposes a full backtest engine
(`backend/strategy/BacktestEngine`, `BacktestController` at `/api/backtest/*`,
documented in `docs/backtesting.md`), but the Vue dashboard had **no UI** for it —
no view, no route, no API client method. Decided (user confirmed) to build a
dedicated `/backtest` view rather than folding it into `PortfolioView` (portfolio
is wired to live trading data; backtest is a separate "run against history" concept
with its own inputs/outputs).

## Backend endpoints being wrapped

- `POST /api/backtest/run?symbol=X&exchange=NSE` — single symbol, default `BacktestConfig`, returns `BacktestResult`
- `POST /api/backtest/run-all?exchange=NSE` — whole active watchlist, saves JSON+CSV report, returns `BacktestReportSummary`
- `GET /api/backtest/reports` — list saved report filenames, most recent first
- `GET /api/backtest/reports/{filename}` — fetch a saved report JSON

## Changes made

1. **`dashboard/src/api/types.ts`** — added `BacktestTrade`, `BacktestResult`,
   `BacktestReportSummary` interfaces mirroring the backend records
   (`BacktestTrade.java`, `BacktestResult.java`, `BacktestReportSummary.java`).
2. **`dashboard/src/api/client.ts`** — added `runBacktest`, `runBacktestAll`,
   `listBacktestReports`, `getBacktestReport`, following the existing
   `rawFetch`/`ApiResponse<T>` wrapper pattern used by every other client function.
3. **`dashboard/src/views/BacktestView.vue`** (new) —
   - Run form: symbol input + exchange select (NSE/BSE) + "Run Backtest" button,
     plus a "Run Whole Watchlist" button.
   - Single-result panel: `MetricCard` grid (trades, win rate, total return, Sharpe,
     avg gain/loss, max drawdown, expectancy) + a trades table (entry/exit
     date/price, exit reason, P&L, P&L%, holding days).
   - Watchlist summary panel: aggregate metrics + per-symbol results table, shown
     after `run-all` or when opening a saved report.
   - Saved reports list with a "View" action per report (calls `getReport` and
     renders it in the summary panel).
   - Followed `WatchlistView.vue`'s existing form/table Tailwind conventions.
4. **`dashboard/src/router/index.ts`** — added `/backtest` route (lazy-loaded).
5. **`dashboard/src/components/Icons.ts`** — added a `backtest` icon path (clock/
   history glyph).
6. **`dashboard/src/components/Sidebar.vue`** — added nav item linking to
   `/backtest` between Watchlist and Data.

## Verification so far

- `npm run typecheck` (vue-tsc) passes clean.
- Backend health confirmed UP via `curl localhost:8080/actuator/health`.
- Dashboard dev server confirmed serving on `:3003` (Vite ready).
- **Not yet done**: actually opening `/backtest` in a browser and clicking through
  the run flow (browser navigation tool call was rejected mid-session).

## Remaining steps to manually test

1. Ensure dev stack is running: `./dev-stack.sh status` (or start it if not).
2. Ensure at least one watchlist symbol has enough OHLCV history (check
   `/api/data/status` or the Data Ingestion view — the engine needs sufficient
   candle history or `/run` returns 404).
3. Open `http://localhost:3003/backtest` in a browser.
4. Single-symbol run: enter a symbol known to have data (e.g. one shown as
   "hasData: true" in Data Ingestion), exchange NSE, click "Run Backtest".
   Expect a metrics grid + trades table to render, or a friendly error if that
   symbol lacks enough history.
5. Watchlist run: click "Run Whole Watchlist". Expect the summary panel to
   populate and a new entry to appear in "Saved Reports" (this call also writes
   a report file server-side under `backend/api`'s configured `reports` dir).
6. Saved reports: click "View" on a report row, confirm the summary panel
   re-renders with that report's data via `GET /api/backtest/reports/{filename}`.
7. Sanity-check numbers against `docs/backtesting.md`'s field definitions
   (win rate, Sharpe, expectancy, etc.) for at least one manually-reasoned trade.
