---
phase: 02-strategy-engine
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - strategy/pom.xml
  - api/src/main/java/com/swingtrade/api/app/SwingTradeApiApplication.java
  - strategy/src/main/java/com/swingtrade/strategy/SignalEngine.java
autonomous: true
requirements:
  - REQ-009
user_setup: []
gap_closure: true
must_haves:
  truths:
    - "SignalEngine generates daily signals with Redis caching for repeated queries"
    - "getLatestSignal() returns cached results for previously queried symbols"
    - "getSignalsForSymbol() returns cached signal history"
    - "generateDailySignals() evicts cached entries after new signals saved"
  artifacts:
    - path: "strategy/pom.xml"
      provides: "Redis caching dependencies"
      contains: "spring-boot-starter-cache, spring-boot-starter-data-redis"
    - path: "api/src/main/java/com/swingtrade/api/app/SwingTradeApiApplication.java"
      provides: "Caching enabled"
      contains: "@EnableCaching"
    - path: "strategy/src/main/java/com/swingtrade/strategy/SignalEngine.java"
      provides: "Cached signal operations"
      contains: "@Cacheable, @CachePut, @CacheEvict"
  key_links:
    - from: "api/src/main/java/com/swingtrade/api/app/SwingTradeApiApplication.java"
      to: "org.springframework.cache.annotation.EnableCaching"
      via: "annotation import"
      pattern: "@EnableCaching"
    - from: "strategy/src/main/java/com/swingtrade/strategy/SignalEngine.java"
      to: "signalRepository"
      via: "cache eviction after save"
      pattern: "@CacheEvict.*signals"
---

<objective>
Add Redis caching to SignalEngine for improved performance on repeated signal queries.
</objective>

<execution_context>
@/Users/kayisrahman/.claude/get-shit-done/workflows/execute-plan.md
</execution_context>

<context>
@.planning/phases/02-strategy-engine/02-UAT.md

# Gap Source: 02-UAT.md Test 3
- SignalEngine has no Redis caching despite Redis being configured
- Missing @EnableCaching annotation on main application
- Missing cache dependencies in strategy module
- Missing @Cacheable annotations on SignalEngine methods

# User Decisions from UAT
- Redis caching is NOT implemented - confirmed missing @Cacheable annotations
- Scheduled generation (17:00 IST, MON-FRI) and manual trigger are working
- Cache should cover: generateSignalsForSymbol(), getLatestSignal(), getSignalsForSymbol()
</context>

<tasks>

<task type="auto">
  <name>Task 1: Add Redis caching dependencies to strategy module</name>
  <files>strategy/pom.xml</files>
  <action>
    Add Spring Boot cache and Redis starter dependencies to strategy/pom.xml:
    - spring-boot-starter-cache (provides Spring Cache abstraction)
    - spring-boot-starter-data-redis (provides Redis cache backend)

    Insert these dependencies after the existing spring-boot-starter dependency in the dependencies section:
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-cache</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-redis</artifactId>
    </dependency>

    Use Spring Boot dependency management (no version needed - parent POM controls it).
  </action>
  <verify>
    <automated>cd strategy && mvn dependency:tree -Dincludes=org.springframework.boot:spring-boot-starter-cache,org.springframework.boot:spring-boot-starter-data-redis | grep -E "spring-boot-starter-(cache|data-redis)"</automated>
  </verify>
  <done>
    - strategy/pom.xml contains spring-boot-starter-cache dependency
    - strategy/pom.xml contains spring-boot-starter-data-redis dependency
    - mvn dependency:tree shows both dependencies resolved
  </done>
</task>

<task type="auto">
  <name>Task 2: Enable caching on main application</name>
  <files>api/src/main/java/com/swingtrade/api/app/SwingTradeApiApplication.java</files>
  <action>
    Add @EnableCaching annotation to SwingTradeApiApplication:

    1. Import the annotation: import org.springframework.cache.annotation.EnableCaching;

    2. Add @EnableCaching to the @SpringBootApplication class definition:
       @SpringBootApplication
       @EnableCaching
       public class SwingTradeApiApplication { ... }

    This enables Spring's cache abstraction throughout the application.
  </action>
  <verify>
    <automated>grep -n "@EnableCaching" api/src/main/java/com/swingtrade/api/app/SwingTradeApiApplication.java</automated>
    <manual>Verify @EnableCaching import exists and annotation is present on class</manual>
  </verify>
  <done>
    - SwingTradeApiApplication.java imports org.springframework.cache.annotation.EnableCaching
    - @EnableCaching annotation is present on the @SpringBootApplication class
  </done>
