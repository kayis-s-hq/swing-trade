---
phase: 04-llm-sentiment-layer
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - strategy/src/main/java/com/swingtrade/strategy/SignalEngine.java
  - data/src/main/java/com/swingtrade/data/entity/SignalEntity.java
  - data/src/main/java/com/swingtrade/data/repository/SentimentResultRepository.java
  - llm/src/main/java/com/swingtrade/llm/service/SentimentAnalysisService.java
autonomous: true
requirements:
  - REQ-025
  - REQ-026
  - REQ-027
  - REQ-028
user_setup: []

user_setup: []
must_haves:
  truths:
    - BUY signals are checked against sentiment before being saved
    - NEGATIVE sentiment signals are suppressed from database
    - NEUTRAL sentiment signals are saved with a warning flag
    - POSITIVE sentiment signals are saved normally
    - SignalEngine logs sentiment decision for each signal
  artifacts:
    - path: strategy/src/main/java/com/swingtrade/strategy/SignalEngine.java
      provides: "Signal generation with sentiment filtering"
      min_lines: 280
      contains: "SentimentAnalysisService dependency"
    - path: data/src/main/java/com/swingtrade/data/entity/SignalEntity.java
      provides: "Signal with warning flag support"
      contains: "flag field with getter/setter"
    - path: data/src/main/java/com/swingtrade/data/repository/SentimentResultRepository.java
      provides: "SentimentResult CRUD operations"
      contains: "findBySymbolAndDate method"
    - path: llm/src/main/java/com/swingtrade/llm/service/SentimentAnalysisService.java
      provides: "Sentiment result persistence"
      contains: "SentimentResultRepository injection"
  key_links:
    - from: strategy/src/main/java/com/swingtrade/strategy/SignalEngine.java
      to: llm/src/main/java/com/swingtrade/llm/service/SentimentAnalysisService.java
      via: "sentiment check before signal save"
      pattern: "sentimentAnalysisService\\.analyzeStockSentiment"
    - from: llm/src/main/java/com/swingtrade/llm/service/SentimentAnalysisService.java
      to: data/src/main/java/com/swingtrade/data/repository/SentimentResultRepository.java
      via: "result persistence"
      pattern: "sentimentResultRepository\\.save"
---

<objective>
Integrate sentiment filtering into SignalEngine so that technical BUY signals are evaluated against news sentiment before being saved to the database.

Purpose: REQ-028 requires that BUY signals be suppressed if NEGATIVE sentiment exists, and flagged if NEUTRAL. This prevents trading against adverse news sentiment. The existing SentimentAnalysisService is not wired into SignalEngine.

Output: SignalEngine with SentimentAnalysisService integration, SignalEntity with warning flag support, SentimentResultRepository for persistence.
</objective>

<execution_context>
@/Users/kayisrahman/.claude/get-shit-done/workflows/execute-plan.md
@/Users/kayisrahman/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/ROADMAP.md
@.planning/REQUIREMENTS.md
@.planning/phases/04-llm-sentiment-layer/04-llm-sentiment-layer-RESEARCH.md

# Existing Code Context
@strategy/src/main/java/com/swingtrade/strategy/SignalEngine.java
@data/src/main/java/com/swingtrade/data/entity/SignalEntity.java
@llm/src/main/java/com/swingtrade/llm/service/SentimentAnalysisService.java
@llm/src/main/java/com/swingtrade/llm/service/NewsIngestionService.java
@llm/src/main/java/com/swingtrade/llm/service/SentimentAnalyzer.java
@core/src/main/java/com/swingtrade/domain/SentimentResult.java
@core/src/main/java/com/swingtrade/domain/Signal.java
@data/src/main/java/com/swingtrade/data/repository/SignalRepository.java

