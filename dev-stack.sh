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
#   8080 - dev API (no monitoring)
#   8081 - stage API (scraped by pi-prometheus)
#   9090 - pi-prometheus
#   3001 - pi-grafana
#   3002 - local Grafana (optional, for local dashboard access)

set -e

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$PROJECT_ROOT/backend"
DASHBOARD_DIR="$PROJECT_ROOT/dashboard"
INFRA_DIR="$PROJECT_ROOT/infra"
PIDFILE="$PROJECT_ROOT/.swing-trade-pids"
PI_NODE_HOST="piworm.local"

echo "=========================================="
echo "  Swing Trade - Dev Stack Manager"
echo "=========================================="
echo ""

# PID tracking for local processes
save_pid() {
    mkdir -p "$PROJECT_ROOT"
    echo "$2" >> "$PIDFILE"
}

cleanup_pids() {
    if [ -f "$PIDFILE" ]; then
        while read -r pid; do
            kill "$pid" 2>/dev/null || true
        done < "$PIDFILE"
        rm -f "$PIDFILE"
    fi
}
trap cleanup_pids EXIT

do_stage_monitoring() {
    local cmd="${2:-up}"
    docker compose -f "$INFRA_DIR/docker-compose.monitoring-stage.yml" "$cmd"
}

do_stage_infra() {
    local cmd="${2:-up}"
    docker context use pi-node
    cd "$INFRA_DIR"
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
    cd "$INFRA_DIR"
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
    # NOTE: we build the jar and run it with plain `java -jar` instead of
    # `./gradlew :api:bootRun`. bootRun (via Gradle's JavaExec + DevTools'
    # Restarter) was found to silently die mid-startup with zero exception
    # logged, every time, for reasons never fully diagnosed. Running the
    # built jar directly is reliable and surfaces real startup errors.
    echo "☕ Building and starting Spring Boot app locally..."
    # Load .env so Spring Boot picks up env vars (Spring doesn't auto-load .env)
    if [ -f "$INFRA_DIR/env/.env" ]; then
        set -a
        source "$INFRA_DIR/env/.env"
        set +a
        echo "✓ Loaded environment from $INFRA_DIR/env/.env"
    fi
    export LOG_FILE="$BACKEND_DIR/logs/swing-trade-local.log"
    cd "$BACKEND_DIR"
    ./gradlew :api:bootJar -q
    java -Duser.timezone=Asia/Kolkata -jar api/build/libs/api.jar --spring.profiles.active=local &
    BACKEND_PID=$!
    save_pid "$BACKEND_PID"
    echo "✓ Backend PID: $BACKEND_PID"
    echo ""

    # Start Vue dev server locally
    echo "🖥️  Starting Vue dev server locally..."
    cd "$DASHBOARD_DIR"
    yarn dev &
    FRONTEND_PID=$!
    save_pid "$FRONTEND_PID"
    echo "✓ Frontend PID: $FRONTEND_PID"
    echo ""

    echo "=========================================="
    echo "  Dev Stack Running"
    echo "=========================================="
    echo "  API:      http://localhost:8080"
    echo "  Dashboard: http://localhost:3003"
    echo ""
    echo "Useful commands:"
    echo "  Logs:    $0 logs"
    echo "  Stop:    $0 stop"
    echo "  Frontend only: $0 frontend"
    ;;

  stop)
    echo "🛑 Stopping Dev Stack..."
    echo ""

    # Stop local processes via PID file
    cleanup_pids
    echo "✓ Stopped local processes"
    echo ""

    # Fallback: kill by pattern
    pkill -f "java.*-jar.*api/build/libs/api.jar" 2>/dev/null || true
    pkill -f "vite" 2>/dev/null || true
    echo "✓ Stopped local Spring Boot app and Vue dev server"
    echo ""

    # Stop infrastructure on pi-node
    echo "📦 Stopping infrastructure on pi-node..."
    docker context use pi-node
    cd "$INFRA_DIR"
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
    cd "$INFRA_DIR"
    docker compose -f docker-compose.infra-dev.yml "${@:2}"
    ;;

  status)
    echo "📊 Dev Stack Status"
    echo ""
    echo "Infrastructure (pi-node):"
    docker context use pi-node
    cd "$INFRA_DIR"
    docker compose -f docker-compose.infra-dev.yml ps
    echo ""

    echo "Local Spring Boot:"
    docker context use desktop-linux
    pgrep -f "java.*-jar.*api/build/libs/api.jar" && echo "✓ Running" || echo "✗ Not running"
    echo ""

    echo "Local Vue Dev Server:"
    pgrep -f "vite" && echo "✓ Running" || echo "✗ Not running"
    echo ""

    echo "API Health:"
    curl -s "http://localhost:8080/actuator/health" | python3 -m json.tool 2>/dev/null || echo "✗ API not reachable"
    ;;

  logs)
    echo "📋 Dev Stack Logs"
    echo ""
    echo "=== Infrastructure Logs ==="
    docker context use pi-node
    cd "$INFRA_DIR"
    docker compose -f docker-compose.infra-dev.yml logs "${@:2}"
    ;;

  stage-monitoring)
    echo "📊 Managing stage monitoring (local Grafana → pi-node Prometheus)..."
    do_stage_monitoring "${@:2}"
    ;;

  frontend)
    case "${2:-start}" in
      start)
        echo "🖥️  Starting Vue dev server..."
        cd "$DASHBOARD_DIR"
        if pgrep -f "vite" > /dev/null 2>&1; then
          echo "✓ Vue dev server already running"
        else
          yarn dev &
          FRONTEND_PID=$!
          save_pid "$FRONTEND_PID"
          echo "✓ Frontend started (PID: $FRONTEND_PID)"
          echo "  Dashboard: http://localhost:3003"
        fi
        ;;
      stop)
        echo "🛑 Stopping Vue dev server..."
        pkill -f "vite" 2>/dev/null || true
        echo "✓ Stopped Vue dev server"
        ;;
      logs)
        echo "📋 Vue dev server logs:"
        cd "$DASHBOARD_DIR"
        yarn dev
        ;;
      *)
        echo "Usage: $0 frontend {start|stop|logs}"
        ;;
    esac
    ;;

  frontend-logs)
    echo "📋 Vue dev server logs:"
    cd "$DASHBOARD_DIR"
    yarn dev
    ;;

  logs-json)
    LOG_FILE="${PROJECT_ROOT}/backend/logs/swing-trade-local.log"
    if [ -f "$LOG_FILE" ]; then
      tail -n "${2:-50}" "$LOG_FILE" | jq '.' 2>/dev/null || tail -n "${2:-50}" "$LOG_FILE"
    else
      echo "Log file not found: $LOG_FILE (start app first with '$0 start')"
    fi
    ;;

  stage)
    echo "=========================================="
    echo "  Swing Trade - Stage Deployment"
    echo "=========================================="
    echo ""
    echo "Build happens on this Mac from a git worktree checked out to the"
    echo "'stage' branch. pi-node only receives prebuilt artifacts and packages"
    echo "them into a lightweight Docker image — no compilation on the Pi."
    echo ""

    STAGE_PATH="/home/dietpi/swing-trade"
    WORKTREE_DIR="$PROJECT_ROOT/.worktrees/stage"

    # --- Step 1: Ensure the stage worktree exists ---
    if [ ! -d "$WORKTREE_DIR" ]; then
      if ! git show-ref --verify --quiet refs/heads/stage && ! git show-ref --verify --quiet refs/remotes/origin/stage; then
        echo "✗ No local or remote 'stage' branch found."
        echo "  Create it first (e.g. from main) and push it to origin, then retry."
        exit 1
      fi
      echo "🌳 Creating stage worktree at $WORKTREE_DIR..."
      if git show-ref --verify --quiet refs/heads/stage; then
        git worktree add "$WORKTREE_DIR" stage
      else
        git worktree add "$WORKTREE_DIR" -b stage origin/stage
      fi
      echo ""
    fi

    # --- Step 2: Sync worktree to latest origin/stage ---
    echo "🔄 Syncing worktree to origin/stage..."
    if ! (cd "$WORKTREE_DIR" && git fetch origin && git merge --ff-only origin/stage); then
      echo "✗ Worktree at $WORKTREE_DIR is not fast-forwardable to origin/stage."
      echo "  Resolve manually in that directory (branch content is managed outside this script), then retry."
      exit 1
    fi
    echo ""

    # --- Step 3: Switch to Java 21 for Gradle build ---
    echo "☕ Switching to Java 21 for Gradle build..."
    if [ -f "$HOME/.sdkman/bin/sdkman-init.sh" ]; then
      source "$HOME/.sdkman/bin/sdkman-init.sh"
    fi
    echo "  Java: $(java -version 2>&1 | head -1)"
    echo ""

    # --- Step 4: Build backend jar + runtime deps in the worktree ---
    echo "🔨 Building backend (jar + runtime deps) in worktree..."
    cd "$WORKTREE_DIR/backend"
    ./gradlew installFyersSdk :api:jar :api:copyRuntimeDeps -x test -q
    rm -rf "$WORKTREE_DIR/lib"
    mkdir -p "$WORKTREE_DIR/lib"
    cp api/build/libs/api-plain.jar "$WORKTREE_DIR/lib/"
    cp api/build/runtimeDeps/*.jar "$WORKTREE_DIR/lib/"
    LIB_COUNT=$(ls -1 "$WORKTREE_DIR/lib" | wc -l | xargs)
    echo "✓ Backend built: $LIB_COUNT jars in lib/"
    echo ""

    # --- Step 5: Build frontend dist in the worktree ---
    echo "🖥️  Building frontend (yarn build) in worktree..."
    cd "$WORKTREE_DIR/dashboard"
    yarn install --frozen-lockfile --silent
    yarn build
    echo "✓ Frontend built"
    echo ""

    # --- Step 6: Transfer artifacts to pi-node ---
    echo "📦 Transferring backend artifacts to pi-node..."
    ssh dietpi@piworm.local "rm -rf $STAGE_PATH/lib && mkdir -p $STAGE_PATH/lib $STAGE_PATH/dashboard"
    scp -r "$WORKTREE_DIR/lib/"* dietpi@piworm.local:"$STAGE_PATH/lib/"
    scp "$WORKTREE_DIR/infra/Dockerfile" dietpi@piworm.local:"$STAGE_PATH/Dockerfile"
    scp "$WORKTREE_DIR/infra/docker-compose.infra-stage.yml" dietpi@piworm.local:"$STAGE_PATH/docker-compose.infra-stage.yml"
    echo "✓ Backend artifacts transferred"

    echo "📦 Transferring frontend dist..."
    ssh dietpi@piworm.local "rm -rf $STAGE_PATH/dashboard/dist"
    scp -r "$WORKTREE_DIR/dashboard/dist" dietpi@piworm.local:"$STAGE_PATH/dashboard/dist"
    scp "$WORKTREE_DIR/infra/dashboard/Dockerfile" dietpi@piworm.local:"$STAGE_PATH/dashboard/Dockerfile"
    scp "$WORKTREE_DIR/infra/nginx/dashboard-nginx/default.conf" dietpi@piworm.local:"$STAGE_PATH/dashboard/nginx.conf"
    echo "✓ Frontend transferred"

    echo "📋 Transferring .env.stage to pi-node..."
    scp "$WORKTREE_DIR/infra/env/.env.stage" dietpi@piworm.local:"$STAGE_PATH/.env.stage"
    echo "✓ Env file transferred"
    echo ""

    # --- Step 7: Stop existing stage deployment ---
    echo "🛑 Stopping existing stage deployment on pi-node..."
    ssh dietpi@piworm.local "cd $STAGE_PATH && docker compose -f docker-compose.infra-stage.yml down" 2>/dev/null || true
    echo "✓ Existing deployment stopped"
    echo ""

    # --- Step 8: Build + start stage stack on pi-node ---
    echo "🐳 Building images on pi-node (packaging only, no compilation)..."
    ssh dietpi@piworm.local "cd $STAGE_PATH && DOCKER_BUILDKIT=1 docker compose -f docker-compose.infra-stage.yml build"
    echo "✓ Images built"
    IMAGE_SIZE=$(ssh dietpi@piworm.local "docker image inspect swing-trade-api:stage --format='{{.Size}}'" 2>/dev/null | awk '{printf "%.0f", $1/1024/1024}')
    DASH_SIZE=$(ssh dietpi@piworm.local "docker image inspect swing-trade-dashboard:stage --format='{{.Size}}'" 2>/dev/null | awk '{printf "%.0f", $1/1024/1024}')
    echo "  API image: ~${IMAGE_SIZE}MB   Dashboard image: ~${DASH_SIZE}MB"
    echo ""

    echo "🚀 Starting stage stack on pi-node..."
    ssh dietpi@piworm.local "cd $STAGE_PATH && docker compose -f docker-compose.infra-stage.yml up -d"
    echo "✓ Stage stack started"
    echo ""

    # --- Step 9: Wait for infra to be healthy ---
    echo "⏳ Waiting for stage infrastructure to be ready..."
    for i in $(seq 1 30); do
      PG_HEALTH=$(ssh dietpi@piworm.local "cd $STAGE_PATH && docker compose -f docker-compose.infra-stage.yml ps --filter health=healthy postgres 2>/dev/null | wc -l")
      if [ "$PG_HEALTH" -gt 0 ]; then
        echo "✓ PostgreSQL is healthy"
        break
      fi
      [ "$i" -eq 30 ] && echo "⚠ PostgreSQL health check timed out, continuing anyway..."
      sleep 3
    done
    echo ""

    # --- Step 10: Wait for API startup ---
    echo "⏳ Waiting for Spring Boot startup (~30s)..."
    for i in $(seq 1 30); do
      HEALTH=$(curl -sf "http://piworm.local:8081/actuator/health" 2>/dev/null || true)
      if [ -n "$HEALTH" ]; then
        echo ""
        echo "✓ API is responding"
        break
      fi
      sleep 1
    done
    echo ""

    # --- Step 11: Health check ---
    echo "🏥 API Health Check:"
    HEALTH=$(curl -s "http://piworm.local:8081/actuator/health" 2>/dev/null)
    if [ -n "$HEALTH" ]; then
      echo "$HEALTH" | python3 -m json.tool 2>/dev/null || echo "$HEALTH"
    else
      echo "⚠ API not yet reachable — it may still be starting"
    fi
    echo ""

    # --- Step 12: Connect pi-prometheus to stage network ---
    echo "📊 Connecting pi-prometheus to stage network..."
    ssh dietpi@piworm.local "docker network connect swing-trade-stage_swingtrade-network pi-prometheus" 2>/dev/null || true
    echo "✓ Prometheus can now scrape stage API"
    echo ""

    # --- Step 13: Verify Prometheus scrape ---
    echo "📊 Checking Prometheus scrape target..."
    sleep 5
    PROM_TARGETS=$(curl -sf "http://piworm.local:9090/api/v1/targets" 2>/dev/null || true)
    if echo "$PROM_TARGETS" 2>/dev/null | grep -q '"swing-trade-stage"'; then
      echo "✓ Prometheus is scraping stage API"
    else
      echo "⚠ Prometheus target may take one scrape interval (15s) to appear"
    fi
    echo ""

    echo "=========================================="
    echo "  Stage Stack Running"
    echo "=========================================="
    echo "  API:         http://piworm.local:8081"
    echo "  Prometheus:  http://piworm.local:9090"
    echo "  Grafana:     http://piworm.local:3001"
    echo ""
    echo "Useful commands:"
    echo "  Logs:    docker --context pi-node logs -f swing-trade-stage-api"
    echo "  Stop:    ./dev-stack.sh stage-down"
    echo "  Restart: ./dev-stack.sh stage restart"
    ;;

  stage-down)
    echo "🛑 Stopping stage stack..."
    ssh dietpi@piworm.local "cd /home/dietpi/swing-trade && docker compose -f docker-compose.infra-stage.yml down"
    echo "✓ Stage stack stopped"
    ;;

  stage-logs)
    docker --context pi-node logs "${2:--f --tail=100}" swing-trade-stage-api
    ;;

  stage-restart)
    $0 stage-down
    sleep 3
    $0 stage
    ;;

  *)
    echo "Usage: $0 {start|stage|stage-down|stage-logs|stage-restart|stop|infra|status|logs|logs-json|stage-monitoring|frontend|frontend-logs}"
    echo ""
    echo "Commands:"
    echo "  start            - Start dev infra on pi-node + Spring Boot + Vue locally"
    echo "  stage            - Build in .worktrees/stage (branch 'stage'), deploy artifacts to pi-node"
    echo "  stage-down       - Stop stage stack on pi-node"
    echo "  stage-logs       - View stage API logs (pass --tail=N for limit)"
    echo "  stage-restart    - Stop and restart stage stack (full rebuild)"
    echo "  stop             - Stop dev stack"
    echo "  infra            - Manage dev infrastructure (pass docker compose commands)"
    echo "  status           - Check status of all services"
    echo "  logs             - View infrastructure logs"
    echo "  logs-json        - View structured JSON logs (requires jq)"
    echo "  stage-monitoring - Start/stop local Grafana (scrapes pi-node Prometheus)"
    echo "  frontend         - Manage Vue dev server (start|stop|logs)"
    echo "  frontend-logs    - View Vue dev server logs"
    echo ""
    echo "Stage stack:"
    echo "  API:         http://piworm.local:8081"
    echo "  Prometheus:  http://piworm.local:9090"
    echo "  Grafana:     http://piworm.local:3001"
    echo ""
    echo "Examples:"
    echo "  $0 start"
    echo "  $0 stage"
    echo "  $0 stage-down"
    echo "  $0 stage-logs --tail=100"
    echo "  $0 stage-restart"
    echo "  $0 infra up -d"
    echo "  $0 status"
    echo "  $0 logs --tail=100"
    echo "  $0 logs-json 50"
    echo "  $0 stage-monitoring up -d"
    echo "  $0 frontend start"
    echo "  $0 frontend stop"
    echo "  $0 frontend-logs"
    ;;
esac