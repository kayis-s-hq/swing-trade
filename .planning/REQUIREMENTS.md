# REQUIREMENTS.md - SwingTrade System Requirements

**Document Version:** 2.0
**Created:** 2026-03-07
**Last Updated:** 2026-03-22 (Re-initialized with all 7 phases)
**Core Value:** Execute the 4-factor swing strategy across Nifty 500, validate with 2-3 months paper trading, then deploy live capital.

---

## v1 Requirements (Phases 1–7)

All v1 requirements are committed. Phases 1–3 are complete; Phases 4–7 are pending.

---

## Phase 1: Core Domain Implementation ✅ COMPLETE

**Objective:** Domain models with required fields and validation.
**Status:** Complete (2026-03-20)

### REQ-001: Stock Model

- [x] Symbol, exchange (NSE/BSE), name, sector, ISIN, lot size
- [x] Enum: Exchange (NSE, BSE)
- [x] Enum: Sector (17 sectors: AUTO, BANK, CHEMICAL, etc.)
- **File:** `core/src/main/java/com/swingtrade/domain/Stock.java`
- **Status:** ✅ Implemented

### REQ-002: OhlcvCandle Model

- [x] Symbol, date, open, high, low, close, volume, adjClose
- [x] Methods: getRange(), getChangePercent(), isBullish(), isBearish()
- [x] Factory: of() with auto-calculated adjClose
- **File:** `core/src/main/java/com/swingtrade/domain/OhlcvCandle.java`
- **Status:** ✅ Implemented

### REQ-003: Signal Model

- [x] ID, symbol, date, type (BUY/SELL/HOLD), confidence (0.0–1.0)
- [x] Reasoning, entryPrice, stopLoss, target, riskReward
- [x] Indicators field (which indicators triggered)
- [x] Enum: SignalType
- [x] Methods: create(), isBuySignal(), isSellSignal(), isHoldSignal()
- **File:** `core/src/main/java/com/swingtrade/domain/Signal.java`
- **Status:** ✅ Implemented

### REQ-004: Position Model

- [x] ID, symbol, entryPrice, entryDate, quantity, stopLoss, target, status
- [x] EntryReason, currentPrice
- [x] Enum: PositionStatus (OPEN, CLOSED, STOPPED, TARGET_HIT)
- [x] Methods: createWithRisk(), calculateUnrealizedPnL(), calculatePnLPercent()
- **File:** `core/src/main/java/com/swingtrade/domain/Position.java`
- **Status:** ✅ Implemented

### REQ-005: Trade Model

- [x] ID, positionId, symbol, entryDate, exitDate, entryPrice, exitPrice
- [x] Quantity, totalPnL, durationDays, status, entryReason, exitReason, fees
- [x] Enum: TradeStatus (OPEN, CLOSED, STOPPED, TARGET_HIT, TIME_STOP)
- [x] Methods: open(), close(), isProfitable(), isLoss(), isOpen()
- **File:** `core/src/main/java/com/swingtrade/domain/Trade.java`
- **Status:** ✅ Implemented

### REQ-006: SentimentResult Model

- [x] ID, symbol, date, score (POSITIVE/NEUTRAL/NEGATIVE), summary, rawContent
- [x] Confidence (0.0–1.0), analyzedAt
- [x] Enum: SentimentScore
- [x] Methods: create(), isPositive(), isNeutral(), isNegative(), supportsEntry()
- **File:** `core/src/main/java/com/swingtrade/domain/SentimentResult.java`
- **Status:** ✅ Implemented

---

## Phase 2: Strategy Engine ✅ COMPLETE

**Objective:** Technical indicators and multi-factor signal generation.
**Status:** Complete (2026-03-20)

### REQ-007: TechnicalIndicators Service

**4-factor entry conditions (all must be true):**

- [x] **REQ-007a: EMA Crossover** — Price > EMA20 > EMA50 (uptrend confirmed)
  - calculateEMA(20), calculateEMA(50)
  - File: `strategy/src/main/java/com/swingtrade/strategy/TechnicalIndicators.java`
  - Status: ✅ Implemented (TA4J v0.16)

- [x] **REQ-007b: RSI Analysis** — RSI(14) between 50–65 (momentum rising, not overbought)
  - calculateRSI(14)
  - File: `strategy/src/main/java/com/swingtrade/strategy/TechnicalIndicators.java`
  - Status: ✅ Implemented (TA4J v0.16, double-precision)

