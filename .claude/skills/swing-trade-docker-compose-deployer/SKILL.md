---
name: swing-trade-docker-compose-deployer
description: Deploy swing-trade system with Docker Compose including health checks and monitoring. Use this skill when deploying infrastructure changes, setting up new environments, scaling services, or when database/cache configuration changes. This skill is essential for infrastructure deployment and should be triggered proactively when deploying to staging/production or when infrastructure configuration changes.
---

# SwingTrade Docker Compose Deployer Skill

## Overview

This skill automates Docker Compose deployment of the swing-trade infrastructure including PostgreSQL/TimescaleDB, Redis, and application services with proper health checks and monitoring.

## When to Use This Skill

Trigger this skill when:
- Deploying infrastructure changes
- Setting up new environments (dev/staging/prod)
- Scaling services
- Database/cache configuration changes
- Before production deployment
- After docker-compose.yml modifications
- When troubleshooting infrastructure issues

## Infrastructure Components

### Docker Compose Services

```yaml
version: '3.8'

services:
  postgres:
    image: timescale/timescaledb:latest
    container_name: swing-trade-postgres
    ports:
      - "5432:5432"
    environment:
      POSTGRES_DB: swingtrade_db
      POSTGRES_USER: swingtrade_user
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U swingtrade_user"]
      interval: 10s
      timeout: 5s
      retries: 5

  redis:
    image: redis:alpine
    container_name: swing-trade-redis
    ports:
      - "6379:6379"
    volumes:
      - redis_data:/data
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5

  api:
    build: ./api
    container_name: swing-trade-api
    ports:
      - "8080:8080"
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/swingtrade_db
      SPRING_REDIS_HOST: redis
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/api/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3

  broker:
    build: ./broker
    container_name: swing-trade-broker
    ports:
      - "8081:8081"
    depends_on:
      - api
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/swingtrade_db
```

## Deployment Workflow

```
1. Validate configuration
   ↓
2. Check existing containers
   ↓
3. Pull latest images
   ↓
4. Run database migrations
   ↓
5. Start services in order
   ↓
6. Wait for health checks
   ↓
7. Verify connectivity
   ↓
8. Generate deployment report
```

## Deployment Scripts

### Pre-Deployment Checks

```bash
#!/bin/bash
# pre-deploy-checks.sh

echo "Checking Docker version..."
docker --version

echo "Checking Docker Compose..."
docker-compose --version

echo "Checking existing containers..."
docker-compose ps

echo "Checking port availability..."
netstat -tuln | grep -E "5432|6379|8080|8081"

echo "Validating environment variables..."
test -f .env || echo "WARNING: .env file not found"

echo "Pre-deployment checks complete"
```

### Deployment Script

```bash
#!/bin/bash
# deploy.sh

set -e

echo "Starting deployment..."

# Pull latest images
docker-compose pull

# Run database migrations
docker-compose exec postgres flyway migrate

# Start services
docker-compose up -d

# Wait for health checks
echo "Waiting for services to be healthy..."
docker-compose wait

# Verify connectivity
echo "Verifying service connectivity..."
docker-compose exec postgres pg_isready -U swingtrade_user
docker-compose exec redis redis-cli ping
curl -f http://localhost:8080/api/actuator/health

echo "Deployment complete!"
```

### Rollback Script

```bash
#!/bin/bash
# rollback.sh

set -e

echo "Starting rollback..."

# Stop services
docker-compose down

# Remove volumes (optional - comment out to keep data)
# docker-compose down -v

# Pull previous version
docker-compose pull [previous-tag]

# Start previous version
docker-compose up -d

echo "Rollback complete!"
```

## Environment Configuration

### Development Environment

```bash
# .env.development
DB_PASSWORD=dev_password
REDIS_PASSWORD=dev_redis_password
SPRING_PROFILES_ACTIVE=dev
LOGGING_LEVEL=com.swingtrade=DEBUG
```

### Staging Environment

```bash
# .env.staging
DB_PASSWORD=staging_password
REDIS_PASSWORD=staging_redis_password
SPRING_PROFILES_ACTIVE=staging
LOGGING_LEVEL=com.swingtrade=INFO
```

### Production Environment

```bash
# .env.production
DB_PASSWORD=${DB_PASSWORD_SECRET}
REDIS_PASSWORD=${REDIS_PASSWORD_SECRET}
SPRING_PROFILES_ACTIVE=prod
LOGGING_LEVEL=com.swingtrade=WARN
```

## Deployment Report Format

```
# Docker Compose Deployment Report - [Timestamp]

## Deployment Summary
- Environment: [dev/staging/prod]
- Deployment Time: [X] minutes
- Status: [SUCCESS/FAILED]

## Services Deployed
| Service | Status | Port | Health | Uptime |
|---------|--------|------|--------|--------|
| postgres | RUNNING | 5432 | HEALTHY | 2h |
| redis | RUNNING | 6379 | HEALTHY | 2h |
| api | RUNNING | 8080 | HEALTHY | 1h |
| broker | RUNNING | 8081 | HEALTHY | 1h |

## Database Status
- Database: swingtrade_db
- Tables: [X]
- Hypertables: [X]
- Continuous Aggregates: [X]

## Cache Status
- Redis Memory: [X] MB
- Cache Hit Rate: [X]%
- Connected Clients: [X]

## Health Check Results
| Service | Last Check | Status | Response Time |
|---------|------------|--------|---------------|
| postgres | 1m ago | UP | 15ms |
| redis | 30s ago | UP | 5ms |
| api | 2m ago | UP | 45ms |

## Logs Summary
- API Errors: [X]
- Database Warnings: [X]
- Redis Issues: [X]

## Recommendations
1. [Monitoring adjustments]
2. [Resource scaling]
3. [Configuration changes]
```

## Example Usage

```bash
# Full deployment
/skill: swing-trade-docker-compose-deployer

# Deploy to specific environment
/skill: swing-trade-docker-compose-deployer --env staging

# Deploy with rebuild
/skill: swing-trade-docker-compose-deployer --rebuild

# Deploy with migrations
/skill: swing-trade-docker-compose-deployer --migrate

# Rollback to previous version
/skill: swing-trade-docker-compose-deployer --rollback

# Generate deployment report
/skill: swing-trade-docker-compose-deployer --report

# Check deployment status
/skill: swing-trade-docker-compose-deployer --status
```

## Dependencies

- Docker 20+
- Docker Compose 2+
- Network access for pulling images
- Port availability (5432, 6379, 8080, 8081)

## Performance Considerations

- Deployment should complete within 5 minutes
- Health checks should not block deployment
- Use caching for faster builds
- Monitor resource usage during deployment
