# CI/CD Deployment

## Architecture

### Stage Environment (Docker on pi-node)
Stage runs entirely in Docker containers on pi-node. Monitoring is built in — Prometheus scrapes the API and Grafana provides JVM/Spring Boot dashboards.

| Component | Container | Port (pi-node) | Network |
|-----------|-----------|----------------|---------|
| PostgreSQL | `swing_trade_stage_postgres` | 5436 | swing-trade-stage_swingtrade-network |
| Redis | `swing_trade_stage_redis` | 6380 | swing-trade-stage_swingtrade-network |
| Spring Boot API | `swing-trade-stage-api` | 8081 | swing-trade-stage_swingtrade-network |
| Prometheus | `pi-prometheus` | 9090 | pi-stack_monitoring + swing-trade-stage_swingtrade-network |
| Grafana | `pi-grafana` | 3001 | pi-stack_monitoring |

### Dev Environment (pi-node infra + local app)
Dev runs PostgreSQL on pi-node and the Spring Boot API plus Vue dashboard locally through `dev-stack.sh`. Use the Gradle-built API jar or the stack wrapper; Maven is not the local runtime path. Dev monitoring is available through the API actuator endpoints and local dashboard.

| Component | Location | Port (pi-node) |
|-----------|----------|----------------|
| PostgreSQL | pi-node container | 5435 |
| Redis | pi-node container | 6379 |
| Spring Boot API | Local machine (`dev-stack.sh`) | 8080 |
| Vue dashboard | Local machine (`dev-stack.sh`) | 3003 |

## Stage Deployment

### Local Development (`dev-stack.sh stage`)
```bash
./dev-stack.sh stage
```

Flow:
1. **Build Docker image** — `docker build -f backend/Dockerfile --target runtime-jar ..`
2. **Start infra** — `docker compose -f docker-compose.infra-stage.yml up -d` (PostgreSQL + Redis)
3. **Start API container** — runs on `swing-trade-stage_swingtrade-network` with all stage env vars
4. **Health check** — curls `http://piworm.local:8081/actuator/health`
5. **Prometheus verification** — checks that `swing-trade-stage` target appears in Prometheus

