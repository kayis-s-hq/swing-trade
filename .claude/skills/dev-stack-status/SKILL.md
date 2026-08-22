# dev-stack-status

Check status of all dev stack services: infra, Spring Boot, Vue, API health.

## Command

```bash
./dev-stack.sh status
```

## What It Shows

1. Infrastructure containers on pi-node (docker compose ps)
2. Local Spring Boot process status
3. Local Vue dev server process status
4. API health endpoint response (curl /actuator/health)