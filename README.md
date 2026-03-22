# Swing Trade System

A production-ready automated swing trading system for NSE/BSE Indian equities with multi-factor technical analysis and LLM sentiment filtering.

## Project Overview

This system implements swing trading strategies (1-4 week hold periods) on Nifty 500 stocks, combining:
- **Technical Analysis**: EMA crossovers, RSI, MACD, ATR-based stops using TA4J
- **LLM Sentiment Filtering**: vLLM-powered sentiment analysis on financial news
- **Paper Trading**: Full order management with risk controls
- **Automated Execution**: Scheduled EOD signal generation and trade execution

## Architecture

```
Swing Trade System
│
├── API Module (REST Interface)
│   ├── TradingController - Execute trades, manage positions
│   ├── SignalController - Get analysis signals
│   ├── PositionController - Position management
│   └── HealthController - System health checks
│
├── Strategy Module (Signal Generation)
│   ├── TechnicalIndicators - TA4J integration (RSI, EMA, MACD, ATR)
│   ├── SwingTradingStrategy - Multi-factor signal logic
│   └── SignalEngine - Scheduled signal generation
│
├── LLM Module (Sentiment Analysis)
│   ├── VLLMClient - OpenAI-compatible LLM client
│   ├── SentimentAnalysisService - Sentiment pipeline
│   ├── SentimentAnalyzer - Prompt formatting
│   └── NewsIngestionService - RSS news fetching
│
├── Broker Module (Paper Trading)
│   ├── PaperTradingEngine - Order execution
│   ├── OrderManager - Order lifecycle
│   ├── PositionManager - Position tracking
│   └── TelegramNotificationService - Alert system
│
├── Data Module (Data Ingestion)
│   ├── DataIngestionService - EOD data collection
│   ├── UpstoxRestClient - Market data API
│   ├── Repositories - JPA data access
│   └── Entity Layer - JPA entities
│
└── Core Module (Domain Models)
    ├── Stock, OhlcvCandle, Signal, Position, Trade
    └── Domain interfaces and enums

Infrastructure:
├── PostgreSQL + TimescaleDB (time-series storage)
├── Redis (caching)
└── Docker Compose (orchestration)
```

## Technology Stack

| Component | Technology |
|-----------|------------|
| Language | Java 21 |
| Framework | Spring Boot 3.3.1 |
| Build Tool | Maven 3.8+ |
| Database | PostgreSQL 15 + TimescaleDB |
| Caching | Redis 7 |
| Technical Analysis | TA4J 0.17 |
| LLM Integration | LangChain4j + vLLM |
| HTTP Client | Spring WebFlux (reactive) |
| Database Migration | Flyway |
| Containerization | Docker |

## Prerequisites

- Java 21 (OpenJDK or Oracle JDK)
- Maven 3.8+
- Docker and Docker Compose
- Mac M1/M2 or x86_64 Linux (Raspberry Pi 5 also supported)

## Quick Start

### 1. Start Infrastructure

```bash
docker-compose up -d
```

This starts:
- PostgreSQL with TimescaleDB (port 5432)
- Redis (port 6379)

Wait 10-15 seconds for databases to initialize.

### 2. Configure Environment

The API module uses environment variables for configuration. Create `.env` in project root:

```bash
# Database
DATABASE_URL=jdbc:postgresql://host.docker.internal:5432/swingtrade_db
DATABASE_USERNAME=swingtrade_user
DATABASE_PASSWORD=swingtrade_password
DATABASE_POOL_SIZE=10

# Redis
REDIS_URL=redis://host.docker.internal:6379

# Upstox API (for market data)
UPSTOX_API_KEY=your_upstox_api_key
UPSTOX_API_SECRET=your_upstox_secret
UPSTOX_ACCESS_TOKEN=your_access_token

# LLM/vLLM (optional - disable by setting BASE_URL empty)
LLM_VLLM_BASE_URL=http://host.docker.internal:8000/v1
LLM_VLLM_MODEL_NAME=qwen3

# Telegram (optional)
TELEGRAM_BOT_TOKEN=your_bot_token
TELEGRAM_ADMIN_IDS=123456789
```

### 3. Build and Run

```bash
# Build entire project
mvn clean install

# Run API module (main entry point)
cd api
mvn spring-boot:run -Pdev
```

Application starts on `http://localhost:8080/api`

## Configuration Guide

### Application Properties

All configurable properties in `api/src/main/resources/application.properties`:

| Category | Key | Description |
|----------|-----|-------------|
| Server | `server.port` | API port (default: 8080) |
| Database | `spring.datasource.*` | PostgreSQL connection |
| Redis | `spring.data.redis.*` | Redis cache |
| Upstox | `upstox.api.*` | Market data API |
| LLM | `llm.vllm.*` | vLLM endpoint |
| Telegram | `telegram.bot.*` | Bot configuration |
| Trading | `trading.*` | Position limits, risk |
| Strategy | `strategy.*` | Indicator parameters |

### Development Mode

Use `application-local.properties` for local development:
- Verbose DEBUG logging
- Development CORS settings
- Paper trading only mode
- Disabled Telegram notifications

## API Endpoints

### Health Check
```bash
GET /api/health
```
Returns system status, database/Redis/Upstox connection states.

### Get Signals
```bash
GET /api/signals/{symbol}
GET /api/signals
```
Returns latest technical + sentiment analysis signal.

