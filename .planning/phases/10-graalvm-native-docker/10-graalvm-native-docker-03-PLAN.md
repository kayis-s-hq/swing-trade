---
phase: 10-graalvm-native-docker
plan: 03
type: execute
wave: 2
depends_on: [10-graalvm-native-docker-01]
files_modified: [docker-compose.yml, .env.example]
autonomous: true
requirements: [REQ-203, REQ-205]

must_haves:
  truths:
    - "docker-compose.yml includes postgres, redis, and swing-trade-api services"
    - "API service depends on healthy postgres and redis"
    - "Container health checks verify all services"
  artifacts:
    - path: "docker-compose.yml"
      provides: "Complete service orchestration with health checks"
      min_lines: 0
    - path: ".env.example"
      provides: "Environment variable template"
      min_lines: 0
  key_links:
    - from: "docker-compose.yml"
      to: "Dockerfile"
      via: "swing-trade-api image"
      pattern: "image: swing-trade-api"
    - from: "docker-compose.yml"
      to: ".env.example"
      via: "environment variables"
      pattern: "LANGCHAIN4J_OPEN_AI_API_KEY"
---

<objective>
Update docker-compose.yml with swing-trade-api service and health checks

Purpose: Integrate the swing-trade API into the existing docker-compose setup. Add both native (production) and JAR (development) API service variants with proper health checks, networking, and environment configuration.

Output: Updated docker-compose.yml with postgres, redis, api (native), api-dev (JAR) services plus .env.example template
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/ROADMAP.md
@.planning/phases/10-graalvm-native-docker/10-graalvm-native-docker-CONTEXT.md
@.planning/phases/10-graalvm-native-docker/10-graalvm-native-docker-RESEARCH.md
@/Users/kayisrahman/Documents/workspace/ideas/swing-trade/docker-compose.yml
@/Users/kayisrahman/Documents/workspace/ideas/swing-trade/.planning/phases/10-graalvm-native-docker/10-graalvm-native-docker-01-PLAN.md
@/Users/kayisrahman/Documents/workspace/ideas/swing-trade/.planning/phases/10-graalvm-native-docker/10-graalvm-native-docker-02-PLAN.md

<!-- Existing docker-compose structure -->
<interfaces>
From docker-compose.yml (existing):
```yaml
services:
  postgres:
    image: timescale/timescaledb:latest
    volumes:
      - postgres_data:/var/lib/postgresql/data
    environment:
      POSTGRES_DB: swingtrade_db
      POSTGRES_USER: swingtrade_user
      POSTGRES_PASSWORD: swingtrade_password
    networks:
      - swingtrade-network

  redis:
    image: redis:alpine
    volumes:
      - redis_data:/data
    networks:
      - swingtrade-network

networks:
  swingtrade-network:
    driver: bridge

volumes:
  postgres_data:
  redis_data:
```
</interfaces>
</context>

<tasks>

<task type="auto">
  <name>Task 1: Add health checks to existing postgres and redis services</name>
  <files>docker-compose.yml</files>
  <action>
Update existing postgres and redis services with health checks:

**Postgres health check (use TimescaleDB image, add health check):**
```yaml
postgres:
  # ... existing config
  healthcheck:
    test: ["CMD-SHELL", "pg_isready -U swingtrade_user -d swingtrade_db"]
    interval: 10s
    timeout: 5s
    retries: 5
    start_period: 30s
```

**Redis health check:**
```yaml
redis:
  # ... existing config
  command: redis-server --appendonly yes
  healthcheck:
    test: ["CMD", "redis-cli", "ping"]
    interval: 10s
    timeout: 5s
    retries: 5
    start_period: 10s
```

**Rationale:**
- postgres health check uses pg_isready (TimescaleDB compatible)
- redis health check uses redis-cli ping
- Both use swingtrade-network for API service dependency
- start_period gives services time to initialize

Reference: User decision D-05/D-06 (Keep single docker-compose.yml), RESEARCH.md Section 6.1
</action>
  <verify>
  <automated>grep -A 5 "healthcheck:" docker-compose.yml | grep -q "pg_isready" && grep -q "redis-cli.*ping"</automated>
</verify>
  <done>
  Both postgres and redis services have health checks configured. postgres uses pg_isready, redis uses redis-cli ping. Services will report healthy status for API dependencies.
  </done>
</task>

<task type="auto">
  <name>Task 2: Add swing-trade-api native service</name>
  <files>docker-compose.yml</files>
  <action>
Add swing-trade-api service for native production deployment:

```yaml
  # Swing Trade API - Native Build (Production)
  api:
    image: swing-trade-api:latest
    container_name: swing-trade-api
    restart: unless-stopped
    ports:
      - "8080:8080"
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
    environment:
      - SPRING_PROFILES_ACTIVE=native
      - SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/swingtrade_db
      - SPRING_DATASOURCE_USERNAME=swingtrade_user
      - SPRING_DATASOURCE_PASSWORD=swingtrade_password
      - SPRING_REDIS_HOST=redis
      - SPRING_REDIS_PORT=6379
      - LANGCHAIN4J_OPEN_AI_API_KEY=${LANGCHAIN4J_OPEN_AI_API_KEY}
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 60s
    networks:
      - swingtrade-network
    mem_limit: 256m
    memswap_limit: 256m
```

**Key configuration:**
- depends_on with condition: service_healthy ensures DB/Redis ready first
- Environment variables reference existing postgres credentials
- LANGCHAIN4J_OPEN_AI_API_KEY from .env file
- start_period 60s (native is fast but needs DB connection time)
- mem_limit 256m (target is <100MB RSS, leave headroom)

