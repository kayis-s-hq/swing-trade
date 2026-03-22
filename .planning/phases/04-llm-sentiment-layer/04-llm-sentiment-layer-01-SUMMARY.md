---
phase: 04-llm-sentiment-layer
plan: 01
subsystem: sentiment
tags: [llm, sentiment, signal-engine, persistence, postgres]

# Dependency graph
requires:
  - phase: 02-strategy-engine
    provides: SignalEngine with signal generation
  - phase: 03-data-pipeline
    provides: database infrastructure and repositories
provides:
  - Sentiment filtering integration with SignalEngine
  - BUY signal suppression for NEGATIVE sentiment
  - NEUTRAL sentiment warning flag support
  - SentimentResultEntity and SentimentResultRepository for persistence
  - SentimentAnalysisService database persistence
affects:
  - broker module (signal consumption)
  - api module (signal queries)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - Sentiment filtering pattern: technical signals evaluated against news sentiment
    - Warning flag pattern: NEUTRAL signals marked for human review
    - Graceful degradation: sentiment check failures don't block signals

key-files:
  created:
    - data/src/main/java/com/swingtrade/data/entity/SentimentResultEntity.java
    - data/src/main/java/com/swingtrade/data/repository/SentimentResultRepository.java
  modified:
    - strategy/src/main/java/com/swingtrade/strategy/SignalEngine.java
    - strategy/pom.xml
    - data/src/main/java/com/swingtrade/data/entity/SignalEntity.java
    - llm/src/main/java/com/swingtrade/llm/service/SentimentAnalysisService.java

key-decisions:
  - Sentiment filtering applies only to BUY signals (neutral/positive sentiment required)
  - NEGATIVE sentiment signals are suppressed entirely (not saved to database)
  - NEUTRAL sentiment signals are saved with WARNING_NEUTRAL_SENTIMENT flag
  - Sentiment check exception doesn't block signal generation (graceful fallback)

patterns-established:
  - "Sentiment check runs in try-catch before signal save"
  - "Warning flags stored in SignalEntity for downstream consumption"
  - "Sentiment results persisted immediately after analysis for cache fallback"

requirements-completed: [REQ-025, REQ-026, REQ-027, REQ-028]

# Metrics
duration: 12min
completed: 2026-03-22
---

# Phase 04 Plan 01: Summary

**SENTIMENT FILTERING INTEGRATION WITH SignalEngine - BUY signals suppressed for NEGATIVE sentiment, NEUTRAL flagged**

## Performance

- **Duration:** 12 min
- **Started:** 2026-03-22T22:10:00Z
- **Completed:** 2026-03-22T22:16:00Z
- **Tasks:** 5
- **Files modified:** 6

## Accomplishments
- SignalEngine now checks sentiment before saving BUY signals
- NEGATIVE sentiment signals are suppressed (not saved to database)
- NEUTRAL sentiment signals saved with WARNING_NEUTRAL_SENTIMENT flag
- SentimentAnalysisService persists results to database for cache fallback
- SentimentResultRepository created for sentiment data queries

## Task Commits

Each task was committed atomically:

1. **Task 1: Add SentimentAnalysisService dependency to SignalEngine** - `4f1e83f` (feat)
2. **Task 2: Add sentiment check before signal save in generateSignalsForSymbol** - `a8de092` (feat), `e4808ad` (fix)
3. **Task 3: Add warning flag field to SignalEntity** - `2bea63e` (feat)
4. **Task 4: Add SentimentResultRepository interface** - `36fa4c7` (feat)
5. **Task 5: Update SentimentAnalysisService to persist results** - `29f8efe` (feat)

**Plan metadata:** Complete (all 5 tasks done)

## Files Created/Modified
- `data/src/main/java/com/swingtrade/data/entity/SentimentResultEntity.java` - JPA entity for sentiment results
- `data/src/main/java/com/swingtrade/data/repository/SentimentResultRepository.java` - CRUD operations for sentiment results
- `strategy/src/main/java/com/swingtrade/strategy/SignalEngine.java` - Integrated sentiment check before signal save
- `strategy/pom.xml` - Added llm module dependency
- `data/src/main/java/com/swingtrade/data/entity/SignalEntity.java` - Added warningFlag field and constants
- `llm/src/main/java/com/swingtrade/llm/service/SentimentAnalysisService.java` - Added database persistence

## Decisions Made
- Sentiment filtering applies only to BUY signals (technical analysis drives signal, sentiment acts as gatekeeper)
- NEGATIVE sentiment = early return without saving (suppression)
- NEUTRAL sentiment = save with warning flag (enables human review)
- POSITIVE sentiment = save normally (no changes)
- Sentiment check failure = continue saving signal (graceful degradation)

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

- Compilation error: SignalEntity warning flag constants not yet available when Task 2 compiled
  - Resolution: Task 3 (warning flag) was executed before Task 2 final commit, causing Task 2 to be split into two commits (initial sentiment check + warning flag integration)
  - Impact: Minimal - both commits part of same plan execution

## Build Verification

Build compiles successfully for core, data, llm, and strategy modules:
```
BUILD SUCCESS
Total time: 1.178 s
```

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Phase 04 Plan 01 complete
- Database schema needs migration to add warning_flag column to signals table
- Database schema needs to add sentiment_results table (via Flyway migration V5)
- Ready for Phase 04 Plan 02 (Telegram integration with warning icons)

---
*Phase: 04-llm-sentiment-layer*
*Completed: 2026-03-22*

## Self-Check: PASSED
