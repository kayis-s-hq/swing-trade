# External Integrations

**Analysis Date:** 2026-03-07

## APIs & External Services

**Stock Market Data:**
- **Upstox API** - Market data retrieval and order execution
  - Base URL: `https://api.upstox.com/v2`
  - Endpoints:
    - Login: `${upstox.api.base-url}/v2/login`
    - Token: `${upstox.api.base-url}/v2/token`
    - Market Data: `${upstox.api.base-url}/v2/market-data`
    - Orders: `${upstox.api.base-url}/v2/orders`
    - Subscription: `${upstox.api.base-url}/v2/market-data/subscribe`
  - SDK/Client: `UpstoxRestClient` in `data/src/main/java/com/swingtrade/data/service/UpstoxRestClient.java`
  - Auth: Environment variables `UPSTOX_CLIENT_ID`, `UPSTOX_CLIENT_SECRET`, `UPSTOX_API_KEY`
  - Rate Limiting: 10 requests/second, 100 requests/minute

**LLM / Sentiment Analysis:**
- **vLLM Server** - Local LLM inference server for sentiment analysis
  - Base URL: `${LLM_BASE_URL:http://localhost:8000}`
  - Model: `meta-llama/Llama-3.2-3B-Instruct` (configurable via `LLM_MODEL_NAME`)
  - Client: `LangChain4jLlmClient` in `llm/src/main/java/com/swingtrade/llm/impl/LangChain4jLlmClient.java`
  - Auth: No authentication configured (local deployment)
  - Timeout: 30 seconds default (`llm.timeout.millis`)
  - Retry: Enabled with exponential backoff (max 3 attempts)

**News Sources:**
- **NSE Corporate Announcements** - Stock announcements
  - Endpoint: `https://nseindia.com/api/corporate-announcements`
  - Config: `news.nse.endpoint`
  - Cache TTL: 3600 seconds

- **Google Custom Search** - News aggregation
  - Engine: `langchain4j-web-search-engine-google-custom`
  - Config: `news.google-rss.url` (RSS endpoint)
  - Limit: 50 news items (`news.fetch.limit`)

## Data Storage

**Databases:**
- **PostgreSQL with TimescaleDB** - Primary data store for time-series market data
  - Connection: `spring.datasource.url=jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:swingtrade_db}`
  - User: `${DB_USER:swingtrade_user}`
  - Password: `${DB_PASSWORD:swingtrade_password}`
  - Client: Hibernate JPA with HikariCP connection pool
  - Migration: Flyway with SQL scripts in `data/src/main/resources/db/migration/`
  - Hypertable: `ohlcv_candles` with TimescaleDB extension

**Caching:**
- **Redis** - Distributed caching layer
  - Host: `${REDIS_HOST:localhost}`
  - Port: `${REDIS_PORT:6379}`
  - Cache Names: `stocks`, `ohlcv`, `signals`, `sentiment` (api module), `positions`, `orders`, `portfolio` (broker module)
  - TTL: 3600 seconds default
  - Client: Spring Data Redis with Lettuce
  - Key Prefix: `swingtrade:` (api), `broker:` (broker)

## Authentication & Identity

**Auth Provider:**
- **Upstox OAuth 2.0** - Broker authentication
  - Implementation: Custom OAuth flow in `UpstoxRestClient`
  - Client ID: `${UPSTOX_CLIENT_ID}`
  - Client Secret: `${UPSTOX_CLIENT_SECRET}`
  - Redirect URI: `${UPSTOX_REDIRECT_URI:http://localhost:8080/callback}`
  - Token storage: In-memory/session storage

**System Authentication:**
- No user authentication implemented in REST API
- Telegram bot authentication via bot token

## Monitoring & Observability

**Error Tracking:**
- Not configured (no dedicated error tracking service like Sentry or Datadog)

**Logging:**
- Framework: SLF4J + Logback
- Configuration: `logging.level.root=INFO`, `logging.level.com.swingtrade=DEBUG`
- Output: Console and file (`${LOG_FILE:/var/log/swing-trade/api.log}`)
- Pattern: `%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n`
- Package-specific levels: DEBUG for api, data, strategy, llm, broker; INFO for core

**Metrics:**
- Framework: Micrometer
- Registry: Prometheus
- Endpoints:
  - `/api/actuator/health` - Health checks
  - `/api/actuator/info` - Application info
  - `/api/actuator/metrics` - Metrics data
  - `/api/actuator/prometheus` - Prometheus exposition format
- Configuration: `management.endpoints.web.exposure.include=health,info,metrics,prometheus`
- Histograms: HTTP server request percentiles enabled
- Prometheus scrape interval: 60 seconds

**Health Checks:**
- Disabled by default in broker module
- Enabled in api module with:
  - Liveness probe: `management.health.livelystate.enabled=true`
  - Readiness probe: `management.health.readystate.enabled=true`
  - Redis health: `management.health.redis.enabled=true`
  - Disk space: 10MB threshold

## CI/CD & Deployment

**Containerization:**
- Docker Compose for local development
- Services:
  - `postgres` - TimescaleDB image
  - `redis` - Redis alpine image
- Networking: Bridge network `swingtrade-network`
- Volumes: `postgres_data`, `redis_data` for persistence

**Build & Packaging:**
- Tool: Maven multi-module build
- Output: Executable JARs with Spring Boot Maven plugin
- Layering: Enabled for optimized Docker images
- Profile: `coverage` for JaCoCo reporting

**Deployment Target:**
- Self-hosted (vLLM for LLM)
- Spring Boot fat JAR execution: `java -jar api/target/api-1.0.0.jar`
- Server port: 8080 (api), 8081 (broker if standalone)
- Context path: `/api`

## Webhooks & Callbacks

**Incoming:**
- **Upstox OAuth Callback** - Authorization callback endpoint
  - URL: `${UPSTOX_REDIRECT_URI:http://localhost:8080/callback}`
  - Purpose: Receive authorization code from Upstox OAuth flow

**Outgoing:**
- **Telegram Notifications** - Bot messages to chat IDs
  - Configuration: `telegram.chat.ids`
  - Events: trades, signals, errors, positions
  - Rate limit: 60 seconds between messages
  - Max message length: 4096 characters

## Environment Variables

**Required Environment Variables:**
```
# Database
DB_HOST=localhost
DB_PORT=5432
DB_NAME=swingtrade_db
DB_USER=swingtrade_user
DB_PASSWORD=swingtrade_password

# Upstox API
UPSTOX_CLIENT_ID=
UPSTOX_CLIENT_SECRET=
UPSTOX_API_KEY=
UPSTOX_REDIRECT_URI=

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379

# LLM
LLM_BASE_URL=http://localhost:8000
LLM_MODEL_NAME=meta-llama/Llama-3.2-3B-Instruct
LLM_TEMPERATURE=0.0
LLM_TIMEOUT=30000

# Telegram
TELEGRAM_BOT_TOKEN=
TELEGRAM_BOT_USERNAME=
TELEGRAM_ADMIN_IDS=
TELEGRAM_CHAT_IDS=

# Trading Controls
TRADING_ENABLED=false
```

**Optional:**
- `CORS_ALLOWED_ORIGINS` - Cross-origin resource sharing
- `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` - Google OAuth (if used)
- `LOG_FILE` - Custom log file path

---

*Integration audit: 2026-03-07*
