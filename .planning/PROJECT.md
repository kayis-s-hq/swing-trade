# PROJECT.md - SwingTrade Swing Trading System

**Document Version:** 2.0
**Created:** 2026-03-07
**Last Updated:** 2026-03-22 (Re-initialized with full project context)

---

## What This Is

SwingTrade is a fully automated swing trading system for NSE/BSE Indian equities. Built in Java 21 with Spring Boot 3.x, it uses a multi-factor technical analysis approach (EMA, RSI, Volume, Support/Resistance) enhanced by LLM sentiment analysis to filter signals, paper trades for 2-3 months of validation, then deploys live capital with strict risk controls.

**Hold period:** 1–4 weeks (pure swing, no intraday, no F&O)
**Universe:** Nifty 500 filtered to ~250 liquid stocks
**Paper trading:** Started 2026-03-20, running indefinitely until live validation complete
**Live trading:** Target ₹50,000 capital after paper validation phase

---

## Core Value

**Execute the 4-factor swing strategy across Nifty 500, validate with 2-3 months paper trading, then deploy live capital.**

Everything else serves this: if the strategy doesn't work in paper, live trading doesn't happen. If live capital isn't deployed, the system remains a backtest toy, not a production trading system.

---

## Requirements

### Validated (Phases 1–3 Complete)

Core domain models, technical indicators, data pipeline, signal generation, and paper trading engine all implemented and running:

- ✓ Domain models (Stock, OhlcvCandle, Signal, Position, Trade, SentimentResult) — Phase 1
- ✓ Technical indicators (EMA, SMA, RSI, MACD, ATR, VolumeMA) with TA4J — Phase 2
- ✓ 4-factor signal generation logic — Phase 2
- ✓ Historical backtesting engine — Phase 2
- ✓ PostgreSQL + TimescaleDB schema with Flyway migrations — Phase 3
- ✓ Upstox API integration for OHLCV data ingestion — Phase 3
- ✓ Scheduled auto-ingestion at 16:30 IST (weekday) — Phase 3
- ✓ Paper trading engine with order placement and position tracking — Phase 3–4
- ✓ Risk controls: max 5 positions, 20% capital/position, daily loss circuit breaker — Phase 4
- ✓ Telegram notifications for trade events — Phase 4
- ✓ REST API endpoints for signals, positions, portfolio, performance — Phase 5

### Active (Phases 4–7 Pending)

- [ ] **Phase 4 – LLM Sentiment Layer** (module exists, needs integration verification):
  - News ingestion from Google News RSS + NSE corporate announcements (last 7 days per stock)
  - Sentiment analysis pipeline with vLLM (Qwen3-30B-AWQ, OpenAI-compatible)
  - Signal filtering: NEGATIVE suppressed, NEUTRAL flagged with ⚠️ in Telegram
  - Weekly sector sentiment digest (Sunday scheduled job)

- [ ] **Phase 5 – Testing Foundation**:
  - Unit tests for core domain models (100% coverage)
  - Unit tests for strategy module (85%+ coverage)
  - Integration tests with TestContainers (PostgreSQL + TimescaleDB, NO WireMock)
  - HTTP mocking with MockRestServiceServer for Upstox + vLLM
  - API endpoint tests with @SpringBootTest + MockMvc
  - 80%+ code coverage across all modules (JaCoCo)

- [ ] **Phase 6 – Live Trading**:
  - Zerodha Kite Connect Java SDK integration (₹2000/yr license)
  - BrokerServiceFactory routes to Kite vs paper mode based on config
  - Kill switch: halts live orders, closes positions, sends Telegram alert
  - Capital management: ₹50K initial, max 3 concurrent live positions

- [ ] **Phase 7 – Observability + Iteration**:
  - Grafana dashboards (Spring Actuator + Micrometer metrics)
  - Monthly strategy review reports (win rate, R:R, drawdown)
  - Trade outcome labelling for future Qwen3 fine-tuning (6+ months of data)

### Out of Scope

