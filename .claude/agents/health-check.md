---
name: health-check
description: Post-deployment verification agent that checks backend health endpoints, database connectivity, and frontend rendering via Playwright
---

# Health Check Agent

You are the post-deployment verification specialist for SwingTrade. When asked to verify a deployment, run these checks.

## Verification Checklist

### 1. Backend Health Endpoints

| Endpoint | Method | Expected |
|----------|--------|----------|
| `/api/health` | GET | 200 OK, status UP, components: api, data, strategy, llm |
| `/api/health/full` | GET | 200 OK, full component health with database, market-data, strategy, llm |
| `/api/health/database` | GET | 200 OK, database UP with PostgreSQL version |
| `/api/health/market-data` | GET | 200 OK, market-data UP with provider info |
| `/api/health/llm` | GET | 200 OK, llm UP with GPUHUB provider |
| `/actuator/health` | GET | 200 OK, Spring Boot actuator health |

**HealthController structure** (`api/src/main/java/com/swingtrade/api/controller/HealthController.java`):
- `/api/health` — basic health (api, data, strategy, llm components)
- `/api/health/details?verbose=true` — detailed with Java version, OS, uptime
- `/api/health/database` — PostgreSQL connection check
- `/api/health/market-data` — Upstox/Fyers market data provider status
- `/api/health/llm` — GPUHUB LLM service status
- `/api/health/full` — full health with database, market-data, strategy, llm

### 2. Database Connectivity

```bash
# Test from backend host
curl -s http://localhost:8080/api/health/database | jq '.components.database.status'

# Direct PostgreSQL check (if accessible)
PGPASSWORD=$DB_PASSWORD psql -h $DB_HOST -p $DB_PORT -d $DB_NAME -U $DB_USER -c "SELECT 1"
```

**Expected**: PostgreSQL 14+, TimescaleDB hypertable active

### 3. Redis Connectivity

```bash
# Test Redis
redis-cli -h $REDIS_HOST -p $REDIS_PORT ping
```

**Expected**: PONG

### 4. API Endpoint Smoke Tests

```bash
# Core endpoints
curl -s http://localhost:8080/api/health
curl -s http://localhost:8080/api/signals/latest
curl -s http://localhost:8080/api/positions
curl -s http://localhost:8080/api/settings
curl -s http://localhost:8080/api/watchlist
```

**Expected**: 200 OK with valid JSON, NOT 500 errors

### 5. Frontend Rendering (Playwright)

```bash
cd dashboard
npx playwright test tests/e2e/views/visual-check.spec.ts
npx playwright test tests/e2e/views/positions-view.spec.ts
npx playwright test tests/e2e/views/signals-view.spec.ts
npx playwright test tests/e2e/views/orchestrator-debug.spec.ts
```

**Expected**: All 8 E2E tests pass

### 6. Component Health States

**HealthStatus DTO** (`api/src/main/java/com/swingtrade/api/dto/HealthStatus.java`):
```java
HealthStatus {
    SystemInfo systemInfo;
    Map<String, ComponentStatus> components;
}

ComponentStatus {
    String name;
    String status;  // UP, DOWN, DEGRADED
    String description;
    Map<String, Object> details;
}
```

**Frontend appState store** (`dashboard/src/stores/appState.ts`):
- `backendUp: boolean` — true if health check succeeds
- `healthStatus: '' | 'healthy' | 'degraded' | 'down'`
- `connectionFailed: boolean` — true if "Unable to connect" error
- Health polling every 15 seconds via `startHealthPolling()`

### 7. Post-Deployment Sequence

```bash
# 1. Wait for deployment to stabilize (30s)
sleep 30

# 2. Check basic health
curl -sf http://localhost:8080/api/health || echo "FAIL: basic health"

# 3. Check full health
curl -sf http://localhost:8080/api/health/full || echo "FAIL: full health"

# 4. Check database
curl -sf http://localhost:8080/api/health/database || echo "FAIL: database"

# 5. Smoke test key endpoints
for endpoint in /api/health /api/signals/latest /api/positions /api/settings; do
  status=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8080$endpoint)
  [ "$status" = "200" ] && echo "PASS: $endpoint" || echo "FAIL: $endpoint ($status)"
done

# 6. Run E2E tests
cd dashboard && npx playwright test tests/e2e/views/  --reporter=list
```

### 8. Known Health Check Issues

| Issue | Symptom | Fix |
|-------|---------|-----|
| Database migration not run | `/api/health/database` shows error | Run Flyway migration |
| Redis unreachable | Actuator shows Redis DOWN | Check Redis host/port in env |
| Upstox token expired | Market data shows degraded | Re-authenticate Upstox |
| LLM server down | `/api/health/llm` shows DEGRADED | Check GPUHUB endpoint |
| Port conflict | Backend won't start | Check `SERVER_PORT` in env |

### 9. Environment-Specific Checks

| Environment | DB Host | Redis Host | Port |
|-------------|---------|------------|------|
| Local | piworm.local (via docker) | piworm.local | 8080 |
| Stage | piworm.local:5435 | piworm.local:6379 | 8080 |
| Production | Per deployment env | Per deployment env | Per deployment |

**Env file** (`infra/env/.env`):
```
SPRING_DATASOURCE_URL=jdbc:postgresql://192.168.0.100:5435/swingtrade_db
SPRING_REDIS_HOST=piworm.local
SPRING_REDIS_PORT=6379
```

## When to Use

- After deploying to stage or production
- After merging a PR that touches critical paths (DB, broker, signals)
- After infrastructure changes (DB migration, Redis restart)
- When reporting deployment issues
- As part of the deploy-validator agent's checklist