# feat(data): add data quality monitoring

**Labels:** `enhancement` `tier-3-infra` `data` `monitoring`
**Estimated effort:** 2-3 days

## Problem

There is no automated detection of data quality issues. Missing candles, stale data, outlier prices, and ingestion failures go unnoticed until a user notices missing data in the UI.

## Proposed Solution

Create a `DataQualityService` that runs scheduled checks on OHLCV data, detects anomalies, and generates reports with alerts.

## Quality Checks

| Check | Description | Severity |
|-------|-------------|----------|
| `missingCandles` | Detect gaps in daily candle sequence | CRITICAL |
| `staleData` | Check if latest candle is older than expected | CRITICAL |
| `outlierPrices` | Detect prices > 3 ATR from previous close | WARNING |
| `zeroVolume` | Candles with zero volume on active stocks | WARNING |
| `duplicateCandles` | Same symbol + date with different data | WARNING |
| `priceJumps` | Day-over-day price change > 10% | WARNING |
| `negativeValues` | Negative open/high/low/close prices | CRITICAL |
| `volumeSpikes` | Volume > 10x 20-day average | INFO |

## API Endpoint

```
GET /api/admin/data/quality?symbol=AUTONATION&days=30
    Returns: DataQualityReport with all check results
```

## Response DTO

```java
public class DataQualityReport {
    private String symbol;
    private LocalDate dateFrom;
    private LocalDate dateTo;
    private long totalCandles;
    private long missingCandles;
    private long staleDays;
    private List<QualityIssue> issues;
    private DataQualityStatus status; // GOOD, DEGRADED, CRITICAL
}

public class QualityIssue {
    private CheckType type;        // MISSING_CANDLE, OUTLIER_PRICE, etc.
    private Severity severity;     // INFO, WARNING, CRITICAL
    private LocalDate date;
    private String description;
    private Object details;        // Type-specific details
}
```

## Scheduled Execution

```java
@Component
@EnableScheduling
public class DataQualityScheduler {

    @Scheduled(cron = "0 */4 * * * *")  // Every 4 hours
    public void runQualityChecks() {
        List<String> symbols = stockRepository.findAllActiveSymbols();
        for (String symbol : symbols) {
            DataQualityReport report = dataQualityService.check(symbol, 30);
            if (report.getStatus() == DataQualityStatus.CRITICAL) {
                alertService.sendCriticalAlert(report);
            }
        }
    }
}
```

## Alert Integration

- Webhook to existing notification system (Telegram/Signal)
- Log to `quality_issues` table for history
- Include symbol, issue type, severity, timestamp

## Files to Create

- `data/src/main/java/com/swingtrade/data/service/DataQualityService.java`
- `data/src/main/java/com/swingtrade/data/scheduler/DataQualityScheduler.java`
- `data/src/main/java/com/swingtrade/data/entity/QualityIssueEntity.java`
- `data/src/main/java/com/swingtrade/data/repository/QualityIssueRepository.java`
- `api/src/main/java/com/swingtrade/api/dto/DataQualityReport.java`
- `api/src/main/java/com/swingtrade/api/dto/QualityIssue.java`
- `api/src/main/java/com/swingtrade/api/controller/DataQualityController.java`
- `data/src/main/resources/db/migration/V9__create_quality_issues_table.sql`

## DataQualityService API

```java
public class DataQualityService {

    public DataQualityReport check(String symbol, int days) {
        List<OhlcvCandle> candles = getCandles(symbol, days);
        return buildReport(symbol, candles);
    }

    public DataQualityReport checkAll(int days) {
        // Run checks for all active symbols
    }

    private ReportBuilder checkMissingCandles(List<OhlcvCandle> candles) { ... }
    private ReportBuilder checkStaleData(List<OhlcvCandle> candles) { ... }
    private ReportBuilder checkOutlierPrices(List<OhlcvCandle> candles) { ... }
    private ReportBuilder checkZeroVolume(List<OhlcvCandle> candles) { ... }
    private ReportBuilder checkDuplicateCandles(List<OhlcvCandle> candles) { ... }
    private ReportBuilder checkPriceJumps(List<OhlcvCandle> candles) { ... }
    private ReportBuilder checkNegativeValues(List<OhlcvCandle> candles) { ... }
    private ReportBuilder checkVolumeSpikes(List<OhlcvCandle> candles) { ... }
}
```

## Acceptance Criteria

- [ ] `DataQualityService` implements all 8 quality checks
- [ ] `DataQualityReport` correctly classifies status (GOOD/DEGRADED/CRITICAL)
- [ ] Scheduled check runs every 4 hours
- [ ] CRITICAL issues trigger alerts via notification system
- [ ] Issues persisted to `quality_issues` table
- [ ] GET /api/admin/data/quality returns report for a symbol
- [ ] Unit tests for each quality check with edge cases
- [ ] Integration test with database
- [ ] Code coverage >= 80%

## Notes

- A "missing candle" is a gap in the expected trading day sequence (exclude weekends/holidays)
- Use NSE holiday calendar for accurate gap detection (hardcode common holidays for now)
- Outlier detection: price > (previousClose + 3 * ATR14) or price < (previousClose - 3 * ATR14)
- Consider adding a `data_quality_dashboard` page in the frontend (future)
