# dev-stack-stage-restart

Stop and fully restart the stage stack on pi-node (full rebuild).

## Command

```bash
./dev-stack.sh stage-restart
```

## What It Does

1. Runs `stage-down`
2. Waits 3 seconds
3. Runs `stage` (full 11-step deploy)

## Use Case

After changing Dockerfile, docker-compose, or JAR dependencies — forces a complete rebuild.

## Related

- `stage` — Deploy without stopping (if infra already running)
- `stage-logs` — View logs after restart