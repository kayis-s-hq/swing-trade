# Phase 10: Dockerize and Use GraalVM Spring Boot Native - Context

**Gathered:** 2026-03-23
**Status:** Ready for planning

---

## Phase Boundary

Containerize the swing-trade system with Docker and create GraalVM native executable for improved startup time and reduced memory footprint.

**Requirements:**
- REQ-201: Dockerfile with multi-stage build
- REQ-202: GraalVM native compilation configuration
- REQ-203: docker-compose.yml for all services
- REQ-204: Native image build optimization
- REQ-205: Container health checks and monitoring

---

## Implementation Decisions

### Docker Image Strategy
- **D-01:** Use multi-stage Dockerfile with GraalVM native executable for production
- **D-02:** Build both JAR (for development) and native (for production)
- **Rationale:** Native provides fast startup (<2s) and low memory footprint (~50MB); JAR allows faster local iteration

### GraalVM Configuration
- **D-03:** Use standard Spring Boot native compilation with auto-configuration
- **D-04:** Avoid custom native-image configuration unless required by dependencies
- **Rationale:** Spring Boot 3.x has excellent native support; minimize complexity

### Docker Compose Structure
- **D-05:** Update existing docker-compose.yml to add swing-trade-api service
- **D-06:** Keep single source of truth (postgres, redis, api on same network)
- **Rationale:** Simpler maintenance, single configuration file

### Health Checks and Monitoring
- **D-07:** Use Spring Actuator health endpoints only
- **D-08:** No custom health checks or resource limits for now
- **Rationale:** Actuator provides sufficient coverage; keep implementation simple

### Build Optimization
- **D-09:** Build native image in CI/CD pipeline only
- **D-10:** Distribute pre-built native binaries for local development
- **Rationale:** Native builds are slow (5-10 min); build once and reuse

---

## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase Documentation
- `.planning/ROADMAP.md` — Phase 10 requirements and success criteria
- `.planning/STATE.md` — Current project state and milestone tracking

### Existing Infrastructure
- `docker-compose.yml` — Current docker-compose configuration (postgres, redis)
- `pom.xml` — Maven multi-module build configuration
- `api/pom.xml` — API module dependencies and Spring Boot configuration

### Spring Boot Native Documentation
- [Spring Boot 3.x Native Documentation](https://docs.spring.io/spring-boot/3.3.x/native-builds/reference/htmlsingle/)
- [GraalVM Native Image with Spring Boot](https://docs.spring.io/spring-boot/docs/current/reference/htmlsingle/#aot-native)

### LangChain4j Native Support
- [LangChain4j Native Image Support](https://docs.langchain4j.dev/tutorials/deployment/native-image)

---

## Existing Code Insights

### Reusable Assets
- **Spring Boot Actuator** — Already configured in api module for health monitoring
- **docker-compose.yml** — Existing postgres and redis services to extend
- **Maven multi-module build** — Existing structure to leverage for native build

### Established Patterns
- **Multi-module Maven** — core, data, strategy, llm, broker, api modules
- **Spring Boot 3.3.1** — Current version with native build support
- **TimescaleDB + Redis** — Existing database and caching infrastructure

### Integration Points
- **docker-compose.yml** — Add swing-trade-api service to existing network
- **pom.xml** — Add native-maven-plugin for GraalVM compilation
- **application.properties** — May need profile-specific configuration for native

---

## Specific Ideas

- Target native image startup: < 2 seconds
- Target native image memory footprint: < 100MB
- Keep docker-compose.yml compatible with existing setup

---

## Deferred Ideas

None — discussion stayed within phase scope.

---

*Phase: 10-graalvm-native-docker*
*Context gathered: 2026-03-23*