<!-- Key Types and Contracts -->
<interfaces>
<!-- From SignalEngine.java -->
From strategy/src/main/java/com/swingtrade/strategy/SignalEngine.java:
```java
@Component
public class SignalEngine {
    private final OhlcvCandleRepository candleRepository;
    private final SignalRepository signalRepository;
    private final SwingTradingStrategy strategy;

    @Scheduled(cron = "0 0 17 * * MON-FRI", zone = "Asia/Kolkata")
    public void generateDailySignals();

    @Transactional
    public void generateSignalsForSymbol(String symbol);
}
```

<!-- From SentimentAnalysisService.java -->
From llm/src/main/java/com/swingtrade/llm/service/SentimentAnalysisService.java:
```java
@Service
public class SentimentAnalysisService {
    public SentimentResult analyzeStockSentiment(String stockSymbol, LocalDate date);
    public Map<String, SentimentResult> analyzeMultipleStocks(List<String> stockSymbols, LocalDate date);
}

public class SentimentResult {
    public enum SentimentScore { POSITIVE, NEUTRAL, NEGATIVE }
    public boolean isPositive(), isNeutral(), isNegative(), supportsEntry();
    public String summary(), rawContent();
    public Double confidence();
    public LocalDate date();
}
```

<!-- From SignalEntity.java -->
From data/src/main/java/com/swingtrade/data/entity/SignalEntity.java:
```java
@Entity
@Table(name = "signals")
public class SignalEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private String symbol;

    @Column(nullable = false)
    private LocalDate date;

    @Column(name = "signal_type", nullable = false, length = 10)
    private String signalType;  // BUY, SELL, HOLD

    @Column(name = "confidence_score", precision = 5, scale = 4)
    private BigDecimal confidenceScore;

    @Column(columnDefinition = "TEXT")
    private String reasoning;

    // getters, setters...
}
```

<!-- From SentimentResult domain -->
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
    public boolean isPositive() { return SentimentScore.POSITIVE == score; }
    public boolean isNeutral() { return SentimentScore.NEUTRAL == score; }
    public boolean isNegative() { return SentimentScore.NEGATIVE == score; }
}
```
</interfaces>
</context>

<tasks>

<task type="auto">
  <name>Task 1: Add SentimentAnalysisService dependency to SignalEngine</name>
  <files>strategy/src/main/java/com/swingtrade/strategy/SignalEngine.java</files>
  <action>
Add SentimentAnalysisService as a constructor dependency in SignalEngine.

Steps:
1. Add import: `import com.swingtrade.llm.service.SentimentAnalysisService;`
2. Add private final field: `private final SentimentAnalysisService sentimentAnalysisService;`
3. Update constructor to accept SentimentAnalysisService parameter

Before constructor:
```java
public SignalEngine(OhlcvCandleRepository candleRepository,
                    SignalRepository signalRepository,
                    SwingTradingStrategy strategy) {
    this.candleRepository = candleRepository;
    this.signalRepository = signalRepository;
    this.strategy = strategy;
}
```

After constructor:
```java
public SignalEngine(OhlcvCandleRepository candleRepository,
                    SignalRepository signalRepository,
                    SwingTradingStrategy strategy,
                    SentimentAnalysisService sentimentAnalysisService) {
    this.candleRepository = candleRepository;
    this.signalRepository = signalRepository;
    this.strategy = strategy;
    this.sentimentAnalysisService = sentimentAnalysisService;
}
```

Important: The strategy module must depend on llm module. Verify llm/pom.xml exists and is a dependency of strategy/pom.xml.
  </action>
  <verify>
<automated>cd /Users/kayisrahman/Documents/workspace/ideas/swing-trade && mvn compile -pl strategy -am -q</automated>
  </verify>
  <done>
SignalEngine constructor accepts SentimentAnalysisService parameter and stores it as a final field. Code compiles successfully.
  </done>
</task>

<task type="auto">
  <name>Task 2: Add sentiment check before signal save in generateSignalsForSymbol</name>
  <files>strategy/src/main/java/com/swingtrade/strategy/SignalEngine.java</files>
  <action>
Modify generateSignalsForSymbol() to check sentiment before saving BUY signals.

Location: After line 130 (`Signal signal = strategy.analyze(domainCandles);`) and before SignalEntity creation (line 133).

Add sentiment check logic:

```java
Signal signal = strategy.analyze(domainCandles);

