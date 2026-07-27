# LLM Sentiment Layer — Implementation Plan

**Date:** 2026-07-05
**Status:** Draft — awaiting approval
**Scope:** Full LLM sentiment pipeline for NSE/BSE swing trading system
**vLLM endpoint:** `https://u425-84cf-d540ae09.singapore-b.gpuhub.com:8443/v1`
**Model:** Qwen3-30B-AWQ

---

## 1. Current State Analysis

### What Already Exists (substantial sentiment pipeline)

| Component | Module | Location | Purpose |
|-----------|--------|----------|---------|
| `SentimentAnalysisService` | llm | `service/SentimentAnalysisService.java` | Orchestrates news → LLM → cache → DB → signal |
| `NewsIngestionService` | llm | `service/NewsIngestionService.java` | RSS fetching from 5 Indian feeds (NSE, MoneyControl, ET, Bloomberg, Reuters) |
| `VLLMClient` | llm | `client/VLLMClient.java` | WebClient-based `/chat/completions` client, OpenAI-compatible |
| `SentimentAnalyzer` | llm | `service/SentimentAnalyzer.java` | Prompt builder with system/user templates |
| `SentimentCacheService` | llm | `service/SentimentCacheService.java` | In-memory TTL cache with LRU |
| `NewsFilterService` | llm | `service/NewsFilterService.java` | Relevance scoring, spam detection, trading keywords |
| `SentimentResult` (domain) | core | `domain/SentimentResult.java` | Record: id, symbol, date, score, summary, rawContent, confidence, analyzedAt |
| `SentimentResultEntity` | data | `entity/SentimentResultEntity.java` | JPA entity mapped to `sentiment_results` table |
| `SentimentResultRepository` | data | `repository/SentimentResultRepository.java` | JPA repo with findBySymbolAndDate, findAllByDateBetween |
| `SignalExecutionJob` | api | `SignalExecutionJob.java` | Polls unprocessed BUY signals, executes via PaperTradingEngine |
| `WeeklySectorDigestScheduler` | api | `scheduler/WeeklySectorDigestScheduler.java` | Sunday 17:00 IST, sends digest via Telegram |
| `LlmConfig` | llm | `config/LlmConfig.java` | Config + weekly digest scheduler |
| `PaperTradingEngine` | broker | `engine/PaperTradingEngine.java` | Core trading engine with executeSignal() |
| `BrokerService` | broker | `service/BrokerService.java` | Broker abstraction interface |
| `PaperTradingServiceImpl` | broker | `service/PaperTradingServiceImpl.java` | Implements BrokerService, delegates to PaperTradingEngine |
| `SettingsController` | api | `controller/SettingsController.java` | In-memory Map.of() — settings lost on restart |
| `SettingsView.vue` | dashboard | `views/SettingsView.vue` | Broker + Trading Config + Health sections |
| `settings.ts` | dashboard | `stores/settings.ts` | localStorage-based reactive settings |

### What Does NOT Exist

- NSE corporate announcements endpoint (symbol-specific)
- Google News RSS per-symbol fetching
- PDF earnings extraction
- Discord webhook notifications (Telegram-based system exists in broker/telegram/)
- Signal filter between signals and paper trading
- LLM accuracy tracking
- REST API for sentiment/news/PDF
- Durable settings persistence (DB table)
- Rome library dependency

### Known Issues

| Issue | Severity | Fix |
|-------|----------|-----|
| Two V4 migrations (duplicate version) | **CRITICAL** — Flyway will fail | Merge into single V4 |
| vLLM URL outdated | MEDIUM | Update to new GPU endpoint |
| Model name is `claude-sonnet-4-6` | MEDIUM | Set to `Qwen3-30B-AWQ` |
| Settings lost on restart | MEDIUM | Add app_settings DB table |

---

## 2. Architecture Decisions

### 2.1 Extend vs New Services

**Decision: Extend existing code.** The sentiment pipeline is already 80% built. New services should integrate with existing ones, not duplicate them.

- `NewsIngestionService` gets NSE + Google News methods (extends existing RSS pattern)
- `SentimentAnalyzer` gets new prompt template (replaces existing one)
- `SentimentAnalysisService` accepts earnings data parameter (extends existing flow)
- `SignalExecutionJob` routes through new `SignalFilterService` (wraps existing flow)
- `WeeklySectorDigestScheduler` replaces Telegram → Discord (replaces existing flow)

### 2.2 Prompt Format

**Decision: New format with Jackson JSON parsing.**

Existing prompt returns: `{sentiment, confidence, reasoning, keyFactors, tradingImplication}`
New prompt returns: `{score, confidence, summary, red_flags[], catalysts[]}`

Rationale: `red_flags` and `catalysts` are critical for swing trade decisions. New format is more actionable.

Parsing: Replace regex-based extraction with Jackson `JsonNode` — more robust, handles nested arrays.

### 2.3 Notification Channel

**Decision: Replace Telegram with Discord.**

- Discord webhooks are simpler (no bot token management)
- Spec explicitly requests Discord
- Telegram service stays in codebase but is unused
- New `DiscordNotificationService` uses WebClient (no new dependency)

### 2.4 Settings Persistence

**Decision: DB table + env-var override.**

Create `app_settings` key/value table. Spring `@ConfigurationProperties` reads from DB, env vars override. Dashboard can update at runtime.

### 2.5 NSE API Reliability

**Decision: Retry with exponential backoff + fallback.**

NSE blocks automated requests. Pattern:
1. Try NSE with User-Agent header
2. Retry 3x with backoff (1s, 2s, 4s)
3. If all retries fail → return empty list
4. Google News + existing RSS feeds provide coverage

### 2.6 RSS Parsing Library

**Decision: Add Rome library.**

Rome (`com.rometools:rome:2.1.0`) provides clean RSS/Atom parsing with proper namespace handling. Existing javax.xml DOM parser works but is fragile for complex feeds.

### 2.7 SentimentResult Schema Extension

**Decision: Add `red_flags` and `catalysts` fields to domain record.**

`SentimentResult` record needs new fields. Migration adds columns to `sentiment_results` table. Entity and repository updated accordingly.

---

## 3. Flyway Migration Plan

### 3.1 Version Reordering

Current state has two V4 migrations — Flyway will fail. Fix:

| Old | New | Purpose |
|-----|-----|---------|
| V4 (fix_sentiment) | **V4** | Drop/recreate sentiment_results with correct schema |
| V4 (signal_processed) | **MERGED into V4** | Add processed BOOLEAN to signals table |
| V5 (trade_labels) | **V5** | No content change |
| V6 (fyers_symbol_master) | **V6** | No content change |
| V7 (signal_strategy) | **V7** | No content change |
| — | **V8** | New tables: news_items, pdf_extractions, sentiment_accuracy |
| — | **V9** | New table: app_settings |

### 3.2 V4 Changes

Merge the two V4 migrations into one:

