# ==============================================================================
# SwingTrade API - Multi-Stage Dockerfile
# ==============================================================================
# Builds both GraalVM native (production) and JAR (development) images
#
# Usage:
#   Native (production): docker build --target runtime-native -t swing-trade-api:latest .
#   JAR (development):   docker build --target runtime-jar -t swing-trade-api:dev .
# ==============================================================================

# ------------------------------------------------------------------------------
# Stage 1: dependency-cache - Cache Maven dependencies
# ------------------------------------------------------------------------------
FROM maven:3.9.9-eclipse-temurin-21 AS dependency-cache

WORKDIR /app

# Copy only pom.xml files to cache dependencies
COPY pom.xml ./
COPY core/pom.xml ./core/
COPY data/pom.xml ./data/
COPY strategy/pom.xml ./strategy/
COPY llm/pom.xml ./llm/
COPY broker/pom.xml ./broker/
COPY api/pom.xml ./api/

# Download dependencies (cached layer)
RUN mvn dependency:go-offline -B -N && \
    mvn dependency:resolve -B

# ------------------------------------------------------------------------------
# Stage 2: source-build - Build JAR and native artifacts
# ------------------------------------------------------------------------------
FROM maven:3.9.9-eclipse-temurin-21 AS source-build

WORKDIR /app

# Copy cached dependencies
COPY --from=dependency-cache /root/.m2 /root/.m2

# Copy source code
COPY pom.xml ./
COPY core/src ./core/src
COPY core/pom.xml ./core/
COPY data/src ./data/src
COPY data/pom.xml ./data/
COPY strategy/src ./strategy/src
COPY strategy/pom.xml ./strategy/
COPY llm/src ./llm/src
COPY llm/pom.xml ./llm/
COPY broker/src ./broker/src
COPY broker/pom.xml ./broker/
COPY api/src ./api/src
COPY api/pom.xml ./api/

# Set timezone to Asia/Kolkata for correct scheduling of data ingestion and signals
RUN ln -sf /usr/share/zoneinfo/Asia/Kolkata /etc/localtime && \
    echo "Asia/Kolkata" > /etc/timezone

# Build JAR (development)
RUN mvn clean package -DskipTests -B && \
    echo "JAR build completed"

# Build native executable (production)
RUN mvn clean package -Pnative -B && \
    echo "Native build completed"

# ------------------------------------------------------------------------------
# Stage 3: runtime-native - GraalVM native production image
# ------------------------------------------------------------------------------
FROM gcr.io/distroless/base-debian12 AS runtime-native

WORKDIR /app

# Create non-root user for security
RUN adduser --disabled-password --gecos '' --uid 1000 appuser

# Copy native executable from build stage
COPY --from=source-build /app/api/target/swing-trade-api /app/swing-trade-api

# Set ownership
RUN chown -R appuser:appuser /app

# Switch to non-root user
USER appuser

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
FROM eclipse-temurin:21-jre-alpine AS runtime-jar

WORKDIR /app

# Create non-root user for security
RUN adduser -D -u 1000 appuser

# Copy JAR from build stage
COPY --from=source-build /app/api/target/api-1.0.0.jar /app/swing-trade-api.jar

# Set ownership
RUN chown -R appuser:appuser /app

# Switch to non-root user
USER appuser

# Expose API port
EXPOSE 8080

# Health check using Spring Actuator (longer start period for JAR)
HEALTHCHECK --interval=30s --timeout=10s --start-period=120s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# Run the JAR
CMD ["java", "-jar", "/app/swing-trade-api.jar"]
