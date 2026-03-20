# ROADMAP.md - Implementation Phases and Timeline

**Document Version:** 1.0
**Created:** 2026-03-07
**Last Updated:** 2026-03-08

---

## Overview

This document outlines the implementation phases for building the SwingTrade swing trading system. The system is organized into 8 phases: 5 feature development phases followed by 3 testing phases.

---

## Phase 1: Core Domain Implementation

**Objective:** Complete domain models with all required fields and validation.

**Estimated Duration:** 2-3 days
**Actual Duration:** 2-3 days (pre-GSD implementation)
**Priority:** High
**Status:** ✅ Complete
**Completion Date:** 2026-03-20

---

### Phase 1 Deliverables

| ID | Deliverable | Description | Estimated Effort |
|----|-------------|-------------|------------------|
| 1.1 | Stock | Symbol, exchange, sector, industry, ISIN, lot size | 0.5 day |
| 1.2 | OhlcvCandle | Open, High, Low, Close, Volume, timestamp, adjClose | 0.5 day |
| 1.3 | Signal | Symbol, signal type, confidence, timestamp, reasoning, risk parameters | 0.5 day |
| 1.4 | Position | Symbol, quantity, entry price, stop loss, target, status | 0.5 day |
| 1.5 | Trade | Position reference, order type, price, quantity, timestamp, P&L | 0.5 day |
| 1.6 | SentimentResult | Symbol, sentiment score, confidence, analysis, date | 0.5 day |

---

### Phase 1 Detailed Tasks

#### 1.1 Stock Model

**File:** `core/src/main/java/com/swingtrade/domain/Stock.java`

**Fields:**
- `symbol` - Unique stock identifier (e.g., "RELIANCE", "TCS")
- `exchange` - NSE or BSE
- `name` - Full company name
- `sector` - Industry sector classification
- `isin` - International Securities Identification Number
- `lotSize` - Trading lot size

**Status:** ✅ Implemented

#### 1.2 OhlcvCandle Model

**File:** `core/src/main/java/com/swingtrade/domain/OhlcvCandle.java`

**Fields:**
- `symbol` - Stock symbol
- `date` - Trading date
- `open` - Opening price
- `high` - Highest price
- `low` - Lowest price
- `close` - Closing price
- `volume` - Trading volume
- `adjClose` - Adjusted closing price

**Methods:**
- `getRange()` - Daily price range (high - low)
- `getChangePercent()` - Daily change from open to close
- `isBullish()` - Close > Open
- `isBearish()` - Close < Open

**Status:** ✅ Implemented

#### 1.3 Signal Model

**File:** `core/src/main/java/com/swingtrade/domain/Signal.java`

**Fields:**
- `id` - Database auto-generated ID
- `symbol` - Stock symbol
- `date` - Signal date
- `type` - BUY, SELL, or HOLD
- `confidence` - 0.0 to 1.0
- `reasoning` - Textual explanation
- `entryPrice` - Recommended entry price zone
- `stopLoss` - Stop loss price level
- `target` - Target price for profit taking
- `riskReward` - Calculated risk-reward ratio
- `indicators` - Technical indicators that triggered the signal
- `generatedAt` - Timestamp when signal was generated

**Status:** ✅ Implemented

#### 1.4 Position Model

**File:** `core/src/main/java/com/swingtrade/domain/Position.java`

**Fields:**
- `id` - Database auto-generated ID
- `symbol` - Stock symbol
- `entryPrice` - Entry price
- `entryDate` - Entry date
- `quantity` - Number of shares
- `stopLoss` - Stop-loss price
- `target` - Target price
- `status` - OPEN, CLOSED, STOPPED, TARGET_HIT
- `entryReason` - Reason for entry
- `currentPrice` - Current market price

**Methods:**
- `createWithRisk()` - Factory with calculated stop loss and target
- `calculateUnrealizedPnL()` - Unrealized profit/loss
- `calculatePnLPercent()` - Percentage P&L

**Status:** ✅ Implemented

#### 1.5 Trade Model

**File:** `core/src/main/java/com/swingtrade/domain/Trade.java`

**Fields:**
- `id` - Database auto-generated ID
- `positionId` - Associated position ID
- `symbol` - Stock symbol
- `entryDate` - Entry date
- `exitDate` - Exit date (null if open)
- `entryPrice` - Entry price
- `exitPrice` - Exit price (null if open)
- `quantity` - Number of shares
- `totalPnL` - Total profit/loss
- `durationDays` - Trade duration
- `tradeStatus` - OPEN, CLOSED, STOPPED, TARGET_HIT, TIME_STOP
- `entryReason` - Entry reason
- `exitReason` - Exit reason (null if open)
- `fees` - Total fees paid