```sql
-- V4__fix_sentiment_results.sql (updated)
DROP TABLE IF EXISTS sentiment_results;

CREATE TABLE sentiment_results (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(10) NOT NULL,
    date DATE NOT NULL,
    sentiment_score VARCHAR(20) NOT NULL,
    summary TEXT,
    raw_content TEXT,
    confidence REAL,
    analyzed_at DATE,
    -- NEW COLUMNS for red_flags and catalysts
    red_flags TEXT[],          -- PostgreSQL array
    catalysts TEXT[],          -- PostgreSQL array
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_sentiment_results_symbol_date ON sentiment_results(symbol, date);

-- Add processed flag to signals table (merge from old V4)
ALTER TABLE signals ADD COLUMN IF NOT EXISTS processed BOOLEAN DEFAULT FALSE;

-- Update trigger
CREATE TRIGGER update_sentiment_results_updated_at BEFORE UPDATE ON sentiment_results
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
```

### 3.3 V8 — New Intelligence Tables

```sql
-- V8__create_intelligence_tables.sql

-- news_items: fetched headlines
CREATE TABLE news_items (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(10) NOT NULL,
    headline TEXT NOT NULL,
    source VARCHAR(50) NOT NULL,  -- 'nse', 'google_news', 'rss'
    published_at TIMESTAMP NOT NULL,
    url TEXT,
    raw_content TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- pdf_extractions: extracted earnings data
CREATE TABLE pdf_extractions (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(10) NOT NULL,
    document_type VARCHAR(20) NOT NULL,  -- 'earnings', 'annual_report', 'press_release'
    extracted_json JSONB NOT NULL,
    source_url TEXT,
    extraction_date DATE NOT NULL,
    model_used VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- sentiment_accuracy: LLM prediction accuracy tracking
CREATE TABLE sentiment_accuracy (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(10) NOT NULL,
    signal_date DATE NOT NULL,
    sentiment_score VARCHAR(20) NOT NULL,
    actual_outcome VARCHAR(20) NOT NULL,  -- 'TARGET_HIT', 'STOP_LOSS', 'HOLD'
    was_correct BOOLEAN,
    pnl_pct DECIMAL(10,2),
    recorded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Indexes
CREATE INDEX idx_news_symbol_date ON news_items(symbol, published_at);
CREATE INDEX idx_news_headline ON news_items(LOWER(headline));
CREATE INDEX idx_pdf_symbol_date ON pdf_extractions(symbol, extraction_date);
CREATE INDEX idx_sentiment_accuracy_symbol_date ON sentiment_accuracy(symbol, signal_date);
```

### 3.4 V9 — App Settings Table

```sql
-- V9__create_app_settings.sql

CREATE TABLE app_settings (
    id BIGSERIAL PRIMARY KEY,
    key VARCHAR(64) NOT NULL UNIQUE,
    value TEXT,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_app_settings_key ON app_settings(key);

-- Seed default values
INSERT INTO app_settings (key, value) VALUES
    ('llm.vllm.base_url', 'https://u425-84cf-d540ae09.singapore-b.gpuhub.com:8443/v1'),
    ('llm.vllm.model', 'Qwen3-30B-AWQ'),
    ('llm.pdf.base_url', ''),
    ('llm.pdf.model', 'gemma-4-E2B'),
    ('discord.webhook.url', ''),
    ('discord.webhook.enabled', 'false');
```

---

## 4. Detailed Implementation — By Module

### 4.1 Data Module

#### 4.1.1 `AppSettingEntity.java` (NEW)

```java
@Entity
@Table(name = "app_settings", indexes = {@Index(name = "idx_app_settings_key", columnList = "key", unique = true)})
public class AppSettingEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 64, unique = true)
    private String key;
    
    @Column(columnDefinition = "TEXT")
    private String value;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    // JPA default constructor, getters, setters
    // fromEnv(key, defaultValue) — checks System.getProperty then System.getenv
}
```

#### 4.1.2 `AppSettingRepository.java` (NEW)

```java
@Repository
public interface AppSettingRepository extends JpaRepository<AppSettingEntity, Long> {
    Optional<AppSettingEntity> findByKey(String key);
    @Modifying
    @Query("UPDATE AppSettingEntity a SET a.value = :value, a.updatedAt = CURRENT_TIMESTAMP WHERE a.key = :key")
    void updateValue(String key, String value);
}
```

#### 4.1.3 `AppSettingsService.java` (NEW)

```java
@Service
public class AppSettingsService {
    // Read: key → check env var → check system property → check DB → return default
    String get(String key, String defaultValue);
    // Write: env var check (read-only in prod) → DB update
    void set(String key, String value);
    // Get all settings as Map
    Map<String, String> getAll();
}
```

#### 4.1.4 `SentimentResultEntity.java` (MODIFY)

Add fields:
```java
@Column(name = "red_flags", columnDefinition = "TEXT[]")
private String[] redFlags;

@Column(name = "catalysts", columnDefinition = "TEXT[]")
private String[] catalysts;
```

Update `fromDomain()` and `toDomain()` to map new fields.

#### 4.1.5 `SentimentAccuracyTracker.java` (NEW)

```java
@Service
public class SentimentAccuracyTracker {
    void recordOutcome(String symbol, LocalDate signalDate)
    // Called when paper position closes
    // 1. Look up SentimentResult for symbol + signalDate
    // 2. Check actual outcome from trade/position data
    // 3. was_correct = (score == POSITIVE && TARGET_HIT) || (score == NEGATIVE && STOP_LOSS)
    // 4. Insert into sentiment_accuracy table
    
    AccuracyStats getAccuracyStats()
    // Overall accuracy, per-symbol accuracy, per-sentiment breakdown
    
    record AccuracyStats(int total, int correct, double accuracyPct,
                         Map<String, Integer> bySentiment, Map<String, Integer> bySymbol)
}
```

#### 4.1.6 `SentimentResultRepository.java` (MODIFY)

Add query:
```java
@Query("SELECT s FROM SentimentResultEntity s WHERE s.symbol = :symbol ORDER BY s.date DESC LIMIT 1")
Optional<SentimentResultEntity> findLatestBySymbol(@Param("symbol") String symbol);
```

### 4.2 LLM Module

#### 4.2.1 `EarningsData.java` (NEW)

```java
public record EarningsData(
    String symbol,
    String quarter,
    BigDecimal revenue,
    BigDecimal netProfit,
    BigDecimal eps,
    BigDecimal ebitda,
    String guidance,
    LocalDate extractionDate
) {
    public static EarningsData fromJson(Map<String, Object> json) {
        // Parse JSON from LLM response
    }
}
```

#### 4.2.2 `PdfExtractionService.java` (NEW)

