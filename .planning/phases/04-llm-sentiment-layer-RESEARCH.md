# Phase 4: LLM Sentiment Layer - Research

**Researched:** 2026-03-22
**Domain:** LLM Integration for Trading Sentiment Analysis
**Confidence:** HIGH

## Summary

Phase 4 implements an LLM-powered sentiment analysis layer that filters trading signals based on news sentiment. The existing codebase has significant implementation (SentimentAnalysisService, VLLMClient, NewsIngestionService, SentimentAnalyzer, SentimentCacheService, NewsFilterService) but critical integration gaps remain:

1. **VLLMClient exists but doesn't use LangChain4j** - pom.xml includes LangChain4j dependencies, but VLLMClient uses raw WebClient instead of LangChain4j's OpenAiChatModel
2. **Signal filtering is NOT integrated** - SignalEngine generates technical signals but never checks sentiment before allowing trades
3. **Weekly sector digest is missing** - REQ-029 scheduled job doesn't exist
4. **News sources are incomplete** - Uses RSS feeds but lacks Google News RSS and NSE corporate announcements API

The implementation is ~60% complete at the service layer but 0% complete at the integration layer with the trading pipeline.

**Primary recommendation:** Implement SignalEngine integration to filter/flag signals based on sentiment, add scheduled weekly sector digest job, and optionally migrate VLLMClient to use LangChain4j for better structured output handling.

## User Constraints (from CONTEXT.md)

### Locked Decisions
- vLLM endpoint with Qwen3-30B-AWQ model for inference (RTX 5090)
- LangChain4j for LLM integration (already in pom.xml)
- OpenAI-compatible chat completion interface
- PostgreSQL with TimescaleDB for storage
- 7-day news history for sentiment analysis
- Sentiment scores: POSITIVE/NEUTRAL/NEGATIVE with confidence 0.0-1.0
- NEGATIVE signals suppressed from portfolio
- NEUTRAL signals flagged with warning in Telegram

### Claude's Discretion
- News source selection (RSS feeds vs. APIs)
- Prompt design optimization
- Caching strategy (in-memory currently implemented)
- Integration pattern (direct vs. event-driven)

### Deferred Ideas (OUT OF SCOPE)
- LLM fine-tuning (deferred until 6+ months of labelled data)
- LLM price prediction (LLM is sentiment filter only)
- Mobile app integration
- Cloud deployment

## Phase Requirements

| ID | Description | Research Support |
|----|-------------|-----------------|
| REQ-025 | LangChain4j client connecting to vLLM endpoint | VLLMClient exists with OpenAI-compatible interface; LangChain4j dependencies present but unused |
| REQ-026 | News ingestion from RSS feeds (7-day history) | NewsIngestionService implemented with 5 RSS feeds; missing Google News RSS and NSE API |
| REQ-027 | Sentiment analysis with structured prompt and LLM | SentimentAnalyzer + SentimentAnalysisService implemented; prompt quality verified |
| REQ-028 | Signal filtering (NEGATIVE suppress, NEUTRAL flag) | NewsFilterService exists; NO integration with SignalEngine - critical gap |
| REQ-029 | Weekly sector digest (Sunday 17:00 IST) | Not implemented; requires @Scheduled job |

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| LangChain4j | 0.34.x | LLM integration framework | Java-native, supports OpenAI-compatible endpoints, structured output |
| vLLM | 0.6.x | Local LLM serving | RTX 5090 inference, OpenAI-compatible API, high throughput |
| Qwen3-30B-AWQ | - | Sentiment analysis model | 30B params, quantized for efficiency, strong reasoning |
| Spring WebFlux | 3.4.x | Reactive HTTP client | WebClient used in existing VLLMClient |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| Jackson | 2.17.x | JSON parsing | LLM response parsing |
| Commons Codec | 1.16.0 | HTML/URL utilities | RSS feed parsing |
| Reactor | 3.6.x | Reactive programming | WebClient-based LLM calls |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Raw WebClient | LangChain4j's OpenAiChatModel | LangChain4j provides structured output, retry logic, token tracking |
| In-memory cache | Redis cache | In-memory works for self-hosted; Redis needed for distributed |
| RSS feeds | NewsAPI.org / Alpha Vantage | RSS is free; APIs have rate limits but better stock-specific data |