### Scan Stocks
```bash
GET /api/scan?days=30&marketCap=min
```
Scan multiple stocks for signals.

### Execute Trade
```bash
POST /api/trade
{
  "symbol": "RELIANCE",
  "direction": "BUY",
  "quantity": 100
}
```
Execute market order (paper mode).

### Position Management
```bash
GET /api/positions
GET /api/positions/{symbol}
POST /api/positions/{symbol}/close
```
View and close positions.

### Performance
```bash
GET /api/performance
```
Returns P&L, win rate, trade statistics.

## Trading Strategy

### Entry Signals (BUY)
The system generates BUY signals when 4+ of these conditions align:

1. **EMA Crossover**: Fast EMA (20) > Slow EMA (50)
2. **RSI Oversold**: RSI < 30 (potential reversal)
3. **MACD Positive**: MACD histogram > 0
4. **Price Above EMA**: Close > Fast EMA
5. **Volume Confirmation**: Volume > 10-day average with bullish candle
6. **Positive Momentum**: Daily price change > 0

### Exit Signals (SELL)
Exit when 4+ conditions indicate bearish momentum:
- EMA bearish crossover
- RSI > 70 (overbought)
- MACD negative
- Volume spike with bearish candle
- Price decline > threshold

### Risk Management
- **Stop Loss**: Entry price - 2×ATR
- **Target**: Entry price + 2.5×R (2.5:1 risk-reward)
- **Position Size**: Based on ATR and account risk %
- **Max Exposure**: Configurable per-position and total

## Scheduled Jobs

| Job | Schedule | Description |
|-----|----------|-------------|
| Data Ingestion | 16:30 IST M-F | Fetch EOD candles from Upstox |
| Signal Generation | 17:00 IST M-F | Analyze all stocks, generate signals |
| Stop Loss Check | Every 5 min | Monitor open positions for stops |
| News Ingestion | Every 30 min | Fetch and analyze financial news |

## Project Structure

```
swing-trade/
├── core/                 # Domain models
│   └── src/main/java/com/swingtrade/domain/
│       ├── Stock.java
│       ├── OhlcvCandle.java
│       ├── Signal.java
│       ├── Position.java
│       ├── Trade.java
│       └── SentimentResult.java
│
├── data/                 # Data layer
│   ├── src/main/java/com/swingtrade/data/
│   │   ├── entity/       # JPA entities
│   │   ├── repository/   # Spring Data repos
│   │   └── service/      # DataIngestionService, MarketDataClient
│   └── src/main/resources/db/migration/
│       ├── V1__swing_trade_schema.sql
│       ├── V2__create_hypertables.sql
│       ├── V3__create_stocks_table.sql
│       └── V4__add_trades_table.sql
│
├── strategy/             # Trading strategies
│   └── src/main/java/com/swingtrade/strategy/
│       ├── TechnicalIndicators.java
│       ├── SwingTradingStrategy.java
│       └── SignalEngine.java
│
├── llm/                  # LLM integration
│   └── src/main/java/com/swingtrade/llm/
│       ├── client/VLLMClient.java
│       ├── service/SentimentAnalysisService.java
│       ├── service/SentimentAnalyzer.java
│       └── service/NewsIngestionService.java
│
├── broker/               # Paper trading
│   └── src/main/java/com/swingtrade/broker/
│       ├── engine/PaperTradingEngine.java
│       ├── manager/OrderManager.java
│       ├── manager/PositionManager.java
│       └── telegram/TelegramNotificationService.java
│
└── api/                  # REST interface
    └── src/main/java/com/swingtrade/api/
        ├── controller/    # REST controllers
        ├── service/       # Business logic services
        ├── dto/           # Request/Response DTOs
        └── config/        # Spring configuration
```

## Testing

```bash
# Run all tests
mvn test

# Run specific module tests
cd data && mvn test
cd strategy && mvn test
cd broker && mvn test
cd api && mvn test

# Run with coverage report
mvn clean test jacoco:report
```

## Troubleshooting

### Database Connection Failed
- Verify PostgreSQL is running: `docker-compose ps`
- Check credentials match `.env` file
- Ensure Flyway migrations completed: check logs for "schema validated"

### Upstox API Errors
- Verify API key/secret are valid
- Check access token hasn't expired
- Review Upstox API documentation for rate limits

### vLLM/LLM Not Available
- Ensure vLLM server running on configured port
- Check model name matches deployed model
- Set `LLM_VLLM_BASE_URL=` empty to disable LLM features

### Build Failures
- Ensure Java 21 is installed: `java -version`
- Clear Maven cache: `mvn clean`
- Update dependencies: `mvn update-snapshots`

## Production Deployment

### Environment Variables Required
- `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`
- `REDIS_URL`
- `UPSTOX_API_KEY`, `UPSTOX_API_SECRET`, `UPSTOX_ACCESS_TOKEN`
- `TELEGRAM_BOT_TOKEN` (optional)
- `LLM_VLLM_BASE_URL` (optional)

### Docker Deployment
```bash
docker-compose -f docker-compose.yml -f docker-compose.prod.yml up -d
docker build -t swing-trade-api ./api
docker push swing-trade-api:latest
```

## License

MIT License - See LICENSE file for details.

## Disclaimer

This software is for educational purposes only. Trading stocks involves risk of loss.
Past performance does not guarantee future results. Use at your own risk.
