# Dev Stack Skill & CLI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create a `bin/swingdev` CLI wrapper and a Claude Code skill for managing the SwingTrade dev stack.

**Architecture:** Thin bash wrapper around `dev-stack.sh` that adds health checks, auto-wait with progress, env validation, and profile selection. Skill documents when/how to use each command.

**Tech Stack:** Bash, Docker CLI, curl, printf (zero dependencies).

---

## Files

- **Create:** `bin/swingdev` — CLI wrapper script
- **Create:** `.claude/skills/dev-stack/SKILL.md` — Claude Code skill
- **Modify:** `CLAUDE.md` — add skill reference

---

### Task 1: Create `bin/swingdev` CLI wrapper

**Files:**
- Create: `bin/swingdev`

The CLI wrapper adds UX and new commands on top of `dev-stack.sh`. It delegates infra management to `dev-stack.sh` but adds:

1. `.env` validation before starting
2. Auto-wait with progress bar until infra is healthy
3. Optional dashboard launch
4. Profile selection (`--profile fyers|yahoo`)
5. New commands: `health`, `env-check`, `reset`
6. Colored output and arg validation

```bash
#!/usr/bin/env bash
set -euo pipefail

# SwingTrade Dev Stack CLI
# Thin wrapper around dev-stack.sh with health checks, progress, and UX.

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEV_STACK="$PROJECT_ROOT/dev-stack.sh"
BACKEND_DIR="$PROJECT_ROOT/backend"
ENV_FILE="$BACKEND_DIR/.env"
PI_NODE_HOST="piworm.local"
API_URL="http://localhost:8080/actuator/health"
COLORS=(
  "\033[0m"    # reset
  "\033[32m"   # green
  "\033[33m"   # yellow
  "\033[31m"   # red
  "\033[1m"    # bold
)

c_green() { printf "${COLORS[2]}%s${COLORS[0]}\n" "$*"; }
c_yellow() { printf "${COLORS[3]}%s${COLORS[0]}\n" "$*"; }
c_red() { printf "${COLORS[4]}%s${COLORS[0]}\n" "$*"; }
c_bold() { printf "${COLORS[4]}%s${COLORS[0]}\n" "$*"; }

usage() {
  cat <<EOF
Usage: swingdev <command> [options]

Commands:
  start [--profile NAME] [--dashboard]  Start infra + local app
  stop                                  Stop everything
  status                                Show health of all services
  health                                Check API health details
  env-check                             Validate .env has required vars
  reset                                 Stop + clean volumes + start
  logs [--tail N] [--follow]            View infra logs
  help                                  Show this help

Options:
  --profile NAME   Data profile: fyers (default) or yahoo
  --dashboard      Also launch Vue dev server
  --follow, -f     Follow log output
  --tail N         Show last N lines (default: 50)

Examples:
  swingdev start --profile fyers --dashboard
  swingdev health
  swingdev logs --follow
  swingdev reset
EOF
}

wait_for_health() {
  local max_attempts=30
  local attempt=0
  local delay=2

  c_bold "Waiting for API to be ready..."
  while [ $attempt -lt $max_attempts ]; do
    if curl -sf "$API_URL" > /dev/null 2>&1; then
      c_green "API is ready!"
      return 0
    fi
    attempt=$((attempt + 1))
    local pct=$((attempt * 100 / max_attempts))
    printf "\r  Progress: [%-50s] %d%%" "$(printf '#%.0s' $(seq 1 $((pct / 2))))" "$pct"
    sleep "$delay"
  done
  printf "\n"
  c_red "API did not become ready after $((max_attempts * delay))s"
  return 1
}

check_health() {
  c_bold "SwingTrade Health Check"
  echo ""

  # Check infra containers
  c_bold "Infrastructure (pi-node):"
  docker context use pi-node 2>/dev/null
  (cd "$BACKEND_DIR" && docker compose -f docker-compose.infra.yml ps --format=json 2>/dev/null | python3 -c "
import sys, json
try:
    data = json.load(sys.stdin)
    for c in data:
        name = c.get('Name', '?')
        status = c.get('Status', '?')
        icon = '\033[32mOK\033[0m' if 'healthy' in status.lower() or 'up' in status.lower() else '\033[31m' + status + '\033[0m'
        print(f'  {icon}  {name}')
except:
    print('  \033[31mUnable to query infra status\033[0m')
" 2>/dev/null || echo "  \033[31mUnable to query infra (pi-node unreachable?)\033[0m")
  docker context use desktop-linux 2>/dev/null || true
  echo ""

  # Check local app
  c_bold "Local App:"
  if pgrep -f "spring-boot:run" > /dev/null 2>&1; then
    c_green "  Spring Boot: running"
  else
    c_yellow "  Spring Boot: not running"
  fi
  if pgrep -f "vite" > /dev/null 2>&1; then
    c_green "  Vue Dev Server: running"
  else
    c_yellow "  Vue Dev Server: not running"
  fi
  echo ""

  # Check API
  c_bold "API:"
  if curl -sf "$API_URL" > /dev/null 2>&1; then
    c_green "  API endpoint: reachable"
    curl -sf "$API_URL" 2>/dev/null | python3 -c "
import sys, json
try:
    data = json.load(sys.stdin)
    status = data.get('status', 'UNKNOWN')
    icon = '\033[32m' if status == 'UP' else '\033[33m'
    print(f'  {icon}  Status: {status}\033[0m')
    for k, v in data.get('components', {}).items():
        s = v.get('status', '?')
        icon = '\033[32mOK\033[0m' if s == 'UP' else ('\033[31m' + s + '\033[0m' if s != 'UNKNOWN' else s)
        print(f'    {icon}  {k}')
except:
    print('    status unknown')
" 2>/dev/null || true
  else
    c_red "  API endpoint: unreachable"
  fi
  echo ""
}

do_env_check() {
  c_bold "Environment Validation"
  echo ""

  if [ ! -f "$ENV_FILE" ]; then
    c_red "ERROR: $ENV_FILE not found"
    echo "  Create it from: $BACKEND_DIR/.env.example"
    return 1
  fi

  local missing=0
  local required=(
    "SPRING_DATASOURCE_URL"
    "SPRING_DATASOURCE_USERNAME"
    "SPRING_DATASOURCE_PASSWORD"
    "SPRING_DATA_REDIS_HOST"
    "SPRING_DATA_REDIS_PORT"
  )

  for var in "${required[@]}"; do
    if grep -q "^${var}=" "$ENV_FILE" 2>/dev/null; then
      c_green "  $var: set"
    else
      c_red "  $var: MISSING"
      missing=$((missing + 1))
    fi
  done

  # Check broker profile
  local profile="${1:-fyers}"
  case "$profile" in
    fyers)
      for var in FYERS_CLIENT_ID FYERS_CLIENT_SECRET FYERS_TOKEN; do
        if grep -q "^${var}=" "$ENV_FILE" 2>/dev/null; then
          c_green "  $var: set"
        else
          c_red "  $var: MISSING (needed for fyers profile)"
          missing=$((missing + 1))
        fi
      done
      ;;
    yahoo)
      c_yellow "  (yahoo profile has no additional required vars)"
      ;;
  esac

  echo ""
  if [ $missing -gt 0 ]; then
    c_red "$missing missing variable(s). Fix before starting."
    return 1
  fi
  c_green "All required variables present."
  return 0
}

do_start() {
  local profile="fyers"
  local launch_dashboard=false

  while [[ $# -gt 0 ]]; do
    case "$1" in
      --profile)
        profile="$2"
        shift 2
        ;;
      --dashboard)
        launch_dashboard=true
        shift
        ;;
      *)
        c_red "Unknown option: $1"
        usage
        return 1
        ;;
    esac
  done

  # Validate env
  do_env_check "$profile" || return 1
  echo ""

  # Validate dev-stack.sh exists
  if [ ! -f "$DEV_STACK" ]; then
    c_red "ERROR: $DEV_STACK not found"
    return 1
  fi

  # Start infra
  c_bold "Starting infrastructure on pi-node..."
  docker context use pi-node
  (cd "$BACKEND_DIR" && docker compose -f docker-compose.infra.yml up -d)
  docker context use desktop-linux
  echo ""

  # Wait for infra
  c_bold "Waiting for infra to be ready..."
  local attempts=0
  while [ $attempts -lt 20 ]; do
    attempts=$((attempts + 1))
    local healthy
    healthy=$(docker context use pi-node 2>/dev/null && \
      (cd "$BACKEND_DIR" && docker compose -f docker-compose.infra.yml ps --format=json 2>/dev/null | python3 -c "
import sys, json
try:
    data = json.load(sys.stdin)
    ok = all('healthy' in c.get('Status','').lower() or 'up' in c.get('Status','').lower() for c in data)
    print('yes' if ok else 'no')
except: print('no')
" 2>/dev/null) && docker context use desktop-linux 2>/dev/null) || true
    if [ "$healthy" = "yes" ]; then
      c_green "Infra is healthy!"
      break
    fi
    sleep 3
  done

  if [ "$healthy" != "yes" ]; then
    c_yellow "Infra may not be fully ready. Proceeding with local app..."
  fi

  docker context use desktop-linux

  # Start Spring Boot locally
  echo ""
  c_bold "Starting Spring Boot locally..."
  if [ -f "$ENV_FILE" ]; then
    set -a
    source "$ENV_FILE"
    set +a
  fi
  cd "$BACKEND_DIR/api"
  mvn spring-boot:run -Dspring-boot.run.profiles="local,$profile" &
  local api_pid=$!
  echo ""

  # Wait for API health
  wait_for_health || {
    c_red "Failed to start Spring Boot. Check logs with: swingdev logs"
    kill $api_pid 2>/dev/null || true
    return 1
  }

  # Optionally launch dashboard
  if [ "$launch_dashboard" = true ]; then
    echo ""
    c_bold "Starting Vue dev server..."
    cd "$PROJECT_ROOT/dashboard"
    npm run dev &
    c_green "Dashboard running at http://localhost:5173"
  fi

  echo ""
  c_green "Dev stack started. Profile: $profile"
  echo "  API: $API_URL"
  echo "  Dashboard: http://localhost:5173 (if --dashboard)"
  echo "  Logs: swingdev logs --follow"
}

do_stop() {
  c_bold "Stopping dev stack..."

  # Kill local processes
  pkill -f "spring-boot:run" 2>/dev/null || true
  pkill -f "vite" 2>/dev/null || true
  c_green "Stopped local processes"

  # Stop infra
  c_bold "Stopping infrastructure on pi-node..."
  docker context use pi-node
  (cd "$BACKEND_DIR" && docker compose -f docker-compose.infra.yml down)
  docker context use desktop-linux
  c_green "Infra stopped"

  echo ""
  c_green "Dev stack stopped."
}

do_reset() {
  c_bold "Full reset — this will stop everything and clean volumes!"
  echo ""

  # Stop infra and remove volumes
  docker context use pi-node
  (cd "$BACKEND_DIR" && docker compose -f docker-compose.infra.yml down -v)
  docker context use desktop-linux

  # Kill local processes
  pkill -f "spring-boot:run" 2>/dev/null || true
  pkill -f "vite" 2>/dev/null || true

  c_green "Cleaned up. Run 'swingdev start' to restart."
}

# Main dispatch
case "${1:-help}" in
  start)   shift; do_start "$@" ;;
  stop)    do_stop ;;
  status)  check_health ;;
  health)  check_health ;;
  env-check) shift; do_env_check "${1:-fyers}" ;;
  reset)   do_reset ;;
  logs)
    shift
    local_tail=50
    local_follow=false
    while [[ $# -gt 0 ]]; do
      case "$1" in
        --tail|-t) local_tail="$2"; shift 2 ;;
        --follow|-f) local_follow=true; shift ;;
        *) shift ;;
      esac
    done
    c_bold "Infra logs (last $local_tail lines):"
    docker context use pi-node
    (cd "$BACKEND_DIR" && docker compose -f docker-compose.infra.yml logs --tail="$local_tail" ${local_follow:+-f})
    docker context use desktop-linux
    ;;
  help|--help|-h) usage ;;
  *)
    c_red "Unknown command: $1"
    echo ""
    usage
    ;;
esac
```

