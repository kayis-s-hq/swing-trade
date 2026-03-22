---
name: swing-trade-health-check-monitor
description: Monitor system health via Actuator endpoints with alerting for the swing-trade system. Use this skill for continuous system monitoring, proactive issue detection, incident response, or before critical trading operations. This skill is essential for maintaining system reliability and should be triggered proactively before market open, during trading hours, and after any system changes.
---

# SwingTrade Health Check Monitor Skill

## Overview

This skill automates continuous health monitoring of the swing-trade system via Spring Boot Actuator endpoints. It provides proactive issue detection, alerting, and system status reporting.

## When to Use This Skill

Trigger this skill when:
- Before market open (pre-trade readiness check)
- During trading hours (continuous monitoring)
- After system deployments or changes
- When investigating performance issues
- Before executing critical trading operations
- Scheduled periodic health checks
- Incident response

## Health Check Endpoints

### Primary Endpoints

```
GET /api/actuator/health
GET /broker/actuator/health
GET /data/actuator/health
GET /llm/actuator/health
GET /strategy/actuator/health
```

### Detailed Health Endpoints

```
GET /api/actuator/healthdetail
GET /api/actuator/metrics
GET /api/actuator/info
GET /api/actuator/prometheus
```

### Custom Health Indicators

```java
@Component
public class DataHealthIndicator implements HealthIndicator {
    @Override
    public Health health() {
        try {
            // Check database connectivity
            databaseHealth = databaseRepository.count();

            // Check data freshness
            latestCandle = ohlcvRepository.findLatestDate();
            boolean dataFresh = latestCandle.isAfter(LocalDate.now().minusDays(1));

            return dataFresh
                ? Health.up().build()
                : Health.down().withDetail("data_fresh", "stale").build();
        } catch (Exception e) {
            return Health.down(e).build();
        }
    }
}
```

## Health Check Components

### 1. Application Health

```java
public class ApplicationHealthCheck {
    // Check all microservices are responding
    public Map<String, HealthStatus> checkServices() {
        return Map.of(
            "api", checkService("http://localhost:8080/api/actuator/health"),
            "broker", checkService("http://localhost:8081/broker/actuator/health"),
            "data", checkService("http://localhost:8082/data/actuator/health"),
            "llm", checkService("http://localhost:8083/llm/actuator/health"),
            "strategy", checkService("http://localhost:8084/strategy/actuator/health")
        );
    }
}
```

### 2. Database Health

```java
public class DatabaseHealthCheck {
    // Check database connectivity
    public HealthStatus checkConnectivity() {
        try {
            connection = dataSource.getConnection();
            return HealthStatus.UP;
        } catch (SQLException e) {
            return HealthStatus.DOWN.withReason(e.getMessage());
        }
    }

    // Check connection pool status
    public ConnectionPoolStatus checkPool() {
        return ConnectionPoolStatus.builder()
            .activeConnections(hikariPool.getActiveConnections())
            .idleConnections(hikariPool.getIdleConnections())
            .maxConnections(hikariPool.getMaxPoolSize())
            .pendingTasks(hikariPool.getPendingTasks())
            .build();
    }
}
```

### 3. Cache Health

```java
public class CacheHealthCheck {
    // Check Redis connectivity
    public HealthStatus checkRedis() {
        try {
            redisTemplate.ping();
            return HealthStatus.UP;
        } catch (Exception e) {
            return HealthStatus.DOWN.withReason("Redis connection failed");
        }
    }

    // Check cache statistics
    public CacheStats getStats() {
        return CacheStats.builder()
            .sentimentCacheSize(sentimentCache.size())
            .signalCacheSize(signalCache.size())
            .ohlcvCacheSize(ohlcvCache.size())
            .build();
    }
}
```

### 4. External Service Health

