---
phase: 04-llm-sentiment-layer
plan: 02
type: execute
wave: 2
depends_on:
  - "04-llm-sentiment-layer-01"
files_modified:
  - llm/src/main/java/com/swingtrade/llm/service/SentimentAnalysisService.java
  - broker/src/main/java/com/swingtrade/broker/telegram/TelegramService.java
  - api/src/main/java/com/swingtrade/api/SwingTradeApiApplication.java
autonomous: true
requirements:
  - REQ-029
user_setup: []

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
user_setup: []
must_haves:
  truths:
    - Weekly sector digest runs every Sunday at 17:00 IST
    - Digest includes sentiment summary by sector (POSITIVE/NEUTRAL/NEGATIVE counts per sector)
    - Digest identifies top 3 positive sectors and top 3 negative sectors
    - Digest is sent via Telegram notification
    - Digest includes date range and total stocks analyzed
  artifacts:
    - path: llm/src/main/java/com/swingtrade/llm/service/SentimentAnalysisService.java
      provides: "Sector digest generation logic"
      contains: "generateSectorDigest() and sendSectorDigest() methods"
    - path: llm/src/main/java/com/swingtrade/llm/config/LlmConfig.java
      provides: "Scheduled job configuration"
      contains: "@Scheduled for weekly digest"
    - path: api/src/main/java/com/swingtrade/api/SwingTradeApiApplication.java
      provides: "Scheduling enabled"
      contains: "@EnableScheduling annotation"
  key_links:
    - from: llm/src/main/java/com/swingtrade/llm/config/LlmConfig.java
      to: llm/src/main/java/com/swingtrade/llm/service/SentimentAnalysisService.java
      via: "SentimentAnalysisService bean dependency"
      pattern: "SentimentAnalysisService sentimentAnalysisService"
    - from: llm/src/main/java/com/swingtrade/llm/service/SentimentAnalysisService.java
      to: broker/src/main/java/com/swingtrade/broker/telegram/TelegramService.java
      via: "Telegram notification"
      pattern: "telegramService\\.sendMessage"
---

<objective>
Implement weekly sector digest scheduled job that runs every Sunday at 17:00 IST and sends sentiment summary by sector via Telegram.

Purpose: REQ-029 requires a weekly digest to provide portfolio oversight and sector trend analysis. This helps identify which sectors are showing positive/negative sentiment patterns over the past week.

Output: Scheduled job in LlmConfig that generates sector digest from sentiment data and sends via TelegramService.
</objective>

<execution_context>
@/Users/kayisrahman/.claude/get-shit-done/workflows/execute-plan.md
@/Users/kayisrahman/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/ROADMAP.md
@.planning/phases/04-llm-sentiment-layer/04-llm-sentiment-layer-RESEARCH.md

# Existing Code Context
@llm/src/main/java/com/swingtrade/llm/service/SentimentAnalysisService.java
@broker/src/main/java/com/swingtrade/broker/telegram/TelegramService.java
@core/src/main/java/com/swingtrade/domain/Stock.java
@core/src/main/java/com/swingtrade/domain/SentimentResult.java
@data/src/main/java/com/swingtrade/data/repository/SentimentResultRepository.java

# Key Types and Contracts
<interfaces>
<!-- From Stock.java -->
From core/src/main/java/com/swingtrade/domain/Stock.java:
```java
public record Stock(String symbol, String name, Sector sector, Exchange exchange) {
    public enum Sector { AUTO, BANK, CHEMICAL, CHEMICALS, CONSUMER, ENERGY, FINANCIAL, HEALTHCARE, INDUSTRIAL, INFRASTRUCTURE, IT, MEDIA, METAL, OIL_GAS, PHARMA, POWER, TELECOM, TEXTILE }
    public enum Exchange { NSE, BSE }
}
```

