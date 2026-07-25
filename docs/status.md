# Pre-Pilot Status

Last checked: 2026-07-25

## Data

- [x] 3yr candles backfilled for all 15 stocks
- [ ] Daily EOD scheduler tested - ran at least once successfully
- [ ] No data gaps - check /api/ingestion/status
- [x] NSE holidays set for FY27 in scheduler

## Strategy

- [ ] Backtest run on all 15 stocks
- [ ] Win rate > 45% on at least 8 of 15 stocks
- [ ] Max drawdown < 20% on portfolio
- [ ] Signal scanner ran today - check /api/signals/latest
- [ ] Manually verify 1 signal against TradingView chart

## Paper Trading

- [ ] Initial capital set: Rs.5,00,000
- [ ] Max positions: 5
- [ ] Max capital per position: 20%
- [ ] Risk per trade: 1%
- [ ] 9:15am scheduler tested - fills pending orders
- [ ] 3:30pm monitor tested - checks SL/target
- [ ] 3:45pm snapshot tested - saves portfolio state
- [ ] Manual close position tested via API

## LLM Layer

- [x] News ingestion fetching for all 15 stocks
- [ ] Sentiment running on BUY signals
- [ ] NEGATIVE signals being suppressed
- [x] Accuracy tracker recording outcomes - pipeline verified end-to-end: evaluation job (nightly 2 AM), 8 metric endpoints, prompt_hash/model_version tracking, SMA200 regime detection. Fixed: OhlcvCandleRepository query returning multiple results (added LIMIT 1), SentimentAccuracyEntity createdAt not set (null constraint violation), SentimentAccuracyService missing LocalDateTime import. VERIFIED: POST /api/sentiment/evaluate/trigger processes pending sentiments, saves accuracy records, computes returns/labels/regimes.
- [x] Graceful degradation tested - kill vLLM, confirm NEUTRAL default

## Dashboard

- [ ] dashboard.html loading at localhost:8080
- [ ] Equity curve rendering
- [ ] Open positions showing with live LTP
- [ ] Signals table showing today's signals
- [ ] Auto-refresh working every 60 seconds
- [ ] All REST endpoints returning 200

## Pilot Stocks Confirmed

- [ ] NMDC - BUY signal active
- [ ] SUNPHARMA - BUY signal active
- [ ] BHARTIARTL - BUY signal active
- [ ] ADANIPORTS - BUY signal active
- [ ] All 4 sentiment: POSITIVE or NEUTRAL
- [ ] All 4 paper orders placed for tomorrow open

## Go / No-Go

All boxes checked -> PILOT STARTS
Any box failing -> Fix before starting
Backtest below 45% -> Tune strategy parameters first
LLM endpoint down -> Still go - NEUTRAL default is safe