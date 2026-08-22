# dev-stack-logs

View dev stack logs. Pass arguments to filter (e.g., `--tail=100`).

## Command

```bash
./dev-stack.sh logs
./dev-stack.sh logs --tail=100
```

## What It Shows

Infrastructure logs from pi-node (PostgreSQL + Redis docker compose).

## Related

- `logs-json` — View structured JSON logs from Spring Boot (requires jq)
- `frontend-logs` — View Vue dev server logs