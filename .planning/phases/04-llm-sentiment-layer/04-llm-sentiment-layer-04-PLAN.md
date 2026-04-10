---
phase: 04-llm-sentiment-layer
plan: 04
type: execute
wave: 1
depends_on: []
files_modified:
  - api/src/main/java/com/swingtrade/api/scheduler/WeeklySectorDigestScheduler.java
  - strategy/src/test/java/com/swingtrade/strategy/SignalEngineIntegrationTest.java
  - llm/src/test/java/com/swingtrade/llm/service/SectorDigestTest.java
autonomous: true
gap_closure: true
requirements: [REQ-028, REQ-029]

# WORKTREE WORKFLOW ENFORCEMENT
# CRITICAL: This plan MUST be executed in a git worktree environment
# Root .planning/ is source of truth - worktree .planning/ is working copy
worktree_enforcement:
  required: true
  reason: "Prevents direct edits to root .planning/ on main branch"
  workflow:
    - step: 1
      action: "Verify worktree directory"
      command: "pwd | grep worktrees"
      fail_message: "ERROR: Must be in a worktree directory (e.g., .claude/worktrees/phase-05/)"
    - step: 2
      action: "Verify worktree branch"
      command: "git branch --show-current"
      expected_pattern: "worktree-phase-.*"
      fail_message: "ERROR: Must be on a worktree branch (e.g., worktree-phase-05)"
    - step: 3
      action: "Edit planning docs in worktree"
      path: ".planning/phases/04-llm-sentiment-layer/"
      note: "Do NOT edit .planning/ in root repository"
    - step: 4
      action: "Sync to root before merge"
      command: "Skill(\"superpowers:gsd-worktree-workflow --sync-to-root\")"
      when: "Before merging worktree branch to main"
    - step: 5
      action: "Verify before merge"
      command: "gsd:verify"
      when: "After plan completion, before merge"
requirements: [REQ-028, REQ-029]
must_haves:
  truths:
    - "Weekly sector digest is generated and sent via Telegram every Sunday 17:00 IST"
    - "SentimentFilteringTest verifies SignalEngine.generateSignalsForSymbol() suppresses NEGATIVE and flags NEUTRAL"
    - "SectorDigestTest verifies SentimentAnalysisService.groupBySectorAndSentiment() groups correctly and getTopSectors() ranks correctly"
  artifacts:
    - path: "api/src/main/java/com/swingtrade/api/scheduler/WeeklySectorDigestScheduler.java"
      provides: "Scheduled bean bridging SentimentAnalysisService (llm) and TelegramNotificationService (broker) to send weekly digest"
      must_exist: true
    - path: "strategy/src/test/java/com/swingtrade/strategy/SignalEngineIntegrationTest.java"
      provides: "Integration test verifying SignalEngine.generateSignalsForSymbol() with mocked SentimentAnalysisService"
      must_exist: true
    - path: "llm/src/test/java/com/swingtrade/llm/service/SectorDigestTest.java"
      provides: "Enhanced tests calling SentimentAnalysisService.groupBySectorAndSentiment() and getTopSectors() directly"
      must_exist: true
  key_links:
    - from: "WeeklySectorDigestScheduler"
      to: "TelegramNotificationService.sendMessage"
      via: "dependency injection in api module"
      pattern: "telegramService.sendMessage(digest)"
    - from: "SignalEngineIntegrationTest"
      to: "SignalEngine.generateSignalsForSymbol()"
      via: "direct method call with mocked sentiment service"
      pattern: "signalEngine.generateSignalsForSymbol\\(symbol\\) should suppress NEGATIVE"
    - from: "SectorDigestTest"
      to: "SentimentAnalysisService"
      via: "instantiation with mocked repositories"
      pattern: "sentimentAnalysisService.groupBySectorAndSentiment\\(\\) returns Map"
---

# Phase 04.4: Gap Closure Plan

**Phase Goal:** Close 3 critical gaps blocking Phase 4 verification: wire Telegram delivery for weekly digest, test SignalEngine filtering integration, test sector digest grouping logic.