// NEW: Check sentiment before saving for BUY signals
if (signal.type() == Signal.SignalType.BUY) {
    SentimentResult sentiment = sentimentAnalysisService.analyzeStockSentiment(symbol, latestDate);

    if (sentiment.isNegative()) {
        logger.info("Suppressing BUY signal for {} on {} due to NEGATIVE sentiment (reasoning: {})",
                   symbol, latestDate, sentiment.summary());
        return; // Don't save - signal suppressed
    }

    if (sentiment.isNeutral()) {
        logger.info("Saving NEUTRAL sentiment signal for {} on {} (reasoning: {})",
                   symbol, latestDate, sentiment.summary());
        // SignalEntity will have warning flag set by next task
    }
}

// Save signal (existing code continues)
SignalEntity signalEntity = new SignalEntity();
signalEntity.setSymbol(symbol);
signalEntity.setDate(latestDate);
// ... rest of existing code

// After setting reasoning, add:
if (sentiment != null && sentiment.isNeutral()) {
    signalEntity.setFlag("NEUTRAL_SENTIMENT");
}
```

Important notes:
- Use the same `latestDate` that's already calculated from candles
- If sentiment check throws exception, log and continue (don't block signals)
- NEGATIVE sentiment = return early (don't save)
- NEUTRAL sentiment = save with WARNING flag
- POSITIVE sentiment = save normally (no changes needed)

Add exception handling wrapper:
```java
try {
    // sentiment check code
} catch (Exception e) {
    logger.warn("Failed to check sentiment for {} on {}: {}, saving signal anyway",
               symbol, latestDate, e.getMessage());
    // Continue saving signal - sentiment check failure should not block signals
}
```
  </action>
  <verify>
<automated>cd /Users/kayisrahman/Documents/workspace/ideas/swing-trade && mvn compile -pl strategy -am -q</automated>
  </verify>
  <done>
BUY signals are filtered by sentiment before being saved to database. NEGATIVE signals return early without saving. NEUTRAL signals are saved with WARNING flag. Exception handling ensures signal generation continues even if vLLM is unavailable.
  </done>
</task>

<task type="auto">
  <name>Task 3: Add warning flag field to SignalEntity</name>
  <files>data/src/main/java/com/swingtrade/data/entity/SignalEntity.java</files>
  <action>
Add a warning/flag field to SignalEntity to store NEUTRAL_SENTIMENT marker.

In SignalEntity.java, add after `indicators` field (around line 51):

```java
@Column(name = "warning_flag", length = 50)
private String warningFlag;

// Add constants
public static final String WARNING_NEUTRAL_SENTIMENT = "NEUTRAL_SENTIMENT";
public static final String WARNING_NONE = "";
```

Add getter and setter (after existing getters/setters, before inner classes):

```java
public String getWarningFlag() {
    return warningFlag;
}

public void setWarningFlag(String warningFlag) {
    this.warningFlag = warningFlag;
}
```

Update constructors to support flag:

```java
// If there's a constructor from domain, update it to accept flag
public SignalEntity(Signal signal, String warningFlag) {
    // existing field assignments...
    this.warningFlag = warningFlag;
}