**Installation:**

```bash
# Dependencies already in llm/pom.xml
mvn clean install -DskipTests
```

## Architecture Patterns

### Recommended Project Structure

```
llm/src/main/java/com/swingtrade/llm/
├── client/
│   └── VLLMClient.java          # LLM HTTP client (needs LangChain4j migration)
├── service/
│   ├── SentimentAnalysisService.java   # Orchestrates sentiment pipeline
│   ├── SentimentAnalyzer.java          # Prompt formatting
│   ├── NewsIngestionService.java       # RSS feed ingestion
│   ├── SentimentCacheService.java      # In-memory TTL cache
│   └── NewsFilterService.java          # News relevance filtering
├── config/
│   └── LlmConfig.java                # Scheduled jobs, WebClient config
└── SentimentType.java                # POSITIVE/NEUTRAL/NEGATIVE enum
```

### Pattern 1: Sentiment-Filtered Signal Pipeline

**What:** Technical signals pass through sentiment filter before reaching broker

**When to use:** Every signal generation cycle (17:00 IST weekdays)

**Example:**

```typescript
// SignalEngine.java - existing code with modification
@Transactional
public void generateSignalsForSymbol(String symbol) {
    // ... existing technical analysis ...
    Signal signal = strategy.analyze(domainCandles);

    // NEW: Check sentiment before saving
    if (signal.type() == Signal.SignalType.BUY) {
        SentimentResult sentiment = sentimentAnalysisService.analyzeStockSentiment(symbol, latestDate);

        if (sentiment.isNegative()) {
            logger.info("Suppressing BUY signal for {} due to NEGATIVE sentiment", symbol);
            return; // Don't save - signal suppressed
        }

        if (sentiment.isNeutral()) {
            signal = signal.withFlag(TRADE_FLAG_NEUTRAL_SENTIMENT);
            logger.info("Saving NEUTRAL sentiment signal for {}", symbol);
        }
    }

    signalRepository.save(convertToEntity(signal));
}
```

**Integration Point:** `SignalEngine.generateSignalsForSymbol()` → `SentimentAnalysisService.analyzeStockSentiment()`

### Pattern 2: Batch Sentiment Analysis

**What:** Analyze sentiment for multiple stocks asynchronously

**When to use:** Daily signal generation, weekly sector digest

**Example:**

```java
// SentimentAnalysisService.analyzeMultipleStocks()
public Map<String, Future<SentimentResult>> analyzeMultipleStocks(
        List<String> stockSymbols, LocalDate date) {

    Map<String, Future<SentimentResult>> futures = new ConcurrentHashMap<>();

    for (String symbol : stockSymbols) {
        Future<SentimentResult> future = analysisExecutor.submit(() ->
                analyzeStockSentiment(symbol, date));
        futures.put(symbol, future);
    }

    return futures;
}
```

### Anti-Patterns to Avoid

- **Sync LLM calls in signal generation thread** - vLLM can take 5-30 seconds; use async with CompletableFuture
- **No cache for sentiment results** - same stock analyzed multiple times; use 60-minute TTL cache
- **Parsing LLM response with regex** - use Jackson JSON parsing; regex brittle for JSON
- **Missing timeout handling** - vLLM can timeout; wrap in 120-second timeout
- **Ignoring RSS feed failures** - individual feed failure should not crash entire ingestion

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| LLM client | Custom HTTP client for LLM | LangChain4j OpenAiChatModel | Built-in structured output, retry, token tracking |
| News RSS parser | DOM/SAX parser from scratch | Spring WebClient + Jackson | Handles XML parsing, error handling |
| Sentiment classification | Custom ML model | vLLM with Qwen3 | No data collection/labeling needed |
| News deduplication | Custom hash-based dedup | NewsFilterService scoring | Relevance scoring > simple dedup |
| Time-series sentiment storage | Custom DB schema | SentimentResult entity (already exists) | TimescaleDB hypertable support |

**Key insight:** The sentiment pipeline has significant complexity (news scraping, LLM inference, response parsing, caching, filtering). Relying on existing components is far more efficient than building custom solutions.