```java
@Service
@Slf4j
public class PdfExtractionService {
    private final WebClient webClient;
    private final String pdfBaseUrl;
    private final String pdfModel;
    private final PdfExtractionRepository pdfRepo;
    
    EarningsData extractEarningsPdf(String symbol, String pdfUrl) {
        // 1. Download PDF as byte[] via WebClient
        // 2. Encode as base64
        // 3. Build prompt: "extract revenue, net_profit, eps, ebitda, guidance as JSON only"
        // 4. POST to pdfBaseUrl/v1/chat/completions with base64 image + text prompt
        // 5. Parse JSON response → EarningsData
        // 6. Store to pdf_extractions table
        // 7. Return EarningsData
        
        // Graceful degradation: if endpoint unreachable → return null, log warning
    }
    
    boolean isAvailable() {
        // Check if pdfBaseUrl is configured and reachable
    }
}
```

**Note:** The Pi 5 llama-server endpoint uses a different API than vLLM. It may be a standard llama.cpp server or Ollama-compatible. The implementation should:
- Try `/v1/chat/completions` first (OpenAI-compatible)
- Fall back to `/completion` if that fails
- Handle base64 image input if llama-server supports multimodal

#### 4.2.3 `NewsIngestionService.java` (MODIFY)

**Add new methods:**

```java
// NSE corporate announcements — symbol-specific
List<NewsArticle> fetchNseAnnouncements(String symbol) {
    // GET https://www.nseindia.com/api/corp-info?symbol={symbol}
    // Header: User-Agent: SwingTrade/1.0 (Spring Boot default UA is "Java/..." which NSE blocks)
    // Retry: 3 attempts, exponential backoff (1s, 2s, 4s)
    // If response is HTML (blocked) → return empty list
    // Parse JSON: [{"subject": ..., "description": ..., "date": ...}]
    // Return list of NewsArticle
}

// Google News RSS — symbol-specific
List<NewsArticle> fetchGoogleNews(String symbol) {
    // GET https://news.google.com/rss/search?q={symbol}+NSE+stock&hl=en-IN&gl=IN&ceid=IN:en
    // Parse with Rome library: SyndFeedInput → SyndFeed → SyndEntry
    // Extract: title, link, pubDate, source
    // Return list of NewsArticle
}

// Unified fetch with dedup
List<NewsArticle> fetchAllNews(String symbol) {
    // 1. Call both NSE + Google News in parallel (CompletableFuture)
    // 2. Combine results
    // 3. Deduplicate by normalized headline (lowercase, trim) + published_at within 1 hour
    // 4. Filter: last 7 days only
    // 5. Sort by published_at desc
    // 6. Log: "fetched N headlines for {symbol}"
    // 7. Return
}

// Scheduled job
@Scheduled(cron = "0 0 16 * * MON-FRI", zone = "Asia/Kolkata")
public void fetchNewsForActivePositions() {
    // 1. Get all symbols with open paper positions
    // 2. Get all symbols with BUY signals today
    // 3. For each unique symbol, call fetchAllNews(symbol)
    // 4. Store results to news_items table
}
```

**Add Rome dependency to llm/pom.xml:**
```xml
<dependency>
    <groupId>com.rometools</groupId>
    <artifactId>rome</artifactId>
    <version>2.1.0</version>
</dependency>
```

#### 4.2.4 `SentimentAnalyzer.java` (MODIFY)

**Add new method:**

```java
public List<Map<String, String>> createSentimentAnalysisPrompt(
    String symbol, List<String> headlines, EarningsData earningsData) {
    
    // System prompt: Indian equity market analyst role
    // User prompt:
    //   "You are a financial analyst specialising in Indian equity markets.
    //    Analyse the following for a swing trade entry decision on {symbol}.
    //    
    //    Recent news headlines (last 7 days):
    //    {headlines}
    //    
    //    Latest earnings summary:
    //    Revenue: {revenue} | Net Profit: {netProfit} | EPS: {eps}
    //    Guidance: {guidance}
    //    
    //    Task: Determine if news sentiment supports a 1-4 week swing trade entry.
    //    Consider: earnings momentum, regulatory news, management changes,
    //    sector tailwinds, FII/DII activity, promoter actions.
    //    
    //    Respond in this exact JSON format only, no other text:
    //    {
    //      "score": "POSITIVE|NEUTRAL|NEGATIVE",
    //      "confidence": 0.85,
    //      "summary": "2 sentence max reasoning",
    //      "red_flags": ["list any specific risks"],
    //      "catalysts": ["list any upcoming catalysts"]
    //    }
    //    
    //    Examples (Indian market calibration):
    //    POSITIVE: Strong quarterly results, FII buying, sector tailwind
    //    NEUTRAL: Mixed results, no major news
    //    NEGATIVE: Promoter pledge, SEBI action, earnings miss"
}
```

**Replace `extractSentimentFromResponse()`** — remove regex-based parsing. Add new method:

```java
public SentimentAnalysisResult parseResponse(String jsonResponse) {
    try {
        JsonNode root = objectMapper.readTree(jsonResponse);
        String score = root.get("score").asText();
        double confidence = root.get("confidence").asDouble();
        String summary = root.get("summary").asText();
        
        List<String> redFlags = new ArrayList<>();
        if (root.has("red_flags") && root.get("red_flags").isArray()) {
            root.get("red_flags").forEach(node -> redFlags.add(node.asText()));
        }
        
        List<String> catalysts = new ArrayList<>();
        if (root.has("catalysts") && root.get("catalysts").isArray()) {
            root.get("catalysts").forEach(node -> catalysts.add(node.asText()));
        }
        
        return new SentimentAnalysisResult(
            SentimentType.valueOf(score.toUpperCase()),
            summary,
            confidence,
            redFlags,
            catalysts
        );
    } catch (Exception e) {
        logger.warn("Failed to parse LLM response for {}: {}", symbol, e.getMessage());
        return new SentimentAnalysisResult(SentimentType.NEUTRAL, "Parse failed", 0.3, List.of(), List.of());
    }
}
```

#### 4.2.5 `SentimentAnalysisService.java` (MODIFY)

**Changes:**

1. Add `EarningsData` parameter to `performSentimentAnalysis()`:
```java
private SentimentAnalysisResult performSentimentAnalysis(
    String symbol, List<String> newsContent, EarningsData earningsData) {
    // Pass earningsData to SentimentAnalyzer.createSentimentAnalysisPrompt()
}
```

2. Add new public method:
```java
public SentimentResult analyseSentiment(String symbol, List<String> headlines, EarningsData earningsData) {
    // 1. Check cache
    // 2. Fetch news if not provided
    // 3. Call performSentimentAnalysis(symbol, headlines, earningsData)
    // 4. Parse with Jackson (via SentimentAnalyzer.parseResponse)
    // 5. Build SentimentResult with redFlags/catalysts
    // 6. Persist to DB
    // 7. Cache result
    // 8. Return SentimentResult
}
```

3. Update `buildSentimentResult()` to include redFlags and catalysts.

