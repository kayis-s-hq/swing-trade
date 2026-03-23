---
phase: 10-graalvm-native-docker
plan: 01
type: execute
wave: 1
depends_on: []
files_modified: [Dockerfile]
autonomous: true
requirements: [REQ-201, REQ-204, REQ-205]

must_haves:
  truths:
    - "Multi-stage Dockerfile builds both JAR and native images"
    - "Native image startup completes in < 2 seconds"
    - "Native image memory footprint stays < 100MB"
    - "Container health checks configured via Spring Actuator"
  artifacts:
    - path: "Dockerfile"
      provides: "Multi-stage Docker build with JAR and native targets"
      min_lines: 100
  key_links:
    - from: "Dockerfile"
      to: "api/pom.xml"
      via: "Maven build command in build stage"
      pattern: "mvn.*package.*-Pnative"
---

<objective>
Create multi-stage Dockerfile for both JAR and GraalVM native builds

Purpose: Containerize the swing-trade API with both development (JAR) and production (native) build targets. Native builds provide fast startup (<2s) and low memory footprint (~50-100MB) for production, while JAR builds enable faster local iteration.

Output: Multi-stage Dockerfile with two runtime targets (runtime-native and runtime-jar)
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/ROADMAP.md
@.planning/phases/10-graalvm-native-docker/10-graalvm-native-docker-CONTEXT.md
@.planning/phases/10-graalvm-native-docker/10-graalvm-native-docker-RESEARCH.md
@/Users/kayisrahman/Documents/workspace/ideas/swing-trade/docker-compose.yml
@/Users/kayisrahman/Documents/workspace/ideas/swing-trade/api/pom.xml
@/Users/kayisrahman/Documents/workspace/ideas/swing-trade/pom.xml
@/Users/kayisrahman/Documents/workspace/ideas/swing-trade/api/src/main/java/com/swingtrade/api/SwingTradeApiApplication.java

<!-- Key types and contracts for Docker build context -->
<interfaces>
From pom.xml:
```xml
<properties>
    <java.version>21</java.version>
    <spring-boot.version>3.3.1</spring-boot.version>
</properties>
```

From api/pom.xml:
- Artifact ID: api
- Packaging: jar
- Spring Boot parent with maven-plugin for repackaging

Key files in multi-module structure:
- api/pom.xml - API module with Spring Boot application
- pom.xml - Parent POM with module definitions
</interfaces>
</context>

<tasks>

<task type="auto">
  <name>Task 1: Create multi-stage Dockerfile with JAR and native build targets</name>
  <files>Dockerfile</files>
  <action>
Create a multi-stage Dockerfile with 4 build stages:

**Stage 1 - dependency-cache:** Cache Maven dependencies from parent pom.xml and all module pom.xml files

**Stage 2 - source-build:** Build both JAR and native artifacts
- Copy cached dependencies and source code
- Build JAR: `mvn clean package -DskipTests -B`
- Build native: `mvn clean package -Pnative -B` (in parallel or sequentially)

**Stage 3 - runtime-native:** Distroless minimal runtime
- Copy native executable from build stage
- Non-root user (UID 1000)
- Expose port 8080
- Health check: `curl -f http://localhost:8080/actuator/health`
- CMD: `/app/swing-trade-api`

**Stage 4 - runtime-jar:** Eclipse Temurin JRE Alpine runtime
- Copy JAR from build stage
- Expose port 8080
- Health check: `curl -f http://localhost:8080/actuator/health`
- CMD: `java -jar /app/swing-trade-api.jar`

Reference: 10-graalvm-native-docker-RESEARCH.md Section 3 (Multi-Platform Dockerfile)
</action>
  <verify>
  <automated>docker build --target runtime-native -t swing-trade-api:test --dry-run 2>&1 | grep -q "successfully"</automated>
</verify>
  <done>
  Dockerfile exists with 4 build stages (dependency-cache, source-build, runtime-native, runtime-jar). Native build uses GraalVM, JAR build uses Maven. Both runtime stages have health checks configured.
  </done>
</task>

<task type="auto">
  <name>Task 2: Configure Docker health checks for Spring Actuator endpoints</name>
  <files>Dockerfile</files>
  <action>
Add comprehensive health checks to both runtime stages:

**Health Check Configuration (per RESEARCH.md Section 5):**
```dockerfile
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1
```

**Notes:**
- Start-period of 60s for native (fast startup), 120s for JAR
- Interval: 30 seconds between checks
- Timeout: 10 seconds per check
- Retries: 3 before marking unhealthy
- Endpoint: /actuator/health (Spring Actuator)

Reference: User decision D-07 (Spring Actuator health endpoints only), RESEARCH.md Section 5
</action>
  <verify>
  <automated>grep -q "HEALTHCHECK.*actuator/health" Dockerfile</automated>
</verify>
  <done>
  Both runtime-native and runtime-jar stages have HEALTHCHECK directives pointing to /actuator/health endpoint with appropriate timing parameters.
  </done>
</task>

<task type="auto">
  <name>Task 3: Add timezone configuration for Asia/Kolkata</name>
  <files>Dockerfile</files>
  <action>
Add timezone configuration to source-build stage:

```dockerfile
# Set timezone to Asia/Kolkata for correct scheduling
RUN ln -sf /usr/share/zoneinfo/Asia/Kolkata /etc/localtime \
    && echo "Asia/Kolkata" > /etc/timezone
```

Reference: RESEARCH.md Section 8.5 (Timezones challenge), User decision D-09 (CI/CD only native builds)
</action>
  <verify>
  <automated>grep -q "Asia/Kolkata" Dockerfile</automated>
</verify>
  <done>
  Dockerfile includes timezone configuration for Asia/Kolkata in source-build stage to ensure scheduled jobs (16:30 data ingestion, 17:00 signal generation) run at correct times.
  </done>
</task>

</tasks>

<verification>
- Multi-stage Dockerfile exists with 4 build stages
- Native build uses GraalVM Java 21
- JAR build uses Maven with Eclipse Temurin
- Both runtime stages have health checks
- Timezone configured for Asia/Kolkata
- Build commands verify: `docker build --target runtime-native -t swing-trade-api:test .`
</verification>

<success_criteria>
- [ ] Dockerfile created with multi-stage build (4 stages)
- [ ] Native target builds with GraalVM Java 21
- [ ] JAR target builds with standard Maven
- [ ] Health checks configured for /actuator/health
- [ ] Timezone set to Asia/Kolkata
- [ ] docker build --target runtime-native succeeds
- [ ] docker build --target runtime-jar succeeds
</success_criteria>

<output>
After completion, verify with: `docker build --target runtime-native -t swing-trade-api:test -f Dockerfile .`
</output>