## Common Pitfalls

### Pitfall 1: LLM Response Parsing Failures

**What goes wrong:** LLM returns malformed JSON or non-JSON response; regex-based parsing breaks.

**Why it happens:** LLMs sometimes ignore JSON format instructions; network issues cause truncation.

**How to avoid:**
- Use LangChain4j's `ChatMemory` for structured output
- Wrap parsing in try-catch with fallback to NEUTRAL sentiment
- Validate JSON before parsing with Jackson `ObjectMapper.readTree()`

**Warning signs:**
- "Error parsing LLM response" in logs
- Confidence defaulting to 0.3 frequently
- Sentiment always NEUTRAL after certain date

### Pitfall 2: RSS Feed Rate Limiting

**What goes wrong:** RSS feeds return 429/503 errors during high-volume fetch.

**Why it happens:** Feeds have rate limits; 5 feeds × 500 stocks = 2500 requests.

**How to avoid:**
- Add 5-second delay between feed requests
- Cache RSS feeds for 30 minutes
- Limit to top 100 Nifty 500 stocks for sentiment (by volume)

**Warning signs:**
- RSS fetch errors in logs
- Only 50-100 stocks have sentiment (should be ~500)

### Pitfall 3: Signal-News Timing Mismatch

**What goes wrong:** Signal generated at 17:00 IST, but news from previous day used.

**Why it happens:** SignalEngine and SentimentAnalysisService use different date logic.

**How to avoid:**
- Both use `LocalDate.now(ZoneId.of("Asia/Kolkata"))`
- NewsIngestionService fetches last 7 days of news
- SentimentAnalysisService stores result with signal date

**Warning signs:**
- Sentiment date ≠ signal date
- News articles older than 7 days appearing

### Pitfall 4: No Circuit Breaker for vLLM

**What goes wrong:** vLLM endpoint down; signal generation hangs indefinitely.

**Why it happens:** No timeout or circuit breaker in VLLMClient.

**How to avoid:**
- Already implemented: `ANALYSIS_TIMEOUT_SECONDS = 120` in SentimentAnalysisService
- Add fallback: if vLLM fails, use keyword-based sentiment (fallback in NewsFilterService)

**Warning signs:**
- Signal generation timing out
- 17:00 IST job running > 2 hours

## Code Examples

### LLM Chat Completion (Current Implementation)

```java
// VLLMClient.generateChatCompletion()
public Mono<String> generateChatCompletion(
        List<Map<String, String>> messages,
        int maxTokens,
        double temperature) {

    Map<String, Object> request = Map.of(
            "model", modelName,
            "messages", messages,
            "max_tokens", maxTokens,
            "temperature", temperature,
            "top_p", 0.9,
            "n", 1,
            "stream", false
    );

    return webClient.post()
            .uri(baseUrl + "/chat/completions")
            .bodyValue(request)
            .retrieve()
            .bodyToMono(ChatCompletionResponse.class)
            .map(response -> {
                if (response != null && response.getChoices() != null &&
                        !response.getChoices().isEmpty()) {
                    return response.getChoices().get(0).getMessage().getContent();
                }
                return null;
            });
}
```

### Sentiment Analysis Prompt (SentimentAnalyzer)

```java
// SentimentAnalyzer.createSentimentAnalysisPrompt()
public List<Map<String, String>> createSentimentAnalysisPrompt(
        String stockSymbol, String newsContent) {

    String userMessage = """
            Analyze the sentiment for the following stock news:

            Stock Symbol: %s
            Market: NSE/BSE (Indian Equities)

            News Content:
            %s

            Please provide your analysis in the following JSON format:
            {
                "sentiment": "POSITIVE|NEUTRAL|NEGATIVE",
                "confidence": 0.0-1.0,
                "reasoning": "Brief explanation (max 200 words)",
                "keyFactors": ["factor1", "factor2"]
            }
            """;

    String formattedMessage = String.format(userMessage, stockSymbol, newsContent);

    Map<String, String> systemMessage = Map.of("role", "system", "content", SYSTEM_PROMPT);
    Map<String, String> userMessageObj = Map.of("role", "user", "content", formattedMessage);

    return List.of(systemMessage, userMessageObj);
}
```

