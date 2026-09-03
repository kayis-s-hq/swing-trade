---
name: dev-stack
description: Start, stop, inspect, and troubleshoot the Swing Trade development and stage stacks.
---

# Development stack

Use the repository entry point from the repository root. `bin/swingdev` is a convenience wrapper; `dev-stack.sh` exposes the full command set.

## Development stack

```bash
./dev-stack.sh start
./dev-stack.sh status
./dev-stack.sh logs --tail=100
./dev-stack.sh logs-json 50
./dev-stack.sh stop
```

Services:

- Dashboard: `http://localhost:3003`
- API: `http://localhost:8080`
- Health: `http://localhost:8080/actuator/health`
- PostgreSQL: pi-node `192.168.0.100:5435`, database `swingtrade_db`

Run `./dev-stack.sh frontend start|stop|logs` for dashboard-only work, or `./dev-stack.sh infra up -d` to manage development infrastructure.

## Stage stack

Stage actions are explicit and destructive:

```bash
./dev-stack.sh stage
./dev-stack.sh stage-logs --tail=100
./dev-stack.sh stage-restart
./dev-stack.sh stage-down
```

Stage API is `http://piworm.local:8081`. Never use stage commands to troubleshoot local development.

## Troubleshooting

1. Run `./dev-stack.sh status` and check the API actuator health endpoint.
2. Check the API and frontend logs before restarting.
3. Check for a stale process on port 8080 before rebuilding or replacing the API jar.
4. Confirm the active Spring profile and database target from `infra/env/.env`.
5. After a restart, verify health, database connectivity, and the relevant API endpoint—not health alone.

Do not truncate or drop database data as part of ordinary troubleshooting. Confirm the exact database and requested scope first.
