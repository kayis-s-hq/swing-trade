# ==============================================================================
# SwingTrade API - Multi-Stage Dockerfile
# ==============================================================================
# For Gradle 9.6.1 + Spring Boot 3.3.1: bootJar is incompatible.
# Uses plain jar + lib/ with shell classpath script.
# ==============================================================================

# ------------------------------------------------------------------------------
# Stage 1: jar-build - Build JAR artifacts via Gradle
# ------------------------------------------------------------------------------
FROM eclipse-temurin:21-jdk-alpine AS jar-build

WORKDIR /app

# Copy Gradle wrapper and build files
COPY backend/gradlew ./
COPY backend/gradle/ ./gradle/
COPY backend/build.gradle.kts ./
COPY backend/settings.gradle.kts ./
COPY backend/core/ ./core/
COPY backend/data/ ./data/
COPY backend/strategy/ ./strategy/
COPY backend/llm/ ./llm/
COPY backend/broker/ ./broker/
COPY backend/api/ ./api/
COPY libs/ ./libs/

# Set timezone to Asia/Kolkata for correct scheduling
RUN ln -sf /usr/share/zoneinfo/Asia/Kolkata /etc/localtime && \
    echo "Asia/Kolkata" > /etc/timezone

# Build JAR + copy dependencies (skip tests)
RUN chmod +x gradlew && \
    ./gradlew :api:jar -x test --quiet && \
    mkdir -p /app/lib && \
    cp api/build/libs/api-plain.jar /app/lib/ && \
    ./gradlew -q --no-daemon :api:copyDeps --quiet 2>/dev/null || \
    ./gradlew -q --no-daemon :api:copyRuntimeDeps --quiet 2>/dev/null || \
    true && \
    echo "Lib count: $(ls /app/lib/*.jar 2>/dev/null | wc -l)"

# ------------------------------------------------------------------------------
# Stage 2: runtime-stage - Stage deployment image
# ------------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runtime-stage

WORKDIR /app

# Copy app JAR and dependencies from build stage
COPY --from=jar-build /app/lib/ ./lib/

# Expose API port
EXPOSE 8080

# Health check using Spring Actuator
HEALTHCHECK --interval=30s --timeout=10s --start-period=120s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health || exit 1

# Startup script builds classpath from all JARs in lib/
RUN printf '#!/bin/sh\n' > /app/start.sh && \
    printf 'CP=""\n' >> /app/start.sh && \
    printf 'for f in /app/lib/*.jar; do\n' >> /app/start.sh && \
    printf '  CP="${CP:+$CP:}$f"\n' >> /app/start.sh && \
    printf 'done\n' >> /app/start.sh && \
    printf 'exec java -cp "$CP" -Duser.timezone=Asia/Kolkata -Dspring.profiles.active=${SPRING_PROFILES_ACTIVE:-stage} -Dserver.port=${SERVER_PORT:-8080} com.swingtrade.api.app.SwingTradeApiApplication\n' && \
    chmod +x /app/start.sh

CMD ["/app/start.sh"]
