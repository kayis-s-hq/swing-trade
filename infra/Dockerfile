# ==============================================================================
# SwingTrade API - Multi-Stage Dockerfile
# ==============================================================================
# Builds JAR images for production and development
#
# Build Process:
#   1. CI builds JAR: ./gradlew :api:bootJar -x test
#   2. Docker image built with pre-built JAR
#
# Usage:
#   # Stage deployment (production):
#   docker build --target runtime-stage -t swing-trade-api:stage .
#
#   # Development with hot reload:
#   docker build --target runtime-jar -t swing-trade-api-dev .
#   docker run -p 8081:8080 -p 5005:5005 -v $(pwd):/app swing-trade-api-dev
#
# CI/CD flow: JAR is built by Gradle on the runner, then copied to Docker context.
# ==============================================================================

# ------------------------------------------------------------------------------
# Stage 1: jar-build - Build JAR artifacts via Gradle
# ------------------------------------------------------------------------------
FROM eclipse-temurin:21-jdk-alpine AS jar-build

WORKDIR /app

# Copy Gradle wrapper and build files
COPY gradlew ./
COPY gradle/ ./gradle/
COPY build.gradle.kts ./
COPY settings.gradle.kts ./
COPY core/ ./core/
COPY data/ ./data/
COPY strategy/ ./strategy/
COPY llm/ ./llm/
COPY broker/ ./broker/
COPY api/ ./api/

# Set timezone to Asia/Kolkata for correct scheduling
RUN ln -sf /usr/share/zoneinfo/Asia/Kolkata /etc/localtime && \
    echo "Asia/Kolkata" > /etc/timezone

# Build JAR (skip tests)
RUN chmod +x gradlew && \
    ./gradlew :api:bootJar -x test -B

# ------------------------------------------------------------------------------
# Stage 2: runtime-jar - JAR development image
# ------------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runtime-jar

WORKDIR /app

# Copy JAR from build stage
COPY --from=jar-build /app/api/build/libs/api-*.jar /app/swing-trade-api.jar

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
#
# Run with volume mount for hot reload:
#   docker run -p 8081:8080 -p 5005:5005 -v $(pwd):/app swing-trade-api-dev
CMD ["java", \
     "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005", \
     "-jar", "/app/swing-trade-api.jar"]

# ------------------------------------------------------------------------------
# Stage 3: runtime-stage - Stage deployment image (clean, no debug)
# ------------------------------------------------------------------------------
# For stage deployment on pi-node. Built locally, transferred via docker save/load.
# Uses pre-built JAR — no build tools inside Docker.
#
# Usage:
#   docker build --target runtime-stage -t swing-trade-api:stage .
# ==============================================================================
FROM eclipse-temurin:21-jre-alpine AS runtime-stage

WORKDIR /app

# Copy JAR from build stage
COPY --from=jar-build /app/api/build/libs/api-*.jar /app/swing-trade-api.jar

# Expose API port
EXPOSE 8080

# Health check using Spring Actuator
HEALTHCHECK --interval=30s --timeout=10s --start-period=120s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# Run the JAR — no debug flags, no devtools
CMD ["java", "-jar", "/app/swing-trade-api.jar"]