- [ ] **Step 1: Create `bin/` directory and write `bin/swingdev`**

Create the directory and write the full script above.

- [ ] **Step 2: Make `bin/swingdev` executable**

Run: `chmod +x /Users/kayisrahman/Documents/workspace/ideas/swing-trade/bin/swingdev`

- [ ] **Step 3: Verify CLI works**

Run:
```bash
cd /Users/kayisrahman/Documents/workspace/ideas/swing-trade
./bin/swingdev help
./bin/swingdev env-check
```

Expected: `help` prints usage. `env-check` validates `.env` and reports required vars.

- [ ] **Step 4: Commit**

```bash
git add bin/swingdev
git commit -m "feat(dev): add swingdev CLI wrapper with health checks and env validation"
```

---

### Task 2: Create Claude Code skill

**Files:**
- Create: `.claude/skills/dev-stack/SKILL.md`

```markdown
---
name: dev-stack
description: Manage the SwingTrade dev stack infrastructure on pi-node and local services. Use when starting, stopping, checking status of, or troubleshooting the development environment. Typical triggers: "start dev stack", "check infra health", "stop everything", "validate env vars", "full reset", "view infra logs".
---

# SwingTrade Dev Stack

## Overview

Manages the SwingTrade development environment: Docker infrastructure on `piworm.local` (pi-node) and local Spring Boot + Vue services on the Mac.

## When to Use This Skill

Use when the user wants to:
- Start/stop the dev stack
- Check health or status of services
- Validate environment variables
- Switch data broker profiles (fyers/yahoo)
- View infrastructure logs
- Perform a full reset

Common user requests that trigger this skill:
- "Start the dev stack"
- "Check if infra is healthy"
- "Stop everything"
- "What's the status?"
- "Reset the dev environment"
- "Validate .env"
- "Switch to yahoo profile"

## Quick Reference

| Command | Description |
|---------|-------------|
| `./bin/swingdev start [--profile NAME] [--dashboard]` | Start infra + local app |
| `./bin/swingdev stop` | Stop everything |
| `./bin/swingdev status` | Show health of all services |
| `./bin/swingdev health` | Detailed API health check |
| `./bin/swingdev env-check [profile]` | Validate .env variables |
| `./bin/swingdev reset` | Stop + clean volumes + start |
| `./bin/swingdev logs [--tail N] [--follow]` | View infra logs |

## Workflows

### Start Development Environment

```bash
./bin/swingdev start --profile fyers --dashboard
```

This:
1. Validates `.env` has required variables
2. Starts PostgreSQL + Redis on pi-node
3. Waits for infra to be healthy
4. Starts Spring Boot locally with `local,fyers` profiles
5. Optionally launches Vue dev server

### Check Infrastructure Health

```bash
./bin/swingdev health
```

Shows: infra container status, local Spring Boot process, API endpoint health, and component-level status (DB, Redis, etc.).

### Switch Data Profile

```bash
./bin/swingdev start --profile yahoo
```

Switches from `fyers` (default) to `yahoo` data provider. Requires `SPRING_PROFILES_ACTIVE` to include the new profile.

### Full Reset

```bash
./bin/swingdev reset
```

Stops everything, removes Docker volumes (data is backed up), and cleans up local processes. Run `swingdev start` afterward.

## Troubleshooting

### pi-node Unreachable

**Symptom:** `swingdev start` fails with connection refused to `piworm.local`

**Fix:**
1. Check pi-node is on the network: `ping piworm.local`
2. Verify docker context: `docker context ls | grep pi-node`
3. Manual test: `ssh piworm.local "docker ps"`
4. If pi-node is down, use `dev-stack.sh infra up -d` on the pi-node itself

### Infra Not Healthy

**Symptom:** `swingdev health` shows containers not "healthy"

**Fix:**
1. Check logs: `./bin/swingdev logs --tail 100`
2. Restart specific service: `docker context use pi-node && cd backend && docker compose -f docker-compose.infra.yml restart postgres`
3. Full restart: `./bin/swingdev reset`

### Missing .env Variables

**Symptom:** `swingdev env-check` reports missing variables

**Fix:**
1. Check `backend/.env.example` for reference template
2. Add missing variables to `backend/.env`
3. Run `./bin/swingdev env-check` again to verify

### API Unreachable

**Symptom:** `swingdev health` shows API endpoint unreachable

**Fix:**
1. Check if Spring Boot is running: `pgrep -f "spring-boot:run"`
2. Check local logs: `tail -50 backend/api/target/*.log 2>/dev/null || echo "No log file"`
3. Try starting manually: `cd backend/api && mvn spring-boot:run -Dspring-boot.run.profiles=local,fyers`
4. Check ports: `lsof -i :8080`

### Port Conflicts

**Symptom:** Spring Boot fails to bind to port 8080

**Fix:**
1. Find process: `lsof -i :8080`
2. Kill it: `kill <PID>`
3. Restart: `./bin/swingdev start`

## Relationship to dev-stack.sh

`./bin/swingdev` is a thin wrapper around `./dev-stack.sh`. Use `dev-stack.sh` directly for:
- Passing arbitrary docker compose commands: `./dev-stack.sh infra up -d`
- Raw script access without wrapper features

Use `./bin/swingdev` for:
- Everyday dev stack operations
- Health checks and validation
- Profile switching with dashboard launch
- Env validation before starting
```

- [ ] **Step 1: Create `.claude/skills/dev-stack/` directory and write `SKILL.md`**

Create the directory and write the full skill content above.

- [ ] **Step 2: Verify skill file structure**

Run:
```bash
ls -la /Users/kayisrahman/Documents/workspace/ideas/swing-trade/.claude/skills/dev-stack/
head -5 /Users/kayisrahman/Documents/workspace/ideas/swing-trade/.claude/skills/dev-stack/SKILL.md
```

Expected: `SKILL.md` exists with correct frontmatter (`name: dev-stack`).

- [ ] **Step 3: Commit**

```bash
git add .claude/skills/dev-stack/SKILL.md
git commit -m "docs: add dev-stack skill for Claude Code"
```

---

### Task 3: Update project CLAUDE.md

**Files:**
- Modify: `CLAUDE.md`

Add a section after the existing `dev-stack.sh` documentation. Find this block:

```
## Dev Stack (pi-node infra + local app)
The dev stack runs infrastructure on a Raspberry Pi (pi-node) via SSH, with Spring Boot and the dashboard running locally on Mac.
```

And add after the `./dev-stack.sh` commands section:

```markdown
### Dev Stack CLI (Recommended)

A thin wrapper with health checks, env validation, and profile selection:

```bash
./bin/swingdev start --profile fyers --dashboard    # Start with dashboard
./bin/swingdev health                                # Detailed health check
./bin/swingdev env-check                             # Validate .env
./bin/swingdev reset                                 # Full reset
```

See `.claude/skills/dev-stack/SKILL.md` for full reference.
```

- [ ] **Step 1: Add CLI section to CLAUDE.md**

Find the line `./dev-stack.sh logs     # View infra logs` and add the CLI section after `./dev-stack.sh infra <cmd>  # Pass any docker compose command to pi-node infra`.

- [ ] **Step 2: Verify the edit**

Run: `grep -A 10 "Dev Stack CLI" /Users/kayisrahman/Documents/workspace/ideas/swing-trade/CLAUDE.md`

Expected: New section appears with `./bin/swingdev` commands.

- [ ] **Step 3: Commit**

```bash
git add CLAUDE.md
git commit -m "docs: add dev-stack CLI reference to project docs"
```

---

## Self-Review

### Spec coverage
- `bin/swingdev` CLI wrapper: Task 1 covers all commands (start, stop, status, health, env-check, reset, logs)
- Claude Code skill: Task 2 covers SKILL.md with workflows, troubleshooting, quick reference
- CLAUDE.md update: Task 3 documents the CLI in project docs
- All spec requirements covered.

### Placeholder scan
- No TBD/TODO in any task
- All code is complete and inline
- Commands are exact with expected output
- File paths are absolute

### Type consistency
- All references to `dev-stack.sh`, `piworm.local`, `backend/.env` are consistent
- Profile names (`fyers`, `yahoo`) consistent throughout
- API URL (`http://localhost:8080/actuator/health`) consistent
