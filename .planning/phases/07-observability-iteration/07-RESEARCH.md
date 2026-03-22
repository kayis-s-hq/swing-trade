# Phase 7: Observability + Iteration - Research

**Researched:** 2026-03-22
**Domain:** Observability, Monitoring, Scheduling, Trade Analytics
**Confidence:** HIGH

## Summary

Phase 7 focuses on establishing comprehensive observability through Spring Boot Actuator and Grafana, implementing automated monthly reporting, and creating a trade labelling system for iterative improvement. This phase builds on the existing Spring Boot foundation and adds monitoring infrastructure, scheduled reporting, and data collection for future model fine-tuning.

**Primary recommendation:** Use Spring Boot Actuator with Micrometer for metrics collection, connect Grafana via Prometheus for visualization, leverage Spring @Scheduled for monthly report generation, and create a `trade_labels` table for manual exit reason tracking.

## User Constraints (from CONTEXT.md)

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
[No CONTEXT.md found - research scope is open based on requirements]

### Claude's Discretion
- Select appropriate observability stack (Spring Actuator + Grafana)
- Design monthly report scheduling mechanism
- Define trade labelling data model

### Deferred Ideas (OUT OF SCOPE)
- Phase 8: Vue Dashboard + Monitoring UI
- Phase 9: Telegram to Signal/Signl4 migration
- LLM fine-tuning (deferred until 6+ months of labelled data)
</user_constraints>

## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| REQ-034 | Grafana dashboards connected to Spring Actuator | Actuator metrics, Micrometer, Prometheus integration |
| REQ-035 | Monthly review reports generated first Sunday of month | Spring @Scheduled with cron expressions |
| REQ-036 | Trade outcomes labelled with exit reason | Trade labels table design, manual labelling UI |

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| spring-boot-starter-actuator | 3.2.x+ | Production-ready monitoring endpoints | Built into Spring Boot, zero configuration for basic health/metrics |
| micrometer-registry-prometheus | 1.11.x+ | Expose metrics in Prometheus format | Official Micrometer registry, Grafana/Prometheus standard |
| spring-boot-starter-web | 3.2.x+ | REST API with actuator endpoints | Already in use, actuator integrates seamlessly |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| grafana | 10.x+ | Dashboard visualization | External deployment, connect to Prometheus as data source |
| prometheus | 2.x+ | Metrics scraping and storage | External deployment for Grafana integration |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Spring Actuator | Custom metrics logging | Actuator provides standard health checks, metrics, and endpoints |
| Grafana + Prometheus | Manual logging/CSV reports | Grafana provides real-time dashboards, alerting capabilities |
| Spring @Scheduled | Quartz scheduler | @Scheduled is sufficient for monthly reports; Quartz adds complexity for simple cron jobs |

**Installation:**
```bash
# Already included in existing pom.xml as part of spring-boot-starter-web
# Add for Prometheus metrics:
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

## Architecture Patterns

### Recommended Project Structure
```
src/main/java/com/swingtrade/
├── api/
│   ├── controller/
│   │   ├── TradingController.java
│   │   └── AdminController.java
│   └── service/
│       ├── PerformanceService.java
│       └── MonthlyReportService.java    # NEW: Phase 7
├── config/
│   ├── ActuatorConfig.java             # NEW: Phase 7
│   └── SchedulingConfig.java           # NEW: Phase 7
└── monitor/
    ├── metrics/                        # NEW: Phase 7
    │   ├── TradeMetrics.java
    │   └── PortfolioMetrics.java
    └── health/
        └── CustomHealthIndicators.java # NEW: Phase 7
```

### Pattern 1: Actuator Metrics Exposure

**What:** Expose Spring Boot Actuator metrics via Prometheus format for Grafana consumption.

**When to use:** For all production monitoring, health checks, and custom business metrics.

**Example:**
```java
// src/main/resources/application.properties
management.endpoints.web.exposure.include=health,metrics,prometheus,info
management.endpoint.health.show-details=when_authorized
management.metrics.tags.application=${spring.application.name}
management.metrics.distribution.percentiles.http.server.requests=0.5,0.95,0.99