4. Update `SentimentAnalysisResult` class:
```java
public class SentimentAnalysisResult {
    private SentimentType sentiment;
    private String reasoning;
    private Double confidence;
    private List<String> redFlags;      // NEW
    private List<String> catalysts;     // NEW
    // ... getters/setters
}
```

#### 4.2.6 `SignalFilterService.java` (NEW)

```java
@Service
@Slf4j
public class SignalFilterService {
    private final SentimentAnalysisService sentimentService;
    private final NewsIngestionService newsService;
    private final PdfExtractionService pdfService;
    private final PaperTradingEngine paperTradingEngine;
    private final DiscordNotificationService discordService;
    private final SentimentAccuracyTracker accuracyTracker;
    
    /**
     * Main entry point: filters signals through sentiment analysis.
     * Called instead of direct PaperTradingEngine.executeSignal().
     */
    public Order filterAndProcess(Signal signal, BigDecimal currentPrice) {
        // 1. Fetch latest news for signal.symbol
        List<NewsArticle> news = newsService.fetchAllNews(signal.symbol());
        List<String> headlines = news.stream()
            .map(NewsIngestionService::cleanNewsText)
            .toList();
        
        // 2. Try to extract earnings PDF if available
        EarningsData earnings = null;
        try {
            if (pdfService.isAvailable()) {
                earnings = pdfService.extractLatestEarnings(signal.symbol());
            }
        } catch (Exception e) {
            logger.warn("PDF extraction failed for {}: {}", signal.symbol(), e.getMessage());
        }
        
        // 3. Run sentiment analysis
        SentimentResult sentiment = sentimentService.analyseSentiment(
            signal.symbol(), headlines, earnings);
        
        // 4. Decision logic
        return switch (sentiment.score()) {
            case POSITIVE -> {
                logger.info("Signal {} POSITIVE sentiment — proceeding to order", signal.symbol());
                yield paperTradingEngine.executeSignal(signal, currentPrice);
            }
            case NEUTRAL -> {
                logger.warn("Signal {} NEUTRAL sentiment — proceeding with warning", signal.symbol());
                discordService.sendEmbed(new EmbedBuilder(
                    "Neutral Sentiment — {}", signal.symbol(),
                    "⚠️ Sentiment is NEUTRAL. Proceeding with caution.",
                    1554693  // yellow
                ));
                yield paperTradingEngine.executeSignal(signal, currentPrice);
            }
            case NEGATIVE -> {
                logger.warn("Signal {} NEGATIVE sentiment — SUPPRESSED. Reason: {}", 
                    signal.symbol(), sentiment.summary());
                discordService.sendEmbed(new EmbedBuilder(
                    "Negative Sentiment — {}", signal.symbol(),
                    "🔴 Sentiment is NEGATIVE. Signal suppressed.\n" + sentiment.summary(),
                    15158332  // red
                ));
                yield null;
            }
        };
    }
    
    /**
     * Daily re-analysis of open positions.
     * @Scheduled(cron = "0 0 8 * * *", zone = "Asia/Kolkata")
     */
    public void reanalysePending() {
        List<Position> openPositions = paperTradingEngine.getOpenPositions();
        for (Position pos : openPositions) {
            try {
                SentimentResult sentiment = sentimentService.analyseStockSentiment(
                    pos.getSymbol(), LocalDate.now());
                if (sentiment.isNegative()) {
                    discordService.sendEmbed(new EmbedBuilder(
                        "Position Alert — {}", pos.getSymbol(),
                        "🔴 Sentiment turned NEGATIVE. Position: " + pos.getPositionId() + 
                        "\nRecommendation: Review position manually. Do not auto-exit.",
                        1554693  // yellow (warning, not red — human decision)
                    ));
                }
            } catch (Exception e) {
                logger.warn("Reanalysis failed for {}: {}", pos.getSymbol(), e.getMessage());
            }
        }
    }
}
```

#### 4.2.7 `WeeklyIntelligenceService.java` (NEW)

```java
@Service
@Slf4j
public class WeeklyIntelligenceService {
    private final VLLMClient vllmClient;
    private final NewsIngestionService newsService;
    private final DiscordNotificationService discordService;
    
    private static final List<String> SECTORS = List.of(
        "IT", "Banking", "Pharma", "Metals", "Infra", "Telecom", "Auto", "FMCG"
    );
    
    @Scheduled(cron = "0 0 18 * * SUN", zone = "Asia/Kolkata")
    public String generateWeeklyDigest() {
        // 1. For each sector, fetch sector-level news
        // 2. Send to Qwen3-30B for 3-line sentiment summary
        // 3. Compile digest
        // 4. Send to Discord as embed
        // 5. Return formatted digest string
        
        StringBuilder digest = new StringBuilder();
        digest.append("📊 Weekly Sector Digest — ").append(LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy"))).append("\n\n");
        
        for (String sector : SECTORS) {
            // Fetch news for sector (search Google News for sector name + NSE)
            List<String> sectorNews = fetchSectorNews(sector);
            
            // Send to LLM for summary
            String summary = llmClient.generateChatCompletion(
                List.of(
                    Map.of("role", "system", "content", "You are a financial analyst. Provide a 3-line sentiment summary for an Indian equity sector."),
                    Map.of("role", "user", "content", 
                        "Sector: " + sector + "\n\nRecent news:\n" + String.join("\n", sectorNews))
                ), 200, 0.5
            ).block(Duration.ofSeconds(30));
            
            // Determine emoji based on sentiment keyword
            String emoji = summary.contains("positive") || summary.contains("bullish") ? "🟢" :
                           summary.contains("negative") || summary.contains("bearish") ? "🔴" : "🟡";
            
            digest.append(emoji).append(" ").append(sector).append(": ").append(summary).append("\n");
        }
        
        // Send to Discord
        discordService.sendEmbed(new EmbedBuilder(
            "📊 Weekly Sector Digest", digest.toString(),
            0x5865F2  // blurple
        ));
        
        return digest.toString();
    }
    
    private List<String> fetchSectorNews(String sector) {
        // Search Google News for sector + NSE
        return newsService.fetchGoogleNews(sector);
    }
}
```

#### 4.2.8 `SentimentAnalysisResult.java` (MODIFY)

Add fields:
```java
private List<String> redFlags;
private List<String> catalysts;
```

#### 4.2.9 `LlmConfig.java` (MODIFY)

Remove the duplicate `sendWeeklySectorDigest()` scheduled method (now handled by `WeeklySectorDigestScheduler`).

#### 4.2.10 `VLLMClient.java` (MODIFY)

Add `response_format` support for strict JSON mode:
```java
// In generateChatCompletion request:
Map<String, Object> request = Map.of(
    "model", modelName,
    "messages", messages,
    "max_tokens", maxTokens,
    "temperature", temperature,
    "top_p", 0.9,
    "n", 1,
    "stream", false,
    "response_format", Map.of("type", "json_object")  // STRICT JSON MODE
);
```

This tells vLLM to enforce valid JSON output, reducing parse failures.

### 4.3 Broker Module

