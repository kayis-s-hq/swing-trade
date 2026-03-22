# Codebase Structure

**Analysis Date:** 2026-03-07

## Directory Layout

```
[project-root]/
├── .planning/                # GSD planning artifacts
├── api/                      # REST API module
├── broker/                   # Broker/paper trading module
├── core/                     # Domain models module
├── data/                     # Data ingestion/storage module
├── docs/                     # Documentation (user-facing)
├── llm/                      # LLM/sentiment analysis module
├── strategy/                 # Strategy/technical analysis module
├── .git/                     # Git repository
├── .idea/                    # IntelliJ IDEA configuration
├── docker-compose.yml        # Infrastructure orchestration
├── pom.xml                   # Root Maven POM
└── README.md                 # Project documentation
```

## Directory Purposes

**api/ (REST API Module):**
- Purpose: Entry point for external clients, REST endpoints
- Contains: Controllers, services, DTOs, application entry point
- Key files:
  - `/api/src/main/java/com/swingtrade/api/app/SwingTradeApiApplication.java`: Main application class
  - `/api/src/main/java/com/swingtrade/api/SwingTradeController.java`: Main controller
  - `/api/src/main/java/com/swingtrade/api/controller/`: REST controller sub-packages
  - `/api/src/main/java/com/swingtrade/api/dto/`: Request/Response DTOs
  - `/api/src/main/resources/application.properties`: API configuration

**broker/ (Broker Module):**
- Purpose: Paper trading engine, order management, position tracking
- Contains: Trading engine, models, services, Telegram integration
- Key files:
  - `/broker/src/main/java/com/swingtrade/broker/BrokerApplication.java`: Broker entry point
  - `/broker/src/main/java/com/swingtrade/broker/engine/PaperTradeEngine.java`: Core trading logic
  - `/broker/src/main/java/com/swingtrade/broker/service/BrokerService.java`: Broker interface
  - `/broker/src/main/java/com/swingtrade/broker/model/`: Order, Position, Trade models
  - `/broker/src/main/java/com/swingtrade/broker/telegram/`: Telegram notification services

**core/ (Domain Module):**
- Purpose: Immutable domain models, no framework dependencies
- Contains: Stock, Signal, Position, OhlcvCandle domain records
- Key files:
  - `/core/src/main/java/com/swingtrade/domain/Stock.java`: Stock domain model
  - `/core/src/main/java/com/swingtrade/domain/Signal.java`: Trading signal model
  - `/core/src/main/java/com/swingtrade/domain/OhlcvCandle.java`: OHLCV candle model
  - `/core/src/main/java/com/swingtrade/domain/Position.java`: Position model
  - `/core/src/main/java/com/swingtrade/domain/Trade.java`: Trade model

**data/ (Data Module):**
- Purpose: Market data ingestion, PostgreSQL/TimescaleDB storage
- Contains: Services, repositories, entities, configuration, migrations
- Key files:
  - `/data/src/main/java/com/swingtrade/data/Application.java`: Data module entry
  - `/data/src/main/java/com/swingtrade/data/service/DataIngestionService.java`: Data ingestion logic
  - `/data/src/main/java/com/swingtrade/data/service/MarketDataClient.java`: Upstox API client
  - `/data/src/main/java/com/swingtrade/data/entity/`: JPA entities
  - `/data/src/main/java/com/swingtrade/data/repository/`: Spring Data repositories
  - `/data/src/main/resources/db/migration/`: Flyway migrations (V1, V2, V3, V4)

**llm/ (LLM Module):**
- Purpose: LLM-based sentiment analysis, news ingestion
- Contains: LLM clients, sentiment services, news services
- Key files:
  - `/llm/src/main/java/com/swingtrade/llm/LlmClient.java`: LLM client interface
  - `/llm/src/main/java/com/swingtrade/llm/impl/LangChain4jLlmClient.java`: LangChain4j implementation
  - `/llm/src/main/java/com/swingtrade/llm/service/SentimentAnalysisService.java`: Sentiment analysis
  - `/llm/src/main/java/com/swingtrade/llm/service/NewsIngestionService.java`: News fetching
  - `/llm/src/main/java/com/swingtrade/llm/config/LlmConfig.java`: LLM configuration

**strategy/ (Strategy Module):**
- Purpose: Technical analysis, signal generation, backtesting
- Contains: Strategy implementations, indicators, signal engine
- Key files:
  - `/strategy/src/main/java/com/swingtrade/strategy/SwingTradingStrategy.java`: Main strategy
  - `/strategy/src/main/java/com/swingtrade/strategy/SignalEngine.java`: Scheduled signal generator
  - `/strategy/src/main/java/com/swingtrade/strategy/TechnicalIndicators.java`: Indicator calculations
  - `/strategy/src/main/java/com/swingtrade/strategy/impl/DefaultStrategy.java`: TA4J strategy
  - `/strategy/src/main/java/com/swingtrade/strategy/BacktestEngine.java`: Backtesting logic

## Key File Locations

**Entry Points:**
- `/api/src/main/java/com/swingtrade/api/app/SwingTradeApiApplication.java`: Main Spring Boot application
- `/broker/src/main/java/com/swingtrade/broker/BrokerApplication.java`: Broker application (standalone)
- `/data/src/main/java/com/swingtrade/data/Application.java`: Data module application (standalone)

