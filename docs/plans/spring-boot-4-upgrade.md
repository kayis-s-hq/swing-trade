# Spring Boot 4.1.1 + Spring AI 2.0.1 Upgrade Plan

## Resolved Version Matrix

All versions verified against Spring Boot 4.1.1 BOM (`spring-boot-dependencies-4.1.1.pom`) and Maven Central.

| Dependency | Current | New | Managed by Boot 4.1.1? | Source |
|-------------|---------|-----|----------------------|--------|
| Spring Boot | 3.5.9 | 4.1.1 | N/A (parent) | BOM |
| Spring AI | 1.1.0 | 2.0.1 | N/A (separate BOM) | spring-ai-bom-2.0.1 |
| Hibernate | 6.6.39.Final | 7.4.5.Final | Yes (`hibernate.version`) | BOM |
| Flyway | 12.9.0 | 12.4.0 | Yes (`flyway.version`) | BOM |
| Jackson | 2.17.x | 3.1.5 | Yes (`jackson-bom.version`) | BOM |
| HikariCP | 5.1.x | 7.0.2 | Yes (`hikaricp.version`) | BOM |
| langchain4j | 1.18.1 | 1.18.1 (unchanged) | No — verify compatibility | Manual |
| springdoc-openapi | current | 3.1.0 | No — latest compatible | Maven Central |
| Micrometer | current | 1.15.0 | No — latest compatible | Maven Central |

## What's Already Done

- Root cause of Jetty 11/12 conflict identified: `spring-ai-openai:1.1.0` transitively pulls `jetty-client:11.0.25`
- Spring AI 2.0.1 uses OkHttp via official `openai-java` SDK — no Jetty
- Context MCP docs pulled for Spring Boot 4.1.1, Spring AI 2.0.1, Hibernate 7.4.5, Jackson 3, Flyway 12.4.0
- All 24 Flyway migrations (V1–V24) reviewed
- All 8 backend modules examined for explicit Jetty dependencies (none found — all transitive)

## What Needs to Be Done

### Step 1: Update root build config

**File: `backend/build.gradle.kts`**

- Change `libs.spring.boot.bom` from `3.5.9` to `4.1.1`
- Change `libs.spring.boot.plugin` from `3.5.9` to `4.1.1`
- Change `libs.langchain4j.bom` from `1.18.1` to `1.18.1` (unchanged — NOT managed by Spring Boot 4.1.1 BOM; verify 1.18.1 compatibility with Spring Boot 4)
- Update `libs.hibernate.core` from `6.6.39.Final` to `7.4.5` (managed by Spring Boot 4.1.1 BOM as `hibernate.version`)
- Update `libs.flyway` from `12.9.0` to `12.4.0` (managed by Spring Boot 4.1.1 BOM as `flyway.version`)
- Update `libs.jackson.databind` from `2.17.x` to `3.1.5` (managed by Spring Boot 4.1.1 BOM as `jackson-bom.version`; Jackson 3 is the default, Jackson 2 deprecated)
- Update `libs.springdoc.openapi` from current to `3.1.0` (NOT managed by Spring Boot 4.1.1 BOM; 3.x is the latest release compatible with Spring Boot 4)
- Update `libs.hikaricp` from `5.1.x` to `7.0.2` (managed by Spring Boot 4.1.1 BOM as `hikaricp.version`)
- Update `libs.micrometer` to `1.15.0` (NOT managed by Spring Boot 4.1.1 BOM; latest is 1.15.0)

### Step 2: Update LLM module — replace spring-ai with Spring AI 2.0.1

**File: `backend/llm/build.gradle.kts`**

- Replace `spring-ai-starter-model-openai:1.1.0` with `spring-ai-starter-model-openai:2.0.1`
- Remove any explicit `spring-ai-openai` dependency (replaced by official `openai-java` SDK)
- Verify `langchain4j-http-client-jdk` is still used (should be via langchain4j BOM)

**File: `backend/llm/src/main/java/com/swingtrade/llm/config/LlmConfig.java`**

- Update property references: `llm.base-url` → `spring.ai.openai.base-url`
- Update property references: `llm.model.name` → `spring.ai.openai.chat.model`
- Update property references: `llm.temperature` → `spring.ai.openai.chat.temperature`
- Update property references: `llm.timeout.millis` → `spring.ai.openai.timeout`
- Update property references: `llm.max-tokens` → `spring.ai.openai.chat.max-tokens`
- Update OpenAI client construction — use `OpenAiChatModel` builder from Spring AI 2.x
- Add `extraBody` support for vLLM-specific params (`top_k`, `repetition_penalty`, `min_p`)

### Step 3: Update application properties

