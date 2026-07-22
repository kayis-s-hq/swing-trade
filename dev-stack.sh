#!/bin/bash
# Dev Stack - Swing Trade Development Environment
#
# Architecture:
#   - Dev stack: runs infra on pi-node, Spring Boot app locally on Mac
#   - Stage stack: runs everything (infra + API) in Docker on pi-node
#   - Monitoring: pi-node runs Prometheus + Grafana for all stage metrics
#
# Ports on pi-node:
#   5435 - dev PostgreSQL
#   5436 - stage PostgreSQL
#   6379 - dev Redis
#   6380 - stage Redis
#   8080 - dev API (no monitoring)
#   8081 - stage API (scraped by pi-prometheus)
#   9090 - pi-prometheus
#   3001 - pi-grafana
#   3002 - local Grafana (optional, for local dashboard access)

set -e

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$PROJECT_ROOT/backend"
PI_NODE_HOST="piworm.local"

echo "=========================================="
echo "  Swing Trade - Dev Stack Manager"
echo "=========================================="
echo ""

do_stage_monitoring() {
    local cmd="${2:-up}"
    docker compose -f docker-compose.monitoring-stage.yml "$cmd"
}

do_stage_infra() {
    local cmd="${2:-up}"
    docker context use pi-node
    cd "$BACKEND_DIR"
    docker compose -f docker-compose.infra-stage.yml "$cmd"
    docker context use desktop-linux
}

