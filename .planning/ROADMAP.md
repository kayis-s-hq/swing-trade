# ROADMAP.md - SwingTrade Implementation Phases

**Document Version:** 2.4
**Created:** 2026-03-07
**Last Updated:** 2026-03-22 (Phase 9 plans created)

---

## Overview

SwingTrade is a 7-phase project to build a production swing trading system for Indian equities. Phases 1–3 are complete (paper trading active). Phase 4 (LLM) has been planned with 3 plans covering all requirements. Phases 5–7 are deferred pending Phase 4 completion.

**Paper trading started:** 2026-03-20
**Target live trading:** 2026-05-15 (after Phase 4 + Phase 5 complete)
**Target observability:** 2026-06-30

---

## Phase 1: Core Domain Implementation ✅ COMPLETE

**Objective:** Complete domain models with all required fields.

**Status:** ✅ Complete (2026-03-20)
**Duration:** 2–3 days
**Priority:** High

### Requirements Mapped

- REQ-001: Stock model ✅
- REQ-002: OhlcvCandle model ✅
- REQ-003: Signal model ✅
- REQ-004: Position model ✅
- REQ-005: Trade model ✅
- REQ-006: SentimentResult model ✅

### Success Criteria

- [x] All 6 domain models implemented
- [x] Factory methods present
- [x] Business methods for calculations
- [x] Enums properly defined

---

## Phase 2: Strategy Engine ✅ COMPLETE

**Objective:** Technical indicators and multi-factor signal generation.

**Status:** ✅ Complete (2026-03-20), ✅ Gap closure complete (2026-03-22)
**Duration:** 3–4 days (base), 0.5 days (gap closure)
**Priority:** High

### Requirements Mapped

- REQ-007a: EMA Crossover (Price > EMA20 > EMA50) ✅
- REQ-007b: RSI Analysis (RSI 50–65) ✅
- REQ-007c: Volume Spike (Vol > 1.5x 20d avg) ✅
- REQ-007d: ATR-based Stops (entry − 2×ATR, target +2.5×risk) ✅
- REQ-008a: 4-factor entry logic ✅
- REQ-008b: Exit logic (stop/target/time/EMA break) ✅
- REQ-009: Scheduled signal generation at 17:00 IST ✅ ⚠️ Redis caching pending
- REQ-010: Backtest engine ✅ ⚠️ Performance metrics pending

### Success Criteria

- [x] All indicators implemented (TA4J v0.16)
- [x] 4-factor signal generation working
- [x] Backtest engine calculates accurate metrics (Sharpe ratio, Max drawdown, Avg trade duration)
- [x] Scheduled signal generation runs at 17:00 IST
- [x] Redis caching implemented for SignalEngine

### Gap Closure Plans (Complete)

| Plan | Objective | Status |
|------|-----------|--------|
| 02-01 | Add Redis caching to SignalEngine (@EnableCaching, @Cacheable, @CacheEvict) | ✅ Complete |
| 02-02 | Implement BacktestEngine performance metrics (Sharpe, MaxDrawdown, AvgTradeDuration) | ✅ Complete |

---

## Phase 3: Data Pipeline + Signal Engine + Paper Trading ✅ COMPLETE

**Objective:** OHLCV data ingestion, paper trading engine, REST API.

**Status:** ✅ Complete (2026-03-20), ✅ UAT Verified (2026-03-22)
**Duration:** 2 weeks
**Priority:** High

### Requirements Mapped

- REQ-011: Upstox API client ✅
- REQ-012: Data ingestion service ✅
- REQ-013: Repository layer ✅
- REQ-014: Flyway migrations ✅
- REQ-015: Scheduling (16:30 IST) ✅
- REQ-016: Paper trading engine ✅
- REQ-017: Risk controls ✅
- REQ-018: Telegram notifications ✅
- REQ-019: Broker modes ✅
- REQ-020: TradingController ✅
- REQ-021: SignalController ✅
- REQ-022: PositionController ✅
- REQ-023: PerformanceService ✅
- REQ-024: ScanService ✅

### Success Criteria

