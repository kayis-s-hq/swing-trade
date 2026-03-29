---
phase: 05-api-layer
plan: 06
subsystem: api
tags: [bigdecimal, performance, pnl, type-safety]

# Dependency graph
requires:
  - phase: 03-data-pipeline
    provides: "PaperTradingEngine with BigDecimal getTotalPnL()"
provides:
  - "PerformanceService with consistent BigDecimal P&L calculations"
  - "PerformanceResponse DTO with BigDecimal winRate"
affects: [05-api-layer, testing]

# Tech tracking
tech-stack:
  added: []
  patterns: [BigDecimal arithmetic with RoundingMode for financial calculations]

key-files:
  created: []
  modified:
    - api/src/main/java/com/swingtrade/api/PerformanceService.java
    - api/src/main/java/com/swingtrade/api/dto/PerformanceResponse.java
    - api/src/main/java/com/swingtrade/api/service/MonthlyReportService.java

key-decisions:
  - "Used BigDecimal.compareTo() for P&L win detection instead of missing getPnl() method"
  - "Changed winRate from Integer to BigDecimal for precision in percentage calculation"

patterns-established:
  - "BigDecimal with RoundingMode.HALF_UP for all financial division operations"

requirements-completed: [REQ-023]

# Metrics
duration: 3min
completed: 2026-03-23
---

# Phase 5 Plan 6: Fix PerformanceService Type Mismatches Summary

**Converted PerformanceService P&L calculations from double to BigDecimal, fixing compile errors from PaperTradingEngine return type mismatch**

## Performance

- **Duration:** 3 min
- **Started:** 2026-03-23T09:17:39Z
- **Completed:** 2026-03-23T09:20:20Z
- **Tasks:** 2
- **Files modified:** 3

## Accomplishments
- Converted all P&L calculation methods in PerformanceService from double to BigDecimal
- Fixed getWinningTrades() which referenced non-existent getPnl() method on PositionEntity
- Updated PerformanceResponse winRate field from Integer to BigDecimal for precision

## Task Commits

Each task was committed atomically:

1. **Task 1: Fix PerformanceService P&L type from double to BigDecimal** - `fd0b03b` (fix)
2. **Task 2: Update PerformanceResponse DTO to use BigDecimal** - `5929107` (fix)

## Files Created/Modified
- `api/src/main/java/com/swingtrade/api/PerformanceService.java` - All calculation methods now return BigDecimal; fixed missing getPnl() call
- `api/src/main/java/com/swingtrade/api/dto/PerformanceResponse.java` - Changed winRate from Integer to BigDecimal
- `api/src/main/java/com/swingtrade/api/service/MonthlyReportService.java` - Added missing import for PerformanceService

## Decisions Made
- Used `currentPrice.compareTo(entryPrice) > 0` to determine winning trades since PositionEntity has no dedicated pnl field
- Changed winRate from Integer to BigDecimal for more precise percentage representation

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed getWinningTrades() calling non-existent getPnl()**
- **Found during:** Task 1 (PerformanceService BigDecimal conversion)
- **Issue:** `p.getPnl() > 0` called on PositionEntity which has no getPnl() method
- **Fix:** Replaced with `p.getCurrentPrice().compareTo(p.getEntryPrice()) > 0` with null checks
- **Files modified:** api/src/main/java/com/swingtrade/api/PerformanceService.java
- **Verification:** Compilation passes for PerformanceService
- **Committed in:** fd0b03b (Task 1 commit)

**2. [Rule 3 - Blocking] Added missing PerformanceService import in MonthlyReportService**
- **Found during:** Task 1 (verifying compilation)
- **Issue:** MonthlyReportService in com.swingtrade.api.service referenced PerformanceService without import
- **Fix:** Added `import com.swingtrade.api.PerformanceService;`
- **Files modified:** api/src/main/java/com/swingtrade/api/service/MonthlyReportService.java
- **Verification:** MonthlyReportService PerformanceService reference resolves
- **Committed in:** fd0b03b (Task 1 commit)

---

**Total deviations:** 2 auto-fixed (1 bug, 1 blocking)
**Impact on plan:** Both fixes necessary for compilation. No scope creep.

## Issues Encountered
- Pre-existing compile errors in ScanResponse, ActuatorConfig, PositionService, and TradingController remain but are out of scope for this plan

## Known Stubs
- `calculateSharpeRatio()` returns `BigDecimal.ONE` placeholder (line ~108)
- `calculateMaxDrawdown()` returns `BigDecimal.valueOf(5)` placeholder (line ~116)
- `calculateAvgWin()` returns `BigDecimal.valueOf(500.0)` placeholder (line ~148)
- `calculateAvgLoss()` returns `BigDecimal.valueOf(300.0)` placeholder (line ~155)

These stubs are intentional -- they existed before this plan and will be replaced when actual trade data accumulates during paper trading validation.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- PerformanceService compiles with BigDecimal P&L types
- Ready for integration testing in Phase 5 testing plans

---
*Phase: 05-api-layer*
*Completed: 2026-03-23*
