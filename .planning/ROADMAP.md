# ROADMAP.md - SwingTrade Implementation Phases

**Document Version:** 2.0
**Created:** 2026-03-07
**Last Updated:** 2026-03-22 (Re-initialized with 7 phases)

---

## Overview

SwingTrade is a 7-phase project to build a production swing trading system for Indian equities. Phases 1–3 are complete (paper trading active). Phase 4 (LLM) is partially implemented and needs verification. Phases 5–7 are deferred pending Phase 4 completion.

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

**Status:** ✅ Complete (2026-03-20)
**Duration:** 3–4 days
**Priority:** High

### Requirements Mapped

- REQ-007a: EMA Crossover (Price > EMA20 > EMA50) ✅
- REQ-007b: RSI Analysis (RSI 50–65) ✅
- REQ-007c: Volume Spike (Vol > 1.5x 20d avg) ✅
- REQ-007d: ATR-based Stops (entry − 2×ATR, target +2.5×risk) ✅
- REQ-008a: 4-factor entry logic ✅
- REQ-008b: Exit logic (stop/target/time/EMA break) ✅
- REQ-009: Scheduled signal generation at 17:00 IST ✅
- REQ-010: Backtest engine ✅

### Success Criteria

- [x] All indicators implemented (TA4J v0.16)
- [x] 4-factor signal generation working
- [x] Backtest engine calculates accurate metrics
- [x] Scheduled signal generation runs at 17:00 IST

---

## Phase 3: Data Pipeline + Signal Engine + Paper Trading ✅ COMPLETE

**Objective:** OHLCV data ingestion, paper trading engine, REST API.

**Status:** ✅ Complete (2026-03-20)
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

## Phase 4: LLM Sentiment Layer ⚠️ PARTIAL

**Objective:** News ingestion, sentiment analysis, signal filtering.

**Status:** ⚠️ Partial (module exists, integration TBD)
**Duration:** 1–2 weeks
**Priority:** High
**Blocking:** Phase 5 cannot start until Phase 4 verified

### Requirements Mapped

- REQ-025: LangChain4j client ⚠️ Partial
- REQ-026: News ingestion ⚠️ Partial
- REQ-027: Sentiment analysis ⚠️ Partial
- REQ-028: Signal filtering ⚠️ Partial
- REQ-029: Weekly sector digest ⚠️ Partial

### Success Criteria

- [ ] vLLM endpoint reachable (RTX 5090 inference)
- [ ] News fetched for all Nifty 500 stocks (7-day history)
- [ ] Sentiment analysis returns POSITIVE/NEUTRAL/NEGATIVE
- [ ] NEGATIVE signals suppressed from paper portfolio
- [ ] NEUTRAL signals flagged with ⚠️ in Telegram
- [ ] Weekly sector digest sent Sunday 17:00 IST
- [ ] Integration tests pass with TestContainers

### What Needs to Be Done

1. **Verify LLM module:** Confirm all classes exist and are integrated
2. **Verify news ingestion:** Google News RSS, NSE API, 7-day history
3. **Verify sentiment pipeline:** Structured prompt, vLLM response parsing
4. **Verify signal filtering:** Integration with SignalEngine, NEGATIVE suppression
5. **Verify scheduling:** Weekly digest job Sundays, Telegram formatting
6. **Add integration tests:** TestContainers for PostgreSQL, MockRestServiceServer for vLLM

---

## Phase 5: Testing Foundation ⏳ DEFERRED

**Objective:** Unit + integration tests, 80%+ code coverage.

**Status:** ⏳ Deferred (start after Phase 4 verified)
**Duration:** 2–3 weeks
**Priority:** High
**Depends On:** Phase 4 completion
**Blocking:** Phase 6 cannot start until testing complete

### Requirements Mapped

- REQ-101: Core domain unit tests ⏳
- REQ-102: Strategy module unit tests ⏳
- REQ-103: TestContainers integration ⏳
- REQ-104: MockRestServiceServer (NOT WireMock) ⏳
- REQ-105: API endpoint tests ⏳
- REQ-106: JaCoCo coverage (80%+) ⏳

### Success Criteria

- [ ] All unit tests passing
- [ ] All integration tests passing
- [ ] core: 100% coverage
- [ ] strategy: 85%+ coverage
- [ ] data, broker, api, llm: 80%+ coverage
- [ ] JaCoCo Maven profile working (`mvn clean install -P coverage`)

### Key Implementation Decisions

- **NO WireMock:** Use MockRestServiceServer (Spring-native, lighter)
- **Real database:** TestContainers for PostgreSQL
- **Mockito + Spring MockBean:** For external API mocking

---

## Phase 6: Live Trading ⏳ PLANNED

**Objective:** Zerodha Kite Connect integration.

**Status:** ⏳ Planned (start after Phase 5 + paper validation)
**Duration:** 1–2 weeks
**Priority:** Medium
**Depends On:** Phase 5 completion + 2–3 months paper validation

### Requirements Mapped

- REQ-030: Zerodha Kite Connect ⏳
- REQ-031: BrokerServiceFactory routing ⏳
- REQ-032: Kill switch ⏳
- REQ-033: Capital management ⏳

### Success Criteria

- [ ] Kite Java SDK integrated
- [ ] Live order placement working
- [ ] Kill switch tested and verified
- [ ] BrokerServiceFactory switches modes via config
- [ ] Capital tracking enforces ₹50K limit
- [ ] Max 3 live positions enforced

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

## Phase Dependencies

```
Phase 1: Core Domain ✅
    │
    ├──> Phase 2: Strategy Engine ✅
    │       │
    │       ├──> Phase 3: Data Pipeline + Paper Trading ✅
    │       │       │
    │       │       ├──> Phase 4: LLM Sentiment ⚠️ (verification needed)
    │       │       │       │
    │       │       │       ├──> Phase 5: Testing Foundation ⏳
    │       │       │       │       │
    │       │       │       │       ├──> Phase 6: Live Trading ⏳
    │       │       │       │       │       │
    │       │       │       │       │       ├──> Phase 7: Observability ⏳
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
|-------|--------|--------------|-----------|
| 1 | ✅ Complete | 2026-03-15 | 2026-03-20 |
| 2 | ✅ Complete | 2026-03-15 | 2026-03-20 |
| 3 | ✅ Complete | 2026-03-15 | 2026-03-20 |
| 4 | ⏳ In Planning | 2026-03-23 | 2026-04-06 |
| 5 | ⏳ Pending Phase 4 | 2026-04-07 | 2026-04-30 |
| 6 | ⏳ Pending Phase 5 | 2026-05-01 | 2026-05-15 |
| 7 | ⏳ Pending Phase 6 | 2026-05-15 | 2026-06-30 |

---

## Next Immediate Action

**Phase 4 (LLM Sentiment Layer) Verification**

The LLM module exists but needs verification:

1. Run `/gsd:plan-phase 4` to create detailed implementation plan
2. Verify news ingestion pipeline completeness
3. Verify sentiment filtering integration with SignalEngine
4. After Phase 4 verified, run `/gsd:plan-phase 5` for testing foundation

---

*Roadmap: 2026-03-22 (Re-initialized with 7 phases, full context)*
