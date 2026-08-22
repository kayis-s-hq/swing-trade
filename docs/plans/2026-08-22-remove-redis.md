# Plan: Remove Redis from SwingTrade Stack

## What's already done

- `application-local.properties` already disables Redis (`spring.autoconfigure.exclude=...RedisAutoConfiguration`) and sets `spring.cache.type=none`
- `docker-compose.infra-dev.yml` does NOT include Redis (only PostgreSQL)
- No Java code in `broker/`, `strategy/`, or `data/` modules actually uses Redis — only `SignalEngine.java` in `api/` uses `@Cacheable`/`@CacheEvict`

## What needs to be done

### Step 1: Remove build dependencies (4 files)

Remove `spring-boot-starter-data-redis` from:

1. **`backend/api/build.gradle.kts`** (line 43) — remove `implementation("org.springframework.boot:spring-boot-starter-data-redis")`
2. **`backend/broker/build.gradle.kts`** (line 27) — remove `implementation("org.springframework.boot:spring-boot-starter-data-redis")`
3. **`backend/strategy/build.gradle.kts`** (lines 18-19) — remove `spring-boot-starter-cache` AND `spring-boot-starter-data-redis`
4. **`backend/data/build.gradle.kts`** (line 29) — remove `implementation("org.springframework.boot:spring-boot-starter-data-redis")`

### Step 2: Remove cache annotations from Java code (2 files)

5. **`backend/api/src/main/java/.../SwingTradeApiApplication.java`** — remove line 6 (`import org.springframework.cache.annotation.EnableCaching;`) and line 33 (`@EnableCaching`)
6. **`backend/api/src/main/java/.../SignalEngine.java`** — remove:
   - Line 23: `import org.springframework.cache.annotation.CacheEvict;`
   - Line 24: `import org.springframework.cache.annotation.Cacheable;`
   - Line 70: `@CacheEvict(value = {"latestSignal", "signals"}, key = "#symbol")`
   - Line 81: `@CacheEvict(value = {"latestSignal", "signals"}, key = "#symbol")`
   - Line 93: `@Cacheable(value = "latestSignal", key = "#symbol")`
   - Line 104: `@Cacheable(value = "signals", key = "#symbol")`
   - Update Javadoc (lines 34-37) to remove cache references

### Step 3: Remove Redis config from properties (4 files)

7. **`backend/api/src/main/resources/application.properties`** — remove:
   - Lines 47-65: entire Redis + Cache configuration section
   - Line 242: `logging.level.io.lettuce=INFO`
   - Line 256: `management.health.redis.enabled=true`

8. **`backend/api/src/main/resources/application-local.properties`** — remove:
   - Lines 41-46: Redis disabled section (now irrelevant)

9. **`backend/api/src/main/resources/application-stage.properties`** — remove:
   - Lines 26-35: Redis configuration section
   - Line 106: `management.health.redis.enabled=true`

10. **`backend/broker/src/main/resources/application.properties`** — remove:
    - Lines 41-59: Redis + Cache configuration section
    - Line 145: `logging.level.io.lettuce=INFO`

### Step 4: Remove test files and config (2 files)

11. **`backend/api/src/test/java/.../RedisTestContainer.java`** — **delete the entire file**

12. **`backend/api/src/test/resources/application-test.properties`** — remove:
    - Lines 19-22: Redis test config

### Step 5: Update E2E test (1 file)

13. **`dashboard/tests/e2e/views/stage-sanity.spec.ts`** — remove line 59:
    ```ts
    expect(body.components.redis.status).toBe('UP')
    ```

### Step 6: Update CI/CD (1 file)

14. **`.github/workflows/deploy-stage.yml`** — remove:
    - Lines 119-127: Redis health wait loop
    - Lines 163-164: `REDIS_HOST` and `REDIS_PORT` from .env block

### Step 7: Update dev scripts (1 file)

15. **`dev-stack.sh`** — remove:
    - Lines 12-13: Redis port comments in header
    - Lines 338-346: Redis health check loop in stage deployment

### Step 8: Update documentation (4 files)

16. **`CLAUDE.md`** — remove:
    - Line 174: `- Redis 7 (caching)` from Key Technologies table
    - Line 234: `| Redis | redis:alpine | 6379 | swing_trade_redis | AOF enabled |` from Infrastructure table
    - Line 236: `redis_data` from Volumes line
    - Line 275: `(DB/Redis/Upstox connection states)` → `(DB/Upstox connection states)` in API endpoints table
    - Line 355: `Redis` from env description
    - Line 415: `- Set spring.data.redis.host=piworm.local for remote Redis` from Known Issues

17. **`README.md`** — remove:
    - Line 42: `| Cache | Redis 7 |` from Technology Stack table
    - Line 56: `This runs PostgreSQL and Redis on pi-node` → `This runs PostgreSQL on pi-node`
    - Line 87: `(DB/Redis/Upstox states)` → `(DB/Upstox states)` in API endpoints table

18. **`.github/workflows/deploy-stage.yml`** — line 36: change `pom.xml` hash to `build.gradle.kts` (existing bug, unrelated to Redis removal but worth fixing)

## Style guide

- Follow existing patterns: one section per config block, `# ====` separators
- Remove entire sections, not partial edits (cleaner diffs)
- Delete files entirely when they have no remaining purpose (RedisTestContainer.java)
- Keep Javadoc accurate — update SignalEngine doc to remove stale cache references
- Preserve all other config values; only touch Redis/cache/lettuce lines

## Verification

1. `cd backend && ./gradlew :api:compileJava :broker:compileJava :strategy:compileJava :data:compileJava` — no compile errors
2. `cd backend && ./gradlew :api:test` — tests pass (RedisTestContainer no longer referenced)
3. `cd backend && ./gradlew build` — full build succeeds
4. `cd dashboard && yarn playwright test tests/e2e/views/stage-sanity.spec.ts` — health check passes without redis component
5. `./dev-stack.sh start` — dev stack starts without Redis errors
6. `curl -s http://localhost:8080/actuator/health | jq .components` — no redis component in health response