**File: `backend/api/src/main/resources/application-local.properties`**

- Remove `spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect` (auto-detected in Hibernate 6.6+)
- Update `spring.ai.openai.base-url=http://localhost:8080` → point to vLLM: `spring.ai.openai.base-url=http://localhost:8000`
- Add `spring.ai.openai.chat.model` property
- Add `spring.ai.openai.chat.temperature=0.0`
- Add `spring.ai.openai.timeout=30s`
- Add `spring.ai.openai.chat.max-tokens=4096`
- Keep `llm.*` properties as aliases for backward compat or remove if no longer used

**File: `backend/api/src/main/resources/application-stage.properties`**

- Same property updates as local
- Update `llm.base-url` → `spring.ai.openai.base-url`

**File: `backend/api/src/main/resources/application.properties`**

- Check for any `spring.ai.openai.*` or `llm.*` properties that need updating

### Step 4: Fix Flyway migration files

**File: `backend/data/src/main/resources/db/migration/V8__create_intelligence_tables.sql`**

- Change `DECIMAL(10,2)` to `NUMERIC(10,2)` (2 occurrences)

**File: `backend/data/src/main/resources/db/migration/V11__enhance_sentiment_accuracy.sql`**

- Change `DECIMAL(10,6)` to `NUMERIC(10,6)`
- Change `REAL` to `FLOAT` (for `llm_confidence` and `numeric_score` columns)

**File: `backend/data/src/main/resources/db/migration/V21__consolidate_positions.sql`**

- Fix UPDATE-before-INSERT bug: swap the order so the INSERT merge happens first, then the UPDATE NULLs

### Step 5: Fix entity annotations — @Temporal → @JdbcTypeCode

**Files to search**: All `*.java` files in `backend/core/src/main/java/` and `backend/*/src/main/java/`

- Search for `@Temporal` annotations
- Replace with `@JdbcTypeCode(SqlTypes.TIMESTAMP)` or `@JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)`
- Import `org.hibernate.type.SqlTypes` and `org.hibernate.annotations.JdbcTypeCode`

**File: `backend/core/src/main/java/com/swingtrade/domain/SentimentAccuracy.java`**

- Change `llm_confidence` and `numeric_score` from `Double` to `Float` (matches `REAL`/`FLOAT` SQL type)

### Step 6: Audit Jackson customizations

**Search**: All `*.java` files for `@JsonComponent`, `ObjectMapper`, `JsonSerializer`, `JsonDeserializer`

- Replace `@JsonComponent` with `@JacksonComponent`
- Replace `ObjectMapper` with `JsonMapper` (from `tools.jackson.databind`)
- Replace `@JsonMixin` with `@JacksonMixin`
- Update imports from `com.fasterxml.jackson.*` to `tools.jackson.*` where Spring Boot auto-configures

### Step 7: Update other dependency versions

**File: `backend/build.gradle.kts`**

- `ta4j` — check version compatible with Java 21 + Spring Boot 4
- `testcontainers` — update to version compatible with Spring Boot 4
- `archunit` — check latest compatible version
- `mockito` — check latest compatible version
- `assertj` — check latest compatible version
- `junit` — verify BOM managed version

### Step 8: Build and fix compilation errors

Run `./gradlew :api:compileJava` and fix all compilation errors. Expected issues:

- `ObjectMapper` → `JsonMapper` type errors
- `@JsonComponent` → `@JacksonComponent` annotation errors
- Import path changes for Jackson 3
- Any API changes in Spring AI 2.0.1 client construction

### Step 9: Run tests

```bash
./gradlew test
./gradlew check  # includes integration tests
./gradlew jacocoTestReport
```

Fix any test failures. Expected issues:

- Jackson serialization changes
- Hibernate 7 type mapping changes
- Spring AI 2.0.1 API changes in test code

### Step 10: Frontend E2E tests

```bash
cd dashboard
yarn playwright test
```

Verify API endpoints still work correctly after backend changes.

## Verification

1. `./gradlew build` — clean build, no errors
2. `./gradlew test` — all unit tests pass
3. `./gradlew check` — integration tests, checkstyle, PMD pass
4. `./gradlew jacocoTestCoverageVerification` — 80% coverage maintained
5. `./gradlew dependencyInsight --dependency jetty` — no Jetty on classpath
6. `./gradlew dependencyInsight --dependency okhttp` — OkHttp 4.x present
7. `./gradlew dependencyInsight --dependency jackson-databind` — Jackson 3.x present
8. Dev server start: `./gradlew :api:bootRun --args='--spring.profiles.active=local,fyers'` — starts cleanly
9. Playwright E2E: all 8 tests pass