- [x] **REQ-007c: Volume Spike** — Volume > 1.5x 20-day average (conviction move)
  - calculateVolumeMA(20)
  - File: `strategy/src/main/java/com/swingtrade/strategy/TechnicalIndicators.java`
  - Status: ✅ Implemented

- [x] **REQ-007d: ATR-based Stops** — Average True Range for stop/target sizing
  - calculateATR(14)
  - File: `strategy/src/main/java/com/swingtrade/strategy/TechnicalIndicators.java`
  - Status: ✅ Implemented

- [x] **Additional:** SMA, MACD (used in strategy logic and backtests)
  - calculateSMA(), calculateMACD()
  - Status: ✅ Implemented

### REQ-008: DefaultStrategy (Multi-Factor Signal Generation)

- [x] **REQ-008a: Entry Logic** — ALL 4 factors must be true:
  1. Price > EMA20 > EMA50
  2. RSI(14) between 50–65
  3. Volume > 1.5x 20d average
  4. Price within 3% of 52w high OR breaking above resistance
  - Status: ✅ Implemented

- [x] **REQ-008b: Exit Logic** — ANY ONE triggers:
  1. Stop loss: price < entry − (2 × ATR14 at entry)
  2. Target: price > entry + (2.5 × risk) [1:2.5 R:R ratio]
  3. Time stop: position held > 20 trading days
  4. Trend break: close below EMA20 for 2 consecutive days
  - Status: ✅ Implemented

- [x] **Confidence Calculation** — Based on factor agreement
  - High (0.8–1.0): All factors aligned
  - Medium (0.5–0.8): Partial alignment
  - Low (0.0–0.5): Conflicting signals
  - Status: ✅ Implemented

- **File:** `strategy/src/main/java/com/swingtrade/strategy/impl/DefaultStrategy.java`
- **Status:** ✅ Implemented

### REQ-009: SignalEngine

- [x] Scheduled generation at 17:00 IST (weekday)
- [x] Cron configuration correct, skips weekends
- [x] Manual trigger via API endpoint
- [x] Nifty 500 stock coverage
- [x] Redis caching
- **File:** `strategy/src/main/java/com/swingtrade/strategy/SignalEngine.java`
- **Status:** ✅ Implemented

### REQ-010: BacktestEngine

- [x] Historical backtesting over date range
- [x] Accurate trade counting
- [x] Equity calculation
- [x] Performance metrics: total P&L, win rate, Sharpe ratio, max drawdown
- [x] Position sizing: 1% risk/trade, max 5 concurrent, max 20% capital/position
- **File:** `strategy/src/main/java/com/swingtrade/strategy/impl/DefaultBacktestEngine.java`
- **Status:** ✅ Implemented

---

## Phase 3: Data Pipeline + Signal Engine + Paper Trading ✅ COMPLETE

**Objective:** OHLCV data ingestion, storage, signal generation, paper trading.
**Status:** Complete (2026-03-20)

### REQ-011: MarketDataClient (Upstox API)

- [x] POST /v2/login — Authentication
- [x] POST /v2/token — Token refresh
- [x] GET /v2/market-data/ohlcv — OHLCV data
- [x] GET /v2/instruments — Instrument list
- [x] Automatic token refresh
- [x] Retry logic for failed requests
- [x] Response caching
- **File:** `data/src/main/java/com/swingtrade/data/client/UpstoxRestClient.java`
- **Status:** ✅ Implemented

### REQ-012: DataIngestionService

- [x] Daily OHLCV data ingestion
- [x] Nifty 500 stock coverage
- [x] Data quality validation (prices positive, high >= max(o,c), low <= min(o,c), vol >= 0)
- [x] Gap detection and repair
- [x] DataQualityReport generated
- **File:** `data/src/main/java/com/swingtrade/data/service/DataIngestionService.java`
- **Status:** ✅ Implemented

### REQ-013: Repository Layer

- [x] OhlcvCandleRepository — OHLCV CRUD
- [x] StockRepository — Stock entity management
- [x] SignalRepository — Signal storage
- [x] SentimentResultRepository — Sentiment storage
- [x] TradeRepository — Trade history
- [x] Custom query methods, time-range support
- **File:** `data/src/main/java/com/swingtrade/data/repository/*`
- **Status:** ✅ Implemented (Spring Data JPA)