#### 4.3.1 `DiscordNotificationService.java` (NEW)

```java
@Service
@Slf4j
public class DiscordNotificationService {
    private final WebClient webClient;
    private final String webhookUrl;
    private final boolean enabled;
    
    boolean sendMessage(String content) {
        if (!enabled || webhookUrl.isBlank()) {
            logger.debug("Discord notifications disabled");
            return false;
        }
        
        Map<String, Object> payload = Map.of("content", content);
        return postWebhook(payload);
    }
    
    boolean sendEmbed(DiscordEmbed embed) {
        if (!enabled || webhookUrl.isBlank()) {
            logger.debug("Discord notifications disabled");
            return false;
        }
        
        Map<String, Object> payload = Map.of("embeds", List.of(embed.toMap()));
        return postWebhook(payload);
    }
    
    private boolean postWebhook(Map<String, Object> payload) {
        try {
            webClient.post()
                .uri(webhookUrl)
                .bodyValue(payload)
                .retrieve()
                .toBodilessEntity()
                .block(Duration.ofSeconds(10));
            logger.debug("Discord message sent successfully");
            return true;
        } catch (Exception e) {
            logger.error("Discord webhook failed: {}", e.getMessage());
            return false;
        }
    }
    
    // Inner class for Discord embed
    public record DiscordEmbed(String title, String description, int color,
                               List<EmbedField> fields, String footer) {
        Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("title", title);
            map.put("description", description);
            map.put("color", color);
            if (fields != null && !fields.isEmpty()) map.put("fields", fields.stream().map(EmbedField::toMap).toList());
            if (footer != null) map.put("footer", Map.of("text", footer));
            return map;
        }
    }
    
    public record EmbedField(String name, String value, boolean inline) {
        Map<String, Object> toMap() {
            return Map.of("name", name, "value", value, "inline", inline);
        }
    }
}
```

**Color constants:**
- Green: `11625876` (success)
- Yellow: `1554693` (warning)
- Red: `15158332` (danger)
- Blurple: `0x5865F2` (Discord brand)

### 4.4 API Module

#### 4.4.1 `SettingsController.java` (MODIFY)

Add endpoints for LLM and Discord settings:

```java
// GET /api/settings/llm
@GetMapping("/settings/llm")
public ResponseEntity<ApiResponse<Map<String, String>>> getLlmSettings() {
    Map<String, String> settings = Map.of(
        "llm.vllm.base_url", appSettingsService.get("llm.vllm.base_url", ""),
        "llm.vllm.model", appSettingsService.get("llm.vllm.model", ""),
        "llm.pdf.base_url", appSettingsService.get("llm.pdf.base_url", ""),
        "llm.pdf.model", appSettingsService.get("llm.pdf.model", "")
    );
    return ResponseEntity.ok(ApiResponse.ok(settings));
}

// PUT /api/settings/llm
@PutMapping("/settings/llm")
public ResponseEntity<ApiResponse<Map<String, String>>> setLlmSettings(
        @RequestBody Map<String, String> body) {
    body.forEach((key, value) -> appSettingsService.set(key, value));
    return getLlmSettings();
}

// GET /api/settings/discord
@GetMapping("/settings/discord")
public ResponseEntity<ApiResponse<Map<String, String>>> getDiscordSettings() {
    Map<String, String> settings = Map.of(
        "discord.webhook.url", appSettingsService.get("discord.webhook.url", ""),
        "discord.webhook.enabled", appSettingsService.get("discord.webhook.enabled", "false")
    );
    return ResponseEntity.ok(ApiResponse.ok(settings));
}

// PUT /api/settings/discord
@PutMapping("/settings/discord")
public ResponseEntity<ApiResponse<Map<String, String>>> setDiscordSettings(
        @RequestBody Map<String, String> body) {
    body.forEach((key, value) -> appSettingsService.set(key, value));
    return getDiscordSettings();
}

// POST /api/settings/test/discord — test webhook
@PostMapping("/settings/test/discord")
public ResponseEntity<ApiResponse<Map<String, Boolean>>> testDiscord() {
    boolean success = discordNotificationService.sendMessage("SwingTrade: webhook test successful");
    return ResponseEntity.ok(ApiResponse.ok(Map.of("success", success)));
}
```

#### 4.4.2 `SignalExecutionJob.java` (MODIFY)

Replace direct `paperTradingEngine.executeSignal()` call:

```java
// ADD dependency:
private final SignalFilterService signalFilterService;

// IN executeSignal():
// OLD:
//   paperTradingEngine.executeSignal(domainSignal, currentPrice);

// NEW:
Order order = signalFilterService.filterAndProcess(domainSignal, currentPrice);
if (order != null) {
    signalEntity.setProcessed(true);
    signalRepository.save(signalEntity);
    logger.info("Executed signal for {} at price {}", signalEntity.getSymbol(), currentPrice);
} else {
    logger.info("Signal for {} suppressed by sentiment filter", signalEntity.getSymbol());
    // Still mark as processed so we don't re-process it
    signalEntity.setProcessed(true);
    signalRepository.save(signalEntity);
}
```

#### 4.4.3 `WeeklySectorDigestScheduler.java` (MODIFY)

Replace Telegram → Discord, delegate to WeeklyIntelligenceService:

```java
// OLD: TelegramNotificationService dependency
// NEW: WeeklyIntelligenceService dependency

@Scheduled(cron = "0 0 18 * * SUN", zone = "Asia/Kolkata")  // Changed from 17:00 to 18:00
public void sendWeeklySectorDigest() {
    try {
        String digest = weeklyIntelligenceService.generateWeeklyDigest();
        log.info("Weekly sector digest generated: {} characters", digest.length());
    } catch (Exception e) {
        log.error("Error during weekly sector digest: {}", e.getMessage(), e);
    }
}
```

#### 4.4.4 `SentimentApiController.java` (NEW)

