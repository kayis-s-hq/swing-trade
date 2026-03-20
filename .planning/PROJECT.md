# PROJECT.md - GSD Project Definition

**Document Version:** 1.0
**Created:** 2026-03-07
**Last Updated:** 2026-03-08

---

## Project Overview

**Project Name:** SwingTrade - Brownfield Swing Trading System

**Project Type:** Brownfield Development
**Project Description:** An existing multi-module Java/Spring Boot swing trading system built for automated trading of NSE/BSE Indian equities. The system uses a multi-factor technical analysis approach enhanced by LLM sentiment analysis, with a paper trading engine for simulated execution.

**Current Maturity Level:** Feature Complete (v1.0)
**Primary Risk:** Insufficient test coverage in core and strategy modules

---

## Phase Structure (v1.0)

The project is organized into 8 phases: 5 feature development phases followed by 3 testing phases.

| Phase | Name | Status |
|-------|------|--------|
| 1 | Core Domain Implementation | ✅ Complete |
| 2 | Strategy Engine | ✅ Complete |
| 3 | Data Pipeline | ✅ Complete |
| 4 | Broker Integration | ✅ Complete |
| 5 | API Layer | ✅ Complete |
| 6 | Testing Foundation | ⏳ Deferred |
| 7 | Integration Test Infrastructure | ⏳ Deferred |
| 8 | API Endpoint Testing | ⏳ Deferred |

---

## Validated Requirements (v1.0 - Features)

The following requirements have been validated through codebase analysis and are implemented in the current codebase:

| Req ID | Category | Requirement | Status | Module |
|--------|----------|-------------|--------|--------|
| **REQ-001** | Domain Model | Stock entity with symbol, exchange, sector, industry | Implemented | core |
| **REQ-002** | Domain Model | OhlcvCandle with OHLCV data and calculations | Implemented | core |
| **REQ-003** | Domain Model | Signal with type, confidence, reasoning, risk params | Implemented | core |
| **REQ-004** | Domain Model | Position with entry, stop loss, target, status | Implemented | core |
| **REQ-005** | Domain Model | Trade with lifecycle, P&L, duration tracking | Implemented | core |
| **REQ-006** | Domain Model | SentimentResult with score, confidence, analysis | Implemented | core |
| **REQ-007** | Strategy | Technical indicators (EMA, SMA, RSI, MACD, ATR) | Implemented | strategy |
| **REQ-008** | Strategy | Multi-factor signal generation logic | Implemented | strategy |
| **REQ-009** | Strategy | Scheduled signal generation at 17:00 IST | Implemented | strategy |
| **REQ-010** | Strategy | Historical backtesting engine | Implemented | strategy |
| **REQ-011** | Data | Upstox API integration for market data | Implemented | data |
| **REQ-012** | Data | Data ingestion with validation and gap repair | Implemented | data |
| **REQ-013** | Data | Repository layer with Spring Data JPA | Implemented | data |
| **REQ-014** | Data | Flyway migrations with TimescaleDB hypertables | Implemented | data |
| **REQ-015** | Data | Auto-ingestion scheduling at 16:30 IST | Implemented | data |
| **REQ-016** | Broker | Paper trading engine with order execution | Implemented | broker |
| **REQ-017** | Broker | Risk controls (5 position limit, 20% size, daily loss) | Implemented | broker |
| **REQ-018** | Broker | Telegram notification integration | Implemented | broker |
| **REQ-019** | Broker | Broker modes (PAPER, DRY_RUN, LIVE) | Implemented | broker |
| **REQ-020** | API | Trading endpoints (trades, portfolio, positions) | Implemented | api |
| **REQ-021** | API | Signal endpoints with filtering | Implemented | api |
| **REQ-022** | API | Position endpoints with close functionality | Implemented | api |
| **REQ-023** | API | Performance metrics and analytics | Implemented | api |
| **REQ-024** | API | Scan service with manual trigger | Implemented | api |

---

## Active Requirements (Pending Work - Testing)

The following requirements are identified as pending work and are part of the testing phases:

| Req ID | Category | Requirement | Priority | Phase |
|--------|----------|-------------|----------|-------|
| **REQ-101** | Testing | Unit tests for core domain models | High | Phase 6 |
| **REQ-102** | Testing | Unit tests for strategy module | High | Phase 6 |
| **REQ-103** | Testing | TestContainers for database integration | Medium | Phase 7 |
| **REQ-104** | Testing | WireMock for external API mocking | Medium | Phase 7 |
| **REQ-105** | Testing | API endpoint testing suite | Medium | Phase 8 |
| **REQ-106** | Testing | 80%+ code coverage with JaCoCo | High | Phase 7 |
| **REQ-107** | Testing | Automated regression test suite | Low | Phase 8 |

---

## Out of Scope

The following items are explicitly out of scope for the current project:

1. **Real Broker Integration** - System is paper trading only; no live trading with actual money
2. **User Authentication** - No user login/identity management in REST API
3. **Mobile Application** - No mobile app development
4. **Historical Backtesting UI** - Backtest engine exists but no UI for historical analysis
5. **Machine Learning Models** - Only LLM-based sentiment; no custom ML training
6. **Cloud Deployment** - Self-hosted deployment only; no AWS/GCP/Azure configuration
7. **Advanced Order Types** - Only MARKET orders supported; no LIMIT/STOP/SL orders

---

## Key Technical Decisions

| Decision | Rationale | Impact |
|----------|-----------|--------|
| **Multi-module Maven Build** | Separation of concerns; independent compilation; modular deployment | Enables isolated testing per module |
| **PostgreSQL + TimescaleDB** | Time-series optimization; SQL compatibility; open source | Hypertables for OHLCV data require migration scripts |
| **TA4J for Technical Analysis** | Battle-tested library; comprehensive indicator suite; Java-native | Domain-specific technical analysis logic |
| **LangChain4j for LLM** | Abstraction over LLM providers; Spring Boot integration; Java-native | vLLM as primary LLM backend |
| **Spring Boot Caching with Redis** | Distributed caching; cache-aside pattern; TTL management | Cache keys: stocks, ohlcv, signals, sentiment |
| **Flyway for Schema Migrations** | Version-controlled schema; repeatable migrations | Migrations: V1 (core), V2 (hypertables), V3 (stocks), V4 (trades) |
| **TestContainers for Integration Tests** | Real database in CI; no test DB maintenance required | PostgreSQL container for data module tests |
| **WireMock for HTTP Testing** | Isolated external API testing; deterministic responses | Upstox API and vLLM mocking |

---

## Module Structure

```
swing-trade/
├── core/                          # Domain models (no framework dependencies)
│   ├── src/main/java/
│   │   └── com/swingtrade/domain/
│   │       ├── Stock.java         # Stock domain record
│   │       ├── OhlcvCandle.java   # OHLCV candle record
│   │       ├── Signal.java        # Trading signal record
│   │       ├── Position.java      # Position domain record
│   │       ├── Trade.java         # Trade domain record
│   │       └── SentimentResult.java # Sentiment analysis result
│   └── pom.xml                    # Module dependency: none
│
├── data/                          # Data ingestion and storage
│   ├── src/main/java/
│   │   ├── service/
│   │   │   ├── DataIngestionService.java    # Market data ingestion
│   │   │   ├── MarketDataClient.java        # External API abstraction
│   │   │   └── UpstoxRestClient.java        # Upstox API implementation
│   │   ├── entity/                  # JPA entities
│   │   ├── repository/              # Spring Data repositories
│   │   └── config/                  # Data module configuration
│   ├── src/main/resources/
│   │   └── db/migration/            # Flyway migrations
│   │       ├── V1__swing_trade_schema.sql
│   │       ├── V2__create_hypertables.sql
│   │       ├── V3__create_stocks_table.sql
│   │       └── V4__add_trades_table.sql
│   └── pom.xml                      # Dependencies: core, Spring Data JPA, PostgreSQL
│
├── strategy/                        # Technical analysis and signal generation
│   ├── src/main/java/
│   │   ├── SwingTradingStrategy.java   # Main strategy interface
│   │   ├── SignalEngine.java           # Scheduled signal generation
│   │   ├── TechnicalIndicators.java    # Indicator calculations
│   │   └── impl/
│   │       ├── DefaultStrategy.java    # TA4J-based strategy
│   │       ├── DefaultIndicatorService.java
│   │       └── DefaultBacktestEngine.java
│   └── pom.xml                         # Dependencies: core, data, TA4J
│
├── llm/                             # LLM integration and sentiment analysis
│   ├── src/main/java/
│   │   ├── client/
│   │   │   ├── LlmClient.java          # LLM client abstraction
│   │   │   └── LangChain4jLlmClient.java
│   │   └── service/
│   │       ├── SentimentAnalysisService.java
│   │       └── NewsIngestionService.java
│   └── pom.xml                         # Dependencies: core, langchain4j
│
├── broker/                          # Paper trading engine
│   ├── src/main/java/
│   │   ├── engine/
│   │   │   └── PaperTradingEngine.java   # Core trading logic
│   │   ├── service/
│   │   │   ├── BrokerService.java        # Broker abstraction
│   │   │   └── PaperTradingServiceImpl.java
│   │   ├── model/
│   │   │   ├── Order.java                # Order domain model
│   │   │   ├── OrderType.java            # MARKET, LIMIT, etc.
│   │   │   ├── TradeDirection.java       # LONG, SHORT
│   │   │   └── OrderStatus.java          # ACCEPTED, FILLED, etc.
│   │   ├── risk/                         # Risk controls
│   │   │   ├── PositionLimitChecker.java
│   │   │   ├── DailyLossCircuitBreaker.java
│   │   │   ├── PositionSizeValidator.java
│   │   │   └── RiskControlsService.java
│   │   ├── telegram/                     # Telegram notification integration
│   │   └── config/                       # Broker configuration
│   │       ├── BrokerMode.java
│   │       └── factory/
│   │           └── BrokerServiceFactory.java
│   └── pom.xml                           # Dependencies: core, strategy, telegram-bot-api
│
├── api/                             # REST API layer
│   ├── src/main/java/
│   │   ├── app/
│   │   │   └── SwingTradeApiApplication.java  # Main entry point
│   │   ├── controller/                  # REST controllers
│   │   │   ├── TradingController.java
│   │   │   ├── SignalController.java
│   │   │   └── PositionController.java
│   │   ├── service/
│   │   │   ├── SignalService.java
│   │   │   ├── PerformanceService.java
│   │   │   ├── PositionService.java
│   │   │   └── ScanService.java
│   │   └── dto/                         # Request/Response DTOs
│   │       ├── SignalResponse.java
│   │       ├── PositionResponse.java
│   │       ├── PerformanceResponse.java
│   │       ├── ScanResponse.java
│   │       └── TradeRequest.java
│   └── pom.xml                          # Dependencies: all modules, Spring Web, Actuator
│
├── docker-compose.yml                   # Infrastructure orchestration
├── pom.xml                              # Root Maven POM (dependency management)
├── README.md                            # Project documentation
└── .planning/                           # GSD planning artifacts
    ├── PROJECT.md                       # This file
    ├── REQUIREMENTS.md                  # Detailed requirements
    ├── ROADMAP.md                       # Implementation phases
    ├── STATE.md                         # Current state tracking
    └── config.json                      # GSD configuration
```