**Purpose:**
- Gap 1 (REQ-029): Weekly digest is generated but not sent — wire delivery in api module
- Gap 2 (REQ-028): SentimentFilteringTest tests a stub, not SignalEngine — replace with integration test
- Gap 3 (REQ-029): SectorDigestTest doesn't exercise real service methods — add tests calling groupBySectorAndSentiment() and getTopSectors()

**Output:** 3 files (1 production, 2 test files) closing all verified gaps

---

## Objective

Fix 3 gaps identified in Phase 04 verification:

1. **Telegram Delivery (REQ-029 partial)** — `LlmConfig.sendWeeklySectorDigest()` generates digest string but never calls `TelegramNotificationService.sendMessage()`. Solution: Create a thin scheduled bean in api module injecting both services, avoiding circular dependency (llm → broker).

2. **SignalEngine Integration Test (REQ-028 test coverage)** — Current `SentimentFilteringTest` tests a private stub class, not the actual `SignalEngine.generateSignalsForSymbol()` logic. Solution: Create strategy-module integration test that mocks `SentimentAnalysisService` and verifies real SignalEngine behavior.

3. **SectorDigest Service Test (REQ-029 test coverage)** — Current `SectorDigestTest` only validates test data structure, not `SentimentAnalysisService.groupBySectorAndSentiment()` or `getTopSectors()` behavior. Solution: Enhance test to instantiate service with mocked repositories and call public methods directly.

---

## Execution Context

@/Users/kayisrahman/Documents/workspace/ideas/swing-trade/.planning/phases/04-llm-sentiment-layer/04-VERIFICATION.md
@/Users/kayisrahman/Documents/workspace/ideas/swing-trade/.planning/phases/04-llm-sentiment-layer/04-llm-sentiment-layer-02-SUMMARY.md
@/Users/kayisrahman/Documents/workspace/ideas/swing-trade/.planning/phases/04-llm-sentiment-layer/04-llm-sentiment-layer-03-SUMMARY.md

---

## Context

@/Users/kayisrahman/Documents/workspace/ideas/swing-trade/.planning/ROADMAP.md
@/Users/kayisrahman/Documents/workspace/ideas/swing-trade/.planning/STATE.md

---

## Codebase Interfaces

**TelegramNotificationService** (broker module):
```java
public class TelegramNotificationService {
  public boolean sendMessage(String message);
  // Broadcasts to all configured chat IDs
}
```

**SentimentAnalysisService** (llm module):
```java
public SentimentResult analyzeStockSentiment(String symbol, List<String> articles);
public String generateSectorDigestForLastWeek();
public String generateSectorDigest(LocalDate startDate, LocalDate endDate);
public Map<String, SectorSentimentData> groupBySectorAndSentiment(List<SentimentResult> results);
public List<Map.Entry<String, SectorSentimentData>> getTopSectors(Map<String, SectorSentimentData> sectors, int topN, boolean positive);
```

**SignalEngine** (strategy module):
```java
public void generateSignalsForSymbol(String symbol);
// At line 138-159: sentiment check before signal save
// NEGATIVE → return (suppress)
// NEUTRAL → save with WARNING_NEUTRAL_SENTIMENT
// POSITIVE → save normally
```

---

## Tasks

