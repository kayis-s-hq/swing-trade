# Swing Trade System

Automated swing trading system for NSE/BSE Indian equities. Implements 1-4 week hold periods on Nifty 500 stocks using technical analysis, LLM sentiment, and paper trading.

## Architecture

```
swing-trade/
├── backend/                # Multi-module Maven project (Spring Boot 3.3.1)
│   ├── core/               # Domain models (Stock, OhlcvCandle, Signal, Position, Trade)
│   ├── data/               # Data ingestion & storage (Upstox client, JPA, Flyway)
│   ├── strategy/           # TA with TA4j, signal generation, backtesting
│   ├── llm/                # LLM client (vLLM/OpenAI), sentiment analysis, news
│   ├── broker/             # Paper trading engine, order/position management
│   └── api/                # REST endpoints, scheduled jobs
├── dashboard/              # Vue 3 + TypeScript frontend
│   ├── src/                # Components, views, router, stores
│   └── tests/              # Vitest unit + Playwright E2E tests
├── docs/                   # Project documentation
└── dev-stack.sh            # Dev stack orchestration
```

### Module Dependencies

```
api → strategy, llm, broker, data, core
broker → data, core
strategy → data, core
llm → core
data → core
core → (none)
```

## Technology Stack

| Component | Technology |
|-----------|------------|
| Language | Java 21 |
| Framework | Spring Boot 3.3.1 |
| Build | Maven multi-module |
| Database | PostgreSQL 16 + TimescaleDB |
| Cache | Redis 7 |
| TA | TA4j 0.16 |
| LLM | LangChain4j 0.34.0 + vLLM |
| Frontend | Vue 3.5 + TypeScript + Tailwind CSS |
| DB Migrations | Flyway |

## Quick Start

### 1. Start Infrastructure

```bash
./dev-stack.sh start
```

This runs PostgreSQL and Redis on pi-node via SSH, with the app locally on your Mac.

### 2. Build Backend

```bash
cd backend
mvn clean install
```

### 3. Run Backend

```bash
cd api
mvn spring-boot:run -Dspring-boot.run.profiles=local,fyers
```

### 4. Run Frontend

```bash
cd dashboard
yarn
yarn dev
```

Dashboard: http://localhost:5173
API: http://localhost:8080

## API Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/health` | GET | System health (DB/Redis/Upstox states) |
| `/api/signals` | GET | Latest signals |
| `/api/signals/{symbol}` | GET | Signal for specific stock |
| `/api/scan` | GET | Scan stocks (`?days=30&marketCap=min`) |
| `/api/trade` | POST | Execute market order (paper) |
| `/api/positions` | GET | View positions |
| `/api/positions/{symbol}/close` | POST | Close position |
| `/api/performance` | GET | P&L, win rate, trade stats |

## Trading Strategy

**BUY signals** (4+ conditions): EMA 20 > EMA 50 crossover, RSI < 30, MACD positive, price above EMA, volume confirmation, positive momentum.

**SELL signals** (4+ conditions): EMA bearish crossover, RSI > 70, MACD negative, volume spike bearish, price decline.

**Risk management**: 2x ATR stop loss, 2.5x risk-reward target, configurable position sizing.

## Testing

```bash
# Backend — all modules
cd backend && mvn test

# Frontend unit tests
cd dashboard && yarn test

# Frontend E2E tests
cd dashboard && npx playwright test
```

## Configuration

Environment in `backend/.env` (loaded by `dev-stack.sh`). Spring profiles: `local` (dev), `dev`, `fyers` (broker integration).

## Disclaimer

This software is for educational purposes only. Trading stocks involves risk of loss.