- **Intraday trading** — swing only, 1–4 week holds
- **Futures & Options** — NSE/BSE equity spot only
- **LLM-based price prediction** — LLM is a sentiment filter only, not a signal generator
- **LLM fine-tuning** — deferred until 6+ months of labelled trade data exists (Q3 2026+)
- **Mobile app** — REST API only, no mobile client
- **Cloud deployment** — self-hosted on Raspberry Pi 5 + RTX 5090 only
- **User authentication** — single-user system, no login/identity mgmt
- **Advanced order types** — MARKET orders only, no LIMIT/STOP/SL orders
- **Real broker auth in code** — credentials via environment variables only

---

## Context

### Infrastructure

- **App server:** Raspberry Pi 5 (8GB RAM, NVMe) — runs Spring Boot, PostgreSQL, Redis
- **LLM inference:** Rented RTX 5090 (32GB VRAM) — serves Qwen3-30B-AWQ via vLLM (OpenAI-compatible endpoint)
- **Dev machine:** MacBook M1 for development
- **Pi does NOT inference LLM** — only RTX 5090 does. Pi calls vLLM endpoint via HTTP.

### Technology Stack

| Component | Choice | Rationale |
|-----------|--------|-----------|
| **Language** | Java 21 | Type safety, performance, Spring Boot ecosystem |
| **Framework** | Spring Boot 3.x | Scheduling, dependency injection, REST, Actuator |
| **Database** | PostgreSQL + TimescaleDB | Time-series OHLCV, hypertables, open source |
| **Cache** | Redis | Session-like data, indicator cache, signal cache |
| **Indicators** | TA4J | Java-native, battle-tested, 60+ indicators |
| **LLM** | LangChain4j + vLLM | Spring Boot integration, OpenAI-compatible API |
| **Broker (paper)** | Custom engine | Full control, no broker API throttling |
| **Broker (live)** | Zerodha Kite Connect | ₹2000/yr, India-specific, Java SDK available |
| **Data source (dev)** | Yahoo Finance | Free, no auth, for backfill only |
| **Data source (live)** | Upstox v2 REST API | Free tier, live market data |
| **Alerts** | Telegram Bot API | Real-time, mobile push, easy integration |
| **Testing** | JUnit 5 + Mockito + TestContainers | Modern, Spring integration, real DB containers |
| **Build** | Maven multi-module | Clear module boundaries, dependency management |
| **Monitoring** | Grafana + Micrometer | Spring-native metrics, visual dashboards |

### Strategy Logic

**Entry (ALL 4 must be true):**
1. Price > EMA20 > EMA50 (uptrend confirmed)
2. RSI(14) between 50–65 (momentum rising, not overbought)
3. Volume > 1.5x 20-day average (conviction move)
4. Price within 3% of 52-week high OR breaking above resistance

**Exit (ANY ONE triggers):**
1. Stop loss: price < entry − (2 × ATR14 at entry)
2. Target: price > entry + (2.5 × risk) [1:2.5 R:R]
3. Time stop: position held > 20 trading days
4. Trend break: close below EMA20 for 2 consecutive days

**Position sizing:**
- Risk-based: 1% of capital per trade, sized by ATR stop distance
- Max concurrent: 5 positions in paper mode, 3 in live mode
- Max per position: 20% of capital

### User Profile

- **Role:** Staff Security Software Engineer at Qualcomm (10+ years Java/Spring Boot)
- **Side project:** Building with experienced swing trader (non-technical)
- **Preferred:** Java over Python for all backend work
- **Dev constraint:** Works alongside Qualcomm job, part-time availability
- **Decision style:** Pragmatic, values simplicity over over-engineering

---

## Constraints