<task type="auto">
  <name>Task 1: Wire Telegram delivery in api module with scheduled bean</name>
  <files>api/src/main/java/com/swingtrade/api/scheduler/WeeklySectorDigestScheduler.java</files>
  <action>
    Create a new scheduled bean in the api module (package `com.swingtrade.api.scheduler`) named `WeeklySectorDigestScheduler` that:

    1. Is annotated with `@Component`
    2. Injects `SentimentAnalysisService` from llm module (constructor injection)
    3. Injects `TelegramNotificationService` from broker module (constructor injection)
    4. Has a `@Scheduled(cron = "0 0 17 * * SUN", zone = "Asia/Kolkata")` method `sendWeeklySectorDigest()`
    5. This method:
       - Calls `sentimentAnalysisService.generateSectorDigestForLastWeek()` to get digest string
       - Calls `telegramService.sendMessage(digest)` to send via Telegram (single argument, broadcasts to all configured chat IDs)
       - Logs start: "Starting weekly sector digest delivery to Telegram"
       - Logs success: "Weekly sector digest sent successfully to Telegram"
       - Catches all exceptions and logs error without rethrowing (scheduler resilience)

    Design rationale: This scheduled bean lives in the api module, which already depends on both llm (for sentiment) and broker (for notifications). It bridges the two services without creating a circular dependency. The single-argument sendMessage(String) method broadcasts to all configured chat IDs, which is appropriate for a weekly digest. The original `LlmConfig.sendWeeklySectorDigest()` can remain as a utility/test method, but production delivery now uses this bean.

    Note: Do NOT modify LlmConfig.sendWeeklySectorDigest() — that method can stay as-is for testing purposes.
  </action>
  <verify>
    <automated>cd /Users/kayisrahman/Documents/workspace/ideas/swing-trade && mvn clean compile -pl api && grep -n "sendWeeklySectorDigest" api/src/main/java/com/swingtrade/api/scheduler/WeeklySectorDigestScheduler.java | head -1</automated>
  </verify>
  <done>
    WeeklySectorDigestScheduler.java created with:
    - @Component annotation
    - SentimentAnalysisService injected
    - TelegramNotificationService injected (not TelegramService interface)
    - @Scheduled(cron = "0 0 17 * * SUN", zone = "Asia/Kolkata") sendWeeklySectorDigest() method
    - Calls sentimentAnalysisService.generateSectorDigestForLastWeek()
    - Calls telegramService.sendMessage(digest) with single argument
    - Proper error handling and logging
    - mvn clean compile succeeds
  </done>
</task>

<task type="auto">
  <name>Task 2: Create SignalEngine integration test with real filtering logic</name>
  <files>strategy/src/test/java/com/swingtrade/strategy/SignalEngineIntegrationTest.java</files>
  <action>
    Create a new integration test class in the strategy module (package `com.swingtrade.strategy`) named `SignalEngineIntegrationTest` that:

    1. Tests the actual `SignalEngine.generateSignalsForSymbol(String symbol)` method with mocked `SentimentAnalysisService`
    2. Does NOT use the internal stub `SentimentFilterService` — test against the real SignalEngine
    3. Mocks repositories (SignalRepository, StockRepository) using Mockito
    4. Mocks `SentimentAnalysisService` to return controlled sentiment values
    5. Create 4 test methods:
       - `testNegativeSentimentSuppressesBUYSignal()` — sentiment returns NEGATIVE, verify signal is NOT saved (repository.save NOT called)
       - `testNeutralSentimentFlagsBUYSignal()` — sentiment returns NEUTRAL, verify signal IS saved but with WARNING_NEUTRAL_SENTIMENT flag
       - `testPositiveSentimentAllowsBUYSignal()` — sentiment returns POSITIVE, verify signal IS saved with WARNING_NONE
       - `testSentimentCheckExceptionAllowsSignal()` — sentiment throws exception, verify signal IS saved anyway (graceful degradation)

    Test setup:
    - Use Mockito to mock: StockRepository, SignalRepository, SentimentAnalysisService
    - Create real SignalEngine instance with mocked dependencies (constructor injection)
    - Mock Stock data with NSE symbol (e.g., "RELIANCE.NS")
    - Mock TechnicalIndicators to return BUY signal
    - Mock SentimentAnalysisService to return controlled sentiment types

    Method signature: Call `signalEngine.generateSignalsForSymbol(symbol)` with only the symbol parameter (no LocalDate).

    Why replace instead of enhance: The current SentimentFilteringTest uses a local stub that never calls the real SignalEngine. It provides false confidence. The integration test should verify the actual code path in SignalEngine.generateSignalsForSymbol() where sentiment check occurs.
  </action>
  <verify>
    <automated>cd /Users/kayisrahman/Documents/workspace/ideas/swing-trade && mvn test -pl strategy -Dtest=SignalEngineIntegrationTest -DfailIfNoTests=true</automated>
  </verify>
  <done>
    SignalEngineIntegrationTest.java created with:
    - 4 test methods covering NEGATIVE suppression, NEUTRAL flagging, POSITIVE normal, exception handling
    - Mocks real SignalEngine dependencies
    - Calls signalEngine.generateSignalsForSymbol(symbol) with correct signature (no LocalDate parameter)
    - All 4 tests pass
    - Integration test verifies actual SignalEngine.generateSignalsForSymbol() behavior
    - mvn test -pl strategy passes SignalEngineIntegrationTest
  </done>
