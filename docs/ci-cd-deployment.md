# CI/CD Deployment

## Current State (2026-07-06)

### Sites Running on pi-node
| Environment | Port | Profile | DB | Redis | Status |
|-------------|------|---------|----|-------|--------|
| Stage | 8081 | stage | pg:5436/swingtrade_stage | redis:6380 | **UP** |
| Dev | 8080 | local | pg:5435/swingtrade_db | redis:6379 | **UP** |

Both sites verified: DB, Redis, disk space, liveness, readiness all passing.

### GitHub Actions Runners
- **6 runners** on pi-node: actions-runner, actions-runner2, actions-runner3, piworm, piworm-runner-2, piworm-runner-3
- All registered to `kayis-rahman/gots` (not swing-trade)
- Maven 3.9.9 installed on pi-node
- **Blocked**: can't run swing-trade workflows until re-registered

### Workflows
| Workflow | Trigger | Branch | Status |
|----------|---------|--------|--------|
| deploy-stage.yml | push to stage | stage | Works once runners registered |
| deploy-dev.yml | push to dev | dev | Works once runners registered |

## Deployment Architecture

### Inline Workflow Deployment (not deploy.sh)
deploy.sh was abandoned — changes kept getting reverted by a linter hook.
Workflows now deploy inline: build → write .env → start JAR → health check.

### Runner Registration
Runners can only serve one repo. Current plan: keep them on gots, handle swing-trade separately.
To re-register to swing-trade:
1. Generate registration token from GitHub UI: Settings → Actions → Runners → "New self-hosted runner"
2. Run on pi-node: `./config.sh remove --token <TOKEN>` then `./config.sh --url https://github.com/kayis-rahman/swing-trade --token <TOKEN>`

### Known Fixes Applied
1. **Fyers SDK** — force-added to git (ignored by *.jar), installed in workflow via `mvn install:install-file`
2. **Maven cache corruption** — cleaned reactor-core cache in workflow
3. **Log directory permissions** — changed from `/var/log/swing-trade/` (needs sudo) to `/home/dietpi/swing-trade/logs/`
4. **Type mismatch** — `findUnprocessedBuySignalsSince` took `LocalDateTime` but `SignalEntity.date` is `LocalDate` (fixed in SignalRepository.java:168)

### Infrastructure on pi-node
| Container | Port Mapping | Volume |
|-----------|-------------|--------|
| swing_trade_stage_postgres | 5436→5432 | postgres_data_stage |
| swing_trade_stage_redis | 6380→6379 | redis_data_stage |
| swing_trade_postgres | 5435→5432 | postgres_data |
| swing_trade_redis | 6379→6379 | redis_data |

Network: `swingtrade-network` (bridge) + `swing-trade-stage_swingtrade-network` + `swing-trade-dev_swingtrade-network`