| Type | What | Why |
|------|------|-----|
| **Timeline** | Paper trading: 2–3 months (started 2026-03-20) | Validate strategy before risking capital |
| **Capital (live)** | ₹50,000 initial | Limited capital, strict risk mgmt (max 3 positions, 1% risk/trade) |
| **Tech stack** | Java 21 + Spring Boot 3.x only | Expertise, production-grade ecosystem |
| **Testing** | NO WireMock (use MockRestServiceServer instead) | Lighter footprint, Spring-native, sufficient for this use case |
| **Database** | PostgreSQL + TimescaleDB (not ClickHouse, not Mongo) | Time-series native, SQL compatibility, hypertables for daily OHLCV |
| **Broker auth** | Environment variables only (no hardcoded secrets) | Security best practice, CI/CD friendly |
| **LLM inference** | Pi does NOT do inference (only RTX 5090 via HTTP) | Pi doesn't have GPU, inference must be remote |
| **Live trading start** | Only after 2–3 months paper validation | Risk management: prove strategy before real capital |

---

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| **Paper trading first** | Validate strategy logic before risking ₹50K | ✓ Good: running since 2026-03-20, collecting outcome data |
| **LLM as filter, not signal** | LLM hallucination too risky for price prediction | ✓ Good: news-based sentiment filters suppress NEGATIVE signals, NEUTRAL flagged |
| **Qwen3-30B (not GPT/Claude)** | Running locally on RTX 5090, cost control, no API keys in code | ✓ Good: fast, cheap, private |
| **Mockito + TestContainers, NO WireMock** | Simpler setup, sufficient for external API mocking, lighter weight | — Pending: not yet tested at scale |
| **TA4J (not custom indicators)** | Industry-standard, 60+ built-in indicators, battle-tested | ✓ Good: reliable, reduces custom code |
| **Zerodha Kite (live broker)** | Only ₹2000/yr, Java SDK available, India-specific | — Pending: live trading phase deferred |
| **Multi-module Maven** | Clear boundaries, independent testing, modular deployment | ✓ Good: core, data, strategy, llm, broker, api modules decouple well |
| **Redis caching** | Indicator cache (hot data), signal cache, sentiment cache | ✓ Good: reduces DB load, speeds up repeated indicator calcs |
| **Flyway migrations** | Version-controlled schema, CI/CD friendly, repeatable | ✓ Good: 4 migrations (V1–V4) deployed successfully |

---

## Module Responsibilities

| Module | Responsibility | Status |
|--------|-----------------|--------|
| **core** | Domain models (Stock, OhlcvCandle, Signal, Position, Trade, SentimentResult) | ✅ Complete |
| **data** | Data ingestion, PostgreSQL + TimescaleDB, Upstox API client, scheduling | ✅ Complete |
| **strategy** | Technical indicators (TA4J), signal generation, backtesting engine | ✅ Complete |
| **llm** | LangChain4j client, news ingestion, sentiment analysis, signal filtering | ⚠️ Partial (module exists, integration TBD) |
| **broker** | Paper/live trading engine, risk controls, order management, Telegram alerts | ✅ Complete (paper mode only) |
| **api** | REST endpoints, DTOs, service layer, portfolio tracking | ✅ Complete |

---

## Milestones

### v1.0 – Core Features (2026-03-20)
- [x] Domain models
- [x] Technical indicators
- [x] Signal generation
- [x] Data pipeline (Upstox)
- [x] Paper trading engine
- [x] Risk controls
- [x] REST API
- [x] Telegram notifications
- **Status:** ✅ Complete, paper trading active

### v1.1 – LLM + Testing (Target: 2026-04-30)
- [ ] News ingestion pipeline
- [ ] Sentiment analysis + filtering
- [ ] Unit tests (80%+ coverage)
- [ ] Integration tests (TestContainers)
- [ ] API endpoint tests

### v2.0 – Live Trading (Target: 2026-05-15)
- [ ] Zerodha Kite Connect integration
- [ ] Kill switch + capital management
- [ ] Live trading deployment

### v2.1 – Observability (Target: 2026-06-30)
- [ ] Grafana dashboards
- [ ] Monthly review reports
- [ ] Trade labelling for future fine-tuning

---

*Last updated: 2026-03-22 after re-initialization with full project context*
