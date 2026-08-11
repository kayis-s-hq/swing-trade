# Native Image Upgrade: Spring Boot 3.3→3.5 + LangChain4j 0.34→1.x

## Phase Status

| Phase | Status | Result | Timestamp |
|-------|--------|--------|-----------|
| 1: Spring Boot 3.3→3.5 + deprecated property fixes | [ ] PENDING | — | — |
| 2: LangChain4j 0.34→1.x API migration | [ ] PENDING | — | — |
| 3: GraalVM native plugin config | [ ] PENDING | — | — |
| 4: Verify all modules build + tests pass | [ ] PENDING | — | — |

## 1. Feature Map

| Feature | Tested? | Test Type |
|---------|---------|-----------|
| Spring Boot 3.5.9 BOM across all modules | No | Build verification |
| Deprecated `spring.jpa.database-platform` removed | No | Config verification |
| Deprecated `spring.datasource.initialization-mode` removed | No | Config verification |
| `spring.data.redis.*` standardized in broker module | No | Config verification |
| LangChain4j 1.x `ChatModel` API used | No | Source verification |
| OllamaChatModel builder compatible with 1.x | No | Existing test |
| WireMock mock tests still work | Yes (existing) | Integration test |
| All existing tests pass | Yes (existing) | Full test suite |

## 2. Phase Breakdown

### Phase 1: Spring Boot 3.3→3.5 + Deprecated Property Fixes

**What to test (verification):**
- `./gradlew :api:compileJava` — should succeed with 3.5.9
- `./gradlew :llm:test` — should pass (no regression)
- No `spring.jpa.database-platform` properties remain
- No `spring.datasource.initialization-mode` properties remain

**Files to modify:**

#### 1a. Root `build.gradle.kts`
- Line 14: `mavenBom("org.springframework.boot:spring-boot-dependencies:3.3.1")` → `mavenBom("org.springframework.boot:spring-boot-dependencies:3.5.9")`

#### 1b. All 6 sub-module `build.gradle.kts` files — BOM version bump
Same change in each file's `dependencyManagement` block:
- `llm/build.gradle.kts` line 8
- `broker/build.gradle.kts` line 8
- `core/build.gradle.kts` line 8
- `data/build.gradle.kts` line 8
- `strategy/build.gradle.kts` line 8
- `api/build.gradle.kts` line 11

#### 1c. API module `build.gradle.kts` — Spring Boot plugin version
- Line 5: `id("org.springframework.boot") version "3.3.1"` → `id("org.springframework.boot") version "3.5.9"`

#### 1d. Fix deprecated `spring.jpa.database-platform` (removed in 3.5)
**`broker/src/main/resources/application.properties` line 32:**
- REMOVE: `spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect`
- KEEP: `spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect` (already on same line)

**`api/src/test/resources/application-test.properties` line 10:**
- REMOVE: `spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect`

**`data/src/main/resources/application.yml` line 34:**
- Change `database-platform: org.hibernate.dialect.H2Dialect` → `dialect: org.hibernate.dialect.H2Dialect` under `spring.jpa.properties`

**`data/src/test/resources/application-test.yml` line 11:**
- Change `database-platform: org.hibernate.dialect.H2Dialect` → `dialect: org.hibernate.dialect.H2Dialect` under `spring.jpa.properties`

#### 1e. Fix deprecated `spring.datasource.initialization-mode` (removed in 3.5)
**`api/src/main/resources/application-local.properties` line 208:**
- Change `spring.datasource.initialization-mode=never` → `spring.sql.init.mode=never`

#### 1f. Standardize Redis prefix in broker module
**`broker/src/main/resources/application.properties` lines 45-52:**
- Replace all `spring.redis.*` → `spring.data.redis.*`

#### 1g. Add properties migrator (optional, for safety during Phase 1)
**`api/build.gradle.kts`** — add as `runtimeOnly` dependency:
```kotlin
runtimeOnly("org.springframework.boot:spring-boot-properties-migrator")
```
Remove after Phase 1 verified.

### Phase 2: LangChain4j 0.34→1.x API Migration

**What to test:**
- `./gradlew :llm:compileJava` — should succeed with new imports
- `./gradlew :llm:test` — should pass (WireMock tests exercise the client)

**Files to modify:**

#### 2a. Root `build.gradle.kts`
- Line 15: `mavenBom("dev.langchain4j:langchain4j-bom:0.34.0")` → `mavenBom("dev.langchain4j:langchain4j-bom:1.12.1")`

#### 2b. All 6 sub-module `build.gradle.kts` files — BOM version bump
Same change in each file's `dependencyManagement` block:
- `llm/build.gradle.kts` line 9
- `broker/build.gradle.kts` line 9
- `core/build.gradle.kts` line 9
- `data/build.gradle.kts` line 9
- `strategy/build.gradle.kts` line 9
- `api/build.gradle.kts` line 12

#### 2c. LLM module `build.gradle.kts` — update dependencies
- Line 21: `implementation("dev.langchain4j:langchain4j")` — version from BOM, no change needed
- Line 22: `implementation("dev.langchain4j:langchain4j-open-ai")` — stays (still used for native-image hints)
- Line 23: `implementation("dev.langchain4j:langchain4j-spring-boot-starter")` — stays (still valid)
- Line 24: `implementation("dev.langchain4j:langchain4j-web-search-engine-google-custom")` — stays
- Line 25: `implementation("dev.langchain4j:langchain4j-ollama")` — stays

