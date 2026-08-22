# dev-stack-stop

Stop the dev stack: local processes + infra on pi-node.

## Command

```bash
./dev-stack.sh stop
```

## What It Does

1. Kills local Spring Boot and Vue dev server processes (via PID file + pkill)
2. Stops infra on pi-node (docker compose down)