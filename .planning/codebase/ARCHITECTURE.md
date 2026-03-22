# Architecture

**Analysis Date:** 2026-03-07

## Pattern Overview

**Overall:** Layered Architecture with Separation of Concerns

**Key Characteristics:**
- Modular multi-module Maven build with six independent but interconnected modules
- Clean separation between domain models, data persistence, strategy, broker, and API layers
- Spring Boot auto-configuration with dependency injection throughout
- Scheduled jobs for market data ingestion and signal generation
- Repository pattern for data access with JPA/Hibernate

## Layers

**Domain Layer (Core Module):**
- Purpose: Contains immutable domain models representing business entities
- Location: `/core/src/main/java/com/swingtrade/domain/`
- Contains: `Stock`, `OhlcvCandle`, `Signal`, `Position`, `Trade`, `SentimentResult`
- Depends on: None (pure domain objects, no framework dependencies)
- Used by: All other modules depend on core

**Data Layer (Data Module):**
- Purpose: Market data ingestion, storage, and retrieval from PostgreSQL/TimescaleDB
- Location: `/data/src/main/java/com/swingtrade/data/`
- Contains: `DataIngestionService`, `MarketDataClient`, `UpstoxRestClient`, JPA entities
- Depends on: Core module, Spring Data JPA, Flyway
- Used by: Strategy, SignalEngine

**Strategy Layer (Strategy Module):**
- Purpose: Technical analysis and trading signal generation
- Location: `/strategy/src/main/java/com/swingtrade/strategy/`
- Contains: `SwingTradingStrategy`, `SignalEngine`, `TechnicalIndicators`, `DefaultStrategy`
- Depends on: Core, Data modules, TA4J library
- Used by: API module

**LLM Layer (LLM Module):**
- Purpose: Sentiment analysis using LLMs and news ingestion
- Location: `/llm/src/main/java/com/swingtrade/llm/`
- Contains: `LangChain4jLlmClient`, `SentimentAnalysisService`, `NewsIngestionService`, `VLLMClient`
- Depends on: Core, Data modules, LangChain4j
- Used by: API module

**Broker Layer (Broker Module):**
- Purpose: Paper trading engine and order management
- Location: `/broker/src/main/java/com/swingtrade/broker/`
- Contains: `PaperTradeEngine`, `BrokerService`, `Order`, `Position`, `Portfolio`
- Depends on: Core, Strategy, Data modules
- Used by: API module

**API Layer (API Module):**
- Purpose: REST API endpoints for system interaction
- Location: `/api/src/main/java/com/swingtrade/api/`
- Contains: Controllers, services, DTOs
- Depends on: All other modules (core, data, strategy, llm, broker)
- Used by: External clients

## Data Flow

**Signal Generation Flow:**

1. `DataIngestionService` ingests OHLCV candles from Upstox API (scheduled at 16:30 IST)
2. Candles stored in PostgreSQL/TimescaleDB via `OhlcvCandleRepository`
3. `SignalEngine` triggers daily at 17:00 IST (or manually via API)
4. Retrieves recent candles for each symbol from repository
5. Converts entities to domain objects
6. `SwingTradingStrategy.analyze()` evaluates technical indicators (EMA, RSI, MACD, volume)
7. Signal saved to `SignalRepository` with BUY/SELL/HOLD type and confidence

**Trading Flow:**

1. Client invokes API endpoint to scan for signals
2. `SignalService` queries latest signals from database
3. For BUY signals above threshold, client places order via API
4. `BrokerService` delegates to `PaperTradeEngine`
5. Engine validates position limits (max 5 concurrent, 20% capital per position)
6. Order created and stored, position created on execution
7. Telegram notifications sent (if enabled)

**Sentiment Analysis Flow:**

1. `NewsIngestionService` fetches news from NSE corporate announcements
2. `SentimentAnalyzer` processes news content via LangChain4j LLM client
3. Sentiment cached in Redis with TTL
4. Result integrated with technical signal confidence

## Key Abstractions