# Prometheus endpoint configuration
management.endpoints.web.base-path=/actuator
management.endpoint.prometheus.enabled=true
```

```java
// Custom metrics registration
@Service
public class TradeMetrics {

    private final MeterRegistry meterRegistry;

    public TradeMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        // Counter for trade outcomes
        Counter tradeCounter = Counter.builder("trades.outcome")
            .description("Count of trades by exit reason")
            .tag("reason", "stop_loss")
            .register(meterRegistry);

        // Timer for signal generation duration
        Timer signalTimer = Timer.builder("signals generation.duration")
            .description("Time to generate signals")
            .register(meterRegistry);

        // Gauge for active positions
        meterRegistry.gauge("portfolio.active.positions",
            this, m -> m.getActivePositionCount());
    }
}
```

### Pattern 2: Scheduled Monthly Reporting

**What:** Generate monthly strategy review reports on the first Sunday of each month.

**When to use:** Automated report generation for performance tracking and strategy iteration.

**Example:**
```java
// src/main/java/com/swingtrade/api/config/SchedulingConfig.java
@Configuration
@EnableScheduling
public class SchedulingConfig {

    // IST timezone (UTC+5:30)
    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(5);
        scheduler.setThreadNamePrefix("scheduled-task-");
        scheduler.initialize();
        return scheduler;
    }
}

// Monthly report generation on first Sunday at 9 AM IST
@Component
public class MonthlyReportService {

    @Scheduled(cron = "0 0 3 1-7 * SUN", zone = "Asia/Kolkata")
    public void generateMonthlyReport() {
        log.info("Generating monthly strategy review report");

        // Calculate monthly statistics
        Map<String, Object> report = calculateMonthlyPerformance();

        // Send via email and Telegram
        emailService.sendReport(report);
        telegramService.sendReport(report);
    }
}
```

**Cron expression breakdown:**
- `0` - Second: at second 0
- `0` - Minute: at minute 0
- `3` - Hour: at 3 AM (IST)
- `1-7` - Day of month: on days 1-7 (first week)
- `*` - Month: every month
- `SUN` - Day of week: only on Sunday

### Pattern 3: Trade Outcome Labelling

**What:** Track trade exit reasons and manual annotations for iterative improvement.

**When to use:** After trade closes, allow manual labelling of exit reason (stop_loss, target_hit, time_stop, manual).

**Example Schema:**
```sql
-- Trade labels table for manual annotation
CREATE TABLE trade_labels (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trade_id UUID NOT NULL REFERENCES trades(id),
    exit_reason ENUM('STOP_LOSS', 'TARGET_HIT', 'TIME_STOP', 'TREND_BREAK', 'MANUAL'),
    exit_confidence DECIMAL(5,2),  -- Confidence in the labelling decision
    notes TEXT,                     -- Manual annotations
    labelled_by VARCHAR(100),       -- User who labelled
    labelled_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Index for quick lookups
CREATE INDEX idx_trade_labels_trade_id ON trade_labels(trade_id);
CREATE INDEX idx_trade_labels_labelled_at ON trade_labels(labelled_at);
```

```java
// TradeLabel entity
@Entity
@Table(name = "trade_labels")
public class TradeLabel {

    @Id
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "trade_id", nullable = false)
    private Trade trade;

    @Enumerated(EnumType.STRING)
    private ExitReason exitReason;

    private BigDecimal exitConfidence;

    private String notes;

    private String labelledBy;

    private LocalDateTime labelledAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    // ExitReason enum
    public enum ExitReason {
        STOP_LOSS, TARGET_HIT, TIME_STOP, TREND_BREAK, MANUAL
    }
}
```

### Anti-Patterns to Avoid

- **Don't expose all actuator endpoints:** Only expose `health,metrics,prometheus,info` in production
- **Don't use default timezone:** Always specify `zone = "Asia/Kolkata"` for IST scheduling
- **Don't hardcode exit reasons:** Use enum-based ExitReason for type safety and validation
- **Don't skip metrics tags:** Always add `application` tag for multi-instance deployments

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Health checks | Custom health endpoint | Spring Actuator HealthIndicator | Built-in DB, Redis, disk checks; custom indicators easy to add |
| Metrics collection | Manual logging | Micrometer | Standard format, Prometheus integration, custom metrics easy |
| Scheduled jobs | Cron library | Spring @Scheduled | Built-in, timezone support, easy configuration |
| Dashboard | Custom UI | Grafana | Rich visualizations, alerting, proven monitoring tool |
| Trade labelling UI | Build from scratch | Admin panel (Phase 8) | Phase 8 will add Vue dashboard; don't duplicate effort |

**Key insight:** Observability is a commodity—use battle-tested tools (Actuator, Grafana, Prometheus) rather than building custom monitoring infrastructure.

## Common Pitfalls

### Pitfall 1: Actuator Endpoint Exposure Security

**What goes wrong:** Exposing all actuator endpoints (`*`) to public without authentication.

**Why it happens:** Default configuration is too permissive; developers assume localhost-only exposure.

**How to avoid:** Explicitly configure exposure:
```properties
# production/application-prod.properties
management.endpoints.web.exposure.include=health,metrics,prometheus,info
management.security.enabled=true
```

**Warning signs:** `/actuator/env`, `/actuator/beans`, `/actuator/threaddump` accessible externally.

### Pitfall 2: Cron Timezone Misconfiguration

**What goes wrong:** Scheduled jobs run at wrong IST time because default timezone is UTC.

**Why it happens:** `@Scheduled` uses server's default timezone unless explicitly configured.

**How to avoid:** Always specify `zone = "Asia/Kolkata"` or configure `JAVA_OPTS=-Duser.timezone=Asia/Kolkata`.

**Warning signs:** Monthly reports generated at 9 PM instead of 9 AM IST.

### Pitfall 3: Metrics Memory Bloat

**What goes wrong:** High-cardinality metric tags cause memory exhaustion in Prometheus.

**Why it happens:** Using user_id, symbol, or other high-cardinality data as metric tags.

**How to avoid:** Keep tag cardinality low; use databases for detailed tracking:
```java
// BAD: High cardinality tag
Counter.builder("trades.per.symbol")
    .tag("symbol", stock.getSymbol())  // 500+ symbols = memory blowup
    .register(meterRegistry);

