# Pre-Pilot Status

Last checked: 2026-08-08

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
- [x] Strategy consolidated: SwingTradingStrategy deprecated, PriceActionSignalEngine is the single engine (uses TA4j + DecimalNum precision)
- [x] SignalPipeline uses PriceActionSignalEngine for both primary and price-action signals
- [x] BacktestEngine and live engine share same constants from StrategyParams

## Paper Trading

- [x] Initial capital set: Rs.5,00,000 (PaperTradingProperties.initialBalance=500000, injected into PaperTradingEngine)
- [x] Max positions: 5 (PaperTradingProperties.maxConcurrentPositions=5, wired through PositionManager)
- [x] Max capital per position: Rs.2,00,000 (PaperTradingProperties.maxCapitalPerPosition=200000, aligned with BrokerProperties)
- [x] Risk per trade: 1% (changed from 2% hardcoded to 1% in calculatePositionSize)
- [x] CapitalTracker uses PaperTradingProperties (not BrokerProperties) for initial balance, max positions, max capital
- [x] @Transactional added to all closePosition methods (PositionService, PositionManager, PaperTradingEngine, PaperTradingStateService)
- [x] Null direction guards in PaperTradingServiceImpl, LiveTradingService, PositionService
- [x] DailyLossCircuitBreaker persists to DB (V4 migration, DailyLossCircuitBreakerStateEntity) — survives restarts
- [ ] 9:15am scheduler tested - fills pending orders (SignalExecutionJob uses fixedDelay=30s poller, no cron-based 9:15am job)
- [x] 3:30pm monitor cron set (PaperTradingMonitorService: 15:30 IST) — needs runtime test
- [x] 3:45pm snapshot cron set (PortfolioSnapshotScheduler: 15:45 IST) — needs runtime test
- [x] Manual close position endpoint exists (POST /api/positions/{symbol}/close via PositionController)

## Audit Fixes (2026-08-08)

17 phases completed from architecture audit (64 findings):

### Backend (8 phases)
- [x] H5: TradeRequest.isValid() operator precedence fix
- [x] H6: Null direction guards in 3 places (PaperTradingServiceImpl, LiveTradingService, PositionService)
- [x] H7: PositionEntity.toDomain() null status → defaults to OPEN
- [x] C4: @Transactional on 7 closePosition methods
- [x] H4: Capital config mismatch resolved (CapitalTracker → PaperTradingProperties)
- [x] C2: Trade.close() PnL correct for SHORT + fees (TradeDirection field added)
- [x] H1: DailyLossCircuitBreaker DB persistence (entity + repo + migration + wiring)
- [x] C6: Strategy consolidation (SwingTradingStrategy deprecated, PriceActionSignalEngine unified)

### Frontend (9 phases)
- [x] H9: 19 `as any` casts → `unwrap<T>()` helper (1 remaining safe `as any`)
- [x] H10: SSE 60s timeout with AbortController (both generators)
- [x] H11: Position type synced (24 fields: brokerType, direction, entryReason, etc.)
- [x] H12: rawFetch retry with exponential backoff (1s/2s/4s, max 3 retries)
- [x] C8: ErrorBoundary component created and applied to all 9 views
- [x] H8: useAsyncData<T>() composable replacing ~63 lines of boilerplate
- [x] M14: Currency standardized to `Rs.` across all views
- [x] H15: Allocation read from settings store (allocationPerPosition)
- [x] View integration: all 9 views wrapped in ErrorBoundary + useAsyncData

Dismissed (not bugs): C1 (param order safe), C3 (.env not in git), C5 (backtest precision safe)

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