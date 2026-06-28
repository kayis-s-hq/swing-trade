#!/bin/bash
# Dev Stack - Swing Trade Development Environment
# This script manages the dev infrastructure on pi-node and runs the Spring Boot app locally

set -e

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$PROJECT_ROOT/backend"
PI_NODE_HOST="piworm.local"

echo "=========================================="
echo "  Swing Trade - Dev Stack Manager"
echo "=========================================="
echo ""

case "${1:-help}" in
  start)
    echo "🚀 Starting Dev Stack..."
    echo ""

    # Start infrastructure on pi-node
    echo "📦 Starting infrastructure on pi-node..."
    docker context use pi-node
    cd "$BACKEND_DIR"
    docker compose -f docker-compose.infra.yml up -d
    echo ""

    # Wait for services to be healthy
    echo "⏳ Waiting for services to be ready..."
    sleep 15
    docker compose -f docker-compose.infra.yml ps
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
    # Data profile: $2 (e.g. "./dev-stack.sh start yahoo" or "./dev-stack.sh start fyers")
    DATA_PROFILE="${2:-fyers}"
    echo "✓ Using data profile: $DATA_PROFILE"
    mvn spring-boot:run -Dspring-boot.run.profiles=local,$DATA_PROFILE
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
    docker compose -f docker-compose.infra.yml down
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
    docker compose -f docker-compose.infra.yml "${@:2}"
    ;;
    
  status)
    echo "📊 Dev Stack Status"
    echo ""
    echo "Infrastructure (pi-node):"
    docker context use pi-node
    cd "$BACKEND_DIR"
    docker compose -f docker-compose.infra.yml ps
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
    docker compose -f docker-compose.infra.yml logs "${@:2}"
    ;;
    
  *)
    echo "Usage: $0 {start|stop|infra|status|logs}"
    echo ""
    echo "Commands:"
    echo "  start   - Start infrastructure on pi-node and run Spring Boot locally"
    echo "  stop    - Stop everything"
    echo "  infra   - Manage infrastructure (pass docker compose commands)"
    echo "  status  - Check status of all services"
    echo "  logs    - View logs"
    echo ""
    echo "Examples:"
    echo "  $0 start"
    echo "  $0 infra up -d"
    echo "  $0 status"
    echo "  $0 logs --tail=100"
    ;;
esac