</task>

<task type="auto">
  <name>Task 3: Enhance SectorDigestTest to exercise service grouping and ranking logic</name>
  <files>llm/src/test/java/com/swingtrade/llm/service/SectorDigestTest.java</files>
  <action>
    Replace the current `SectorDigestTest.java` with enhanced tests that actually call `SentimentAnalysisService.groupBySectorAndSentiment()` and `getTopSectors()` methods:

    1. Current test setup (keep):
       - Use Mockito to mock SentimentResultRepository, StockRepository
       - Create mock SentimentResult and Stock instances with realistic sector data

    2. Add 3 new test methods (replace trivial assertions):
       - `testGroupBySectorAndSentiment_CorrectlyGroupsBySector()`
         * Instantiate real SentimentAnalysisService with mocked repos
         * Call groupBySectorAndSentiment(mockResults) with 10+ sentiment results across 3 sectors
         * Verify returned Map has correct sector keys
         * Verify sentiment counts are aggregated correctly (not just that result is non-empty)

       - `testGetTopSectors_IdentifiesTopPositiveAndNegativeSectors()`
         * Use same SentimentAnalysisService instance
         * Call getTopSectors(groupedMap, 3, true) for positive sectors
         * Verify returned list has exactly 3 sectors
         * Verify sectors are sorted by POSITIVE count descending
         * Call getTopSectors(groupedMap, 3, false) for negative sectors
         * Verify returned list is sorted by NEGATIVE count descending

       - `testGenerateSectorDigest_FormatsCompleteDigest()`
         * Call generateSectorDigest(startDate, endDate) on real service
         * Verify returned string contains:
           * Date range ("Week of: YYYY-MM-DD to YYYY-MM-DD")
           * "Top Positive Sectors:" section
           * "Top Negative Sectors:" section
           * Sector counts (e.g., "BANK - 45 POS, 12 NEU, 8 NEG")
           * Summary statistics (total stocks, sentiment counts)

    3. Remove or significantly enhance existing tests:
       - `testGroupBySectorAndSentiment()` — currently just checks `isNotEmpty()`, replace with detailed assertions
       - `testEmptyDigestHandling()` — keep for edge case, but add assertions on returned string format

    Implementation pattern:
    ```java
    @Test
    void testGroupBySectorAndSentiment_CorrectlyGroupsBySector() {
        // Create mock sentiment results with known sectors
        List<SentimentResult> results = List.of(
            createMockResult("RELIANCE", "BANK", "POSITIVE"),
            createMockResult("INFY", "IT", "POSITIVE"),
            createMockResult("TCS", "IT", "NEUTRAL"),
            // ... more data
        );

        // Instantiate real service with mocked repos
        SentimentAnalysisService service = new SentimentAnalysisService(
            mockSentimentResultRepository,
            mockStockRepository
        );

        // Call real method
        Map<String, SectorSentimentData> grouped = service.groupBySectorAndSentiment(results);

        // Assert structure and counts
        assertThat(grouped).containsKeys("BANK", "IT");
        assertThat(grouped.get("BANK").positiveCount).isEqualTo(1);
        assertThat(grouped.get("IT").positiveCount).isEqualTo(1);
        assertThat(grouped.get("IT").neutralCount).isEqualTo(1);
    }
    ```
  </action>
  <verify>
    <automated>cd /Users/kayisrahman/Documents/workspace/ideas/swing-trade && mvn test -pl llm -Dtest=SectorDigestTest -DfailIfNoTests=true 2>&1 | grep -E "Tests run:|BUILD SUCCESS|FAILURE"</automated>
  </verify>
  <done>
    SectorDigestTest.java enhanced with:
    - 3+ test methods calling real SentimentAnalysisService.groupBySectorAndSentiment()
    - Tests call real getTopSectors() method with various parameters
    - Tests call real generateSectorDigest() and validate formatted output
    - Detailed assertions on sector grouping and ranking logic (not just non-empty checks)
    - All tests pass with `mvn test -pl llm`
    - Service grouping and ranking logic is now under test
  </done>
