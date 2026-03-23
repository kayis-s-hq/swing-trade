# Phase 10: Dockerize and Use GraalVM Spring Boot Native - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-03-23
**Phase:** 10-graalvm-native-docker
**Areas discussed:** Docker image strategy, GraalVM configuration, docker-compose structure, health checks, build optimization

---

## Docker image strategy

| Option | Description | Selected |
|--------|-------------|----------|
| Multi-stage Dockerfile with JAR | Simpler build, slower startup (~10s), ~200MB RAM | |
| Multi-stage Dockerfile with GraalVM native | Complex build, fast startup (<2s), ~50MB RAM | ✓ |
| Both options | Build both, use JAR for dev, native for prod | ✓ |

**User's choice:** Option 2 (GraalVM native) + Option 3 (both JAR and native)

**Notes:** User selected GraalVM native for production benefits while keeping JAR for development flexibility.

---

## GraalVM configuration approach

| Option | Description | Selected |
|--------|-------------|----------|
| Standard Spring Boot native compilation | Rely on auto-configuration, minimal setup | ✓ |
| Custom native-image configuration | Add reflection config, dynamic proxies, resource processing | |
| Hybrid approach | Start standard, add custom config only for failing components | |

**User's choice:** Option 1 (Standard Spring Boot native compilation)

**Notes:** User prefers minimal complexity; will add custom config only if required.

---

## docker-compose.yml updates

| Option | Description | Selected |
|--------|-------------|----------|
| Update existing docker-compose.yml | Add swing-trade-api service to current file | ✓ |
| Separate files | docker-compose.yml for dev, docker-compose.prod.yml for production | |
| Multiple compose files | docker-compose.base.yml, docker-compose.dev.yml, docker-compose.prod.yml | |

**User's choice:** Option 1 (Update existing docker-compose.yml)

**Notes:** Single source of truth preferred for simplicity.

---

## Health checks and monitoring

| Option | Description | Selected |
|--------|-------------|----------|
| Spring Actuator health endpoints only | Simple, built-in | ✓ |
| Actuator + custom checks | Database connectivity, external API health | |
| Actuator + resource limits | Health checks with CPU/memory constraints | |
| Actuator + monitoring stack | Integrate with Prometheus/Grafana | |

**User's choice:** Option 1 (Spring Actuator health endpoints only)

**Notes:** Keep implementation simple; Actuator provides sufficient coverage.

---

## Build optimization

| Option | Description | Selected |
|--------|-------------|----------|
| Build native in CI/CD pipeline only | Faster local dev, reproducible builds | ✓ |
| Build native locally for development too | Iterate faster on native issues | |
| Hybrid | Build native in CI/CD, provide pre-built binaries for local use | |

**User's choice:** Option 1 (Build native in CI/CD pipeline only)

**Notes:** Native builds are slow (5-10 min); build once and distribute.

---

## Claude's Discretion

No areas deferred to Claude — all decisions captured above.

---

## Deferred Ideas

None — discussion stayed within phase scope.