#### 2d. LangChain4jLlmClient.java — API rename (THE ONLY source change)
**`llm/src/main/java/com/swingtrade/llm/impl/LangChain4jLlmClient.java`:**

Line 8:
- OLD: `import dev.langchain4j.model.chat.ChatLanguageModel;`
- NEW: `import dev.langchain4j.model.chat.ChatModel;`

Line 24:
- OLD: `private final ChatLanguageModel chatLanguageModel;`
- NEW: `private final ChatModel chatModel;`

Line 61:
- OLD: `String response = chatLanguageModel.generate(prompt);`
- NEW: `String response = chatModel.generate(prompt);`

**Note:** `OllamaChatModel` builder API is stable — `.baseUrl()`, `.modelName()`, `.temperature()` all unchanged in 1.x. No builder changes needed.

#### 2e. LlmModuleTest.java — fix test class references
**`llm/src/test/java/com/swingtrade/llm/LlmModuleTest.java`:**
- Line 5: Change `import com.swingtrade.llm.SentimentOutput;` → `import com.swingtrade.llm.SentimentOutput;` (no change — SentimentOutput is in service package)
- The test class uses `VLLMClient` mock, NOT LangChain4j directly — no import changes needed for LangChain4j

**Note:** `LlmModuleTest.java` has duplicate test methods (lines 30-41 and 63-73 are identical). Leave as-is for now — not in scope.

### Phase 3: GraalVM Native Plugin Config

**What to test:**
- `./gradlew :api:compileNative` — should succeed (requires GraalVM on PATH)

**Files to modify:**

#### 3a. API module `build.gradle.kts` — add native plugin
After the existing plugins block:
```kotlin
plugins {
    id("org.springframework.boot") version "3.5.9"
    id("io.spring.dependency-management")
    id("org.graalvm.buildtools.native") version "0.10.6"
}
```

Add `graalvmNative` block:
```kotlin
graalvmNative {
    binaries {
        all {
            // For Pi-node ARM deployment
            if (System.getProperty("os.arch") == "aarch64") {
                buildArgs.add("-march=compatibility")
            }
            verbose = true
            jvmArgs = ["-Xmx2g"]
        }
    }
}
```

#### 3b. LLM module `build.gradle.kts` — no plugin needed
Native image only targets the `api` module (the executable jar). Other modules are libraries.

### Phase 4: Verify All Modules Build + Tests Pass

**Verification commands (run in order):**
```bash
cd backend
./gradlew build                    # all modules, all tests
./gradlew :llm:test --tests "*WireMock*"  # verify mock tests work
./gradlew :api:compileNative       # verify native plugin (if GraalVM installed)
```

## 3. Files Summary

| Action | File | Type |
|--------|------|------|
| Modify | `build.gradle.kts` (root) — 2 version bumps | Build |
| Modify | `api/build.gradle.kts` — plugin version + BOM + native plugin | Build |
| Modify | `llm/build.gradle.kts` — BOM version | Build |
| Modify | `broker/build.gradle.kts` — BOM version | Build |
| Modify | `core/build.gradle.kts` — BOM version | Build |
| Modify | `data/build.gradle.kts` — BOM version | Build |
| Modify | `strategy/build.gradle.kts` — BOM version | Build |
| Modify | `api/src/main/resources/application-local.properties` — 1 property | Config |
| Modify | `broker/src/main/resources/application.properties` — 8 redis prefix + 1 deprecated | Config |
| Modify | `api/src/test/resources/application-test.properties` — 1 deprecated | Config |
| Modify | `data/src/main/resources/application.yml` — 1 deprecated | Config |
| Modify | `data/src/test/resources/application-test.yml` — 1 deprecated | Config |
| Modify | `llm/src/main/java/.../LangChain4jLlmClient.java` — 3 lines | Source |
| Create | `api/src/main/resources/META-INF/native-image/` (auto-generated by Spring AOT) | Auto |

## 4. Verification

```bash
# Phase 1: Spring Boot bump + property fixes
./gradlew :api:compileJava          # fails with old BOM, succeeds with 3.5.9
./gradlew :llm:test                 # should pass (no regression)
grep -r "database-platform" backend/*/src/*/resources/  # should find nothing

# Phase 2: LangChain4j bump
./gradlew :llm:compileJava          # should succeed with ChatModel import
./gradlew :llm:test                 # WireMock tests should pass
grep -r "ChatLanguageModel" backend/llm/src/main/  # should find nothing

# Phase 3: Native plugin
./gradlew :api:compileNative        # requires GraalVM on PATH

# Phase 4: Full verification
./gradlew check                     # all modules, all tests, all checks
```

## 5. Risk Assessment

| Risk | Likelihood | Mitigation |
|------|-----------|------------|
| LangChain4j 1.x OllamaChatModel builder API changed | Low | Docs confirm API stable — baseUrl, modelName, temperature unchanged |
| Spring Boot 3.5 removes a property we depend on | Low | Properties migrator catches this at startup |
| WireMock test responses need updating | Low | WireMock mocks HTTP, not LangChain4j internals |
| Native image fails on Pi-node ARM | Medium | `-march=compatibility` flag helps; tracing agent for reflection hints |
| Flyway 10.13.0 incompatible with Spring Boot 3.5 | Very Low | Flyway version managed by Spring Boot BOM, compatible |

## 6. Rollback Plan

Every change is a version bump or property rename — fully reversible:
- Revert git commit → all old versions restored
- No schema changes, no data migration
- No new dependencies added