// GOOD: Aggregate in database, monitor counts
Counter.builder("trades.total")
    .tag("status", "closed")  // Low cardinality
    .register(meterRegistry);
```

**Warning signs:** Prometheus memory usage > 80%, queries timing out.

### Pitfall 4: Trade Labelling Backfill

**What goes wrong:** Historical trades missing exit reason annotations.

**Why it happens:** Only labelling going forward; old trades never annotated.

**How to avoid:** Create admin endpoint for bulk labelling:
```java
@PostMapping("/api/admin/trades/{tradeId}/label")
public ResponseEntity<?> labelTrade(
    @PathVariable UUID tradeId,
    @RequestBody TradeLabelRequest request) {
    tradeLabelService.labelTrade(tradeId, request.getExitReason(), request.getNotes());
    return ResponseEntity.ok().build();
}
```

**Warning signs:** Trade labels table has NULL exit_reason for closed trades.

## Code Examples

### Spring Actuator Configuration

```yaml
# src/main/resources/application.yml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus,info
    jmx:
      exposure:
        include: '*'
  endpoint:
    health:
      show-details: when_authorized
      probes:
        enabled: true
    metrics:
      enabled: true
    prometheus:
      enabled: true
  metrics:
    export:
      prometheus:
        enabled: true
    tags:
      application: ${spring.application.name}
  observations:
    key-values:
      application: ${spring.application.name}
```

### Custom Health Indicator

```java
@Component
public class TradingEngineHealthIndicator implements HealthIndicator {

    private final TradingEngine tradingEngine;

    public TradingEngineHealthIndicator(TradingEngine tradingEngine) {
        this.tradingEngine = tradingEngine;
    }

