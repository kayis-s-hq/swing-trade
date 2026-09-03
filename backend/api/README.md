# API module

The API module exposes the Spring Boot REST API and orchestration services. It owns HTTP controllers, API DTOs, scheduled jobs, and application-level coordination.

Run from `backend/`:

```bash
./gradlew :api:test
./gradlew :api:bootJar
```

For local development, use the repository stack command from the root:

```bash
./dev-stack.sh start
curl -sS http://localhost:8080/actuator/health
```

Keep the endpoint inventory in the controller code and root `README.md`; avoid duplicating a stale endpoint table here.