<!-- From SentimentResult.java -->
From core/src/main/java/com/swingtrade/domain/SentimentResult.java:
```java
public record SentimentResult(
    Long id,
    String symbol,
    LocalDate date,
    SentimentScore score,  // POSITIVE, NEUTRAL, NEGATIVE
    String summary,
    String rawContent,
    Double confidence,
    LocalDate analyzedAt
) {
    public boolean isPositive(), isNeutral(), isNegative();
}
```

<!-- From TelegramService.java -->
From broker/src/main/java/com/swingtrade/broker/telegram/TelegramService.java:
```java
@Component
public class TelegramService {
    public CompletableFuture<Void> sendMessage(String chatId, String message);
    // Supports markdown formatting
}
```

<!-- From SentimentResultRepository -->
From data/src/main/java/com/swingtrade/data/repository/SentimentResultRepository.java:
```java
public interface SentimentResultRepository extends JpaRepository<SentimentResultEntity, Long> {
    List<SentimentResultEntity> findAllByDateBetween(LocalDate startDate, LocalDate endDate);
    // ... other methods
}
```
</interfaces>
</context>

<tasks>

<task type="auto" tdd="true">
  <name>Task 1: Add generateSectorDigest method to SentimentAnalysisService</name>
  <files>
    llm/src/main/java/com/swingtrade/llm/service/SentimentAnalysisService.java
    llm/src/test/java/com/swingtrade/llm/service/SentimentAnalysisServiceTest.java
  </files>
  <behavior>
    - Test generateSectorDigest() returns valid summary with sector counts
    - Test top positive sectors are correctly identified by POSITIVE count
    - Test top negative sectors are correctly identified by NEGATIVE count
    - Test empty sector handling when no sentiment data exists
    - Test date range filtering works correctly
  </behavior>
  <action>
    Add generateSectorDigest() method to SentimentAnalysisService that:

    1. Fetches sentiment results from last week using SentimentResultRepository
    2. Groups by stock sector (gets sector from Stock entity for each symbol)
    3. Counts POSITIVE/NEUTRAL/NEGATIVE per sector
    4. Identifies top 3 positive sectors (by POSITIVE count)
    5. Identifies top 3 negative sectors (by NEGATIVE count)
    6. Returns formatted digest string for Telegram

    Method signature:
    ```java
    public String generateSectorDigest(LocalDate startDate, LocalDate endDate) {
        // Fetch all sentiment results in date range
        List<SentimentResult> results = sentimentResultRepository
            .findAllByDateBetween(startDate, endDate)
            .stream()
            .map(SentimentResultEntity::toDomain)
            .collect(Collectors.toList());

        // Group by sector and count sentiment
        Map<Sector, Map<SentimentResult.SentimentScore, Long>> sectorCounts =
            groupBySectorAndSentiment(results);

        // Get sector names for each symbol (need Stock lookup)
        // Identify top sectors
        // Format output string
    }
    ```

    Output format (Telegram-ready with emoji and markdown):
    ```
    📊 Weekly Sector Sentiment Digest
    Week of: 2026-03-16 to 2026-03-22

    Top Positive Sectors:
    1. BANK - 45 POS, 12 NEU, 8 NEG
    2. AUTO - 38 POS, 15 NEU, 10 NEG
    3. IT - 32 POS, 18 NEU, 12 NEG

    Top Negative Sectors:
    1. METAL - 10 POS, 15 NEU, 35 NEG
    2. POWER - 12 POS, 14 NEU, 30 NEG
    3. PHARMA - 15 POS, 20 NEU, 28 NEG

    Total stocks analyzed: 487
    Positive signals: 523 | Neutral: 312 | Negative: 198
    ```

    Helper methods to implement:
    - groupBySectorAndSentiment(List<SentimentResult>) -> Map<Sector, Map<SentimentScore, Long>>
    - getTopSectors(Map<Sector, Map<SentimentScore, Long>>, int count, boolean positive) -> List<Sector>
    - formatSectorStats(Sector sector, Map<SentimentScore, Long>) -> String
  </action>
  <verify>