**Methods:**
- `open()` - Factory for new open trade
- `close()` - Close trade with exit details
- `isProfitable()` - Check if trade made profit
- `isLoss()` - Check if trade resulted in loss

**Status:** ✅ Implemented

#### 1.6 SentimentResult Model

**File:** `core/src/main/java/com/swingtrade/domain/SentimentResult.java`

**Fields:**
- `id` - Database auto-generated ID
- `symbol` - Stock symbol
- `date` - Analysis date
- `score` - POSITIVE, NEUTRAL, NEGATIVE
- `summary` - Textual summary
- `rawContent` - Raw content analyzed
- `confidence` - 0.0 to 1.0
- `analyzedAt` - Timestamp

**Status:** ✅ Implemented

---

### Phase 1 Success Criteria

| Criterion | Target | Measurement |
|-----------|--------|-------------|
| **All Domain Models** | Complete | 6 models implemented |
| **Factory Methods** | Present | create(), open(), close() |
| **Business Methods** | Present | P&L calculations, validations |
| **Enum Types** | Complete | SignalType, PositionStatus, TradeStatus, SentimentScore |

---

### Phase 1 Completion Gate

**Phase 1 is complete when:**
1. All 6 domain models are implemented with required fields
2. Factory methods present for object creation
3. Business methods for calculations (P&L, etc.)
4. Enum types properly defined

---

## Phase 2: Strategy Engine

**Objective:** Technical analysis and signal generation.

**Estimated Duration:** 3-4 days
**Priority:** High

---

### Phase 2 Deliverables

| ID | Deliverable | Description | Estimated Effort |
|----|-------------|-------------|------------------|
| 2.1 | TechnicalIndicators | EMA, SMA, RSI, MACD, ATR calculations | 1 day |
| 2.2 | DefaultStrategy | Multi-factor signal generation logic | 1 day |
| 2.3 | SignalEngine | Signal orchestration and scheduling | 0.5 day |
| 2.4 | BacktestEngine | Historical backtesting | 1 day |

---

### Phase 2 Detailed Tasks

#### 2.1 TechnicalIndicators Service

**File:** `strategy/src/main/java/com/swingtrade/strategy/TechnicalIndicators.java`

**Methods:**
- `calculateEMA()` - Exponential Moving Average
- `calculateSMA()` - Simple Moving Average
- `calculateRSI()` - Relative Strength Index
- `calculateMACD()` - Moving Average Convergence Divergence
- `calculateATR()` - Average True Range

**Status:** ✅ Implemented (TA4J integration)

#### 2.2 DefaultStrategy

**File:** `strategy/src/main/java/com/swingtrade/strategy/impl/DefaultStrategy.java`

**Logic:**
- Multi-factor signal generation
- EMA crossover detection
- RSI overbought/oversold analysis
- Volume spike detection
- Confidence calculation

**Status:** ✅ Implemented

#### 2.3 SignalEngine

**File:** `strategy/src/main/java/com/swingtrade/strategy/SignalEngine.java`

**Features:**
- Scheduled signal generation at 17:00 IST
- Manual trigger support
- Nifty 500 stock coverage

**Status:** ✅ Implemented

#### 2.4 BacktestEngine

**File:** `strategy/src/main/java/com/swingtrade/strategy/BacktestEngine.java`

**Features:**
- Historical backtesting over date range
- Trade count accuracy
- Equity calculation
- Sharpe ratio calculation
- Position sizing

**Status:** ✅ Implemented

---

### Phase 2 Success Criteria

| Criterion | Target | Measurement |
|-----------|--------|-------------|
| **Technical Indicators** | Complete | 5+ indicators implemented |
| **Signal Generation** | Working | BUY/SELL/HOLD signals |
| **Backtest Engine** | Working | Historical analysis functional |
| **Scheduling** | Working | Auto-generation at 17:00 IST |

---

### Phase 2 Completion Gate

**Phase 2 is complete when:**
1. All technical indicators implemented and tested
2. Signal generation produces correct BUY/SELL/HOLD signals
3. Backtest engine calculates accurate performance metrics
4. Scheduled signal generation works correctly

---

## Phase 3: Data Pipeline

**Objective:** OHLCV data ingestion and storage.

**Estimated Duration:** 3-4 days
**Priority:** High