**SwingTradingStrategy:**
- Purpose: Core strategy for generating trading signals from OHLCV data
- Examples: `/strategy/src/main/java/com/swingtrade/strategy/SwingTradingStrategy.java`
- Pattern: Component with technical indicator calculations

**SignalEngine:**
- Purpose: Orchestrates signal generation workflow with scheduling
- Examples: `/strategy/src/main/java/com/swingtrade/strategy/SignalEngine.java`
- Pattern: @Component with @Scheduled annotations

**BrokerService:**
- Purpose: Abstraction over trading operations (paper/real broker)
- Examples: `/broker/src/main/java/com/swingtrade/broker/service/BrokerService.java`
- Pattern: Service interface with `PaperTradingServiceImpl` implementation

**MarketDataClient:**
- Purpose: Abstraction over market data sources
- Examples: `/data/src/main/java/com/swingtrade/data/service/MarketDataClient.java`
- Pattern: Interface with `UpstoxRestClient` implementation

**LlmClient:**
- Purpose: Abstraction over LLM providers
- Examples: `/llm/src/main/java/com/swingtrade/llm/LlmClient.java`
- Pattern: Interface with `LangChain4jLlmClient`, `VLLMClient` implementations

## Entry Points

**SwingTradeApiApplication:**
- Location: `/api/src/main/java/com/swingtrade/api/app/SwingTradeApiApplication.java`
- Triggers: JVM startup via `mvn spring-boot:run`
- Responsibilities: Boots Spring Boot context, scans all modules, starts scheduled tasks

**DataIngestionService.autoIngestData():**
- Location: `/data/src/main/java/com/swingtrade/data/service/DataIngestionService.java` (line 60)
- Triggers: Scheduled cron `0 30 16 * * MON-FRI` (16:30 IST weekdays)
- Responsibilities: Fetches EOD data for Nifty 500 stocks

**SignalEngine.generateDailySignals():**
- Location: `/strategy/src/main/java/com/swingtrade/strategy/SignalEngine.java` (line 50)
- Triggers: Scheduled cron `0 0 17 * * MON-FRI` (17:00 IST weekdays)
- Responsibilities: Analyzes candles, generates signals for all stocks

## Error Handling

**Strategy:** Exception propagation with logging

**Patterns:**
- Validation exceptions thrown as `IllegalArgumentException` with descriptive messages
- Business rule violations thrown as `IllegalStateException`
- Logging via SLF4J at appropriate levels (DEBUG, INFO, WARN, ERROR)
- Transactional rollback via `@Transactional` annotations

**Example from DefaultStrategy:**
```java
if (barSeries == null) {
    throw new IllegalArgumentException("BarSeries cannot be null");
}
```

**Example from PaperTradeEngine:**
```java
if (!validateOrderConstraints(order)) {
    throw new IllegalStateException(
        "Order validation failed - maximum positions..."
    );
}
```

## Cross-Cutting Concerns

**Logging:**
- Framework: SLF4J with Logback
- Configuration: `/api/src/main/resources/application.properties`
- Pattern: `Logger logger = LoggerFactory.getLogger(Class.class);`
- Levels: INFO for operations, DEBUG for tracing, ERROR for failures

**Validation:**
- Domain: Bean Validation (Jakarta Validation) via `spring-boot-starter-validation`
- Business rules: Manual validation in service methods
- Example: `if (order == null) throw new IllegalArgumentException(...)`

**Transaction Management:**
- Framework: Spring `@Transactional`
- Configuration: PostgreSQL with Hibernate
- Usage: All data write operations wrapped in transactions
- Location: `DataIngestionService`, `SignalEngine`, `PaperTradeEngine`

**Caching:**
- Framework: Spring Cache with Redis backend
- Configuration: `/api/src/main/resources/application.properties` (lines 58-73)
- Cache names: `stocks`, `ohlcv`, `signals`, `sentiment`
- TTL: 3600000ms (1 hour)

---

*Architecture analysis: 2026-03-07*