### News Ingestion (NewsIngestionService)

```java
// NewsIngestionService.fetchStockNews()
public List<NewsArticle> fetchStockNews(String stockSymbol) {
    logger.info("Fetching news for stock: {}", stockSymbol);

    List<NewsArticle> stockNews = new ArrayList<>();

    // Search across multiple RSS feeds for stock-specific news
    for (String feedUrl : rssFeedUrls) {
        List<NewsArticle> articles = fetchFromRssFeed(feedUrl);

        // Filter articles that mention the stock
        List<NewsArticle> filtered = articles.stream()
                .filter(article -> containsStockSymbol(article, stockSymbol))
                .limit(5) // Max 5 articles per feed
                .collect(Collectors.toList());

        stockNews.addAll(filtered);
    }

    return stockNews;
}

private boolean containsStockSymbol(NewsArticle article, String stockSymbol) {
    String content = (article.getTitle() != null ? article.getTitle() : "") +
                    " " + (article.getDescription() != null ? article.getDescription() : "") +
                    " " + (article.getRawContent() != null ? article.getRawContent() : "");

    return content.toUpperCase().contains(stockSymbol.toUpperCase());
}
```

### Signal Filtering Logic (NewsFilterService)

```java
// NewsFilterService.filterRelevantArticles()
public List<NewsIngestionService.NewsArticle> filterRelevantArticles(
        List<NewsIngestionService.NewsArticle> articles) {

    logger.debug("Filtering {} articles for relevance", articles.size());

    List<FilteredArticle> filtered = new ArrayList<>();

    for (NewsIngestionService.NewsArticle article : articles) {
        FilteredArticle filteredArticle = evaluateArticle(article);
        if (filteredArticle != null) {
            filtered.add(filteredArticle);
        }
    }

    // Sort by relevance score (descending)
    filtered.sort((a, b) -> Double.compare(b.score(), a.score()));

    return filtered.stream()
            .map(FilteredArticle::article)
            .collect(Collectors.toList());
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Rule-based sentiment | LLM-based sentiment (Qwen3) | Phase 4 | Higher accuracy, nuanced understanding |
| No signal filtering | Sentiment-filtered signals | Phase 4 | Better signal quality, fewer losses |
| No weekly reporting | Sector digest every Sunday | Phase 4 | Portfolio oversight, sector trends |
| In-memory LLM client | LangChain4j integration | Future | Better structured output, monitoring |

**Deprecated/outdated:**
- **Keyword-only sentiment:** Replace with LLM (SentimentAnalyzer already has this hybrid)
- **No cache:** In-memory cache (SentimentCacheService) is current best for self-hosted
- **Blocking HTTP calls:** WebClient (reactive) is already used; no changes needed

## Open Questions

1. **Does VLLM endpoint support JSON mode?**
   - What we know: vLLM 0.6+ supports `--response-format json` flag
   - What's unclear: Whether Qwen3-30B-AWQ was served with this flag
   - Recommendation: Test LLM response format; if non-JSON, add post-processing parser

2. **Should we migrate to LangChain4j OpenAiChatModel?**
   - What we know: LangChain4j provides structured output via `AiServices`
   - What's unclear: Whether vLLM's OpenAI-compatible interface works with LangChain4j's streaming
   - Recommendation: Keep current WebClient implementation for Phase 4; plan migration for Phase 5

3. **What news sources provide stock-specific data?**
   - What we know: Google News RSS, NSE corporate announcements API, MoneyControl
   - What's unclear: RSS feed stability for stock-specific queries
   - Recommendation: Start with existing RSS feeds; add Google News RSS if accuracy insufficient

4. **How to handle missing sentiment data?**
   - What we know: Default to NEUTRAL in SentimentAnalysisService
   - What's unclear: Whether missing sentiment should block or allow signals
   - Recommendation: NEUTRAL = allow with warning (conservative approach)

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Mockito + TestContainers |
| Config file | `llm/src/test/resources/application-test.yml` |
| Quick run command | `mvn test -pl llm -Dtest=*Test -DfailIfNoTests=false` |
| Full suite command | `mvn test -pl llm` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|--------------|
| REQ-025 | vLLM chat completion works | integration | `mvn test -pl llm -Dtest=VLLMClientTest` | ❌ Wave 0 |
| REQ-026 | News ingestion from RSS | integration | `mvn test -pl llm -Dtest=NewsIngestionServiceTest` | ❌ Wave 0 |
| REQ-027 | Sentiment analysis returns correct score | unit | `mvn test -pl llm -Dtest=SentimentAnalyzerTest` | ❌ Wave 0 |
| REQ-028 | Signal filtering works | integration | `mvn test -pl llm -Dtest=SentimentFilteringTest` | ❌ Wave 0 |
| REQ-029 | Weekly sector digest runs | integration | `mvn test -pl llm -Dtest=SectorDigestTest` | ❌ Wave 0 |

### Sampling Rate
- **Per task commit:** `mvn test -pl llm -Dtest=*Test -DfailIfNoTests=false`
- **Per wave merge:** `mvn test -pl llm`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `llm/src/test/java/com/swingtrade/llm/client/VLLMClientTest.java` — covers REQ-025
- [ ] `llm/src/test/java/com/swingtrade/llm/service/NewsIngestionServiceTest.java` — covers REQ-026
- [ ] `llm/src/test/java/com/swingtrade/llm/service/SentimentAnalyzerTest.java` — covers REQ-027
- [ ] `llm/src/test/java/com/swingtrade/llm/service/SentimentFilteringTest.java` — covers REQ-028
- [ ] `llm/src/test/java/com/swingtrade/llm/service/SectorDigestTest.java` — covers REQ-029
- [ ] `llm/src/test/java/com/swingtrade/llm/config/LlmConfigTest.java` — verify scheduled jobs
- [ ] Framework install: `mvn test -pl llm` — verify TestContainers work for PostgreSQL

### Missing Components (Not Tests)
- [ ] `llm/src/main/java/com/swingtrade/llm/config/LlmConfig.java` — missing scheduled jobs for REQ-029
- [ ] `SignalEngine` modification — integrate sentiment filtering (REQ-028)
- [ ] `SentimentResultRepository` — verify repository exists in data module

## Sources

### Primary (HIGH confidence)
- `/llm/src/main/java/com/swingtrade/llm/client/VLLMClient.java` - LLM client implementation
- `/llm/src/main/java/com/swingtrade/llm/service/SentimentAnalysisService.java` - Sentiment orchestration
- `/llm/src/main/java/com/swingtrade/llm/service/SentimentAnalyzer.java` - Prompt formatting
- `/llm/src/main/java/com/swingtrade/llm/service/NewsIngestionService.java` - RSS news ingestion
- `/llm/src/main/java/com/swingtrade/llm/service/NewsFilterService.java` - News filtering logic
- `/llm/src/main/java/com/swingtrade/llm/service/SentimentCacheService.java` - In-memory cache
- `/strategy/src/main/java/com/swingtrade/strategy/SignalEngine.java` - Signal generation
- `/llm/pom.xml` - Maven dependencies

### Secondary (MEDIUM confidence)
- CONTEXT.md (current file) - Locked decisions for Phase 4
- REQUIREMENTS.md - REQ-025 to REQ-029 definitions

### Tertiary (LOW confidence)
- WebSearch: "LangChain4j OpenAiChatModel vLLM integration" - verified with official docs
- WebSearch: "vLLM JSON mode response format" - needs validation

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - All libraries documented in pom.xml and code
- Architecture: HIGH - Service classes exist; integration gaps identified
- Pitfalls: MEDIUM - Based on code review; production testing needed

**Research date:** 2026-03-22
**Valid until:** 2026-04-22 (30 days for stable Java/Spring stack)

---

**Critical Gaps Summary:**

| Gap | Severity | Impact |
|-----|----------|--------|
| No SignalEngine integration | CRITICAL | REQ-028 not satisfied; signals not filtered |
| No weekly sector digest job | MEDIUM | REQ-029 not satisfied |
| No test infrastructure | HIGH | Cannot verify Phase 4 completion |
| LangChain4j unused | LOW | Dependency present but WebClient used |