---

### Phase 3 Deliverables

| ID | Deliverable | Description | Estimated Effort |
|----|-------------|-------------|------------------|
| 3.1 | MarketDataClient | Upstox API integration | 1 day |
| 3.2 | DataIngestionService | Data fetching and validation | 1 day |
| 3.3 | Repository Layer | PostgreSQL/TimescaleDB storage | 1 day |
| 3.4 | Schema Migrations | Flyway migrations V1-V4 | 0.5 day |
| 3.5 | Scheduling | Auto-ingestion at 16:30 IST | 0.5 day |

---

### Phase 3 Detailed Tasks

#### 3.1 MarketDataClient

**File:** `data/src/main/java/com/swingtrade/data/service/MarketDataClient.java`

**Implementation:** UpstoxRestClient.java

**Endpoints:**
- `/v2/login` - Authentication
- `/v2/token` - Token refresh
- `/v2/market-data/ohlcv` - OHLCV data
- `/v2/instruments` - Instrument list

**Status:** ✅ Implemented

#### 3.2 DataIngestionService

**File:** `data/src/main/java/com/swingtrade/data/service/DataIngestionService.java`

**Features:**
- Daily OHLCV data ingestion
- Data quality validation
- Gap detection and repair
- Nifty 500 stock coverage

**Status:** ✅ Implemented

#### 3.3 Repository Layer

**Files:**
- `data/src/main/java/com/swingtrade/data/repository/OhlcvCandleRepository.java`
- `data/src/main/java/com/swingtrade/data/repository/StockRepository.java`
- `data/src/main/java/com/swingtrade/data/repository/SignalRepository.java`

**Status:** ✅ Implemented (Spring Data JPA)

#### 3.4 Schema Migrations

**Files:**
- `V1__swing_trade_schema.sql` - Core tables
- `V2__create_hypertables.sql` - TimescaleDB hypertables
- `V3__create_stocks_table.sql` - Stocks table
- `V4__add_trades_table.sql` - Trades table

**Status:** ✅ Implemented

#### 3.5 Scheduling

**File:** `data/src/main/java/com/swingtrade/data/config/SchedulingConfig.java`

**Schedule:** Auto-ingestion at 16:30 IST (weekday)

**Status:** ✅ Implemented

---

### Phase 3 Success Criteria

| Criterion | Target | Measurement |
|-----------|--------|-------------|
| **Data Ingestion** | Working | Daily OHLCV data ingested |
| **Storage** | Working | PostgreSQL + TimescaleDB |
| **API Integration** | Working | Upstox API connected |
| **Scheduling** | Working | Auto-ingestion at 16:30 IST |

---

### Phase 3 Completion Gate

**Phase 3 is complete when:**
1. Upstox API integration working
2. OHLCV data ingested and stored correctly
3. TimescaleDB hypertables created
4. Auto-ingestion scheduled and running

---

## Phase 4: Broker Integration

**Objective:** Paper trading engine with risk controls.

**Estimated Duration:** 2-3 days
**Priority:** Medium

---

### Phase 4 Deliverables

| ID | Deliverable | Description | Estimated Effort |
|----|-------------|-------------|------------------|
| 4.1 | PaperTradingEngine | Order execution logic | 1 day |
| 4.2 | PositionLimitChecker | Max 5 concurrent positions | 0.5 day |
| 4.3 | DailyLossCircuitBreaker | Stop-loss protection | 0.5 day |
| 4.4 | PositionSizeValidator | 20% capital per position | 0.5 day |
| 4.5 | Telegram Notifications | Trade/signal alerts | 0.5 day |

---

### Phase 4 Detailed Tasks

#### 4.1 PaperTradingEngine

**File:** `broker/src/main/java/com/swingtrade/broker/engine/PaperTradingEngine.java`

**Features:**
- Order placement and execution
- Position management
- P&L calculation
- Trade lifecycle

**Status:** ✅ Implemented

#### 4.2 Risk Controls

**Files:**
- `PositionLimitChecker.java` - Max 5 positions
- `DailyLossCircuitBreaker.java` - Daily loss limit
- `PositionSizeValidator.java` - 20% capital limit
- `RiskControlsService.java` - Risk orchestration

**Status:** ✅ Implemented

#### 4.3 Telegram Integration

**File:** `broker/src/main/java/com/swingtrade/broker/telegram/TelegramNotificationService.java`

**Features:**
- Trade notifications
- Signal alerts
- Position updates

**Status:** ✅ Implemented