- [x] Upstox API integrated, token refresh working
- [x] OHLCV data for Nifty 500 ingested daily
- [x] TimescaleDB hypertables created
- [x] Auto-ingestion runs at 16:30 IST weekdays
- [x] Paper trading engine executes orders
- [x] Risk controls enforce limits
- [x] Telegram notifications working
- [x] REST API endpoints functional
- [x] Paper portfolio running since 2026-03-20

---

## Phase 4: LLM Sentiment Layer ⚠️ PARTIAL → PLANNED

**Objective:** News ingestion, sentiment analysis, signal filtering.

**Status:** ⚠️ Partial (module exists, integration TBD) → **PLANNED** (3 plans)
**Duration:** 1–2 weeks
**Priority:** High
**Blocking:** Phase 5 cannot start until Phase 4 verified

### Requirements Mapped

| Req ID | Description | Plan Coverage |
|--------|-------------|---------------|
| REQ-025 | vLLM client (OpenAI-compatible) | Plan 01, Plan 03 |
| REQ-026 | News ingestion (RSS feeds, 7-day history) | Plan 01, Plan 03 |
| REQ-027 | Sentiment analysis pipeline | Plan 01, Plan 03 |
| REQ-028 | Signal filtering (NEGATIVE suppression, NEUTRAL flagging) | Plan 01, Plan 03 |
| REQ-029 | Weekly sector digest | Plan 02, Plan 03 |

### Success Criteria

- [ ] vLLM endpoint reachable (RTX 5090 inference)
- [ ] News fetched for Nifty 500 stocks (7-day history)
- [ ] Sentiment analysis returns POSITIVE/NEUTRAL/NEGATIVE
- [ ] NEGATIVE signals suppressed from paper portfolio
- [ ] NEUTRAL signals flagged with ⚠️ in Telegram
- [ ] Weekly sector digest sent Sunday 17:00 IST
- [ ] Integration tests pass with TestContainers

### Plans

**3 plans** in 3 waves

#### Plan 01: SignalEngine Sentiment Integration (Wave 1)
- **Objective:** Integrate sentiment filtering into SignalEngine
- **Requirement IDs:** REQ-025, REQ-026, REQ-027, REQ-028
- **Files Modified:** SignalEngine.java, SignalEntity.java, SentimentResultRepository.java, SentimentAnalysisService.java
- **Wave:** 1 (no dependencies)
- **Tasks:**
  1. Add SentimentAnalysisService dependency to SignalEngine
  2. Add sentiment check before signal save in generateSignalsForSymbol
  3. Add warning flag field to SignalEntity
  4. Create SentimentResultEntity and SentimentResultRepository
  5. Update SentimentAnalysisService to persist results

#### Plan 02: Weekly Sector Digest (Wave 2)
- **Objective:** Implement weekly sector digest scheduled job
- **Requirement IDs:** REQ-029
- **Files Modified:** SentimentAnalysisService.java, LlmConfig.java, SwingTradeApiApplication.java
- **Wave:** 2 (depends on Plan 01)
- **Tasks:**
  1. Add generateSectorDigest() method to SentimentAnalysisService
  2. Add sendSectorDigest() with Telegram integration
  3. Add @Scheduled annotation to LlmConfig for Sunday 17:00 IST
  4. Verify @EnableScheduling on main application
  5. Add configuration for Telegram chat ID

#### Plan 03: Test Infrastructure (Wave 3)
- **Objective:** Create comprehensive test suite for LLM module
- **Requirement IDs:** REQ-025, REQ-026, REQ-027, REQ-028, REQ-029
- **Files Modified:** 5 test classes + test resources
- **Wave:** 3 (depends on Plans 01 and 02)
- **Tasks:**
  1. Create VLLMClientTest with MockRestServiceServer (REQ-025)
  2. Create NewsIngestionServiceTest (REQ-026)
  3. Create SentimentAnalyzerTest (REQ-027)
  4. Create SentimentFilteringTest (REQ-028)
  5. Create SectorDigestTest (REQ-029)
  6. Create test resources (application-test.yml, sample-rss.xml)

### Plan Details

