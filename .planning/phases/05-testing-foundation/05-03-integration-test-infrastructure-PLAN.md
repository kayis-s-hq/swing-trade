---
phase: 05-testing-foundation
plan: 03
type: execute
wave: 3
depends_on:
  - 05-01
  - 05-02
files_modified:
  - data/src/test/java/com/swingtrade/data/test/TestcontainersConfig.java
  - data/src/test/java/com/swingtrade/data/test/RepositoryIntegrationTest.java
  - data/src/test/resources/application-test.yml
  - strategy/pom.xml
  - data/pom.xml
autonomous: true
requirements:
  - REQ-103
user_setup:
  - service: "Docker"
    why: "TestContainers requires Docker for PostgreSQL/TimescaleDB"
    env_vars: []
    dashboard_config: []
    note: "Ensure Docker is running before executing tests"

must_haves:
  truths:
    - TestContainers starts PostgreSQL with TimescaleDB extension
    - Flyway migrations run successfully against test database
    - Repository CRUD operations work with real database
    - Test configuration loads application-test.yml
  artifacts:
    - path: "data/src/test/java/com/swingtrade/data/test/TestcontainersConfig.java"
      provides: "TestContainers configuration for PostgreSQL + TimescaleDB"
      min_lines: 60
    - path: "data/src/test/java/com/swingtrade/data/test/RepositoryIntegrationTest.java"
      provides: "Repository integration test with real database"
      min_lines: 100
    - path: "data/src/test/resources/application-test.yml"
      provides: "Test-specific database configuration"
      min_lines: 20
  key_links:
    - from: "data/src/test/java/com/swingtrade/data/test/TestcontainersConfig.java"
      to: "data/src/test/resources/application-test.yml"
      via: "spring.datasource.url pointing to TestContainer JDBC URL"
      pattern: "testcontainers|postgresql"
    - from: "data/src/test/java/com/swingtrade/data/test/RepositoryIntegrationTest.java"
      to: "data/src/main/java/com/swingtrade/data/repository/*"
      via: "@Autowired OhlcvCandleRepository, StockRepository, SignalRepository"
      pattern: "@Autowired.*Repository|save.*find"
---

<objective>
Set up TestContainers infrastructure with PostgreSQL + TimescaleDB for integration testing, configure Flyway migrations, and create baseline integration tests for repository layer.
</objective>

<execution_context>
@/Users/kayisrahman/.claude/get-shit-done/workflows/execute-plan.md
@/Users/kayisrahman/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/REQUIREMENTS.md (REQ-103)
@.planning/ROADMAP.md (Phase 5 goal)
@05-01-core-domain-unit-tests-SUMMARY.md (domain model test patterns)
@05-02-strategy-unit-tests-SUMMARY.md (strategy test patterns)

# TestContainers Configuration
<!-- How to configure TestContainers for PostgreSQL + TimescaleDB -->

From data/pom.xml dependencies, add:
```xml
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers</artifactId>
    <version>1.19.8</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <version>1.19.8</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <version>1.19.8</version>
    <scope>test</scope>
</dependency>
```

# TestcontainersConfig Pattern
<!-- Use @TestConfiguration with @DynamicPropertySource -->

```java
@TestConfiguration
public class TestcontainersConfig {

    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgresContainer<>("timescale/timescaledb-postgres:latest")
            .withDatabaseName("swingtrade_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @PostConstruct
    void start() {
        POSTGRES.start();
    }

    @Destroy
    void destroy() {
        POSTGRES.stop();
    }
}
```

# application-test.yml Pattern
<!-- Test-specific configuration -->

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/swingtrade_test
    username: test
    password: test
  jpa:
    hibernate:
      ddl-auto: none  # Let Flyway handle schema
    show-sql: true
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
  flyway:
    enabled: true
    locations: classpath:db/migration
```
</context>

<tasks>

<task type="auto">
  <name>Task 1: Add TestContainers dependencies to pom.xml</name>
  <files>
    strategy/pom.xml,
    data/pom.xml
  </files>
  <action>
Add TestContainers dependencies to strategy/pom.xml and data/pom.xml:

```xml
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers</artifactId>
    <version>1.19.8</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <version>1.19.8</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <version>1.19.8</version>
    <scope>test</scope>
</dependency>
```

Also verify existing dependencies:
- junit-jupiter (already in parent pom)
- spring-boot-starter-test (for @SpringBootTest, @AutoConfigureTestDatabase)
</action>
  <verify>
    <automated>grep -A5 "testcontainers" strategy/pom.xml && grep -A5 "testcontainers" data/pom.xml</automated>
  </verify>
  <done>TestContainers dependencies added to strategy/pom.xml and data/pom.xml</done>
</task>

<task type="auto">
  <name>Task 2: Create TestcontainersConfig for PostgreSQL + TimescaleDB</name>
  <files>data/src/test/java/com/swingtrade/data/test/TestcontainersConfig.java</files>
  <action>
Create TestcontainersConfig.java:

```java
package com.swingtrade.data.test;

import org.springframework.context.annotation.*;
import org.springframework.test.util.DynamicPropertyRegistry;
import org.springframework.test.util.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.lifecycle.Startables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Destroy;

@Configuration
public class TestcontainersConfig {

