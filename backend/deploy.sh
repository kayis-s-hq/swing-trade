#!/bin/bash
# =============================================================================
# Swing Trade - Deploy Script
# Deploys the API to pi-node for dev and stage environments
# =============================================================================
# Usage:
#   ./deploy.sh deploy dev      — build + deploy dev (port 8080, DB swingtrade_db)
#   ./deploy.sh deploy stage    — build + deploy stage (port 8081, DB swingtrade_stage)
#   ./deploy.sh redeploy stage  — rebuild + redeploy stage
#   ./deploy.sh stop dev|stage  — stop environment
#   ./deploy.sh restart stage   — stop + start
#   ./deploy.sh logs stage      — tail logs
#   ./deploy.sh status          — check all environments
# =============================================================================

set -e

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$PROJECT_ROOT"
PI_NODE_HOST="piworm.local"
PI_USER="pi"
REMOTE_APP_DIR="/home/pi/swing-trade"
JAR_NAME="swing-trade-api.jar"

ENVIRONMENT="${1:-help}"
PROFILE="${2:-dev}"

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log() { echo -e "${GREEN}[deploy]${NC} $*"; }
warn() { echo -e "${YELLOW}[deploy]${NC} $*"; }
err() { echo -e "${RED}[deploy]${NC} $*" >&2; }

# ---------------------------------------------------------------------------
# Build
# ---------------------------------------------------------------------------
do_build() {
    log "Building JAR..."
    cd "$BACKEND_DIR"
    mvn clean package -Dmaven.test.skip=true -B
    log "Build complete: $(ls -1 api/target/*.jar | head -1)"
}

# ---------------------------------------------------------------------------
# Deploy (build + scp + remote start)
# ---------------------------------------------------------------------------
do_deploy() {
    do_build

    local env_label
    if [ "$PROFILE" = "dev" ]; then
        env_label="dev"
    elif [ "$PROFILE" = "stage" ]; then
        env_label="stage"
    else
        err "Unknown profile: $PROFILE (use dev or stage)"
        exit 1
    fi

    log "Deploying $env_label to $PI_NODE_HOST..."

    # Create remote directory
    ssh "$PI_USER@$PI_NODE_HOST" "mkdir -p $REMOTE_APP_DIR/$env_label"

    # Copy JAR
    local jar_path="api/target/api-1.0.0.jar"
    scp "$jar_path" "$PI_USER@$PI_NODE_HOST:$REMOTE_APP_DIR/$env_label/$JAR_NAME"
    log "Copied JAR to $PI_NODE_HOST:$REMOTE_APP_DIR/$env_label/$JAR_NAME"

    # Copy env file if exists
    if [ -f "$BACKEND_DIR/.env" ]; then
        scp "$BACKEND_DIR/.env" "$PI_USER@$PI_NODE_HOST:$REMOTE_APP_DIR/$env_label/.env"
        log "Copied .env"
    fi

    # Deploy remote service via SSH
    ssh "$PI_USER@$PI_NODE_HOST" <<'REMOTE_EOF'
set -e
ENV_LABEL="$1"
APP_DIR="$REMOTE_APP_DIR/$ENV_LABEL"
JAR="$APP_DIR/$JAR_NAME"
PROFILE_NAME="$ENV_LABEL"

# Stop existing service
systemctl --user stop "swing-trade-$ENV_LABEL" 2>/dev/null || true
systemctl --user disable "swing-trade-$ENV_LABEL" 2>/dev/null || true

# Create systemd service file
cat > "$HOME/.config/systemd/user/swing-trade-$ENV_LABEL.service" <<EOF
[Unit]
Description=Swing Trade API ($ENV_LABEL)
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=$USER
WorkingDirectory=$APP_DIR
ExecStart=/usr/bin/java \
  -jar $JAR \
  -Dspring.profiles.active=$PROFILE_NAME \
  -Dserver.port=$([ "$ENV_LABEL" = "stage" ] && echo 8081 || echo 8080) \
  -Dlogging.file.name=/var/log/swing-trade/$ENV_LABEL.log
Restart=on-failure
RestartSec=10
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=default.target
EOF

systemctl --user daemon-reload
systemctl --user enable "swing-trade-$ENV_LABEL"
systemctl --user start "swing-trade-$ENV_LABEL"

echo "Service swing-trade-$ENV_LABEL started"
systemctl --user status "swing-trade-$ENV_LABEL" --no-pager -l
REMOTE_EOF

    log "Waiting for health check..."
    sleep 15

    local port
    [ "$PROFILE" = "stage" ] && port=8081 || port=8080

    if curl -sf "http://localhost:$port/actuator/health" | grep -q '"UP"'; then
        log "$env_label is UP on port $port"
    else
        warn "$env_label health check failed — check logs: ./deploy.sh logs $PROFILE"
    fi
}