| Plan | Objective | Tasks | Files | Wave |
|------|-----------|-------|-------|------|
| 04-01 | SignalEngine sentiment integration | 5 | SignalEngine.java, SignalEntity.java, SentimentResultRepository.java, SentimentAnalysisService.java | 1 |
| 04-02 | Weekly sector digest | 5 | SentimentAnalysisService.java, LlmConfig.java, SwingTradeApiApplication.java | 2 |
| 04-03 | Test infrastructure | 6 | 5 test classes + test resources | 3 |

### What Needs to Be Done

Execute Phase 4 plans in order: `/gsd:execute-phase 04`

1. **Plan 01:** SignalEngine integration with sentiment filtering (REQ-028)
2. **Plan 02:** Weekly sector digest scheduled job (REQ-029)
3. **Plan 03:** Comprehensive test infrastructure (all requirements)

---

## Phase 5: Testing Foundation 📋 PLANNED

**Objective:** Unit + integration tests, 80%+ code coverage.

**Status:** 📋 **Planned** (6 plans created)
**Duration:** 2–3 weeks
**Priority:** High
**Depends On:** Phase 4 completion
**Blocking:** Phase 6 cannot start until testing complete

### Requirements Mapped

| Req ID | Description | Plan Coverage |
|--------|-------------|---------------|
| REQ-101 | Core domain unit tests | Plan 01 |
| REQ-102 | Strategy module unit tests | Plan 02 |
| REQ-103 | TestContainers integration | Plan 03 |
| REQ-104 | MockRestServiceServer (NOT WireMock) | Plan 04 |
| REQ-105 | API endpoint tests | Plan 05 |
| REQ-106 | JaCoCo coverage (80%+) | Plan 06 |

### Success Criteria

- [ ] All unit tests passing
- [ ] All integration tests passing
- [ ] core: 100% coverage
- [ ] strategy: 85%+ coverage
- [ ] data, broker, api, llm: 80%+ coverage
- [ ] JaCoCo Maven profile working (`mvn clean install -P coverage`)

### Plans

**6 plans** in 6 waves

#### Plan 01: Core Domain Unit Tests (Wave 1)
- **Objective:** Unit tests for all domain models (100% coverage)
- **Requirement IDs:** REQ-101
- **Files Modified:** StockTest.java, OhlcvCandleTest.java, SignalTest.java, PositionTest.java, TradeTest.java, SentimentResultTest.java
- **Wave:** 1 (no dependencies)

#### Plan 02: Strategy Unit Tests (Wave 2)
- **Objective:** Unit tests for TechnicalIndicators, DefaultStrategy, BacktestEngine (85%+ coverage)
- **Requirement IDs:** REQ-102
- **Files Modified:** TechnicalIndicatorsTest.java, DefaultStrategyTest.java, DefaultBacktestEngineTest.java
- **Wave:** 2 (depends on Plan 01)

#### Plan 03: TestContainers Integration (Wave 3)
- **Objective:** Integration tests with PostgreSQL + TimescaleDB TestContainers
- **Requirement IDs:** REQ-103
- **Files Modified:** TestContainersConfig.java, FlywayMigrationTest.java, RepositoryIntegrationTest.java
- **Wave:** 3 (depends on Plan 02)

#### Plan 04: HTTP Mocking with MockRestServiceServer (Wave 4)
- **Objective:** Mock Upstox API and vLLM using Spring MockRestServiceServer (NOT WireMock)
- **Requirement IDs:** REQ-104
- **Files Modified:** UpstoxRestClientTest.java, VLLMClientTest.java
- **Wave:** 4 (depends on Plan 03)

#### Plan 05: API Endpoint Tests (Wave 5)
- **Objective:** @SpringBootTest + MockMvc tests for REST endpoints
- **Requirement IDs:** REQ-105
- **Files Modified:** SignalControllerTest.java, TradingControllerTest.java, PositionControllerTest.java, HealthControllerTest.java
- **Wave:** 5 (depends on Plan 04)

#### Plan 06: JaCoCo Coverage Configuration (Wave 6)
- **Objective:** Configure JaCoCo Maven profile with module-specific thresholds
- **Requirement IDs:** REQ-106
- **Files Modified:** 6 pom.xml files (core, data, strategy, llm, broker, api)
- **Wave:** 6 (depends on Plan 05)