**Configuration:**
- `/api/src/main/resources/application.properties`: Main configuration (database, Redis, Upstox, LLM, Telegram, trading)
- `/data/src/main/resources/application.yml`: Data module config (H2 for testing)
- `/data/src/main/resources/db/migration/V1__swing_trade_schema.sql`: Core schema
- `/data/src/main/resources/db/migration/V2__create_hypertables.sql`: TimescaleDB hypertables
- `/data/src/main/resources/db/migration/V3__create_stocks_table.sql`: Stocks table
- `/data/src/main/resources/db/migration/V4__add_trades_table.sql`: Trades table
- `/pom.xml`: Root Maven POM with dependency management

**Core Logic:**
- `/strategy/src/main/java/com/swingtrade/strategy/SignalEngine.java`: Scheduled signal generation
- `/data/src/main/java/com/swingtrade/data/service/DataIngestionService.java`: Market data ingestion
- `/strategy/src/main/java/com/swingtrade/strategy/SwingTradingStrategy.java`: Multi-factor strategy
- `/broker/src/main/java/com/swingtrade/broker/engine/PaperTradeEngine.java`: Order/position management
- `/llm/src/main/java/com/swingtrade/llm/service/SentimentAnalysisService.java`: LLM sentiment analysis

**Testing:**
- `/api/src/test/java/`: API module tests
- `/broker/src/test/java/com/swingtrade/broker/BrokerModuleIntegrationTest.class`: Broker integration tests
- `/data/src/test/java/com/swingtrade/data/service/DataIngestionServiceTest.class`: Data ingestion tests
- `/strategy/src/test/java/`: Strategy module tests

## Naming Conventions

**Files:**
- Package structure: `com.swingtrade.[module].[layer].[Name]`
- Example: `/api/src/main/java/com/swingtrade/api/controller/SignalController.java`
- Test files: `[Name]Test.java` or `[Name]IT.java` for integration tests
- Example: `/data/src/test/java/com/swingtrade/data/service/DataIngestionServiceTest.java`

**Directories:**
- Standard Maven structure: `src/main/java`, `src/test/java`, `src/main/resources`
- Layer sub-packages: `controller`, `service`, `repository`, `entity`, `model`, `dto`, `config`, `impl`
- Example: `/api/src/main/java/com/swingtrade/api/controller/`
- Example: `/api/src/main/java/com/swingtrade/api/service/`

**Classes:**
- Public classes use descriptive names: `SwingTradingStrategy`, `DataIngestionService`
- Interfaces end with `Service`, `Client`, `Engine`: `BrokerService`, `MarketDataClient`, `LlmClient`
- Implementations prefixed with `Default`: `DefaultStrategy`, `DefaultIndicatorService`
- Entities use `Entity` suffix: `OhlcvCandleEntity`, `SignalEntity`, `StockEntity`
- DTOs use `Request`/`Response` suffix: `TradeRequest`, `SignalResponse`, `PositionResponse`

**Methods:**
- Verb-noun pattern: `generateStrategy()`, `placeOrder()`, `fetchCandle()`
- Boolean methods use `is*`, `has*`, `can*`: `isBuySignal()`, `hasIssues()`, `isValid()`
- Getters/setters from Lombok or explicit: `getSymbol()`, `setSymbol()`

## Where to Add New Code

**New Feature (e.g., new data source):**
- Primary code: `/data/src/main/java/com/swingtrade/data/service/`
- Repository: `/data/src/main/java/com/swingtrade/data/repository/`
- Entity: `/data/src/main/java/com/swingtrade/data/entity/`
- API endpoint: `/api/src/main/java/com/swingtrade/api/controller/`
- Tests: `/data/src/test/java/`, `/api/src/test/java/`

**New Component/Module:**
- Implementation: Create in appropriate module (strategy, llm, broker)
- Example: New indicator in `/strategy/src/main/java/com/swingtrade/strategy/impl/`
- Configuration: Add to `/api/src/main/resources/application.properties`

**Utilities:**
- Shared helpers: `/core/src/main/java/com/swingtrade/domain/` (if domain-related)
- Example: Add to existing `SwingTradingStrategy` or create `IndicatorUtils`

**New Strategy:**
- Strategy implementation: `/strategy/src/main/java/com/swingtrade/strategy/impl/`
- Register via Spring `@Component` annotation
- Add configuration to `/api/src/main/resources/application.properties`

**New API Endpoint:**
- Controller: `/api/src/main/java/com/swingtrade/api/controller/`
- DTOs: `/api/src/main/java/com/swingtrade/api/dto/`
- Service: `/api/src/main/java/com/swingtrade/api/` (service layer)

## Special Directories

**/.planning/codebase/:**
- Purpose: GSD-generated codebase analysis documents
- Generated: Yes (by /gsd:map-codebase command)
- Committed: Yes (ARCHITECTURE.md, STRUCTURE.md, etc.)

**/docs/:**
- Purpose: User-facing documentation
- Contains: README.md, API docs, guides
- Generated: No (manual)
- Committed: Yes

**/target/:**
- Purpose: Maven build artifacts
- Generated: Yes (mvn clean install)
- Committed: No (in .gitignore)

**/src/:**
- Purpose: Additional source files (location unclear from git status)
- Generated: No
- Committed: Yes

---

*Structure analysis: 2026-03-07*