// Or add a separate static factory method
public static SignalEntity fromDomain(Signal signal, String warningFlag) {
    SignalEntity entity = new SignalEntity();
    entity.setSymbol(signal.symbol());
    entity.setDate(signal.date());
    entity.setSignalType(signal.type().name());
    entity.setConfidenceScore(signal.confidence());
    entity.setReasoning(signal.reasoning());
    // ... other fields
    entity.setWarningFlag(warningFlag);
    return entity;
}
```

This allows Telegram notifications to show warning icon (⚠️) for NEUTRAL sentiment signals.
  </action>
  <verify>
<automated>cd /Users/kayisrahman/Documents/workspace/ideas/swing-trade && mvn compile -pl data -am -q</automated>
  </verify>
  <done>
SignalEntity has warningFlag field with getter/setter, constants for warning types, and updated domain conversion methods.
  </done>
</task>

<task type="auto">
  <name>Task 4: Add SentimentResultRepository interface</name>
  <files>data/src/main/java/com/swingtrade/data/repository/SentimentResultRepository.java</files>
  <action>
Create a new repository interface for SentimentResult entity. Since SentimentResult is a domain record in core module, we need a SentimentResultEntity in data module for JPA persistence, then create the repository.

First, verify if SentimentResultEntity exists. If not, create it:

data/src/main/java/com/swingtrade/data/entity/SentimentResultEntity.java:

```java
@Entity
@Table(name = "sentiment_results", indexes = {
    @Index(name = "idx_sentiment_symbol_date", columnList = "symbol, date", unique = true)
})
public class SentimentResultEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private String symbol;

    @Column(nullable = false)
    private LocalDate date;

    @Column(name = "sentiment_score", nullable = false, length = 20)
    private String sentimentScore;  // POSITIVE, NEUTRAL, NEGATIVE

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(name = "raw_content", columnDefinition = "TEXT")
    private String rawContent;

    @Column(name = "confidence", precision = 5, scale = 4)
    private Double confidence;

    @Column(name = "analyzed_at")
    private LocalDate analyzedAt;

    // Standard constructor
    public SentimentResultEntity() {}

    // Factory from domain
    public static SentimentResultEntity fromDomain(SentimentResult result) {
        SentimentResultEntity entity = new SentimentResultEntity();
        entity.setId(result.id());
        entity.setSymbol(result.symbol());
        entity.setDate(result.date());
        entity.setSentimentScore(result.score().name());
        entity.setSummary(result.summary());
        entity.setRawContent(result.rawContent());
        entity.setConfidence(result.confidence());
        entity.setAnalyzedAt(result.analyzedAt());
        return entity;
    }

    // To domain
    public SentimentResult toDomain() {
        return new SentimentResult(
            id, symbol, date,
            SentimentResult.SentimentScore.valueOf(sentimentScore),
            summary, rawContent, confidence, analyzedAt
        );
    }

    // Getters and setters...
}
```

Then create SentimentResultRepository:

data/src/main/java/com/swingtrade/data/repository/SentimentResultRepository.java:

```java
public interface SentimentResultRepository extends JpaRepository<SentimentResultEntity, Long> {

    Optional<SentimentResultEntity> findBySymbolAndDate(String symbol, LocalDate date);

    List<SentimentResultEntity> findAllBySymbol(String symbol, Pageable pageable);

    List<SentimentResultEntity> findAllByDateBetween(LocalDate startDate, LocalDate endDate);

    long countByDateBetweenAndSentimentScore(LocalDate startDate, LocalDate endDate, String sentimentScore);