### What Needs to Be Done

Execute Phase 5 plans in order: `/gsd:execute-phase 05`

1. **Plan 01:** Core domain unit tests (100% coverage)
2. **Plan 02:** Strategy unit tests (85%+ coverage)
3. **Plan 03:** TestContainers integration tests
4. **Plan 04:** HTTP mocking tests (MockRestServiceServer)
5. **Plan 05:** API endpoint tests
6. **Plan 06:** JaCoCo coverage configuration

---

### Key Implementation Decisions

- **NO WireMock:** Use MockRestServiceServer (Spring-native, lighter)
- **Real database:** TestContainers for PostgreSQL
- **Mockito + Spring MockBean:** For external API mocking

---

## Phase 6: Live Trading 📋 PLANNED

**Objective:** Zerodha Kite Connect integration.

**Status:** 📋 **Planned** (2 plans created)
**Duration:** 1 week
**Priority:** Medium
**Depends On:** Phase 5 completion + 2–3 months paper validation

### Requirements Mapped

| Req ID | Description | Plan Coverage |
|--------|-------------|---------------|
| REQ-030 | Zerodha Kite Connect | Plan 01, Plan 02 |
| REQ-031 | BrokerServiceFactory routing | Plan 01, Plan 02 |
| REQ-032 | Kill switch | Plan 01, Plan 02 |
| REQ-033 | Capital management | Plan 01, Plan 02 |

### Success Criteria

- [ ] Kite Java SDK integrated
- [ ] Live order placement working
- [ ] Kill switch tested and verified
- [ ] BrokerServiceFactory switches modes via config
- [ ] Capital tracking enforces ₹50K limit
- [ ] Max 3 live positions enforced
- [ ] 80%+ code coverage for broker module

### Plans

**2 plans** in 2 waves

#### Plan 01: Live Trading Verification (Wave 1)
- **Objective:** Verify Kite Connect integration is production-ready
- **Requirement IDs:** REQ-030, REQ-031, REQ-032, REQ-033
- **Files Modified:** KiteConnectClient.java, BrokerServiceFactory.java, RiskControlsService.java, application.properties
- **Wave:** 1 (no dependencies)
- **Tasks:**
  1. Verify KiteConnectClient implementation completeness
  2. Verify BrokerServiceFactory mode switching
  3. Verify RiskControlsService integration
  4. Document Zerodha API setup steps

#### Plan 02: Live Trading Test Suite (Wave 2)
- **Objective:** Create comprehensive unit tests for Kite Connect integration
- **Requirement IDs:** REQ-030, REQ-031, REQ-032, REQ-033
- **Files Modified:** KiteConnectClientTest.java, BrokerServiceFactoryTest.java, LiveTradingServiceTest.java, DryRunServiceTest.java
- **Wave:** 2 (depends on Plan 01)
- **Tasks:**
  1. Create KiteConnectClientTest (15+ tests)
  2. Create BrokerServiceFactoryTest (12+ tests)
  3. Create LiveTradingServiceTest (15+ tests)
  4. Create DryRunServiceTest (12+ tests)
  5. Run all broker tests and verify 80%+ coverage

### Plan Details

| Plan | Objective | Tasks | Files | Wave |
|------|-----------|-------|-------|------|
| 06-01 | Live trading verification | 5 | KiteConnectClient.java, BrokerServiceFactory.java, RiskControlsService.java, application.properties | 1 |
| 06-02 | Live trading test suite | 5 | KiteConnectClientTest.java, BrokerServiceFactoryTest.java, LiveTradingServiceTest.java, DryRunServiceTest.java | 2 |

### What Needs to Be Done

Execute Phase 6 plans in order: `/gsd:execute-phase 06`

1. **Plan 01:** Verify Kite Connect integration (implementation already exists)
2. **Plan 02:** Add comprehensive test suite (80%+ coverage)

---

## Phase 7: Observability + Iteration ⏳ PLANNED

**Objective:** Monitoring, reporting, trade labelling.