### GitHub Actions (`deploy-stage.yml`)
Triggered on push to `stage` branch (backend/** or .github/workflows/**).

Flow:
1. **Checkout + JDK 21** (Temurin)
2. **Install Fyers SDK** — `mvn install:install-file`
3. **Build backend** — `mvn clean package -Dpmd.skip=true -DskipTests -Dcheckstyle.skip=true`
4. **Build Docker image** — `runtime-jar` target from `backend/Dockerfile`
5. **Stop existing container** — `docker stop/rm swing-trade-stage-api`
6. **Verify infra health** — checks PostgreSQL, Redis, and network status
7. **Start API container** — same Docker run command as local dev-stack
8. **Health check** — retries 20 times with 6s interval
9. **Prometheus scrape verification** — confirms target is registered

### Container Configuration
```
--name swing-trade-stage-api
--network swing-trade-stage_swingtrade-network
-p 8081:8080
-e SPRING_PROFILES_ACTIVE=stage
-e DB_HOST=swing_trade_stage_postgres
-e DB_NAME=swingtrade_stage
-e REDIS_HOST=swing_trade_stage_redis
```

Key env vars (all configurable via `-e`):
- `DB_HOST`, `DB_PORT`, `DB_NAME` — PostgreSQL connection (resolves to container name on Docker network)
- `REDIS_HOST`, `REDIS_PORT` — Redis connection
- `LLM_BASE_URL`, `LLM_MODEL_NAME`, `LLM_TIMEOUT` — vLLM integration
- `TRADING_ENABLED`, `PAPER_TRADING_ENABLED`, `REAL_TRADING_ENABLED` — trading mode
- `STRATEGY_ENABLED` — signal generation
- `SIGNAL_ENABLED`, `DISCORD_WEBHOOK_ENABLED` — notification toggles

### Docker Compose Files
- **`docker-compose.infra-stage.yml`** — PostgreSQL + Redis + API service definition
- **`docker-compose.monitoring-stage.yml`** — Local Grafana (optional, bind-mount issue on Docker Desktop Mac)
- **`backend/Dockerfile`** — Multi-stage: Maven build → `runtime-jar` (JRE 21 Alpine)

## Monitoring Stack

### Prometheus (pi-node)
- **Config**: `/home/dietpi/pi-stack/prometheus/prometheus.yml` (mounted into `pi-prometheus`)
- **Scrape targets**:
  - `prometheus` — self-monitoring
  - `node` — node_exporter (system metrics)
  - `grafana` — Grafana health
  - `caddy` — Caddy reverse proxy metrics
  - `swing-trade-stage` — Spring Boot Actuator `/actuator/prometheus`
  - `vllm` — GPU cluster metrics (external, HTTPS)

### Grafana (pi-node)
- **URL**: http://piworm.local:3001
- **Default creds**: admin / (see `/home/dietpi/pi-stack/grafana/admin-password`)
- **Setup**: Add Prometheus datasource at `http://localhost:9090`, import `jvm-spring-boot.json` dashboard
- **Dashboard**: `backend/monitoring/grafana/dashboards/jvm-spring-boot.json` — 9 panels:
  1. JVM Memory Usage (gauge)
  2. HTTP Request Latency P95 (timeseries)
  3. HTTP Request Rate (timeseries)
  4. GC Pause Time (timeseries)
  5. HikariCP Connection Pool (timeseries)
  6. JVM Threads (timeseries)
  7. HTTP 4xx/5xx Error Rates (timeseries)
  8. Heap Memory Usage (timeseries)
  9. System CPU Usage (timeseries)

### Local Grafana (optional)
`./dev-stack.sh stage-monitoring up -d` — runs Grafana on port 3002, configured to scrape pi-node Prometheus at `http://piworm.local:9090`. Bind mounts don't work reliably on Docker Desktop Mac, so provisioning files must be baked into the image or configured manually.

## Infrastructure on pi-node

### Volumes
| Volume | Database |
|--------|----------|
| `postgres_data` | dev (swingtrade_db) |
| `postgres_data_stage` | stage (swingtrade_stage) |
| `redis_data` | dev |
| `redis_data_stage` | stage |

### Networks
| Network | Purpose |
|---------|---------|
| `swingtrade-network` | dev services |
| `swing-trade-stage_swingtrade-network` | stage services + API + Prometheus |
| `swing-trade-dev_swingtrade-network` | dev services |
| `pi-stack_monitoring` | Prometheus + Grafana + node_exporter + caddy |

### Docker Contexts
| Context | Target | Use |
|---------|--------|-----|
| `desktop-linux` | localhost Docker Desktop | Local development |
| `pi-node` | ssh://dietpi@piworm.local | Remote infra + stage |

Switch context: `docker context use pi-node` / `docker context use desktop-linux`

## Known Issues

1. **Fyers SDK** — force-added to git (ignored by *.jar), installed in workflow via `mvn install:install-file`
2. **Maven cache corruption** — cleaned reactor-core cache in workflow
3. **Log directory permissions** — changed from `/var/log/swing-trade/` (needs sudo) to `/home/dietpi/swing-trade/logs/`
4. **Type mismatch** — `findUnprocessedBuySignalsSince` took `LocalDateTime` but `SignalEntity.date` is `LocalDate` (fixed in SignalRepository.java:168)
5. **Docker bind mounts on Mac** — Docker Desktop Mac can't access bind-mounted paths from its VM. Local Grafana provisioning volumes fail. Use pi-grafana instead.
6. **Container names with underscores** — Tomcat rejects Host headers with underscores. Use hyphenated names (e.g., `swing-trade-stage-api`).
7. **GitHub Actions runners** — 6 runners on pi-node registered to `kayis-s-hq/gots`. Can only serve one repo. Swing-trade workflows run on self-hosted runners but need re-registration to the org.