<automated>cd /Users/kayisrahman/Documents/workspace/ideas/swing-trade && mvn test -pl llm -Dtest=SentimentAnalysisServiceTest#testGenerateSectorDigest -q</automated>
  </verify>
  <done>
generateSectorDigest() produces accurate sector sentiment summaries with top positive/negative sectors identified correctly. Returns formatted string ready for Telegram.
  </done>
</task>

<task type="auto">
  <name>Task 2: Add sendSectorDigest method with Telegram integration</name>
  <files>llm/src/main/java/com/swingtrade/llm/service/SentimentAnalysisService.java</files>
  <action>
    Add sendSectorDigest() method that:

    1. Determines date range (last Sunday to last Saturday)
    2. Calls generateSectorDigest() to create message
    3. Sends via TelegramService to main chat ID
    4. Logs confirmation and handles errors gracefully

    Method signature:
    ```java
    @Autowired
    private TelegramService telegramService;

    public CompletableFuture<Void> sendSectorDigest() {
        ZoneId ist = ZoneId.of("Asia/Kolkata");
        LocalDate endDate = LocalDate.now(ist);
        LocalDate startDate = getPreviousSunday(endDate).minusDays(6);

        String digest = generateSectorDigest(startDate, endDate);

        // Send to main Telegram chat (configurable chat ID)
        String chatId = TelegramService.MAIN_CHAT_ID;  // Or read from config
        return telegramService.sendMessage(chatId, digest)
            .exceptionally(ex -> {
                logger.error("Failed to send sector digest to Telegram", ex);
                return null;
            });
    }

    private LocalDate getPreviousSunday(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        int daysSinceSunday = day.getValue() == DayOfWeek.SUNDAY ? 0 : day.getValue() - DayOfWeek.SUNDAY.getValue();
        return date.minusDays(daysSinceSunday);
    }
    ```

    Note:
    - Use existing TelegramService from broker module (already configured with bot token)
    - Handle exceptions gracefully - don't crash scheduler if Telegram fails
    - Consider making chat ID configurable via application.properties
  </action>
  <verify>
<automated>cd /Users/kayisrahman/Documents/workspace/ideas/swing-trade && mvn compile -pl llm -am -q</automated>
  </verify>
  <done>
sendSectorDigest() calls generateSectorDigest() and sends formatted message via TelegramService. Exception handling ensures scheduler continues even if Telegram is unavailable.
  </done>
</task>

<task type="auto">
  <name>Task 3: Add @Scheduled annotation to LlmConfig</name>
  <files>llm/src/main/java/com/swingtrade/llm/config/LlmConfig.java</files>
  <action>
    Add @Scheduled annotation to trigger weekly digest on Sundays at 17:00 IST.

    Spring cron for Sunday 17:00 IST = 11:00 UTC:
    ```
    0 0 11 * * SUN
    ```

    Implementation:
    ```java
    @Configuration
    @EnableScheduling  // Ensure this is present
    public class LlmConfig {

        @Autowired
        private SentimentAnalysisService sentimentAnalysisService;

        @Scheduled(cron = "0 0 11 * * SUN", zone = "Asia/Kolkata")
        public void sendWeeklySectorDigest() {
            logger.info("Starting weekly sector digest generation");
            try {
                sentimentAnalysisService.sendSectorDigest();
                logger.info("Weekly sector digest sent successfully");
            } catch (Exception e) {
                logger.error("Error sending weekly sector digest", e);
                // Don't rethrow - scheduler should continue
            }
        }
    }
    ```

    Important:
    - Ensure @EnableScheduling is present on main application class (verify SwingTradeApiApplication)
    - Handle exceptions gracefully - don't crash scheduler if Telegram or analysis fails
    - Log start and completion of digest generation
  </action>
  <verify>
<automated>cd /Users/kayisrahman/Documents/workspace/ideas/swing-trade && mvn compile -pl llm -am -q</automated>
  </verify>
  <done>