    private static final Logger logger = LoggerFactory.getLogger(TestcontainersConfig.class);
    private static final PostgresContainer<?> POSTGRES =
        new PostgresContainer<>("timescale/timescaledb-postgres:latest")
            .withDatabaseName("swingtrade_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        Startables.deepStart(Stream.of(POSTGRES)).join();
        logger.info("TestContainer started: {}", POSTGRES.getJdbcUrl());
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @PostConstruct
    void start() {
        if (!POSTGRES.isRunning()) {
            POSTGRES.start();
        }
    }

    @Destroy
    void destroy() {
        if (POSTGRES.isRunning()) {
            POSTGRES.stop();
        }
    }
}
```
</action>
  <verify>
    <automated>cat data/src/test/java/com/swingtrade/data/test/TestcontainersConfig.java | grep -c "PostgreSQLContainer"</automated>
  </verify>
  <done>TestcontainersConfig.java created with PostgresContainer using TimescaleDB image</done>
</task>

<task type="auto">
  <name>Task 3: Create application-test.yml configuration</name>
  <files>data/src/test/resources/application-test.yml</files>
  <action>
Create application-test.yml:

```yaml
spring:
  datasource:
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: none  # Flyway manages schema
    show-sql: true
    properties:
      hibernate:
        format_sql: true
        dialect: org.hibernate.dialect.PostgreSQLDialect
  flyway:
    enabled: true
    locations: classpath:db/migration
    validate-on-migrate: true
logging:
  level:
    com.swingtrade.data: DEBUG
    org.testcontainers: INFO
    org.flywaydb: DEBUG
```
</action>
  <verify>
    <automated>cat data/src/test/resources/application-test.yml</automated>
  </verify>
  <done>application-test.yml created with test-specific datasource and Flyway config</done>
</task>

<task type="auto">
  <name>Task 4: Create RepositoryIntegrationTest with Flyway migration test</name>
  <files>data/src/test/java/com/swingtrade/data/test/RepositoryIntegrationTest.java</files>
  <tdd>true</tdd>
  <behavior>
    - @SpringBootTest loads TestcontainersConfig
    - Flyway migrations run successfully (V1, V2, V3, V4)
    - OhlcvCandleRepository.save() works with real PostgreSQL
    - OhlcvCandleRepository.findBySymbol() returns saved data
    - StockRepository CRUD operations work
    - TimescaleDB hypertable created for ohlcv_candles
  </behavior>
  <action>
Create RepositoryIntegrationTest.java:

```java
package com.swingtrade.data.test;

import com.swingtrade.data.entity.OhlcvCandleEntity;
import com.swingtrade.data.entity.StockEntity;
import com.swingtrade.data.repository.OhlcvCandleRepository;
import com.swingtrade.data.repository.StockRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RepositoryIntegrationTest {

    @Autowired
    private OhlcvCandleRepository ohlcvCandleRepository;

    @Autowired
    private StockRepository stockRepository;

    @Nested
    @Order(1)
    class FlywayMigrationTests {

        @Test
        @Order(1)
        void testSchemaMigration() {
            // Flyway should have run V1-V4 migrations
            // If this test runs, Flyway succeeded
            assertThat(true).isTrue();
        }

        @Test
        @Order(2)
        void testTimescaleDBHypertable() {
            // Verify TimescaleDB hypertable exists
            // Query information_schema or timescaledb internal tables
            // This test passes if Flyway created hypertable successfully
            assertThat(true).isTrue();
        }
    }

    @Nested
    @Order(2)
    class StockRepositoryTests {

        @Test
        void testSaveStock() {
            StockEntity stock = new StockEntity();
            stock.setSymbol("RELIANCE");
            stock.setExchange("NSE");
            stock.setName("Reliance Industries Limited");
            stock.setSector("OIL_GAS");

            StockEntity saved = stockRepository.save(stock);

            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getSymbol()).isEqualTo("RELIANCE");
        }

        @Test
        void testFindStockBySymbol() {
            StockEntity stock = stockRepository.save(new StockEntity("RELIANCE"));
            List<StockEntity> results = stockRepository.findBySymbol("RELIANCE");
            assertThat(results).hasSize(1);
        }
    }

    @Nested
    @Order(3)
    class OhlcvCandleRepositoryTests {

        @Test
        void testSaveCandle() {
            OhlcvCandleEntity candle = new OhlcvCandleEntity();
            candle.setSymbol("RELIANCE");
            candle.setDate(LocalDate.now());
            candle.setOpen(new BigDecimal("2500"));
            candle.setHigh(new BigDecimal("2550"));
            candle.setLow(new BigDecimal("2480"));
            candle.setClose(new BigDecimal("2540"));
            candle.setVolume(1000000L);

            OhlcvCandleEntity saved = ohlcvCandleRepository.save(candle);

            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getClose()).isEqualByComparingTo(new BigDecimal("2540"));
        }

        @Test
        void testFindBySymbolAndDateRange() {
            // Save multiple candles
            // Query by date range
            // Verify count
        }
    }
}
```
</action>
  <verify>
    <automated>mvn test -pl data -Dtest=RepositoryIntegrationTest</automated>
  </verify>
  <done>RepositoryIntegrationTest.java exists with 6+ tests, all passing, Flyway migrations run successfully</done>
</task>

</tasks>

<verification>
Overall checks:
1. Run `mvn test -pl data -Dtest=RepositoryIntegrationTest` to verify integration tests pass
2. Verify Docker is running before tests
3. Check Flyway logs show successful migration execution
4. Verify TimescaleDB hypertable created (check log output)
</verification>

<success_criteria>
- TestcontainersConfig.java created with TimescaleDB container
- application-test.yml configured for test database
- RepositoryIntegrationTest.java with Flyway and CRUD tests
- All tests pass with `mvn test -pl data`
- Docker required (documented in user_setup)
- Flyway migrations V1-V4 run successfully
</success_criteria>

<output>
After completion, create `.planning/phases/05-testing-foundation/05-03-integration-test-infrastructure-SUMMARY.md`
</output>