```java
public class ExternalServiceHealthCheck {
    // Check Upstox API
    public HealthStatus checkUpstox() {
        try {
            ResponseEntity<?> response = upstoxClient.getHealth();
            return response.getStatusCode().is2xxSuccessful()
                ? HealthStatus.UP
                : HealthStatus.DOWN.withReason("Upstox API error");
        } catch (Exception e) {
            return HealthStatus.DOWN.withReason(e.getMessage());
        }
    }

    // Check vLLM/Ollama
    public HealthStatus checkLLM() {
        try {
            ResponseEntity<?> response = vLLMClient.getHealth();
            return response.getStatusCode().is2xxSuccessful()
                ? HealthStatus.UP
                : HealthStatus.DOWN.withReason("LLM service error");
        } catch (Exception e) {
            return HealthStatus.DOWN.withReason(e.getMessage());
        }
    }
}
```

## Health Check Report Format

```
# System Health Report - [Timestamp]

## Overall Status: [HEALTHY/DEGRADED/UNHEALTHY]

## Service Health
| Service | Status | Response Time | Uptime |
|---------|--------|---------------|--------|
| API | UP | 45ms | 99.9% |
| Broker | UP | 32ms | 99.8% |
| Data | UP | 28ms | 99.9% |
| LLM | UP | 156ms | 98.5% |
| Strategy | UP | 22ms | 99.9% |

## Database Health
| Component | Status | Details |
|-----------|--------|---------|
| PostgreSQL | UP | Connection pool healthy |
| TimescaleDB | UP | Query performance normal |
| Redis | UP | Cache hit rate: 85% |

## External Services
| Service | Status | Last Check |
|---------|--------|------------|
| Upstox API | UP | 2 minutes ago |
| vLLM/Ollama | UP | 5 minutes ago |

## Metrics Summary
- CPU Usage: 45%
- Memory Usage: 62%
- Disk Usage: 38%
- Active Connections: 15/20

## Alerts
| Severity | Message | Time |
|----------|---------|------|
| WARNING | LLM response time > 100ms | 10:30 |
| INFO | Cache hit rate below 90% | 10:15 |

## Recommendations
1. [Actionable steps]
2. [Monitoring adjustments]
3. [Scale resources if needed]
```

## Alerting Rules

### Critical Alerts (Immediate Notification)

```
- All services DOWN
- Database connection pool exhausted
- Data ingestion failure > 30 minutes
- Signal generation failure
```

### Warning Alerts (Scheduled Notification)

```
- LLM response time > 200ms
- Cache hit rate < 70%
- Connection pool > 80% utilization
- Disk usage > 80%
```

### Info Alerts (Log Only)

```
- Service restart
- Cache cleared
- Configuration change
- Scheduled maintenance
```

## Monitoring Schedule

```
Every 5 minutes:
- Service health checks
- Database connectivity
- External service status

Every 15 minutes:
- Performance metrics collection
- Cache statistics
- Connection pool status

Every hour:
- Comprehensive health report
- Trend analysis
- Alert aggregation

Daily:
- Full system health summary
- Capacity planning review
- Incident summary
```

## Example Usage

```bash
# Full health check
/skill: swing-trade-health-check-monitor

# Check specific service
/skill: swing-trade-health-check-monitor --service api

# Check database health
/skill: swing-trade-health-check-monitor --component database

# Generate health report
/skill: swing-trade-health-check-monitor --report

# Set up continuous monitoring
/skill: swing-trade-health-check-monitor --continuous --interval 5m

# Send alerts via Telegram
/skill: swing-trade-health-check-monitor --alert telegram
```

## Integration with Alerting Systems

- **Telegram**: Real-time alerts for critical issues
- **Signal**: Alternative alerting channel
- **Email**: Daily/weekly health summaries
- **GSD**: Create incident phases for major issues

## Dependencies

- Spring Boot Actuator endpoints
- Database connection
- External service APIs
- Alerting system credentials

## Performance Considerations

- Health checks should complete within 10 seconds
- Continuous monitoring should not impact trading operations
- Use async health checks where possible
- Cache health results for 1 minute to reduce load
