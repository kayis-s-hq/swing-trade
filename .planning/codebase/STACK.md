# Technology Stack

**Analysis Date:** 2026-03-07

## Languages

**Primary:**
- Java 21 - Core application code across all modules (core, data, strategy, llm, broker, api)

**Secondary:**
- SQL - Database migrations (Flyway)
- XML - Maven POM configuration

## Runtime

**Environment:**
- Java 21 (LTS)
- Spring Boot 3.3.1

**Package Manager:**
- Maven 4.x
- Lockfile: pom.xml (no lockfile - deterministic versions in parent POM)

## Frameworks

**Core:**
- Spring Boot 3.3.1 - Web application framework across api, data, broker modules
- Spring Data JPA - Database access in data, broker, api modules
- Spring WebFlux - Reactive HTTP client in data and llm modules
- Spring Validation - Input validation across modules
- Spring Boot Actuator - Health monitoring and metrics in api module

**Technical Analysis:**
- TA4J 0.17.0 - Technical indicators (RSI, SMA, EMA, MACD) in strategy module
  - File: `strategy/pom.xml`

**LLM Integration:**
- LangChain4j 0.34.0 - LLM client integration in llm module
  - Files: `llm/pom.xml`, `llm/src/main/java/com/swingtrade/llm/impl/LangChain4jLlmClient.java`
  - langchain4j-open-ai - OpenAI-compatible API client
  - langchain4j-spring-boot-starter - Auto-configuration
  - langchain4j-web-search-engine-google-custom - Google Custom Search integration

**Data Access:**
- Hibernate 6.5.2.Final - JPA implementation
- Flyway 10.13.0 - Database schema migrations
- PostgreSQL Driver 42.7.3 - Database connectivity

**JSON Processing:**
- Jackson 2.17.1 - JSON serialization/deserialization
  - jackson-databind - Core JSON processing
  - jackson-datatype-jsr310 - Java 8 date/time support

**Observability:**
- Micrometer 1.x - Metrics collection
  - micrometer-core - Core metrics API
  - micrometer-registry-prometheus - Prometheus export
- Logback 1.5.6 - Logging framework
- SLF4J 2.0.13 - Logging facade

**Boilerplate Reduction:**
- Lombok 1.18.34 - Annotations for getters, setters, constructors

## Testing

**Test Frameworks:**
- JUnit Jupiter 5.11.0 - Test framework (all modules)
- Mockito 5.12.0 - Mocking framework
- AssertJ 3.26.3 - Fluent assertions
- WireMock 3.8.0 - HTTP stubbing (llm, api modules)
- Testcontainers - Integration test infrastructure (data module)
  - testcontainers
  - postgresql
  - junit-jupiter

**Code Coverage:**
- JaCoCo 0.8.12 - Code coverage reporting
  - Configured in parent `pom.xml` withjacoco-maven-plugin

## Build Tools

**Maven Plugins:**
- maven-compiler-plugin 3.13.0 - Java compilation
- maven-surefire-plugin 3.3.0 - Test execution
- maven-javadoc-plugin 3.8.0 - Javadoc generation
- maven-source-plugin 3.3.1 - Source JAR generation
- spring-boot-maven-plugin 3.3.1 - JAR packaging with layering
- jacoco-maven-plugin 0.8.12 - Coverage reporting

## Key Dependencies

**Critical:**
- spring-boot-starter-web - REST API endpoints (api, broker modules)
- spring-boot-starter-data-jpa - Database access (data, broker, api modules)
- spring-boot-starter-data-redis - Caching (data, broker, api modules)
- postgresql - PostgreSQL driver
- flyway-core - Database migrations
- ta4j-core - Technical analysis library
- langchain4j-spring-boot-starter - LLM integration
- telegram-spring-boot-starter 0.2.0 - Telegram notifications (broker module)
- commons-codec 1.16.0 - Encoding utilities (llm module)

**Infrastructure:**
- micrometer-registry-prometheus - Metrics export to Prometheus
- spring-boot-starter-actuator - Health checks and endpoints
- jackson-databind - JSON processing
- slf4j-api + logback-classic - Logging

## Configuration

**Environment:**
- Configuration files per module:
  - `api/src/main/resources/application.properties` - Main API configuration
  - `data/src/main/resources/application.yml` - Data module configuration
  - `llm/src/main/resources/application.properties` - LLM module configuration
  - `broker/src/main/resources/application.properties` - Broker module configuration
  - `strategy/src/main/resources/application.properties` - Strategy module configuration
  - `api/src/main/resources/application-local.properties` - Local environment overrides

**Key Configuration Properties Required:**
- `UPSTOX_CLIENT_ID` - Upstox API client ID
- `UPSTOX_CLIENT_SECRET` - Upstox API client secret
- `UPSTOX_API_KEY` - Upstox API key
- `LLM_BASE_URL` - LLM server URL (default: http://localhost:8000)
- `LLM_MODEL_NAME` - LLM model name (default: meta-llama/Llama-3.2-3B-Instruct)
- `TELEGRAM_BOT_TOKEN` - Telegram bot token
- `TELEGRAM_CHAT_IDS` - Telegram chat IDs for notifications
- `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` - Database connection
- `REDIS_HOST`, `REDIS_PORT` - Redis connection

**Build Configuration:**
- Parent POM manages dependency versions centrally (`pom.xml`)
- Multi-module Maven project with modules: core, data, strategy, llm, broker, api
- Spring Boot layering enabled for optimized container images

## Platform Requirements

**Development:**
- Java 21 JDK
- Maven 3.6+
- Docker + docker-compose (for PostgreSQL, Redis, vLLM)
- PostgreSQL with TimescaleDB extension
- Redis

**Production:**
- Deployment: Spring Boot fat JAR (executable)
- Infrastructure: PostgreSQL + TimescaleDB, Redis, external LLM (vLLM)
- Container-friendly with Spring Boot layering
- Health endpoints at `/api/actuator/health`
- Metrics endpoint at `/api/actuator/prometheus`

---

*Stack analysis: 2026-03-07*
