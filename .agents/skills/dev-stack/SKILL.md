---
name: dev-stack
description: Start, inspect, and troubleshoot the Swing Trade local development stack. Use for local API, dashboard, database connectivity, or service health issues.
---

# Development stack operations

Act as a senior/staff engineer and architect responsible for safe diagnosis: establish which environment and service own the failure, gather evidence before changing runtime state, and verify the outcome at the relevant boundary.

Use the repository root entry point:

```bash
./dev-stack.sh status
./dev-stack.sh logs --tail=100
./dev-stack.sh start
./dev-stack.sh stop
```

Local dashboard is `http://localhost:3003`; API is `http://localhost:8080`; health is `http://localhost:8080/actuator/health`. The development database is on pi-node at `192.168.0.100:5435`, database `swingtrade_db`.

## Troubleshooting sequence

1. Inspect status and relevant logs before restarting anything.
2. Check the API health endpoint, active Spring profile, and configured database target.
3. For a suspected API issue, inspect the API logs and check for stale port-8080 processes before replacing a jar or restarting.
4. After a restart, verify health, database connectivity, and the affected endpoint or dashboard flow.

Use `./dev-stack.sh frontend ...` for dashboard-only operations. Stage is separate and must only be touched when explicitly requested; never use stage commands to diagnose local development. Do not truncate, drop, or otherwise clean database data during routine troubleshooting. Confirm the exact target and scope before destructive operations.