**Status:** ⏳ Planned (start after Phase 6 deployed)
**Duration:** 1–2 weeks
**Priority:** Low
**Depends On:** Phase 6 live trading active

### Requirements Mapped

- REQ-034: Grafana dashboards ⏳
- REQ-035: Monthly review reports ⏳
- REQ-036: Trade labelling ⏳

### Success Criteria

- [ ] Grafana connected to Spring Actuator
- [ ] Real-time dashboards: positions, daily P&L, signals
- [ ] Monthly report generated first Sunday of month
- [ ] Trade outcomes labelled with exit reason

---

## Phase 8: Vue Dashboard + Monitoring UI

**Goal:** [To be planned]
**Requirements**: TBD
**Depends on:** Phase 7
**Plans:** 0 plans

Plans:
- [ ] TBD (run /gsd:plan-phase 8 to break down)

---

## Phase 9: Replace Telegram with Signal 📋 PLANNED

**Objective:** Replace Telegram notifications with Signal/Signl4 webhook integration for all notification features.

**Status:** 📋 **Planned** (3 plans created)
**Duration:** 1 week
**Priority:** High
**Depends On:** Phase 8

### Requirements Mapped

- **REQ-018:** Telegram → Signal migration (full feature parity)
  - Signal notifications: BUY/SELL/HOLD signals
  - Position notifications: entry, update, exit
  - Trade notifications: open, close, stop loss, target hit
  - System notifications: daily summaries, error alerts, risk warnings
  - Command handlers: /stop, /resume, /status, /help
  - Quiet hours support
  - Configuration migration (telegram.* → signal.*)

### Success Criteria

- [ ] SignalNotificationService implements all notification methods
- [ ] SignalMessageFormatter implements all formatting methods
- [ ] SignalConfig.java created with signal.* properties
- [ ] application.properties updated with signal configuration
- [ ] Telegram services marked @Deprecated (backward compatibility)
- [ ] BrokerNotificationIntegration wired to Signal by default
- [ ] 80+ unit tests pass (SignalNotificationService, SignalMessageFormatter, SignalConfig, BrokerNotificationIntegration)
- [ ] signal.enabled=true, telegram.enabled=false as defaults

### Plans

**3 plans** in 3 waves

#### Plan 01: Signal Service Expansion (Wave 1)
- **Objective:** Expand Signal/Signl4 notification service to full feature parity
- **Requirement IDs:** REQ-018
- **Files Modified:** SignalNotificationService.java, SignalMessageFormatter.java, SignalConfig.java, application.properties
- **Wave:** 1 (no dependencies)
- **Tasks:**
  1. Expand SignalNotificationService with all notification methods (signals, positions, trades, commands, summaries, warnings)
  2. Expand SignalMessageFormatter with all formatting methods
  3. Create SignalConfig configuration class
  4. Update application.properties with signal.* configuration
  5. Wire SignalNotificationService into BrokerNotificationIntegration

#### Plan 02: Telegram Deprecation (Wave 2)
- **Objective:** Deprecate Telegram notification code, wire Signal as default
- **Requirement IDs:** REQ-018
- **Files Modified:** TelegramNotificationService.java, TelegramMessageFormatter.java, TelegramConfig.java, BrokerNotificationIntegration.java, application.properties
- **Wave:** 2 (depends on Plan 01)
- **Tasks:**
  1. Mark TelegramNotificationService as @Deprecated
  2. Mark TelegramMessageFormatter as @Deprecated
  3. Mark TelegramConfig as @Deprecated
  4. Update BrokerNotificationIntegration to use Signal by default
  5. Update application.properties to enable Signal by default

#### Plan 03: Signal Test Suite (Wave 3)
- **Objective:** Add comprehensive unit tests for Signal integration
- **Requirement IDs:** REQ-018
- **Files Modified:** SignalNotificationServiceTest.java, SignalMessageFormatterTest.java, SignalConfigTest.java, BrokerNotificationIntegrationTest.java
- **Wave:** 3 (depends on Plans 01 and 02)
- **Tasks:**
  1. Create SignalMessageFormatterTest (20+ tests)
  2. Create SignalNotificationServiceTest (25+ tests)
  3. Create SignalConfigTest (13+ tests)
  4. Create BrokerNotificationIntegrationTest (21+ tests)
  5. Run all notification tests and verify coverage