```java
@RestController
@RequestMapping("/api")
@Slf4j
public class SentimentApiController {
    private final SentimentAnalysisService sentimentService;
    private final NewsIngestionService newsService;
    private final PdfExtractionService pdfService;
    private final SentimentAccuracyTracker accuracyTracker;
    private final SentimentResultRepository sentimentRepo;
    private final NewsItemRepository newsItemRepo;
    private final PdfExtractionRepository pdfExtractionRepo;
    
    // GET /api/sentiment/{symbol}/latest
    @GetMapping("/sentiment/{symbol}/latest")
    public ResponseEntity<ApiResponse<SentimentResult>> getLatestSentiment(
            @PathVariable String symbol) {
        Optional<SentimentResultEntity> latest = sentimentRepo.findLatestBySymbol(symbol);
        return latest.map(e -> ResponseEntity.ok(ApiResponse.ok(e.toDomain())))
            .orElse(ResponseEntity.ok(ApiResponse.ok(
                SentimentResult.create(symbol, LocalDate.now(), 
                    SentimentResult.SentimentScore.NEUTRAL, "No data", "", 0.0))));
    }
    
    // GET /api/sentiment/{symbol}/history
    @GetMapping("/sentiment/{symbol}/history")
    public ResponseEntity<ApiResponse<List<SentimentResult>>> getSentimentHistory(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("date").descending());
        List<SentimentResultEntity> results = sentimentRepo.findAllBySymbol(symbol, pageable);
        return ResponseEntity.ok(ApiResponse.ok(
            results.stream().map(SentimentResultEntity::toDomain).toList()));
    }
    
    // GET /api/sentiment/accuracy
    @GetMapping("/sentiment/accuracy")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAccuracyStats() {
        var stats = accuracyTracker.getAccuracyStats();
        Map<String, Object> data = Map.of(
            "total", stats.total(),
            "correct", stats.correct(),
            "accuracy_pct", stats.accuracyPct(),
            "by_sentiment", stats.bySentiment(),
            "by_symbol", stats.bySymbol()
        );
        return ResponseEntity.ok(ApiResponse.ok(data));
    }
    
    // GET /api/news/{symbol}/latest
    @GetMapping("/news/{symbol}/latest")
    public ResponseEntity<ApiResponse<List<NewsArticle>>> getLatestNews(
            @PathVariable String symbol) {
        List<NewsArticle> news = newsService.fetchAllNews(symbol);
        return ResponseEntity.ok(ApiResponse.ok(news));
    }
    
    // GET /api/pdf/{symbol}/latest
    @GetMapping("/pdf/{symbol}/latest")
    public ResponseEntity<ApiResponse<EarningsData>> getLatestEarnings(
            @PathVariable String symbol) {
        Optional<PdfExtractionEntity> latest = pdfExtractionRepo.findLatestBySymbol(symbol);
        return latest.map(e -> ResponseEntity.ok(ApiResponse.ok(
            EarningsData.fromJson(e.getExtractedJson()))))
            .orElse(ResponseEntity.ok(ApiResponse.ok(null)));
    }
    
    // POST /api/sentiment/{symbol}/analyse
    @PostMapping("/sentiment/{symbol}/analyse")
    public ResponseEntity<ApiResponse<SentimentResult>> triggerAnalysis(
            @PathVariable String symbol) {
        List<NewsArticle> news = newsService.fetchAllNews(symbol);
        List<String> headlines = news.stream()
            .map(newsService::cleanNewsText)
            .toList();
        SentimentResult result = sentimentService.analyseSentiment(symbol, headlines, null);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }
    
    // POST /api/pdf/{symbol}/extract?url={url}
    @PostMapping("/pdf/{symbol}/extract")
    public ResponseEntity<ApiResponse<EarningsData>> triggerPdfExtraction(
            @PathVariable String symbol,
            @RequestParam String url) {
        EarningsData data = pdfService.extractEarningsPdf(symbol, url);
        return ResponseEntity.ok(ApiResponse.ok(data));
    }
}
```

#### 4.4.5 `SentimentAnalysisResponse.java` (MODIFY — existing DTO)

Update to include `redFlags` and `catalysts` fields.

### 4.5 Dashboard

#### 4.5.1 `SettingsView.vue` (MODIFY)

Add new card-panel section:

```vue
<!-- LLM & Intelligence Settings -->
<div class="card-panel p-5">
  <h2 class="mb-4 text-base font-semibold text-text-primary">LLM & Intelligence</h2>
  
  <!-- vLLM Configuration -->
  <div class="space-y-4 mb-6">
    <h3 class="text-sm font-medium text-text-secondary">vLLM Endpoint</h3>
    <div class="flex gap-2">
      <input v-model="llmSettings.vllmBaseUrl" placeholder="https://gpuhub:8443/v1"
        class="flex-1 rounded-md border border-border-subtle bg-bg-primary px-3 py-2 text-sm text-text-primary placeholder:text-text-muted focus:border-brand focus:outline-none" />
      <button @click="testLlmConnection" :disabled="testingLlm"
        class="rounded-md border border-brand bg-brand-subtle px-4 py-2 text-sm font-medium text-brand transition-colors hover:bg-brand/20 disabled:opacity-50">
        {{ testingLlm ? 'Testing...' : 'Test' }}
      </button>
    </div>
    
    <div class="flex gap-2">
      <input v-model="llmSettings.model" placeholder="Qwen3-30B-AWQ"
        class="flex-1 rounded-md border border-border-subtle bg-bg-primary px-3 py-2 text-sm text-text-primary placeholder:text-text-muted focus:border-brand focus:outline-none" />
      <span class="self-center text-xs text-text-muted">Model name</span>
    </div>
  </div>
  
  <!-- PDF Extraction -->
  <div class="space-y-4 mb-6">
    <h3 class="text-sm font-medium text-text-secondary">PDF Extraction (Pi 5)</h3>
    <div class="flex gap-2">
      <input v-model="llmSettings.pdfBaseUrl" placeholder="http://pi5-ip:8080"
        class="flex-1 rounded-md border border-border-subtle bg-bg-primary px-3 py-2 text-sm text-text-primary placeholder:text-text-muted focus:border-brand focus:outline-none" />
      <button @click="testPdfExtraction" :disabled="testingPdf"
        class="rounded-md border border-brand bg-brand-subtle px-4 py-2 text-sm font-medium text-brand transition-colors hover:bg-brand/20 disabled:opacity-50">
        {{ testingPdf ? 'Testing...' : 'Test' }}
      </button>
    </div>
    
    <div class="flex gap-2">
      <input v-model="llmSettings.pdfModel" placeholder="gemma-4-E2B"
        class="flex-1 rounded-md border border-border-subtle bg-bg-primary px-3 py-2 text-sm text-text-primary placeholder:text-text-muted focus:border-brand focus:outline-none" />
      <span class="self-center text-xs text-text-muted">Model name</span>
    </div>
  </div>
  
  <!-- Discord Configuration -->
  <div class="space-y-4">
    <h3 class="text-sm font-medium text-text-secondary">Discord Notifications</h3>
    <div class="flex items-center justify-between rounded-lg border border-border-subtle p-3">
      <span class="text-sm">Enable Discord</span>
      <label class="relative inline-flex items-center cursor-pointer">
        <input type="checkbox" v-model="discordSettings.enabled" 
          @change="saveDiscordSettings" class="sr-only peer" />
        <div class="w-9 h-5 bg-gray-700 peer-focus:outline-none rounded-full peer 
          peer-checked:after:translate-x-full peer-checked:after:border-white 
          after:content-[''] after:absolute after:top-[2px] after:left-[2px] 
          after:bg-white after:rounded-full after:h-4 after:w-4 after:transition-all 
          peer-checked:bg-brand"></div>
      </label>
    </div>
    <div class="flex gap-2">
      <input v-model="discordSettings.webhookUrl" placeholder="https://discord.com/api/webhooks/..."
        class="flex-1 rounded-md border border-border-subtle bg-bg-primary px-3 py-2 text-sm text-text-primary placeholder:text-text-muted focus:border-brand focus:outline-none" />
      <button @click="testDiscordWebhook" :disabled="testingDiscord"
        class="rounded-md border border-brand bg-brand-subtle px-4 py-2 text-sm font-medium text-brand transition-colors hover:bg-brand/20 disabled:opacity-50">
        {{ testingDiscord ? 'Testing...' : 'Test' }}
      </button>
    </div>
  </div>
</div>
```