#### 4.4 Broker Modes

**File:** `broker/src/main/java/com/swingtrade/broker/config/BrokerMode.java`

**Modes:**
- PAPER - Paper trading (default)
- DRY_RUN - Dry run mode
- LIVE - Live trading (future)

**Status:** ✅ Implemented

---

### Phase 4 Success Criteria

| Criterion | Target | Measurement |
|-----------|--------|-------------|
| **Paper Trading** | Working | Orders execute correctly |
| **Risk Controls** | Enforced | Position limits respected |
| **Telegram** | Working | Notifications sent |
| **Broker Modes** | Switchable | Mode selection works |

---

### Phase 4 Completion Gate

**Phase 4 is complete when:**
1. Paper trading engine executes orders correctly
2. Risk controls enforce position limits
3. Telegram notifications working
4. Broker modes switchable via configuration

---

## Phase 5: API Layer

**Objective:** REST endpoints for system interaction.

**Estimated Duration:** 2-3 days
**Priority:** Medium

---

### Phase 05.1: Implementation Fixes (INSERTED)

**Goal:** [Urgent work - to be planned]
**Requirements**: TBD
**Depends on:** Phase 5
**Plans:** 0 plans

Plans:
- [ ] TBD (run /gsd:plan-phase 05.1 to break down)

### Phase 5 Deliverables

| ID | Deliverable | Description | Estimated Effort |
|----|-------------|-------------|------------------|
| 5.1 | TradingController | Trade endpoints | 0.5 day |
| 5.2 | SignalController | Signal endpoints | 0.5 day |
| 5.3 | PositionController | Position endpoints | 0.5 day |
| 5.4 | PerformanceService | Performance metrics | 0.5 day |
| 5.5 | ScanService | Signal scanning | 0.5 day |
| 5.6 | DTOs | Request/Response objects | 0.5 day |

---

### Phase 5 Detailed Tasks

#### 5.1 TradingController

**File:** `api/src/main/java/com/swingtrade/api/controller/TradingController.java`

**Endpoints:**
- `POST /api/trades` - Place trade
- `GET /api/portfolio` - Get portfolio
- `GET /api/positions` - Get positions

**Status:** ✅ Implemented

#### 5.2 SignalController

**File:** `api/src/main/java/com/swingtrade/api/controller/SignalController.java`

**Endpoints:**
- `GET /api/signals/latest` - Latest signals
- `GET /api/signals/{symbol}` - Symbol-specific signals
- `GET /api/signals?signalType=BUY` - Filter by type
- `GET /api/signals?minConfidence=0.7` - Filter by confidence

**Status:** ✅ Implemented

#### 5.3 PositionController

**File:** `api/src/main/java/com/swingtrade/api/controller/PositionController.java`

**Endpoints:**
- `GET /api/positions` - Open positions
- `GET /api/positions/{id}` - Position details
- `POST /api/positions/{id}/close` - Close position

**Status:** ✅ Implemented

#### 5.4 Performance & Scan Services

**Files:**
- `PerformanceService.java` - Performance metrics
- `ScanService.java` - Signal scanning

**Endpoints:**
- `GET /api/performance` - Performance metrics
- `GET /api/scan` - Scan results
- `POST /api/scan` - Trigger scan

**Status:** ✅ Implemented

#### 5.5 DTOs

**Files:**
- `SignalResponse.java`
- `PositionResponse.java`
- `PerformanceResponse.java`
- `ScanResponse.java`
- `TradeRequest.java`

**Status:** ✅ Implemented

---

### Phase 5 Success Criteria

| Criterion | Target | Measurement |
|-----------|--------|-------------|
| **REST Endpoints** | Complete | All endpoints working |
| **DTOs** | Complete | Request/Response objects |
| **Error Handling** | Working | Proper HTTP status codes |
| **Documentation** | Complete | API documentation |

---

### Phase 5 Completion Gate

**Phase 5 is complete when:**
1. All REST endpoints functional
2. Request/Response DTOs properly defined
3. Error handling implemented
4. API documentation complete

---

## Phase 6: Testing Foundation

**Objective:** Establish comprehensive unit test coverage for core domain models and strategy module.

**Estimated Duration:** 3-5 days
**Priority:** Medium (deferred)

---

### Phase 6 Deliverables