### Plan Details

| Plan | Objective | Tasks | Files | Wave |
|------|-----------|-------|-------|------|
| 09-01 | Signal service expansion | 5 | SignalNotificationService.java, SignalMessageFormatter.java, SignalConfig.java, application.properties | 1 |
| 09-02 | Telegram deprecation | 5 | TelegramNotificationService.java, TelegramMessageFormatter.java, TelegramConfig.java, BrokerNotificationIntegration.java, application.properties | 2 |
| 09-03 | Signal test suite | 5 | 4 test classes | 3 |

### What Needs to Be Done

Execute Phase 9 plans in order: `/gsd:execute-phase 09`

1. **Plan 01:** Expand Signal notification service (full feature parity)
2. **Plan 02:** Deprecate Telegram, wire Signal as default
3. **Plan 03:** Add 80+ unit tests

---

## Phase Dependencies

```
Phase 1: Core Domain ✅
    │
    ├──> Phase 2: Strategy Engine ✅ (gap closure pending)
    │       │
    │       ├──> Phase 3: Data Pipeline + Paper Trading ✅
    │       │       │
    │       │       ├──> Phase 4: LLM Sentiment ⚠️ (PLANNED)
    │       │       │       │
    │       │       │       ├──> Phase 5: Testing Foundation ⏳
    │       │       │       │       │
    │       │       │       │       ├──> Phase 6: Live Trading ⏳
    │       │       │       │       │       │
    │       │       │       │       │       ├──> Phase 7: Observability ⏳
    │       │       │       │       │       │       │
    │       │       │       │       │       ├──> Phase 8: Vue Dashboard ⏳
    │       │       │       │       │       │       │
    │       │       │       │       │       ├──> Phase 9: Signal Notifications ⏳
```

---

## Milestones

| Milestone | Target Date | Status |
|-----------|-------------|--------|
| **v1.0 – Paper Trading** | 2026-03-20 | ✅ Complete |
| **v1.1 – LLM + Testing** | 2026-04-30 | ⏳ In Planning |
| **v2.0 – Live Trading** | 2026-05-15 | ⏳ Pending |
| **v2.1 – Observability** | 2026-06-30 | ⏳ Pending |

---

## Timeline Summary

| Phase | Status | Target Start | Target End |
|-------|--------|--------------|------------|
| 1 | ✅ Complete | 2026-03-15 | 2026-03-20 |
| 2 | ⚠️ Gap closure | 2026-03-22 | 2026-03-22 |
| 3 | ✅ Complete | 2026-03-15 | 2026-03-20 |
| 4 | 📋 **PLANNED** | **2026-03-23** | **2026-04-06** |
| 5 | ⏳ Pending Phase 4 | 2026-04-07 | 2026-04-30 |
| 6 | 📋 **PLANNED** | **2026-05-01** | **2026-05-08** |
| 7 | ⏳ Pending Phase 6 | 2026-05-15 | 2026-06-30 |
| 8 | ⏳ Pending Phase 7 | TBD | TBD |
| 9 | 📋 **PLANNED** | **TBD** | **TBD** |

---

## Next Immediate Action

**Phase 4: LLM Sentiment Layer Planning Complete**

Phase 4 has been planned with 3 sequential plans covering all 5 requirements:

| Plan | Objective | Requirements |
|------|-----------|--------------|
| 04-01 | SignalEngine sentiment integration | REQ-025, REQ-026, REQ-027, REQ-028 |
| 04-02 | Weekly sector digest | REQ-029 |
| 04-03 | Test infrastructure | REQ-025, REQ-026, REQ-027, REQ-028, REQ-029 |

**Execute Phase 4:** `/gsd:execute-phase 04`

After Phase 4 completion, proceed to Phase 5 (Testing Foundation) for 80%+ code coverage.

---

*Roadmap: 2026-03-22 (Phase 4 & Phase 9 plans created with all requirements mapped)*
