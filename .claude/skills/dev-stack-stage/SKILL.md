# dev-stack-stage

Deploy the full stage stack to pi-node: build JAR locally, transfer, build Docker images, start containers.

## Command

```bash
./dev-stack.sh stage
```

## What It Does (11 steps)

1. Switches to Java 21 via sdkman
2. Builds JAR locally (`./gradlew :api:bootJar -x test`)
3. Transfers JAR + Dockerfile + compose to pi-node
4. Transfers frontend dist
5. Transfers .env.stage
6. Stops existing stage deployment
7. Builds Docker images on pi-node (runtime-stage + nginx)
8. Starts stage stack on pi-node
9. Waits for PostgreSQL + Redis health
10. Waits for Spring Boot startup (~30s)
11. Health check + connects pi-prometheus to stage network

## After Running

- API: http://piworm.local:8081
- Prometheus: http://piworm.local:9090
- Grafana: http://piworm.local:3001

## Related

- `stage-down` — Stop stage stack
- `stage-restart` — Full rebuild + restart
- `stage-logs` — View stage API logs