case "${1:-help}" in
  start)
    echo "🚀 Starting Dev Stack..."
    echo ""

    # Start infrastructure on pi-node
    echo "📦 Starting infrastructure on pi-node..."
    docker context use pi-node
    cd "$BACKEND_DIR"
    docker compose -f docker-compose.infra-dev.yml up -d
    echo ""

    # Wait for services to be healthy
    echo "⏳ Waiting for services to be ready..."
    sleep 15
    docker compose -f docker-compose.infra-dev.yml ps
    echo ""

    # Switch back to local context
    docker context use desktop-linux
    echo ""

    # Start Spring Boot locally
    echo "☕ Starting Spring Boot app locally..."
    # Load .env so Spring Boot picks up env vars (Spring doesn't auto-load .env)
    if [ -f "$BACKEND_DIR/.env" ]; then
        set -a
        source "$BACKEND_DIR/.env"
        set +a
        echo "✓ Loaded environment from $BACKEND_DIR/.env"
    fi
    cd "$BACKEND_DIR/api"
    mvn spring-boot:run -Dspring-boot.run.profiles=local
    ;;

  stop)
    echo "🛑 Stopping Dev Stack..."
    echo ""

    # Stop local Spring Boot
    pkill -f "spring-boot:run" 2>/dev/null || true
    echo "✓ Stopped local Spring Boot app"
    echo ""

    # Stop infrastructure on pi-node
    echo "📦 Stopping infrastructure on pi-node..."
    docker context use pi-node
    cd "$BACKEND_DIR"
    docker compose -f docker-compose.infra-dev.yml down
    echo ""

    # Switch back to local context
    docker context use desktop-linux
    echo ""
    echo "✓ Dev Stack stopped"
    ;;

  infra)
    echo "📦 Managing infrastructure on pi-node..."
    docker context use pi-node
    cd "$BACKEND_DIR"
    docker compose -f docker-compose.infra-dev.yml "${@:2}"
    ;;

  status)
    echo "📊 Dev Stack Status"
    echo ""
    echo "Infrastructure (pi-node):"
    docker context use pi-node
    cd "$BACKEND_DIR"
    docker compose -f docker-compose.infra-dev.yml ps
    echo ""

    echo "Local Spring Boot:"
    docker context use desktop-linux
    pgrep -f "spring-boot:run" && echo "✓ Running" || echo "✗ Not running"
    echo ""
    echo "API Health:"
    curl -s "http://localhost:8080/actuator/health" | python3 -m json.tool 2>/dev/null || echo "✗ API not reachable"
    ;;

  logs)
    echo "📋 Dev Stack Logs"
    echo ""
    echo "=== Infrastructure Logs ==="
    docker context use pi-node
    cd "$BACKEND_DIR"
    docker compose -f docker-compose.infra-dev.yml logs "${@:2}"
    ;;

  stage-monitoring)
    echo "📊 Managing stage monitoring (local Grafana → pi-node Prometheus)..."
    do_stage_monitoring "${@:2}"
    ;;

  logs-json)
    LOG_FILE="${PROJECT_ROOT}/logs/swing-trade-local.log"
    if [ -f "$LOG_FILE" ]; then
      tail -n "${2:-50}" "$LOG_FILE" | jq '.' 2>/dev/null || tail -n "${2:-50}" "$LOG_FILE"
    else
      echo "Log file not found: $LOG_FILE (start app first with '$0 start')"
    fi
    ;;

  stage)
    echo "🚀 Starting Stage Dev Stack..."
    echo ""
    echo "Stage runs entirely on pi-node: Docker containers for infra + API."
    echo "Monitoring is handled by pi-prometheus (port 9090) + pi-grafana (port 3001)."
    echo ""

    # Step 1: Build Docker image from local Maven artifacts
    echo "🔨 Building Docker image from local Maven build..."
    cd "$BACKEND_DIR"
    docker build -t swing-trade-api:dev -f backend/Dockerfile --target runtime-jar ..
    echo "✓ Image built: swing-trade-api:dev"
    echo ""

    # Step 2: Start stage infrastructure (PostgreSQL + Redis)
    echo "📦 Starting stage infrastructure on pi-node..."
    docker context use pi-node
    cd "$BACKEND_DIR"
    docker compose -f docker-compose.infra-stage.yml up -d
    echo ""

    # Step 3: Wait for infra to be healthy
    echo "⏳ Waiting for stage infrastructure to be ready..."
    sleep 15
    docker compose -f docker-compose.infra-stage.yml ps
    echo ""

    # Step 4: Start API container on swingtrade-network
    echo "🐳 Starting stage API container..."
    docker stop swing-trade-stage-api 2>/dev/null || true
    docker rm swing-trade-stage-api 2>/dev/null || true
    sleep 2
    docker run -d \
      --name swing-trade-stage-api \
      --network swing-trade-stage_swingtrade-network \
      -p 8081:8080 \
      -e SPRING_PROFILES_ACTIVE=stage \
      -e DB_HOST=swing_trade_stage_postgres \
      -e DB_PORT=5432 \
      -e DB_NAME=swingtrade_stage \
      -e DB_USER=swingtrade_user \
      -e DB_PASSWORD=swingtrade_password \
      -e REDIS_HOST=swing_trade_stage_redis \
      -e REDIS_PORT=6379 \
      -e LLM_BASE_URL=http://localhost:8000 \
      -e LLM_MODEL_NAME=claude-sonnet-4-6 \
      -e LLM_TIMEOUT=30000 \
      -e TRADING_ENABLED=true \
      -e PAPER_TRADING_ENABLED=true \
      -e REAL_TRADING_ENABLED=false \
      -e STRATEGY_ENABLED=true \
      -e SIGNAL_ENABLED=false \
      -e DISCORD_WEBHOOK_ENABLED=false \
      swing-trade-api:dev \
      java -jar /app/swing-trade-api.jar
    echo "✓ API container started"
    echo ""

    # Step 5: Wait for Spring Boot startup
    echo "⏳ Waiting for Spring Boot startup (~20s)..."
    sleep 20
    docker context use desktop-linux

    # Step 6: Health check
    echo "🏥 Checking API health on piworm.local:8081..."
    curl -s "http://piworm.local:8081/actuator/health" | python3 -m json.tool 2>/dev/null || echo "⚠ API not yet reachable"
    echo ""

    # Step 7: Verify Prometheus scrape target
    echo "📊 Checking Prometheus scrape target..."
    if curl -sf "http://piworm.local:9090/api/v1/targets" | grep -q '"job":"swing-trade-stage"'; then
      echo "✓ Prometheus is scraping stage API"
    else
      echo "⚠ Prometheus target may take one scrape interval (15s) to appear"
    fi
    echo ""

    echo "Stage stack is running:"
    echo "  API:       http://piworm.local:8081"
    echo "  Prometheus: http://piworm.local:9090"
    echo "  Grafana:    http://piworm.local:3001"
    echo ""
    echo "To stop: $0 stage-monitoring down  (local Grafana only)"
    echo "To see logs: docker --context pi-node logs -f swing-trade-stage-api"
    ;;

  *)
    echo "Usage: $0 {start|stage|stop|infra|status|logs|logs-json|stage-monitoring}"
    echo ""
    echo "Commands:"
    echo "  start            - Start dev infra on pi-node + run Spring Boot locally"
    echo "  stage            - Build + deploy stage stack (Docker on pi-node)"
    echo "  stop             - Stop dev stack"
    echo "  infra            - Manage dev infrastructure (pass docker compose commands)"
    echo "  status           - Check status of all services"
    echo "  logs             - View infrastructure logs"
    echo "  logs-json        - View structured JSON logs (requires jq)"
    echo "  stage-monitoring - Start/stop local Grafana (scrapes pi-node Prometheus)"
    echo ""
    echo "Stage monitoring stack:"
    echo "  API metrics:   http://piworm.local:8081/actuator/prometheus"
    echo "  Prometheus:    http://piworm.local:9090  (scrapes stage API)"
    echo "  Grafana:       http://piworm.local:3001  (JVM/Spring Boot dashboard)"
    echo ""
    echo "Examples:"
    echo "  $0 start"
    echo "  $0 stage"
    echo "  $0 infra up -d"
    echo "  $0 status"
    echo "  $0 logs --tail=100"
    echo "  $0 logs-json 50"
    echo "  $0 stage-monitoring up -d"
    ;;
esac