# ==============================================================================
# SwingTrade API - Multi-Stage Dockerfile
# ==============================================================================
# Builds both GraalVM native (production) and JAR (development) images
#
# Build Process:
#   1. Build native executable locally: mvn clean package -Pnative -Dmaven.test.skip=true
#   2. Copy native binary to Docker context
#   3. Build Docker image with pre-built binary
#
# Usage:
#   # Native (production):
#   docker build --target runtime-native -t swing-trade-api:latest .
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
# Alternative (build native in CI/CD): Use GitHub Actions with graalvm/setup-graalvm
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

# Set timezone to Asia/Kolkata for correct scheduling
RUN ln -sf /usr/share/zoneinfo/Asia/Kolkata /etc/localtime && \
    echo "Asia/Kolkata" > /etc/timezone

# Download dependencies and build JAR (skip tests)
RUN mvn dependency:go-offline -B -N && \
    mvn clean package -Dmaven.test.skip=true -B

# ------------------------------------------------------------------------------
# Stage 2: native-build - Prepare context for native binary
# ------------------------------------------------------------------------------
# NOTE: Native builds require GraalVM Java 21 with native-image tool
# GraalVM Docker images (graalvm/graalvm-ce-java21) require authorization
#
# RECOMMENDED APPROACH: Build native locally on developer machine:
#   cd api
#   mvn clean package -Pnative -Dmaven.test.skip=true -B
#   cp target/swing-trade-api ../.docker/
#
# Then build Docker image with the pre-built binary:
#   docker build --target runtime-native -t swing-trade-api:latest .
#
# ALTERNATIVE (CI/CD): Use GitHub Actions workflow to build native in container:
#   - Use graalvm/setup-graalvm@v1 action
#   - Run mvn package -Pnative
#   - Build Docker image and push to registry
# ==============================================================================
FROM alpine:latest AS native-build

WORKDIR /app

# This stage is a placeholder - the native binary should be copied here
# BEFORE running docker build (from local native build)
#
# To use: After building native locally, copy binary to .docker/ directory:
#   cp api/target/swing-trade-api .docker/
# Then use multi-stage build with: COPY --from=host .docker/swing-trade-api /app/
# ==============================================================================

# ------------------------------------------------------------------------------
# Stage 3: runtime-native - GraalVM native production image
# ------------------------------------------------------------------------------
FROM gcr.io/distroless/base-debian12 AS runtime-native

WORKDIR /app

# Copy native executable (pre-built locally with GraalVM)
# IMPORTANT: Build native first with: mvn clean package -Pnative -Dmaven.test.skip=true
COPY swing-trade-api /app/swing-trade-api

# Expose API port
EXPOSE 8080

# Health check using Spring Actuator
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

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