</task>

</tasks>

---

## Verification

**Gap 1 — Telegram Delivery:**
After Task 1 completes, the weekly digest delivery chain is:
1. Spring scheduler calls `WeeklySectorDigestScheduler.sendWeeklySectorDigest()` every Sunday 17:00 IST
2. Method fetches digest from `SentimentAnalysisService.generateSectorDigestForLastWeek()`
3. Method sends via `TelegramNotificationService.sendMessage(digest)` — broadcasts to all configured chat IDs
4. Logs confirm delivery attempt

To verify end-to-end: Run application on Sunday 17:00 IST or manually trigger the scheduler (requires Telegram bot token and chat ID configured via environment variables or application.yml).

**Gap 2 — SignalEngine Integration Test:**
After Task 2 completes, the integration test covers the actual signal filtering in `SignalEngine.generateSignalsForSymbol()`:
- `testNegativeSentimentSuppressesBUYSignal()` verifies line 146 `return;` executes
- `testNeutralSentimentFlagsBUYSignal()` verifies line 152 warning flag assignment
- `testPositiveSentimentAllowsBUYSignal()` verifies normal save path
- `testSentimentCheckExceptionAllowsSignal()` verifies graceful degradation at line 159

All tests use Mockito to verify `signalRepository.save()` calls with expected warning flags.

**Gap 3 — SectorDigest Service Test:**
After Task 3 completes, SectorDigestTest exercises the actual service methods:
- `testGroupBySectorAndSentiment_*()` calls real `groupBySectorAndSentiment()` method (line 285+ of SentimentAnalysisService)
- `testGetTopSectors_*()` calls real `getTopSectors()` method (line 315+ of SentimentAnalysisService)
- `testGenerateSectorDigest_*()` calls real `generateSectorDigest()` method with date range and verifies output format

---

## Success Criteria

- [ ] **Task 1:** WeeklySectorDigestScheduler.java created, @Component and @Scheduled annotations correct, injects TelegramNotificationService (not TelegramService interface), calls sendMessage(digest) with single argument, logs properly, `mvn compile` succeeds
- [ ] **Task 2:** SignalEngineIntegrationTest.java created, 4 test methods cover all sentiment types, calls generateSignalsForSymbol(symbol) with correct signature (no LocalDate), all 4 tests pass, SignalEngine.generateSignalsForSymbol() is actually tested (not a stub)
- [ ] **Task 3:** SectorDigestTest.java enhanced with 3+ tests calling real service methods, assertions on grouping/ranking logic (not just non-empty), all tests pass
- [ ] **Build:** `mvn clean install -pl strategy,llm,api` succeeds
- [ ] **All 3 gaps closed:**
  - Telegram delivery wired: scheduler → sentiment service → telegram service (using actual TelegramNotificationService.sendMessage(String) method)
  - SignalEngine integration tested: real method under test with mocked sentiment, correct method signature used
  - SectorDigest service tested: real grouping and ranking methods called and verified

---

## Output

After completion, create `.planning/phases/04-llm-sentiment-layer/04-llm-sentiment-layer-04-SUMMARY.md` with:
- Gap closure status (all 3 closed or details of any blockers)
- Files created/modified
- Test results (task 2 and 3 test execution output)
- Commit hash(es)
- Readiness for Phase 5