#### 4.5.2 `settings.ts` (MODIFY)

Add LLM and Discord settings to state:

```typescript
interface LlmSettings {
  vllmBaseUrl: string
  model: string
  pdfBaseUrl: string
  pdfModel: string
}

interface DiscordSettings {
  webhookUrl: string
  enabled: boolean
}

interface SettingsState {
  selectedBroker: 'fyers' | 'upstox' | 'yahoo' | 'none'
  tradingConfig: TradingConfig
  llmSettings: LlmSettings      // NEW
  discordSettings: DiscordSettings  // NEW
}

const defaults: SettingsState = {
  selectedBroker: 'fyers',
  tradingConfig: { mode: 'paper', maxPositionSize: 10, stopLoss: 5, takeProfit: 15 },
  llmSettings: {
    vllmBaseUrl: 'https://u425-84cf-d540ae09.singapore-b.gpuhub.com:8443/v1',
    model: 'Qwen3-30B-AWQ',
    pdfBaseUrl: '',
    pdfModel: 'gemma-4-E2B',
  },
  discordSettings: {
    webhookUrl: '',
    enabled: false,
  },
}
```

#### 4.5.3 API Client (MODIFY)

Add API functions in `api/client.ts`:

```typescript
export async function getLlmSettings() {
  const res = await apiFetch<Map<string, string>>('/api/settings/llm')
  return res
}

export async function setLlmSettings(settings: Record<string, string>) {
  const res = await apiFetch<Map<string, string>>('/api/settings/llm', {
    method: 'PUT',
    body: settings,
  })
  return res
}

export async function getDiscordSettings() {
  const res = await apiFetch<Map<string, string>>('/api/settings/discord')
  return res
}

export async function setDiscordSettings(settings: Record<string, string>) {
  const res = await apiFetch<Map<string, string>>('/api/settings/discord', {
    method: 'PUT',
    body: settings,
  })
  return res
}

export async function testDiscordWebhook() {
  const res = await apiFetch<Map<string, boolean>>('/api/settings/test/discord', {
    method: 'POST',
  })
  return res
}
```

---

## 5. Configuration Changes

### 5.1 `application.properties` (MODIFY)

Already updated:
- `llm.vllm.base-url` → `https://u425-84cf-d540ae09.singapore-b.gpuhub.com:8443/v1`

Add:
```properties
# PDF Extraction (Pi 5 llama-server)
llm.pdf.base-url=${LLM_PDF_BASE_URL:}
llm.pdf.model=${LLM_PDF_MODEL:gemma-4-E2B}

# Discord Webhook
discord.webhook.url=${DISCORD_WEBHOOK_URL:}
discord.webhook.enabled=${DISCORD_WEBHOOK_ENABLED:false}

# News Sources
news.nse.announcements.enabled=${NEWS_NSE_ENABLED:true}
news.google.enabled=${NEWS_GOOGLE_ENABLED:true}
news.nse.max-retries=3
news.nse.backoff-ms=1000
```

### 5.2 `application-local.properties` (MODIFY)

Add:
```properties
# Local dev overrides
llm.pdf.base-url=
llm.pdf.model=gemma-4-E2B
discord.webhook.url=
discord.webhook.enabled=false
news.nse.announcements.enabled=true
news.google.enabled=true
```

### 5.3 llm/pom.xml (MODIFY)

Add:
```xml
<dependency>
    <groupId>com.rometools</groupId>
    <artifactId>rome</artifactId>
    <version>2.1.0</version>
</dependency>
```

---

## 6. Integration Points

### 6.1 SignalPipeline Flow

```
SignalScanner → SignalExecutionJob → SignalFilterService → PaperTradingEngine
                                              │
                                              ├─→ NewsIngestionService (fetch news)
                                              │
                                              ├─→ PdfExtractionService (optional)
                                              │
                                              ├─→ SentimentAnalysisService (analyse)
                                              │
                                              └─→ DiscordNotificationService (alerts)
```

### 6.2 Scheduled Jobs

| Job | Cron (IST) | Module | Service |
|-----|------------|--------|---------|
| News fetch (active positions) | `0 0 16 * * MON-FRI` | llm | NewsIngestionService |
| Position reanalysis | `0 0 8 * * *` | llm | SignalFilterService |
| Weekly sector digest | `0 0 18 * * SUN` | api | WeeklyIntelligenceService |
| Signal polling | `fixedDelay 30s` | api | SignalExecutionJob |

### 6.3 Accuracy Tracking

When a paper position closes (via `PaperTradingEngine.closePosition()`):
1. Event: position closed with outcome (TARGET_HIT or STOP_LOSS)
2. Look up `SentimentResult` for the symbol + entry date
3. `was_correct = (sentiment == POSITIVE && TARGET_HIT) || (sentiment == NEGATIVE && STOP_LOSS)`
4. Store in `sentiment_accuracy` table
5. Track running accuracy stats

---

## 7. Test Plan

### 7.1 Unit Tests

| Test Class | Tests |
|------------|-------|
| `SentimentAnalysisServiceTest` | - JSON parse success → correct score<br>- JSON parse failure → NEUTRAL default<br>- Prompt construction with earnings data<br>- Cache hit/miss behavior<br>- Graceful degradation (LLM unreachable) |
| `SignalFilterServiceTest` | - POSITIVE → placeBuyOrder called<br>- NEGATIVE → placeBuyOrder NOT called<br>- NEUTRAL → placeBuyOrder called with Discord flag<br>- reanalysePending sends warning |
| `NewsIngestionServiceTest` | - NSE fetch with retry + fallback<br>- Google News RSS parsing<br>- Deduplication by headline+date<br>- 7-day filter<br>- fetchAllNews combines both sources |
| `PdfExtractionServiceTest` | - PDF download + base64 encode<br>- LLM response parse → EarningsData<br>- Unreachable endpoint → null |
| `DiscordNotificationServiceTest` | - sendMessage POSTs correctly<br>- sendEmbed with color coding<br>- Disabled webhook returns false |
| `SentimentAccuracyTrackerTest` | - recordOutcome stores correctly<br>- was_correct calculation<br>- getAccuracyStats returns accurate data |

### 7.2 Integration Tests

- Full pipeline: signal → news → sentiment → order
- Flyway migration: V4-V9 apply cleanly
- Settings persistence: DB round-trip
- Discord webhook: mock server test

---

## 8. Execution Order (Dependency Graph)