### REQ-014: Schema Migrations (Flyway)

- [x] V1 — Core tables (stocks, ohlcv_candles, signals, positions, trades, sentiment_results)
- [x] V2 — TimescaleDB hypertables (ohlcv_candles)
- [x] V3 — Stocks table
- [x] V4 — Trades table
- [x] Proper indexing for performance
- **File:** `data/src/main/resources/db/migration/V*`
- **Status:** ✅ Implemented

### REQ-015: Scheduling

- [x] Auto-ingestion at 16:30 IST (Monday–Friday)
- [x] Manual trigger available
- **File:** `data/src/main/java/com/swingtrade/data/config/SchedulingConfig.java`
- **Status:** ✅ Implemented

### REQ-016: PaperTradingEngine

- [x] MARKET order execution
- [x] Position management
- [x] P&L calculation
- [x] Trade lifecycle (entry → exit)
- [x] Status updates
- **File:** `broker/src/main/java/com/swingtrade/broker/engine/PaperTradingEngine.java`
- **Status:** ✅ Implemented

### REQ-017: Risk Controls

- [x] **PositionLimitChecker** — Max 5 concurrent positions
- [x] **PositionSizeValidator** — Max 20% capital per position
- [x] **DailyLossCircuitBreaker** — Configurable daily loss limit (default 5%)
- [x] **RiskControlsService** — Orchestrates all checks, returns failure reasons
- **File:** `broker/src/main/java/com/swingtrade/broker/risk/*`
- **Status:** ✅ Implemented

### REQ-018: Telegram Notifications

- [x] Trade open notifications (position entry + details)
- [x] Trade close notifications (exit + P&L)
- [x] Stop loss hit notifications
- [x] Target hit notifications
- [x] Quiet hours support
- [x] Configuration via application.properties
- **File:** `broker/src/main/java/com/swingtrade/broker/telegram/*`
- **Status:** ✅ Implemented

### REQ-019: Broker Modes

- [x] PAPER — Paper trading (default)
- [x] DRY_RUN — Dry run mode
- [x] LIVE — Live trading (future)
- [x] BrokerServiceFactory routes based on config
- **File:** `broker/src/main/java/com/swingtrade/broker/config/BrokerMode.java`
- **Status:** ✅ Implemented

### REQ-020: TradingController

- [x] POST /api/trades — Place new trade
- [x] GET /api/portfolio — Portfolio overview
- [x] GET /api/positions — List all positions
- **File:** `api/src/main/java/com/swingtrade/api/controller/TradingController.java`
- **Status:** ✅ Implemented

### REQ-021: SignalController

- [x] GET /api/signals/latest — Latest signals
- [x] GET /api/signals/{symbol} — Symbol-specific signals
- [x] GET /api/signals — List all signals
- [x] Query params: signalType, minConfidence, date, limit
- **File:** `api/src/main/java/com/swingtrade/api/controller/SignalController.java`
- **Status:** ✅ Implemented

### REQ-022: PositionController

- [x] GET /api/positions — Open positions
- [x] GET /api/positions/{id} — Position details
- [x] POST /api/positions/{id}/close — Close position
- **File:** `api/src/main/java/com/swingtrade/api/controller/PositionController.java`
- **Status:** ✅ Implemented

### REQ-023: PerformanceService

- [x] Total P&L, win rate, total trades
- [x] Average win / Average loss
- [x] Profit factor
- [x] Max drawdown
- [x] Sharpe ratio
- **File:** `api/src/main/java/com/swingtrade/api/PerformanceService.java`
- **Status:** ✅ Implemented

### REQ-024: ScanService

- [x] GET /api/scan — Current scan results
- [x] POST /api/scan — Trigger manual generation
- [x] Returns all Nifty 500 signals
- [x] Signal distribution, confidence distribution, sector breakdown
- **File:** `api/src/main/java/com/swingtrade/api/ScanService.java`
- **Status:** ✅ Implemented

---

## Phase 4: LLM Sentiment Layer ⚠️ PARTIAL (Module exists, integration TBD)

