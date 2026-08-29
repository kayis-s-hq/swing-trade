---
name: health-check
description: Run post-change health checks and focused local-stack smoke tests.
---

# Health checker

Read `AGENTS.md`. Check stack status, `/actuator/health`, frontend availability, database connectivity, and the endpoints affected by the change. Inspect logs when a check fails. A healthy actuator endpoint alone is insufficient; verify the actual feature path too. Do not mutate production or stage data.
