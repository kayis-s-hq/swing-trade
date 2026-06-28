# feat(api): add monthly PDF report generation

**Labels:** `enhancement` `tier-3-infra` `api` `reporting`
**Estimated effort:** 2-3 days

## Problem

There is no automated performance report for traders. Users must manually check the dashboard to review monthly P&L, win rate, and key metrics.

## Proposed Solution

Create a `MonthlyReportService` that generates a PDF trading performance report on the 1st of each month and delivers it via webhook/email.

## PDF Report Contents

```
┌──────────────────────────────────┐
│     SWING TRADE MONTHLY REPORT   │
│     May 2026                     │
├──────────────────────────────────┤
│                                  │
│  SUMMARY                         │
│  ┌──────────────┬──────────────┐ │
│  │ Total P&L    │ +Rs 42,500   │ │
│  │ Win Rate     │ 60%          │ │
│  │ Total Trades │ 25           │ │
│  │ Sharpe Ratio │ 1.42         │ │
│  └──────────────┴──────────────┘ │
│                                  │
│  MONTHLY RETURN                  │
│  ┌──────────────────────────┐   │
│  │ ████████░░░░░░░░ +2.8%   │   │
│  └──────────────────────────┘   │
│                                  │
│  TOP 5 TRADES                    │
│  ┌────────┬───────┬───────┬────┐│
│  │ Symbol │ Entry │ Exit  │ P&L││
│  ├────────┼───────┼───────┼────┤│
│  │ REL    │ 2850  │ 3100  │+8% ││
│  │ TCS    │ 3650  │ 3850  │+5% ││
│  │ ...    │       │       │    ││
│  └────────┴───────┴───────┴────┘│
│                                  │
│  SECTOR ALLOCATION               │
│  ┌────────────┐  ┌───────────┐  │
│  │ IT 35%     │  │  ████ IT  │  │
│  │ Fin 30%    │  │  ███ Fin  │  │
│  │ Ind 25%    │  │  ██ Ind   │  │
│  │ Other 10%  │  │  ██ Other │  │
│  └────────────┘  └───────────┘  │
│                                  │
│  GENERATED: 2026-06-01 09:00 IST │
└──────────────────────────────────┘
```

## API Endpoints

```
POST /api/reports/monthly?month=2026-05    - Generate report for specific month
GET  /api/reports/monthly/2026-05          - Get generated report metadata
GET  /api/reports/monthly/2026-05/download - Download PDF file
```

## Scheduled Execution

```java
@Component
@EnableScheduling
public class ReportScheduler {

    @Scheduled(cron = "0 0 9 1 * ?", zone = "Asia/Kolkata")  // 9 AM IST, 1st of month
    public void generateMonthlyReport() {
        String month = LocalDate.now().minusMonths(1).format(DateTimeFormatter.ofPattern("yyyy-MM"));
        monthlyReportService.generateAndSend(month);
    }
}
```

## Files to Create

- `api/src/main/java/com/swingtrade/api/service/MonthlyReportService.java`
- `api/src/main/java/com/swingtrade/api/service/PDFGenerator.java`
- `api/src/main/java/com/swingtrade/api/scheduler/ReportScheduler.java`
- `api/src/main/java/com/swingtrade/api/dto/ReportMetadata.java`
- `api/src/main/java/com/swingtrade/api/controller/ReportController.java`
- `api/src/main/resources/reports/monthly-report.ftl` - FreeMarker template (if using FreeMarker)

## Files to Modify

- `api/pom.xml` - Add `itextpdf` or `freemarker` + `pdfbox` dependency
- `docker-compose.yml` - Ensure timezone is set: `TZ=Asia/Kolkata`

## Dependency Choice

### Option A: iTextPDF (recommended)
```xml
<dependency>
    <groupId>com.itextpdf</groupId>
    <artifactId>itext7-core</artifactId>
    <version>8.0.4</version>
    <type>pom</type>
</dependency>
```
- Native PDF generation, no template engine needed
- Full control over layout
- Larger JAR (~2MB)

### Option B: FreeMarker + Apache PDFBox
```xml
<dependency>
    <groupId>org.freemarker</groupId>
    <artifactId>freemarker</artifactId>
    <version>2.3.33</version>
</dependency>
<dependency>
    <groupId>org.apache.pdfbox</groupId>
    <artifactId>pdfbox</artifactId>
    <version>3.0.1</version>
</dependency>
```
- Template-based approach
- Easier HTML-to-PDF conversion
- Smaller footprint

## PDFGenerator API

```java
@Service
public class PDFGenerator {

    public byte[] generateMonthlyReport(String month, PortfolioMetrics metrics,
                                         List<Trade> trades, SectorAllocation allocation) {
        // 1. Gather all data
        // 2. Render template
        // 3. Convert to PDF bytes
        // 4. Return PDF
    }

    private Document buildDocument(String month, PortfolioMetrics metrics, ...) {
        // Build iText Document with sections
    }
}
```

## Delivery

- PDF stored in `logs/reports/monthly/{month}.pdf`
- Webhook sent with PDF path to notification service
- Email support (future): attach PDF and send to registered email

## Acceptance Criteria

- [ ] `MonthlyReportService` generates PDF with all sections
- [ ] Report includes: summary, monthly return, top trades, sector allocation
- [ ] PDF is valid and renderable
- [ ] Scheduled job runs on 1st of each month at 9 AM IST
- [ ] Manual generation via POST /api/reports/monthly works
- [ ] PDF stored in logs/reports/monthly/ directory
- [ ] Webhook notification sent after generation
- [ ] Unit tests for PDF generation
- [ ] Integration test with real data
- [ ] Code coverage >= 80%

## Notes

- Use `Asia/Kolkata` timezone for all scheduling
- The report should cover the previous month (not current)
- Consider adding a "trades by exit reason" section
- PDF should use the project's color scheme (green/red for P&L)
- For large trade lists, show top 5 wins and top 5 losses