**Objective:** News ingestion, sentiment analysis, signal filtering.
**Status:** Module partially implemented; integration with signal generation needs verification.

### REQ-025: LangChain4j Client

- [ ] Connects to vLLM endpoint (Qwen3-30B-AWQ, OpenAI-compatible)
- [ ] HTTP request/response handling
- [ ] Token counting for prompt optimization
- [ ] Error handling and retry logic
- **File:** `llm/src/main/java/com/swingtrade/llm/client/VLLMClient.java`
- **Status:** ⚠️ Implemented (verify integration)

### REQ-026: News Ingestion

- [ ] Google News RSS per stock (7-day history)
- [ ] NSE corporate announcements API
- [ ] News deduplication
- [ ] Storage in database with timestamps
- **File:** `llm/src/main/java/com/swingtrade/llm/service/NewsIngestionService.java`
- **Status:** ⚠️ Implemented (verify completeness)

### REQ-027: Sentiment Analysis Pipeline

- [ ] Structured prompt design
- [ ] Calls vLLM with last 7 days of news + company context
- [ ] Parses response: POSITIVE / NEUTRAL / NEGATIVE + 2-line reasoning
- [ ] Confidence score (0.0–1.0)
- [ ] Stores SentimentResult in database
- **File:** `llm/src/main/java/com/swingtrade/llm/service/SentimentAnalyzer.java`
- **Status:** ⚠️ Implemented (verify prompt quality)

### REQ-028: Signal Filtering

- [ ] BUY signals checked for sentiment
- [ ] NEGATIVE sentiment: signal suppressed from portfolio
- [ ] NEUTRAL sentiment: signal included but flagged with ⚠️ in Telegram
- [ ] POSITIVE sentiment: signal as-is
- [ ] Integration point: DefaultStrategy → SignalFiltering → Telegram
- **File:** `llm/src/main/java/com/swingtrade/llm/service/NewsFilterService.java`
- **Status:** ⚠️ Implemented (verify integration with SignalEngine)

### REQ-029: Weekly Sector Digest

- [ ] Sunday scheduled job (17:00 IST)
- [ ] Sentiment summary by sector (POSITIVE/NEUTRAL/NEGATIVE counts)
- [ ] Top 3 positive sectors, top 3 negative sectors
- [ ] Sent via Telegram
- **File:** `llm/src/main/java/com/swingtrade/llm/service/SentimentAnalysisService.java`
- **Status:** ⚠️ Implemented (verify scheduling)

---

## Phase 5: Testing Foundation ⏳ DEFERRED

**Objective:** 80%+ code coverage with unit + integration tests.
**Status:** Deferred (start after Phase 4 verification)

### REQ-101: Core Domain Unit Tests

- [ ] Stock tests: constructor, equality, hashCode, toString
- [ ] OhlcvCandle tests: validation, business methods, factory
- [ ] Signal tests: validation, factory, type checks
- [ ] Position tests: validation, P&L accuracy, factory
- [ ] Trade tests: validation, P&L, factory (open/close)
- [ ] SentimentResult tests: validation, factory, score checks
- **Files:** `core/src/test/java/com/swingtrade/domain/*Test.java`
- **Target Coverage:** 100% (core module)
- **Status:** ⏳ Deferred

### REQ-102: Strategy Unit Tests

- [ ] TechnicalIndicators tests: known input/output, edge cases, NaN behavior
- [ ] DefaultStrategy tests: BUY/SELL/HOLD scenarios, confidence calculation
- [ ] BacktestEngine tests: date range, trade count, performance metrics
- **Files:** `strategy/src/test/java/com/swingtrade/strategy/*Test.java`
- **Target Coverage:** 85%+ (strategy module)
- **Status:** ⏳ Deferred

### REQ-103: Integration Tests with TestContainers

- [ ] PostgreSQL + TimescaleDB TestContainer
- [ ] Schema migration verification
- [ ] CRUD operations with real database
- [ ] Redis TestContainer (optional, for cache tests)
- **Files:** `data/src/test/java/com/swingtrade/data/test/*`
- **Note:** Real database containers, not in-memory
- **Status:** ⏳ Deferred

### REQ-104: HTTP Mocking with MockRestServiceServer