    @Override
    public Health health() {
        try {
            boolean isRunning = tradingEngine.isRunning();
            int activePositions = tradingEngine.getActivePositionsCount();

            if (isRunning) {
                return Health.up()
                    .withDetail("activePositions", activePositions)
                    .withDetail("mode", tradingEngine.getMode())
                    .build();
            } else {
                return Health.down()
                    .withDetail("reason", "Trading engine not running")
                    .build();
            }
        } catch (Exception e) {
            return Health.down()
                .withException(e)
                .withDetail("error", e.getMessage())
                .build();
        }
    }
}
```

### Monthly Report Generation

```java
@Service
public class MonthlyReportService {

    private final TradeRepository tradeRepository;
    private final PerformanceService performanceService;
    private final EmailService emailService;
    private final TelegramService telegramService;

    @Scheduled(cron = "0 0 3 1-7 * SUN", zone = "Asia/Kolkata")
    public void generateMonthlyReport() {
        log.info("Starting monthly report generation");

        // Calculate current month range
        LocalDate now = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        LocalDate firstDay = now.withDayOfMonth(1);
        LocalDate lastDay = now.withDayOfMonth(now.lengthOfMonth());

        // Fetch trades for the month
        List<Trade> monthlyTrades = tradeRepository.findByEntryDateBetween(
            firstDay.atStartOfDay(), lastDay.atTime(23, 59, 59));

        // Calculate metrics
        double winRate = performanceService.calculateWinRate(monthlyTrades);
        double avgRR = performanceService.calculateAverageRiskReward(monthlyTrades);
        double sharpeRatio = performanceService.calculateSharpeRatio(monthlyTrades);
        double maxDrawdown = performanceService.calculateMaxDrawdown(monthlyTrades);

        // Sector breakdown
        Map<String, Integer> sectorPerformance = calculateSectorPerformance(monthlyTrades);

        // Signal distribution
        Map<SignalType, Long> signalDistribution = signalRepository.countByDateRange(
            firstDay, lastDay);

        // Build report
        Map<String, Object> report = Map.of(
            "period", firstDay + " to " + lastDay,
            "totalTrades", monthlyTrades.size(),
            "winRate", winRate,
            "avgRiskReward", avgRR,
            "sharpeRatio", sharpeRatio,
            "maxDrawdown", maxDrawdown,
            "sectorPerformance", sectorPerformance,
            "signalDistribution", signalDistribution
        );

        // Send notifications
        emailService.sendMonthlyReport(report);
        telegramService.sendReport("Monthly Strategy Review\n" + formatReport(report));

        log.info("Monthly report sent successfully");
    }
}
```

### Trade Labelling Service

```java
@Service
public class TradeLabelService {

    private final TradeLabelRepository tradeLabelRepository;
    private final TradeRepository tradeRepository;

    @Transactional
    public TradeLabel labelTrade(UUID tradeId, ExitReason reason, String notes) {
        Trade trade = tradeRepository.findById(tradeId)
            .orElseThrow(() -> new IllegalArgumentException("Trade not found"));

        if (trade.getStatus() != TradeStatus.CLOSED) {
            throw new IllegalStateException("Only closed trades can be labelled");
        }

        TradeLabel label = new TradeLabel();
        label.setTrade(trade);
        label.setExitReason(reason);
        label.setNotes(notes);
        label.setLabelledBy("admin");  // From authentication
        label.setLabelledAt(LocalDateTime.now());

        return tradeLabelRepository.save(label);
    }

    public List<TradeLabel> getLabelsByTrade(UUID tradeId) {
        return tradeLabelRepository.findByTradeId(tradeId);
    }

