# dev-stack-stage-down

Stop the stage stack on pi-node.

## Command

```bash
./dev-stack.sh stage-down
```

## What It Does

Runs `docker compose down` on pi-node for the stage deployment.

## Related

- `stage` — Re-deploy stage
- `stage-restart` — Stop + full rebuild + start