- [ ] **Upstox API mocking** — login, token refresh, OHLCV, instruments
- [ ] **vLLM mocking** — chat completion endpoint, sentiment responses
- [ ] **NOT WireMock** — explicit project decision; MockRestServiceServer sufficient
- **Files:** `data/src/test/java/com/swingtrade/data/test/*`
- **Status:** ⏳ Deferred

### REQ-105: API Endpoint Tests

- [ ] Signal endpoints: GET /api/signals/latest, /{symbol}, with filters
- [ ] Trading endpoints: POST /api/trades (success, max positions, size exceeded)
- [ ] GET /api/portfolio
- [ ] Error handling: 400, 404, 409, 500
- **Files:** `api/src/test/java/com/swingtrade/api/controller/*Test.java`
- **Test framework:** @SpringBootTest + MockMvc
- **Status:** ⏳ Deferred

### REQ-106: Code Coverage Target (80%+)

- [ ] core: 100%
- [ ] strategy: 85%+
- [ ] data: 80%+
- [ ] llm: 75%+
- [ ] broker: 80%+
- [ ] api: 75%+
- [ ] **Tool:** JaCoCo with Maven profile `coverage`
- [ ] **Build:** Threshold violations fail build
- **Status:** ⏳ Deferred

---

## Phase 6: Live Trading ⏳ PLANNED

**Objective:** Zerodha Kite Connect integration with risk controls.
**Status:** Planned (start after Phase 5 complete + paper validation)

### REQ-030: Zerodha Kite Connect Integration

- [ ] Java SDK dependency (com.zerodha.kite)
- [ ] Authentication with API key + access token
- [ ] Order placement (BUY/SELL MARKET orders)
- [ ] Position tracking from Kite API
- [ ] P&L calculation using live positions
- **File:** `broker/src/main/java/com/swingtrade/broker/kite/*`
- **Cost:** ₹2000/year license
- **Status:** ⏳ Planned

### REQ-031: BrokerServiceFactory Routing

- [ ] Config property: `broker.mode` (PAPER / LIVE)
- [ ] Factory detects mode, injects PaperTradingEngine or KiteBrokerService
- [ ] Seamless switching without code changes
- **File:** `broker/src/main/java/com/swingtrade/broker/factory/BrokerServiceFactory.java`
- **Status:** ⏳ Planned

### REQ-032: Kill Switch

- [ ] Admin endpoint: POST /api/admin/kill-switch
- [ ] Halts all live orders immediately
- [ ] Closes all open positions at market
- [ ] Sends Telegram alert with summary
- [ ] Blocks further trading until manual reset
- **File:** `api/src/main/java/com/swingtrade/api/controller/AdminController.java`
- **Status:** ⏳ Planned

### REQ-033: Capital Management (Live Mode)

- [ ] Initial capital: ₹50,000
- [ ] Max concurrent live positions: 3 (vs 5 in paper)
- [ ] Position sizing: 1% risk per trade (same as paper)
- [ ] Daily loss circuit: 5% of capital (same as paper)
- **File:** `broker/src/main/java/com/swingtrade/broker/risk/CapitalTracker.java`
- **Status:** ⏳ Planned

---

## Phase 7: Observability + Iteration ⏳ PLANNED

**Objective:** Monitoring, reporting, trade labelling.
**Status:** Planned (start after Phase 6 deployed)

### REQ-034: Grafana Dashboards

- [ ] Connected to Spring Actuator metrics
- [ ] Real-time metrics: active positions, daily P&L, signal count
- [ ] Latency metrics: API response times, data ingestion duration
- [ ] Health checks: database, LLM endpoint, broker connection
- **Status:** ⏳ Planned

### REQ-035: Monthly Strategy Review Report

- [ ] Generated on first Sunday of month
- [ ] Win rate, average R:R, sharpe ratio, max drawdown
- [ ] Sector breakdown (which sectors performed best)
- [ ] Signal distribution (how many BUY/SELL/HOLD signals)
- [ ] Sent via email + Telegram
- **File:** `api/src/main/java/com/swingtrade/api/service/MonthlyReportService.java`
- **Status:** ⏳ Planned

### REQ-036: Trade Outcome Labelling

- [ ] Captured automatically: trade entry, exit, P&L, sentiment at entry time
- [ ] Manual labelling: reason for exit (stop/target/time/manual)
- [ ] Stored in `trade_labels` table
- [ ] Collected for 6+ months before fine-tuning Qwen3
- **File:** `data/src/main/java/com/swingtrade/data/entity/TradeLabel.java`
- **Status:** ⏳ Planned