    public Map<ExitReason, Long> getExitReasonDistribution() {
        return tradeLabelRepository.countByExitReason();
    }
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Manual CSV exports | Grafana dashboards | Always preferred | Real-time insights, no manual work |
| Log file parsing | Micrometer metrics | 2017 (Spring Boot 2.0) | Standard format, Prometheus integration |
| Quartz scheduler | Spring @Scheduled | When simple cron suffices | Less boilerplate, easier configuration |
| Hardcoded exit reasons | Enum-based labelling | Always | Type safety, validation, analytics |

**Deprecated/outdated:**
- **Custom metrics logging:** Use Micrometer instead (standardized, Prometheus-ready)
- **Static dashboards:** Grafana provides dynamic, configurable dashboards
- **Manual report generation:** Scheduled jobs ensure consistent reporting

## Open Questions

1. **What Grafana dashboard templates to use?**
   - What we know: Spring Boot provides basic metrics
   - What's unclear: Specific dashboard JSON for SwingTrade use case
   - Recommendation: Start with default Grafana dashboards, customize later

2. **Should monthly reports include email or only Telegram?**
   - What we know: Telegram already integrated for alerts
   - What's unclear: Email delivery requirements
   - Recommendation: Both—email for archival, Telegram for quick access

3. **How to handle timezone DST changes?**
   - What we know: Spring supports `Asia/Kolkata` timezone
   - What's unclear: India doesn't observe DST (fixed IST)
   - Recommendation: Safe to use fixed `Asia/Kolkata` timezone

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 + TestContainers |
| Config file | `src/test/resources/application-test.properties` |
| Quick run command | `mvn test -Dtest=*Test` |
| Full suite command | `mvn verify` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| REQ-034 | Actuator endpoints expose metrics | integration | `mvn test -Dtest=ActuatorIntegrationTest` | ❌ Wave 0 |
| REQ-035 | Monthly report scheduled correctly | unit | `mvn test -Dtest=MonthlyReportServiceTest` | ❌ Wave 0 |
| REQ-036 | Trade labelling persists correctly | unit/integration | `mvn test -Dtest=TradeLabelServiceTest` | ❌ Wave 0 |

### Sampling Rate
- **Per task commit:** `mvn test -Dtest=*Test` (quick unit tests)
- **Per wave merge:** `mvn verify` (full suite with TestContainers)
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `src/test/java/com/swingtrade/api/service/MonthlyReportServiceTest.java` — covers REQ-035
- [ ] `src/test/java/com/swingtrade/api/controller/ActuatorIntegrationTest.java` — covers REQ-034
- [ ] `src/test/java/com/swingtrade/data/service/TradeLabelServiceTest.java` — covers REQ-036
- [ ] `src/test/resources/application-test.properties` — test-specific configuration
- [ ] Framework test containers: PostgreSQL TestContainer for trade labels integration tests

## Sources

### Primary (HIGH confidence)
- [Spring Boot Actuator Documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html) - endpoints, configuration properties, health indicators
- [Micrometer Registry Prometheus](https://micrometer.io/docs/registry/prometheus) - metrics exposure format
- [Spring Scheduling](https://docs.spring.io/spring-framework/reference/core/beans/using-beans/scheduling.html) - @Scheduled cron expressions
- [Grafana Prometheus Data Source](https://grafana.com/docs/grafana/latest/datasources/prometheus/) - connection configuration

### Secondary (MEDIUM confidence)
- [Baeldung Spring Scheduled Examples](https://www.baeldung.com/spring-scheduled-methods) - cron patterns, timezone configuration
- [Baeldung Actuator Custom Metrics](https://www.baeldung.com/spring-boot-custom-metrics) - Micrometer patterns

### Tertiary (LOW confidence)
- [Spring Boot Actuator How-To Guide](https://docs.spring.io/spring-boot/docs/current/reference/html/howto.html#howto-actuator) - detailed configuration examples

## Metadata

**Confidence breakdown:**
- Standard stack: **HIGH** - Spring Boot Actuator + Micrometer is well-established, official documentation available
- Architecture: **HIGH** - Cron expressions, scheduled tasks, and entity design are standard patterns
- Pitfalls: **HIGH** - Actuator security, timezone issues, and metric cardinality are documented best practices

**Research date:** 2026-03-22
**Valid until:** 2026-04-22 (30 days for stable Spring ecosystem)