</task>

<task type="auto">
  <name>Task 3: Add caching annotations to SignalEngine</name>
  <files>strategy/src/main/java/com/swingtrade/strategy/SignalEngine.java</files>
  <action>
    Add caching annotations to SignalEngine methods:

    1. Add imports:
       - import org.springframework.cache.annotation.Cacheable;
       - import org.springframework.cache.annotation.CachePut;
       - import org.springframework.cache.annotation.CacheEvict;

    2. Add @Cacheable to getLatestSignal() method:
       @Cacheable(value = "latestSignal", key = "#symbol")
       public java.util.Optional<SignalEntity> getLatestSignal(String symbol)

       Cache entry: latestSignal:{symbol}

    3. Add @Cacheable to getSignalsForSymbol() method:
       @Cacheable(value = "signals", key = "#symbol")
       public List<SignalEntity> getSignalsForSymbol(String symbol)

       Cache entry: signals:{symbol}

    4. Add @CacheEvict to generateSignalsForSymbol() method:
       @CacheEvict(value = {"latestSignal", "signals"}, key = "#symbol")
       public void generateSignalsForSymbol(String symbol)

       Evicts both latestSignal and signals cache entries for the symbol after saving new signal.

    5. Add @Cacheable with same key to generateSignalForSymbolNow() for manual trigger consistency.

    Use Spring Cache annotations - Redis will handle storage automatically via spring-boot-starter-data-redis.
  </action>
  <verify>
    <automated>grep -n "@Cacheable\|@CacheEvict" strategy/src/main/java/com/swingtrade/strategy/SignalEngine.java</automated>
    <manual>Verify @Cacheable on getLatestSignal, getSignalsForSymbol, generateSignalForSymbolNow; @CacheEvict on generateSignalsForSymbol</manual>
  </verify>
  <done>
    - SignalEngine.java imports @Cacheable, @CachePut, @CacheEvict
    - getLatestSignal() has @Cacheable(value="latestSignal", key="#symbol")
    - getSignalsForSymbol() has @Cacheable(value="signals", key="#symbol")
    - generateSignalsForSymbol() has @CacheEvict(value={"latestSignal", "signals"}, key="#symbol")
    - generateSignalForSymbolNow() has @Cacheable(value="latestSignal", key="#symbol")
  </done>
</task>

</tasks>

<verification>
Build and verify caching integration:
<automated>mvn clean install -DskipTests</automated>

Verify cache annotations compile without errors:
<automated>grep -A1 "@Cacheable\|@CacheEvict" strategy/src/main/java/com/swingtrade/strategy/SignalEngine.java</automated>

Verify dependencies present:
<automated>cd strategy && mvn dependency:tree | grep -E "(spring-boot-starter-(cache|data-redis)|spring-cache)"</automated>
</verification>

<success_criteria>
- [ ] strategy/pom.xml contains spring-boot-starter-cache dependency
- [ ] strategy/pom.xml contains spring-boot-starter-data-redis dependency
- [ ] SwingTradeApiApplication.java has @EnableCaching annotation
- [ ] SignalEngine.getLatestSignal() is @Cacheable on "latestSignal" cache
- [ ] SignalEngine.getSignalsForSymbol() is @Cacheable on "signals" cache
- [ ] SignalEngine.generateSignalsForSymbol() evicts cache entries via @CacheEvict
- [ ] mvn clean install succeeds with no cache-related errors
- [ ] Redis cache configured via application.properties works with Spring Cache abstraction
</success_criteria>

<output>
After completion, mark gap closure task as done in 02-UAT.md and verify with:
1. Run signal generation manually
2. Call getLatestSignal() twice for same symbol - second call should hit cache
3. Check Redis keys: keys latestSignal:* signals:*
</output>