---

## Out of Scope

| Feature | Reason |
|---------|--------|
| Intraday trading | Swing strategy only (1–4 week holds) |
| Futures & Options | NSE/BSE equity spot only |
| LLM fine-tuning | Deferred until 6+ months of labelled data (Q3 2026+) |
| LLM price prediction | LLM is sentiment filter only, not signal source |
| Mobile app | REST API only, no mobile client |
| Cloud deployment | Self-hosted on Raspberry Pi 5 + RTX 5090 |
| User authentication | Single-user system, no login |
| LIMIT/STOP orders | MARKET orders only |
| Real broker auth in code | Environment variables only, never hardcoded |

---

## Traceability Matrix

| Req ID | Phase | Category | Status | Module |
|--------|-------|----------|--------|--------|
| REQ-001 | 1 | Domain | ✅ | core/Stock |
| REQ-002 | 1 | Domain | ✅ | core/OhlcvCandle |
| REQ-003 | 1 | Domain | ✅ | core/Signal |
| REQ-004 | 1 | Domain | ✅ | core/Position |
| REQ-005 | 1 | Domain | ✅ | core/Trade |
| REQ-006 | 1 | Domain | ✅ | core/SentimentResult |
| REQ-007 | 2 | Strategy | ✅ | strategy/TechnicalIndicators |
| REQ-008 | 2 | Strategy | ✅ | strategy/DefaultStrategy |
| REQ-009 | 2 | Strategy | ✅ | strategy/SignalEngine |
| REQ-010 | 2 | Strategy | ✅ | strategy/BacktestEngine |
| REQ-011 | 3 | Data | ✅ | data/UpstoxRestClient |
| REQ-012 | 3 | Data | ✅ | data/DataIngestionService |
| REQ-013 | 3 | Data | ✅ | data/Repository |
| REQ-014 | 3 | Data | ✅ | data/Migrations |
| REQ-015 | 3 | Data | ✅ | data/Scheduling |
| REQ-016 | 3 | Broker | ✅ | broker/PaperTradingEngine |
| REQ-017 | 3 | Broker | ✅ | broker/RiskControls |
| REQ-018 | 3 | Broker | ✅ | broker/Telegram |
| REQ-019 | 3 | Broker | ✅ | broker/BrokerModes |
| REQ-020 | 3 | API | ✅ | api/TradingController |
| REQ-021 | 3 | API | ✅ | api/SignalController |
| REQ-022 | 3 | API | ✅ | api/PositionController |
| REQ-023 | 3 | API | ✅ | api/PerformanceService |
| REQ-024 | 3 | API | ✅ | api/ScanService |
| REQ-025 | 4 | LLM | ⚠️ | llm/VLLMClient |
| REQ-026 | 4 | LLM | ⚠️ | llm/NewsIngestionService |
| REQ-027 | 4 | LLM | ⚠️ | llm/SentimentAnalyzer |
| REQ-028 | 4 | LLM | ⚠️ | llm/NewsFilterService |
| REQ-029 | 4 | LLM | ⚠️ | llm/SentimentAnalysisService |
| REQ-101 | 5 | Testing | ⏳ | core/tests |
| REQ-102 | 5 | Testing | ⏳ | strategy/tests |
| REQ-103 | 5 | Testing | ⏳ | TestContainers |
| REQ-104 | 5 | Testing | ⏳ | MockRestServiceServer |
| REQ-105 | 5 | Testing | ⏳ | api/tests |
| REQ-106 | 5 | Testing | ⏳ | JaCoCo |
| REQ-030 | 6 | Live | ⏳ | broker/Kite |
| REQ-031 | 6 | Live | ⏳ | broker/Factory |
| REQ-032 | 6 | Live | ⏳ | api/KillSwitch |
| REQ-033 | 6 | Live | ⏳ | broker/Capital |
| REQ-034 | 7 | Observability | ⏳ | Grafana |
| REQ-035 | 7 | Observability | ⏳ | api/Reports |
| REQ-036 | 7 | Observability | ⏳ | data/Labels |

---

*Requirements specification: 2026-03-22 (Re-initialized with 7 phases)*
