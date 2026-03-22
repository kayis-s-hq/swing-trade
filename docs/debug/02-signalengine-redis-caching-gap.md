# SignalEngine Redis Caching Gap Debug Notes

**Phase:** 02-Strategy-Engine
**Date:** 2026-03-22
**Issue:** Redis caching NOT implemented in SignalEngine despite configuration present

---

## Root Cause Analysis

**Root Cause:** Redis caching was never implemented in SignalEngine despite:
1. Redis configuration present in `application.properties` (lines 58-73)
2. `spring-boot-starter-data-redis` dependency present in API module's pom.xml (lines 97-100)
3. No `@EnableCaching` annotation on the main application class
4. No `@Cacheable` annotations on SignalEngine methods
5. No CacheManager bean configured in strategy module

The UAT file (Phase 02) expects SignalEngine to use Redis caching for signal generation results, but only in-memory cache exists in `DefaultStrategyContext` (lines 36-54).

---

## Files Requiring Changes

### 1. api/src/main/java/com/swingtrade/api/app/SwingTradeApiApplication.java
**Issue:** Missing `@EnableCaching` annotation
**Fix:** Add `@EnableCaching` to enable Spring Cache abstraction

### 2. strategy/pom.xml
**Issue:** Missing Spring Cache and Redis dependencies
**Fix:** Add following dependencies:
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-cache</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

### 3. strategy/src/main/java/com/swingtrade/strategy/SignalEngine.java
**Issue:** No caching annotations on cacheable methods
**Methods to cache:**
- `generateSignalsForSymbol(String symbol)` - cache signal generation per symbol/date
- `getLatestSignal(String symbol)` - cache latest signal lookup
- `getSignalsForSymbol(String symbol)` - cache signals list lookup

**Required annotations:**
```java
@Cacheable(value = "signals", key = "#symbol", condition = "#symbol != null")
@CachePut(value = "signals", key = "#symbol")
@CacheEvict(value = "signals", key = "#symbol")
```

### 4. strategy/src/main/java/com/swingtrade/strategy/impl/DefaultStrategyContext.java
**Issue:** Uses in-memory HashMap cache instead of Redis
**Current approach (lines 36-53):**
```java
private final Map<String, TradingStrategy> strategyCache = new HashMap<>();
```

**Recommended approach:** Replace with Redis-backed cache or keep in-memory for strategy objects (they're lightweight) but use Redis for signal results

---

## Missing Items

1. **@EnableCaching annotation** on main application class
2. **CacheManager bean** configuration (auto-configured with Redis starter, but may need explicit configuration)
3. **@Cacheable annotations** on SignalEngine methods
4. **Strategy module dependencies** for cache support
5. **Redis cache configuration** (TTL, eviction policy) if not using defaults

---

## Redis Configuration Status

**Present in application.properties (api module):**
- Host/Port configured (lines 58-65)
- Cache names defined: `stocks,ohlcv,signals,sentiment` (line 69)
- TTL set to 1 hour (line 70)
- Key prefix enabled (lines 72-73)

**Issue:** Redis is configured but never activated because:
- No `@EnableCaching`
- No cacheable methods
- Strategy module lacks cache dependencies

---

## Comparison with LLM Module

The LLM module has a similar but different pattern:
- `SentimentCacheService.java` uses custom in-memory cache with TTL
- This is fine for LLM sentiment (expensive operation), but SignalEngine should use Redis for consistency

---

## Implementation Priority

1. **Critical:** Add `@EnableCaching` to SwingTradeApiApplication
2. **Critical:** Add cache dependencies to strategy/pom.xml
3. **High:** Add `@Cacheable` to `generateSignalsForSymbol()`
4. **Medium:** Add `@Cacheable` to getter methods
5. **Low:** Consider removing/inactivating DefaultStrategyContext's in-memory cache

---

## References

- UAT: `.planning/phases/02-strategy-engine/02-UAT.md` (Test 3, lines 31-36, 66-74)
- SignalEngine: `strategy/src/main/java/com/swingtrade/strategy/SignalEngine.java`
- Application: `api/src/main/java/com/swingtrade/api/app/SwingTradeApiApplication.java`
- Application Properties: `api/src/main/resources/application.properties` (lines 56-73)
- Strategy POM: `strategy/pom.xml`

---