    @Query("SELECT s.symbol, COUNT(s), s.sentimentScore " +
           "FROM SentimentResultEntity s " +
           "WHERE s.date BETWEEN :start AND :end " +
           "GROUP BY s.symbol, s.sentimentScore")
    List<Object[]> countBySymbolAndSentimentBetween(LocalDate start, LocalDate end);
}
```

This repository enables persistence of sentiment analysis results and retrieval for lookups, cache fallback, and batch queries.
  </action>
  <verify>
<automated>cd /Users/kayisrahman/Documents/workspace/ideas/swing-trade && mvn compile -pl data -am -q</automated>
  </verify>
  <done>
SentimentResultRepository interface exists with findBySymbolAndDate, save, findAllBySymbol, and count methods. SentimentResultEntity exists with proper JPA annotations and conversion methods.
  </done>
</task>

<task type="auto">
  <name>Task 5: Update SentimentAnalysisService to persist results</name>
  <files>llm/src/main/java/com/swingtrade/llm/service/SentimentAnalysisService.java</files>
  <action>
Add SentimentResultRepository dependency to SentimentAnalysisService and persist results after analysis.

Steps:
1. Add import: `import com.swingtrade.data.repository.SentimentResultRepository;`
2. Add private final field: `private final SentimentResultRepository sentimentResultRepository;`
3. Update constructor to accept SentimentResultRepository parameter

Constructor signature update (around line 60):

```java
@Autowired
public SentimentAnalysisService(
        VLLMClient vllmClient,
        SentimentAnalyzer sentimentAnalyzer,
        NewsIngestionService newsIngestionService,
        SentimentCacheService sentimentCacheService,
        SentimentResultRepository sentimentResultRepository,  // NEW
        @Value("${llm.sentiment.cache.max-size:100}") int maxCacheSize,
        @Value("${llm.sentiment.cache.expiry-minutes:60}") long cacheExpiryMinutes,
        @Value("${llm.sentiment.cache.enabled:true}") boolean enableCaching,
        @Value("${llm.sentiment.default-confidence:0.75}") double defaultConfidence) {

    this.vllmClient = vllmClient;
    this.sentimentAnalyzer = sentimentAnalyzer;
    this.newsIngestionService = newsIngestionService;
    this.sentimentCacheService = sentimentCacheService;
    this.sentimentResultRepository = sentimentResultRepository;  // NEW

    // ... rest of existing init code
}
```

After building SentimentResult in analyzeStockSentiment (around line 137), persist it:

```java
// Build and cache result
SentimentResult result = buildSentimentResult(stockSymbol, date, analysisResult, articles);

// NEW: Persist to database
try {
    SentimentResultEntity entity = SentimentResultEntity.fromDomain(result);
    sentimentResultRepository.save(entity);
    logger.debug("Persisted sentiment result for {} on {}", stockSymbol, date);
} catch (Exception e) {
    logger.warn("Failed to persist sentiment result for {}: {}", stockSymbol, e.getMessage());
    // Don't fail the analysis if persistence fails
}

// Cache the result
if (enableCaching) {
    cacheSentimentResult(cacheKey, result, analysisResult);
}
```

This ensures sentiment results are persisted to database and can be recovered as cache fallback.
  </action>
  <verify>
<automated>cd /Users/kayisrahman/Documents/workspace/ideas/swing-trade && mvn compile -pl llm -am -q</automated>
  </verify>
  <done>
SentimentAnalysisService saves SentimentResult to database after analysis via SentimentResultRepository. Compilation succeeds with proper dependency injection.
  </done>
</task>

</tasks>

<verification>
1. Compile all modules: `mvn clean install -DskipTests`
2. Verify SignalEngine integration: Check that sentiment check runs before signal save
3. Verify database schema: Check that signals table has warning_flag column (may need Flyway migration)
4. Verify persistence: Check sentiment_results table is created with proper indexes
</verification>

<success_criteria>
- [ ] SentimentResultRepository interface created with findBySymbolAndDate, save, findAllBySymbol methods
- [ ] SentimentResultEntity created with JPA annotations and fromDomain/toDomain methods
- [ ] SignalEntity updated with warningFlag field, getter/setter, and constants
- [ ] SignalEngine has SentimentAnalysisService dependency in constructor
- [ ] SignalEngine suppresses BUY signals when sentiment is NEGATIVE (returns early)
- [ ] SignalEngine saves BUY signals when sentiment is NEUTRAL with warning flag
- [ ] SignalEngine handles sentiment check exceptions gracefully (doesn't block signals)
- [ ] SentimentAnalysisService persists results via SentimentResultRepository
- [ ] Build compiles without errors
- [ ] Existing tests still pass
</success_criteria>

<output>
After completion, create `.planning/phases/04-llm-sentiment-layer/{phase}-{plan}-SUMMARY.md`
</output>