Weekly sector digest job is scheduled to run every Sunday at 17:00 IST. @EnableScheduling is present in LlmConfig.
  </done>
</task>

<task type="auto">
  <name>Task 4: Verify @EnableScheduling on main application</name>
  <files>api/src/main/java/com/swingtrade/api/SwingTradeApiApplication.java</files>
  <action>
    Verify @EnableScheduling is present on the main application class.

    Check if already present:
    ```java
    @SpringBootApplication
    @EnableScheduling  // Verify this exists
    public class SwingTradeApiApplication {
        public static void main(String[] args) {
            SpringApplication.run(SwingTradeApiApplication.class, args);
        }
    }
    ```

    If not present, add:
    ```java
    @SpringBootApplication
    @EnableScheduling  // ADD THIS
    public class SwingTradeApiApplication {
        // ... existing code
    }
    ```

    Also verify:
    - LlmConfig is component-scanned (check it's in a subpackage of SwingTradeApiApplication)
    - All required dependencies are wired correctly
    - Application can start with scheduled jobs enabled
  </action>
  <verify>
<automated>cd /Users/kayisrahman/Documents/workspace/ideas/swing-trade && grep -r "@EnableScheduling" api/src/main/java/</automated>
  </verify>
  <done>
@EnableScheduling is present on SwingTradeApiApplication. Scheduled jobs will be enabled when application starts.
  </done>
</task>

<task type="auto">
  <name>Task 5: Add configuration for Telegram chat ID</name>
  <files>
    llm/src/main/java/com/swingtrade/llm/config/LlmConfig.java
    api/src/main/resources/application.properties
  </files>
  <action>
    Add configuration property for Telegram digest chat ID.

    In application.properties (add to llm section):
    ```properties
    llm.telegram.digest-chat-id=${TELEGRAM_DIGEST_CHAT_ID:-123456789}
    ```

    In LlmConfig.java:
    ```java
    @Value("${llm.telegram.digest-chat-id:123456789}")
    private String digestChatId;

    @Bean
    public SentimentAnalysisService sentimentAnalysisService(...) {
        // ... existing bean creation
        // Send digestChatId to service or use via TelegramService
    }
    ```

    Alternatively, add to SentimentAnalysisService:
    ```java
    @Value("${llm.telegram.digest-chat-id:123456789}")
    private String digestChatId;
    ```

    This allows users to configure the Telegram chat ID via environment variable.
  </action>
  <verify>
<automated>cd /Users/kayisrahman/Documents/workspace/ideas/swing-trade && grep "digest-chat-id" api/src/main/resources/application.properties</automated>
  </verify>
  <done>
Telegram digest chat ID is configurable via application.properties or environment variable.
  </done>
</task>

</tasks>

<verification>
1. Compile check: `mvn compile -pl llm -am -q`
2. Schedule verification: Start application and check logs for "Starting weekly sector digest generation" on Sunday at 17:00 IST
3. Content verification: Digest should include sector breakdown with POSITIVE/NEUTRAL/NEGATIVE counts
4. Delivery verification: Check Telegram for weekly digest message with correct formatting
5. Exception handling: Stop Telegram service and verify scheduler continues running
</verification>

<success_criteria>
- [ ] generateSectorDigest() correctly groups sentiment by stock sector
- [ ] Top 3 positive and negative sectors are identified correctly by count
- [ ] sendSectorDigest() sends formatted message via TelegramService
- [ ] @Scheduled job runs every Sunday at 17:00 IST (11:00 UTC)
- [ ] Exception in digest does not crash scheduler
- [ ] Digest includes date range and total stocks analyzed
- [ ] Chat ID is configurable via application.properties
- [ ] @EnableScheduling present on main application
</success_criteria>

<output>
After completion, verify on Sunday by checking:
- Application logs show digest generation at 17:00 IST
- Telegram receives sector digest message
- Message format matches expected structure with emoji and sector stats
</output>