---

## Current Project State Summary

| Aspect | Status | Notes |
|--------|--------|-------|
| **Build System** | ✅ Functional | Maven multi-module; `mvn clean install` succeeds |
| **Database Schema** | ✅ Complete | 4 Flyway migrations; PostgreSQL + TimescaleDB |
| **Data Ingestion** | ✅ Scheduled | Auto-ingests at 16:30 IST; manual backfill available |
| **Signal Engine** | ✅ Scheduled | Auto-generates at 17:00 IST; manual trigger available |
| **Paper Trading** | ✅ Functional | Order placement, position tracking, P/L calculation |
| **LLM Integration** | ✅ Functional | vLLM client; sentiment analysis pipeline |
| **REST API** | ✅ Functional | Endpoints for signals, orders, portfolio |
| **Risk Controls** | ✅ Enforced | 5 position max, 20% size limit, daily loss circuit |
| **Telegram** | ✅ Functional | Trade and signal notifications |
| **Test Coverage (core)** | ❌ Missing | No unit tests for domain models |
| **Test Coverage (strategy)** | ❌ Missing | No unit tests for strategy/indicators |
| **Test Coverage (data)** | ⚠️ Partial | DataIngestionServiceTest exists |
| **Test Coverage (broker)** | ⚠️ Partial | PaperTradingEngineTest exists |
| **Test Coverage (api)** | ⚠️ Partial | SignalServiceTest, SwingTradeControllerTest |
| **Test Coverage (llm)** | ⚠️ Partial | LlmModuleTest exists |
| **Integration Tests** | ❌ Missing | No TestContainers, no WireMock tests |
| **API Endpoint Tests** | ❌ Missing | No REST endpoint testing |

---

## Next Steps (Immediate)

**Current State:** Feature development complete (v1.0)

**Recommended Next Actions:**

1. **Option A - Testing First (Original Plan)**
   - Phase 6: Create unit tests for core domain models and strategy module
   - Phase 7: Set up TestContainers and WireMock for integration testing
   - Phase 8: Implement API endpoint testing with proper request/response validation

2. **Option B - Feature Enhancements First**
   - Add additional trading strategies
   - Implement more order types (LIMIT, STOP)
   - Add Zerodha Kite Connect integration for live trading
   - Build backtesting UI

3. **Option C - Production Readiness**
   - Add monitoring and alerting
   - Set up CI/CD pipeline
   - Dockerize application
   - Deploy to staging environment

---

## Milestone Status

### v1.0 - Core Features (Complete)
**Completion Date:** 2026-03-20

- [x] Domain models (6 models) - Phase 01 ✅ Complete
- [x] Technical indicators (5+ indicators)
- [x] Signal generation (multi-factor)
- [x] Data pipeline (Upstox integration)
- [x] Paper trading engine
- [x] Risk controls
- [x] REST API (15+ endpoints)
- [x] Telegram notifications

### v1.1 - Testing Foundation (Deferred)
- [ ] Unit tests (core + strategy)
- [ ] Integration tests (TestContainers + WireMock)
- [ ] API endpoint tests
- [ ] 80%+ code coverage

---

*Project definition: 2026-03-08 (Reordered - Features Before Testing)*
