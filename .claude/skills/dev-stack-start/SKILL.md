# dev-stack-start

Start the full dev stack: infra on pi-node + Spring Boot locally + Vue dev server locally.

## Command

```bash
./dev-stack.sh start
```

## What It Does

1. Starts PostgreSQL + Redis on pi-node via docker compose
2. Waits for infra to be healthy (15s)
3. Starts Spring Boot API on localhost:8080 (profile: local)
4. Starts Vue dev server on localhost:3003

## After Running

- API: http://localhost:8080
- Dashboard: http://localhost:3003
- Use `$0 logs` to view logs
- Use `$0 stop` to stop