```
Phase 1: Settings persistence (V9, entity, service, API endpoints)
    ↓
Phase 2: Flyway migration fix (V4 merge, V8 tables)
    ↓
Phase 3: Config updates (application.properties, local)
    ↓
Phase 4: News ingestion extension (NSE, Google News, Rome)
    ↓
Phase 5: Sentiment prompt + parsing (SentimentAnalyzer, SentimentAnalysisService)
    ↓
Phase 6: PDF extraction (PdfExtractionService, EarningsData)
    ↓
Phase 7: Signal filter (SignalFilterService, modify SignalExecutionJob)
    ↓
Phase 8: Discord notification (DiscordNotificationService)
    ↓
Phase 9: Weekly intelligence + digest (WeeklyIntelligenceService, modify WeeklySectorDigestScheduler)
    ↓
Phase 10: Accuracy tracker (SentimentAccuracyTracker)
    ↓
Phase 11: REST API (SentimentApiController)
    ↓
Phase 12: Dashboard settings section (SettingsView.vue, settings.ts, api/client.ts)
    ↓
Phase 13: Unit tests (all test classes)
```

---

## 9. Files Summary

### New Files (13)

| # | File | Module | Purpose |
|---|------|--------|---------|
| 1 | `V8__create_intelligence_tables.sql` | data | news_items, pdf_extractions, sentiment_accuracy |
| 2 | `V9__create_app_settings.sql` | data | app_settings key/value table |
| 3 | `AppSettingEntity.java` | data | JPA entity for app_settings |
| 4 | `AppSettingRepository.java` | data | JPA repo for app_settings |
| 5 | `AppSettingsService.java` | data | Settings CRUD with env-var override |
| 6 | `EarningsData.java` | llm | Record for extracted earnings |
| 7 | `PdfExtractionService.java` | llm | PDF download + LLM extraction |
| 8 | `SignalFilterService.java` | llm | Signal → sentiment → order filter |
| 9 | `WeeklyIntelligenceService.java` | llm | Sector-level weekly digest |
| 10 | `DiscordNotificationService.java` | broker | Discord webhook notifications |
| 11 | `SentimentAccuracyTracker.java` | data | LLM accuracy tracking |
| 12 | `SentimentApiController.java` | api | REST endpoints for sentiment/news/PDF |
| 13 | `NewsItemRepository.java` | data | Repo for news_items entity |

### Modified Files (18)

| # | File | Change |
|---|------|--------|
| 1 | `V4__fix_sentiment_results.sql` | Merge processed flag, add red_flags/catalysts columns |
| 2 | `V5__add_trade_labels.sql` | No content change (version preserved) |
| 3 | `V6__add_fyers_symbol_master.sql` | No content change (version preserved) |
| 4 | `V7__add_signal_strategy.sql` | No content change (version preserved) |
| 5 | `application.properties` | Add PDF, Discord, news config |
| 6 | `application-local.properties` | Add PDF, Discord, news config |
| 7 | `VLLMClient.java` | Add response_format json_object, update URL/model |
| 8 | `SentimentAnalyzer.java` | New prompt + Jackson JSON parsing |
| 9 | `SentimentAnalysisService.java` | Accept earnings data, Jackson parsing |
| 10 | `SentimentAnalysisResult.java` | Add redFlags, catalysts fields |
| 11 | `NewsIngestionService.java` | Add NSE, Google News, fetchAllNews |
| 12 | `LlmConfig.java` | Remove duplicate scheduler |
| 13 | `SentimentResultEntity.java` | Add redFlags, catalysts fields |
| 14 | `SentimentResultRepository.java` | Add findLatestBySymbol |
| 15 | `SignalExecutionJob.java` | Route through SignalFilterService |
| 16 | `WeeklySectorDigestScheduler.java` | Replace Telegram → Discord, update cron |
| 17 | `SettingsController.java` | Add LLM/Discord settings endpoints |
| 18 | `SentimentAnalysisResponse.java` | Add redFlags, catalysts fields |
| 19 | `SettingsView.vue` | Add LLM & Intelligence card-panel |
| 20 | `stores/settings.ts` | Add llmSettings, discordSettings |
| 21 | `api/client.ts` | Add settings API functions |
| 22 | `llm/pom.xml` | Add Rome dependency |

### Deleted Files (1)

| # | File | Reason |
|---|------|--------|
| 1 | `V4__add_signal_processed_flag.sql` | Merged into V4__fix_sentiment_results.sql |

---

## 10. Risks & Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| NSE blocks automated requests | No news from NSE source | Retry with backoff, fall back to RSS feeds |
| vLLM endpoint timeout | Sentiment analysis fails | 60s timeout, graceful degradation to NEUTRAL |
| Pi 5 llama-server unreachable | PDF extraction fails silently | isAvailable() check, null return, log warning |
| LLM JSON parse failure | Wrong sentiment score | Strict JSON mode (response_format), Jackson parsing, NEUTRAL fallback |
| Discord webhook URL invalid | Notifications fail silently | Test endpoint button in dashboard, log errors |
| Flyway migration conflict | App won't start | Fix V4 duplicate before any other changes |
| Rome library conflict | Build failure | Check for version conflicts in parent POM |
| Telegram service still referenced | Dead code | Leave in place but unused; mark for future removal |

---

## 11. Testing via curl (Post-Implementation)

```bash
# Test vLLM connection
curl -X POST 'http://localhost:8080/api/settings/test/llm' \
  -H 'Content-Type: application/json' \
  -d '{"prompt": "Hello", "max_tokens": 10}'

# Trigger manual sentiment analysis
curl -X POST 'http://localhost:8080/api/sentiment/RELIANCE/analyse'

# Get latest sentiment
curl 'http://localhost:8080/api/sentiment/RELIANCE/latest'

# Get sentiment history
curl 'http://localhost:8080/api/sentiment/RELIANCE/history?page=0&size=20'

# Get accuracy stats
curl 'http://localhost:8080/api/sentiment/accuracy'

# Get latest news
curl 'http://localhost:8080/api/news/RELIANCE/latest'

# Extract earnings PDF
curl -X POST 'http://localhost:8080/api/pdf/RELIANCE/extract?url=https://example.com/reliance-q1.pdf'

# Test Discord webhook
curl -X POST 'http://localhost:8080/api/settings/test/discord'

# Update LLM settings
curl -X PUT 'http://localhost:8080/api/settings/llm' \
  -H 'Content-Type: application/json' \
  -d '{"llm.vllm.base_url": "https://u425-84cf-d540ae09.singapore-b.gpuhub.com:8443/v1",
       "llm.vllm.model": "Qwen3-30B-AWQ"}'

# Update Discord settings
curl -X PUT 'http://localhost:8080/api/settings/discord' \
  -H 'Content-Type: application/json' \
  -d '{"discord.webhook.url": "https://discord.com/api/webhooks/...",
       "discord.webhook.enabled": "true"}'
```