| ID | Deliverable | Description | Estimated Effort |
|----|-------------|-------------|------------------|
| 6.1 | CoreDomainModelTests | Unit tests for Stock, OhlcvCandle, Signal, Position, Trade | 1 day |
| 6.2 | TechnicalIndicatorsTests | Unit tests for EMA, SMA, RSI, MACD, volume calculations | 1 day |
| 6.3 | DefaultStrategyTests | Unit tests for multi-factor signal generation logic | 1 day |
| 6.4 | BacktestEngineTests | Unit tests for backtesting and performance calculations | 1 day |
| 6.5 | TestFixtures | Common test data factories and fixtures | 0.5 day |

---

### Phase 6 Completion Gate

**Phase 6 is complete when:**
1. All domain model tests passing
2. Strategy module tests passing
3. 100% coverage for core module
4. 85%+ coverage for strategy module

---

## Phase 7: Integration Test Infrastructure

**Objective:** Establish integration test framework with TestContainers for database and WireMock for external APIs.

**Estimated Duration:** 4-6 days
**Priority:** Medium (deferred)

---

### Phase 7 Deliverables

| ID | Deliverable | Description | Estimated Effort |
|----|-------------|-------------|------------------|
| 7.1 | DatabaseTestContainer | PostgreSQL + TimescaleDB TestContainer helper | 1 day |
| 7.2 | RedisTestContainer | Redis TestContainer helper | 0.5 day |
| 7.3 | UpstoxWireMock | Upstox API WireMock setup | 1 day |
| 7.4 | LlmWireMock | vLLM sentiment WireMock setup | 0.5 day |
| 7.5 | DataIntegrationTests | Integration tests for data module | 1.5 days |
| 7.6 | BrokerIntegrationTests | Integration tests for broker module | 1 day |
| 7.7 | Coverage Thresholds | JaCoCo configuration with thresholds | 0.5 day |

---

### Phase 7 Completion Gate

**Phase 7 is complete when:**
1. TestContainers configured and working
2. WireMock for external APIs working
3. Integration tests passing
4. JaCoCo thresholds configured

---

## Phase 8: API Endpoint Testing

**Objective:** Create comprehensive API endpoint tests for REST controllers.

**Estimated Duration:** 3-4 days
**Priority:** Low (deferred)

---

### Phase 8 Deliverables

| ID | Deliverable | Description | Estimated Effort |
|----|-------------|-------------|------------------|
| 8.1 | SignalEndpointTests | REST API tests for signal endpoints | 1 day |
| 8.2 | OrderEndpointTests | REST API tests for order endpoints | 1 day |
| 8.3 | PerformanceEndpointTests | REST API tests for performance/scan | 0.5 day |
| 8.4 | ErrorHandlingTests | Validation and error response tests | 0.5 day |
| 8.5 | RegressionSuite | End-to-end regression tests | 1 day |

---

### Phase 8 Completion Gate

**Phase 8 is complete when:**
1. All REST endpoints tested
2. Error handling verified
3. Regression suite passing
4. CI/CD integration ready

---

## Overall Project Completion

### Final Success Criteria

| Criterion | Target | Measurement |
|-----------|--------|-------------|
| **Feature Completeness** | 100% | Phases 1-5 complete |
| **Total Test Coverage** | 80%+ | JaCoCo report |
| **All Tests Pass** | 100% | `mvn test` |
| **Test Execution Time** | < 20 minutes | Maven build output |
| **CI/CD Ready** | Yes | GitHub Actions workflow |

---

## Phase Dependencies

```
Phase 1: Core Domain Implementation
    │
    ├──> Phase 2: Strategy Engine
    │       │
    │       ├──> Phase 3: Data Pipeline
    │       │       │
    │       │       ├──> Phase 4: Broker Integration
    │       │       │       │
    │       │       │       ├──> Phase 5: API Layer
    │       │       │       │       │
    │       │       │       │       ├──> Phase 6: Testing Foundation
    │       │       │       │       │       │
    │       │       │       │       │       ├──> Phase 7: Integration Test Infrastructure
    │       │       │       │       │       │       │
    │       │       │       │       │       │       └──> Phase 8: API Endpoint Testing
    │       │       │       │       │       │
    │       │       │       │       └──> Code Coverage Gate
    │       │       │       │
    │       │       └──> Feature Release (v1.0)
    │       │
    └──> Core Feature Release
```

---

## Notes

- Effort estimates are in working days for a single developer
- Actual timeline may vary based on team size and availability
- Phases 6-8 (testing) are deferred until core features are complete
- All phases should be completed in sequence for optimal results
- Code review is required before marking any phase as complete

---

*Roadmap: 2026-03-08 (Reordered - Features Before Testing)*