Reference: RESEARCH.md Section 6.1, User decision D-07 (Spring Actuator health only)
</action>
  <verify>
  <automated>grep -A 20 "swing-trade-api - Native" docker-compose.yml | grep -q "depends_on"</automated>
</verify>
  <done>
  swing-trade-api native service added with healthy dependency on postgres and redis, Spring Actuator health check, memory limits, and environment variables for configuration.
  </done>
</task>

<task type="auto">
  <name>Task 3: Add swing-trade-api-dev JAR service for development</name>
  <files>docker-compose.yml</files>
  <action>
Add swing-trade-api-dev service for JAR-based development deployment:

```yaml
  # Swing Trade API - JAR Build (Development)
  api-dev:
    image: swing-trade-api:dev
    container_name: swing-trade-api-dev
    restart: unless-stopped
    ports:
      - "8081:8080"
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
    environment:
      - SPRING_PROFILES_ACTIVE=dev
      - SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/swingtrade_db
      - SPRING_DATASOURCE_USERNAME=swingtrade_user
      - SPRING_DATASOURCE_PASSWORD=swingtrade_password
      - SPRING_REDIS_HOST=redis
      - SPRING_REDIS_PORT=6379
      - LANGCHAIN4J_OPEN_AI_API_KEY=${LANGCHAIN4J_OPEN_AI_API_KEY}
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 120s  # Longer for JAR startup
    networks:
      - swingtrade-network
    mem_limit: 512m
    memswap_limit: 512m
```

**Differences from native:**
- Port 8081 (avoid conflict with native on 8080)
- Image tag: dev (from Dockerfile runtime-jar target)
- start_period 120s (JAR takes longer to start)
- mem_limit 512m (JAR needs more memory)

Reference: RESEARCH.md Section 6.1, User decision D-02 (Build both JAR and native)
</action>
  <verify>
  <automated>grep -A 20 "swing-trade-api-dev" docker-compose.yml | grep -q "spring-boot-api:dev"</automated>
</verify>
  <done>
  swing-trade-api-dev service added for development with JAR image, different port (8081), longer health check start period, and higher memory limit.
  </done>
</task>

<task type="auto">
  <name>Task 4: Create .env.example template file</name>
  <files>.env.example</files>
  <action>
Create .env.example file with required environment variables:

```bash
# SwingTrade API Environment Variables
# Copy this file to .env and fill in your values

# OpenAI API Key for LLM Sentiment Analysis (required if LLM module is active)
LANGCHAIN4J_OPEN_AI_API_KEY=your-api-key-here

# Optional: Custom Spring profiles
SPRING_PROFILES_ACTIVE=native

# Optional: Custom server port
SERVER_PORT=8080

# Database (already in docker-compose, but can override)
# POSTGRES_USER=swingtrade_user
# POSTGRES_PASSWORD=swingtrade_password
```

**Instructions:**
- Copy to .env before running docker-compose
- LANGCHAIN4J_OPEN_AI_API_KEY required for vLLM/LLM sentiment
- Add comments explaining each variable
- Include note about security (don't commit .env to git)

Reference: RESEARCH.md Section 6.2
</action>
  <verify>
  <automated>test -f .env.example && grep -q "LANGCHAIN4J_OPEN_AI_API_KEY" .env.example</automated>
</verify>
  <done>
  .env.example created with LANGCHAIN4J_OPEN_AI_API_KEY and optional configuration variables, ready for users to copy and customize.
  </done>
</task>

<task type="auto">
  <name>Task 5: Update docker-compose network configuration</name>
  <files>docker-compose.yml</files>
  <action>
Ensure network configuration is correct for all services:

**Verify network definition:**
```yaml
networks:
  swingtrade-network:
    driver: bridge
```

**Verify all services are on swingtrade-network:**
- postgres: has networks
- redis: has networks
- api: has networks
- api-dev: has networks

**Rationale:** All services need to be on same network for:
- API service to connect to postgres (postgres:5432)
- API service to connect to redis (redis:6379)
- Health checks to communicate

Reference: User decision D-05/D-06 (Single source of truth on same network)
</action>
  <verify>
  <automated>grep -c "swingtrade-network" docker-compose.yml | grep -q "4"</automated>
</verify>
  <done>
  All four services (postgres, redis, api, api-dev) are configured on swingtrade-network with bridge driver for inter-service communication.
  </done>
</task>

</tasks>

<verification>
- docker-compose.yml has postgres, redis, api, api-dev services
- All services have health checks
- api service depends_on healthy postgres and redis
- Environment variables configured for database and Redis connectivity
- .env.example created with required variables
- docker-compose config validates successfully
</verification>

<success_criteria>
- [ ] docker-compose.yml includes all 4 services (postgres, redis, api, api-dev)
- [ ] postgres and redis have health checks
- [ ] api service depends on healthy postgres and redis
- [ ] api-dev uses JAR image with port 8081
- [ ] Environment variables for DB, Redis, and API_KEY configured
- [ ] .env.example created with LANGCHAIN4J_OPEN_AI_API_KEY
- [ ] docker-compose config passes validation
- [ ] docker-compose up -d postgres redis api starts successfully
</success_criteria>

<output>
After completion, test with:
```bash
docker-compose config
docker-compose up -d postgres redis
# Wait for health checks
docker-compose up -d api
```
</output>