# ---------------------------------------------------------------------------
# Redeploy (rebuild + deploy)
# ---------------------------------------------------------------------------
do_redeploy() {
    do_deploy
}

# ---------------------------------------------------------------------------
# Stop
# ---------------------------------------------------------------------------
do_stop() {
    log "Stopping $PROFILE..."
    ssh "$PI_USER@$PI_NODE_HOST" "systemctl --user stop \"swing-trade-$PROFILE\"" 2>/dev/null || warn "Service not running"
    log "Stopped $PROFILE"
}

# ---------------------------------------------------------------------------
# Restart
# ---------------------------------------------------------------------------
do_restart() {
    do_stop
    sleep 3
    ssh "$PI_USER@$PI_NODE_HOST" "systemctl --user start \"swing-trade-$PROFILE\""
    log "Restarted $PROFILE"
}

# ---------------------------------------------------------------------------
# Logs
# ---------------------------------------------------------------------------
do_logs() {
    local follow="${2:---no-follow}"
    ssh "$PI_USER@$PI_NODE_HOST" "journalctl -u swing-trade-$PROFILE -f --no-pager"
}

# ---------------------------------------------------------------------------
# Status
# ---------------------------------------------------------------------------
do_status() {
    echo "=== Infrastructure ==="
    cd "$BACKEND_DIR"
    docker compose -f docker-compose.infra-dev.yml ps 2>/dev/null || warn "Dev infra not running"
    echo ""
    docker compose -f docker-compose.infra-stage.yml ps 2>/dev/null || warn "Stage infra not running"
    echo ""

    echo "=== Applications ==="
    for env in dev stage; do
        local port
        [ "$env" = "stage" ] && port=8081 || port=8080
        echo -n "  $env (port $port): "
        if curl -sf "http://localhost:$port/actuator/health" | grep -q '"UP"'; then
            echo "UP"
        else
            echo "DOWN"
        fi
    done
    echo ""

    echo "=== Remote Services ==="
    ssh "$PI_USER@$PI_NODE_HOST" "systemctl --user list-units 'swing-trade-*' --no-pager" 2>/dev/null || warn "Cannot reach pi-node"
}

# ---------------------------------------------------------------------------
# Infra helpers
# ---------------------------------------------------------------------------
do_infra() {
    local infra_env="${2:-dev}"
    local compose_file="docker-compose.infra-${infra_env}.yml"

    if [ ! -f "$BACKEND_DIR/$compose_file" ]; then
        err "Infra file not found: $compose_file"
        exit 1
    fi

    log "Running infra command on $infra_env: ${@:3}"
    docker compose -f "$compose_file" "${@:3}"
}

# ---------------------------------------------------------------------------
# Main dispatch
# ---------------------------------------------------------------------------
case "$ENVIRONMENT" in
    deploy)   do_deploy ;;
    redeploy) do_redeploy ;;
    stop)     do_stop ;;
    restart)  do_restart ;;
    logs)     do_logs ;;
    status)   do_status ;;
    infra)    do_infra ;;
    *)
        err "Usage: $0 {deploy|redeploy|stop|restart|logs|status|infra} [dev|stage]"
        echo ""
        echo "Commands:"
        echo "  deploy  stage    Build + deploy stage to pi-node"
        echo "  deploy  dev      Build + deploy dev to pi-node"
        echo "  redeploy stage   Rebuild + redeploy"
        echo "  stop    stage    Stop stage service"
        echo "  restart stage    Restart stage service"
        echo "  logs    stage    Tail stage logs"
        echo "  status             Show all environment status"
        echo "  infra   stage up Start stage infra containers"
        exit 1
        ;;
esac