# ==============================================================================
# SwingTrade API - Multi-Stage Dockerfile
# ==============================================================================
# Builds JAR images for stage/production (self-contained in Docker)
# and GraalVM native images for development.
#
# Build Process (stage/production):
#   1. Maven downloads dependencies and builds JAR inside Docker (jar-build stage)
#   2. JAR packaged into Alpine JRE image (runtime-stage target)
#
# Usage:
#   # Stage/production (JAR, self-contained):
#   docker build --target runtime-stage -t swing-trade-api:stage .
#
#   # JAR (development with hot reload):
#   docker build --target runtime-jar -t swing-trade-api-dev .
#   docker run -p 8081:8081 -p 5005:5005 -v $(pwd):/app swing-trade-api-dev
#
#   # IDE Remote Debugging:
#   # 1. Start container with debug port exposed: -p 5005:5005
#   # 2. Configure IDE to connect to localhost:5005
#   # 3. JVM will listen for debug connections without suspending startup
#
# CI/CD flow: JAR is built inside Docker during deploy (no native binary needed).
# ==============================================================================

# ------------------------------------------------------------------------------
# Stage 1: jar-build - Build JAR artifacts only
# ------------------------------------------------------------------------------
FROM maven:3.9.9-eclipse-temurin-21 AS jar-build

WORKDIR /app

# Copy all pom.xml files
COPY pom.xml ./
COPY core/pom.xml ./core/
COPY data/pom.xml ./data/
COPY strategy/pom.xml ./strategy/
COPY llm/pom.xml ./llm/
COPY broker/pom.xml ./broker/
COPY api/pom.xml ./api/

# Copy source code
COPY core/src ./core/src
COPY data/src ./data/src
COPY strategy/src ./strategy/src
COPY llm/src ./llm/src
COPY broker/src ./broker/src
COPY api/src ./api/src

# Copy parent config (PMD ruleset)
COPY config ./config

# Set timezone to Asia/Kolkata for correct scheduling
RUN ln -sf /usr/share/zoneinfo/Asia/Kolkata /etc/localtime && \
    echo "Asia/Kolkata" > /etc/timezone

# Download dependencies and build JAR (skip tests)
RUN mvn dependency:go-offline -B -N && \
    mvn clean package -Dmaven.test.skip=true -B

# ------------------------------------------------------------------------------
# Stage 2: runtime-stage - JAR production image (self-contained)
# ------------------------------------------------------------------------------
# Builds the JAR inside Docker — no GraalVM or native binary needed.
# Uses Alpine JRE with curl for healthchecks.
#
# Usage:
#   docker build --target runtime-stage -t swing-trade-api:stage .
FROM eclipse-temurin:21-jre-alpine AS runtime-stage

WORKDIR /app

# Copy JAR from build stage
COPY --from=jar-build /app/api/target/api-1.0.0.jar /app/swing-trade-api.jar

# Set timezone to Asia/Kolkata
RUN ln -sf /usr/share/zoneinfo/Asia/Kolkata /etc/localtime && \
    echo "Asia/Kolkata" > /etc/timezone

# Expose API port
EXPOSE 8080

# Health check using Spring Actuator
HEALTHCHECK --interval=30s --timeout=10s --start-period=120s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# Run the JAR
CMD ["java", "-jar", "/app/swing-trade-api.jar"]

# ------------------------------------------------------------------------------
# Stage 3: runtime-native - GraalVM native production image
# ------------------------------------------------------------------------------
# The native binary is built on the runner via: mvn package -Pnative
# Then copied to the Docker context (backend/) before docker build.
#
# Usage:
#   docker build --target runtime-native -t swing-trade-api:stage .
FROM gcr.io/distroless/base-debian12 AS runtime-native

WORKDIR /app

# Copy native executable (built on CI runner via mvn package -Pnative, then copied to Docker context)
COPY swing-trade-api /app/swing-trade-api

# Expose API port
EXPOSE 8080

# Health check using Spring Actuator
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Run the native executable
CMD ["/app/swing-trade-api"]

# ------------------------------------------------------------------------------
# Stage 4: runtime-jar - JAR development image
# ------------------------------------------------------------------------------
# For development with hot reload, mount source code as volume:
#   docker run -v $(pwd):/app swing-trade-api-dev
# Ensure source files have correct permissions
# Spring Boot DevTools will detect changes and auto-restart
FROM eclipse-temurin:21-jre-alpine AS runtime-jar

WORKDIR /app

# Copy JAR from build stage
COPY --from=jar-build /app/api/target/api-1.0.0.jar /app/swing-trade-api.jar

# Expose API port
EXPOSE 8080

# Expose debug port for remote IDE debugging (JDWP)
EXPOSE 5005

# Health check using Spring Actuator (longer start period for JAR)
HEALTHCHECK --interval=30s --timeout=10s --start-period=120s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# Run the JAR with development-specific JVM arguments:
#   -agentlib:jdwp: Enable Java Debug Wire Protocol for remote debugging
#     transport=dt_socket,server=y,suspend=n,address=5005
#     - Socket-based debug server, don't suspend on startup, listen on port 5005
#   -Dspring.devtools.*: Spring Boot DevTools properties for hot reload
#     (requires devtools dependency in api/pom.xml)
#
# Run with volume mount for hot reload:
#   docker run -p 8081:8080 -p 5005:5005 -v $(pwd):/app swing-trade-api-dev
CMD ["java", \
     "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005", \
     "-Dspring.devtools.add-properties=true", \
     "-Dspring.devtools.restart.enabled=true", \
     "-Dspring.devtools.remote.secret=dev-secret", \
     "-jar", "/app/swing-trade-api.jar"]
