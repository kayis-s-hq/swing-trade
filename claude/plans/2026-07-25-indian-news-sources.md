# Indian News Sources Integration — Plan

**Date:** 2026-07-25
**Status:** In Progress (20/24 tasks done, 4 remaining — tests, integration, dashboard)
**Spec:** docs/superpowers/specs/2026-07-25-indian-news-sources-design.md

## Decisions (confirmed)
- Separate `fetchFilings()` for NSE/BSE (structured data, not articles)
- Keep existing dedup by normalized headline
- Reddit: password grant with credentials from config (OAuth optional)
- True parallel via CompletableFuture.allOf() with individual timeouts
- NSE/BSE: Jsoup web scraping (no REST API)
- Moneycontrol/ET: RSS feeds via Jsoup HTML parsing
- Google News: refactored from existing monolithic logic
- NewsArticle record moved to core module (cross-module dependency fix)
- No circuit breakers, no registry pattern

## Section 1: What's Already Done

### 1. DB Migration V18
**File:** `backend/data/src/main/resources/db/migration/V18__add_news_articles_table.sql`
- Creates `news_articles` table with indexes on symbol, source, published_at
- Replaces the spec's proposed table — uses same schema

### 2. Jsoup Dependency
**File:** `backend/data/pom.xml`
- Added `org.jsoup:jsoup:1.18.3` to data module dependencies

### 3. NewsArticle Record (moved to core)
**File:** `backend/core/src/main/java/com/swingtrade/domain/NewsArticle.java`
- Record: `symbol, title, link, description, publishedDate, source, rawContent`
- Builder pattern included
- Moved from llm module to core to avoid circular dependency (core → llm is impossible)

### 4. NewsItemEntity + NewsItemRepository (unchanged)
**Files:** `backend/data/src/main/java/com/swingtrade/data/entity/NewsItemEntity.java`
**File:** `backend/data/src/main/java/com/swingtrade/data/repository/NewsItemRepository.java`
- Pre-existing since V8, never used. Left as-is.

### 5. NewsArticleEntity + NewsArticleRepository
**File:** `backend/data/src/main/java/com/swingtrade/data/entity/NewsArticleEntity.java`
- JPA entity for `news_articles` table
- Fields: id, symbol, source, title, link, summary, publishedAt (OffsetDateTime), rawContent, createdAt

**File:** `backend/data/src/main/java/com/swingtrade/data/repository/NewsArticleRepository.java`
- Spring Data JPA: `findRecentBySymbol`, `findBySymbolOrderByPublishedAtDesc`, `countBySymbol`

### 6. NewsArticleStore Interface + Implementation
**File:** `backend/core/src/main/java/com/swingtrade/domain/store/NewsArticleStore.java`
- Interface in core: `saveAll(List<NewsArticle>)`, `findRecentBySymbol(String, OffsetDateTime)`, `countBySymbol(String)`

**File:** `backend/data/src/main/java/com/swingtrade/data/store/NewsArticleStoreImpl.java`
- Implements via `NewsArticleRepository`
- `toEntity()` / `toArticle()` converters between JPA entity and domain record

### 7. NewsSource Interface + StructuredFiling
**File:** `backend/llm/src/main/java/com/swingtrade/llm/service/NewsSource.java`
- Interface: `type()`, `fetch(String symbol)`, `fetchFilings(String symbol)` (default returns empty)

**File:** `backend/llm/src/main/java/com/swingtrade/llm/service/StructuredFiling.java`
- Record: `type (FilingType enum), date, title, description, link`
- FilingType: BOARD_MEETING, SHAREHOLDING, DIVIDEND, CORPORATE_ACTION, PERFORMANCE_RESULT, OTHER
- `typeLabel()` method for prompt formatting

### 8. MoneycontrolNewsSource
**File:** `backend/llm/src/main/java/com/swingtrade/llm/service/MoneycontrolNewsSource.java`
- Fetches from Moneycontrol RSS search endpoint
- Jsoup HTML parsing, symbol filtering, date parsing (RFC 1123)

### 9. EconomicTimesNewsSource
**File:** `backend/llm/src/main/java/com/swingtrade/llm/service/EconomicTimesNewsSource.java`
- Fetches from ET RSS feed
- Jsoup HTML parsing, symbol filtering, date parsing

### 10. GoogleNewsSource
**File:** `backend/llm/src/main/java/com/swingtrade/llm/service/GoogleNewsSource.java`
- Refactored from monolithic NewsIngestionService
- 3 queries per symbol, DOM XML parsing, dedup by normalized headline
- 20 max articles per symbol

### 11. NseAnnouncementsSource
**File:** `backend/llm/src/main/java/com/swingtrade/llm/service/NseAnnouncementsSource.java`
- API endpoint: `GET https://www.nseindia.com/api/corporate-announcements?symbol={symbol}`
- User-Agent: "SwingTrade/1.0" to avoid CAPTCHA
- Fallback: HTML page scraping via Jsoup if API returns HTML
- JSON parsing: manual regex extraction (subject, description, date fields)
- Filing classification: BOARD_MEETING, SHAREHOLDING, CORPORATE_ACTION, PERFORMANCE_RESULT, DIVIDEND, OTHER

### 12. BseAnnouncementsSource
**File:** `backend/llm/src/main/java/com/swingtrade/llm/service/BseAnnouncementsSource.java`
- Web scraping via Jsoup: `https://www.bseindia.com/Share_Warehouse/Announcements.aspx?symbol={symbol}`
- Table parsing: date + title + link from announcement rows
- Same filing classification logic

### 13. RedditIndiaInvestmentsSource
**File:** `backend/llm/src/main/java/com/swingtrade/llm/service/RedditIndiaInvestmentsSource.java`
- Reddit JSON API: `r/IndiaInvestments/search.json?q={symbol}` and `r/IndiaInvestments/hot.json`
- OAuth password grant optional (client_id + client_secret from config)
- Falls back to unauthenticated if no credentials configured
- Parses title, url, selftext, created_utc

### 14. NewsIngestionService Refactored
**File:** `backend/llm/src/main/java/com/swingtrade/llm/service/NewsIngestionService.java`
- Constructor injects all 6 sources + NewsArticleStore
- `fetchStockNews(symbol)`: parallel CompletableFuture.allOf() with individual timeouts
- Each source has `.orTimeout(timeoutSeconds, TimeUnit.SECONDS)` + `.exceptionally()` fallback
- Dedup by normalized headline, persist to DB
- `fetchArticlesAndFilings(symbol)`: returns [articles, filings] array
- `formatFilingsPrompt(filings)`: formats filings as text for LLM prompt
- `cleanNewsText()`, `filterRelevantNews()`, `getNewsForSentimentAnalysis()` preserved

### 15. SentimentService Updated
**File:** `backend/llm/src/main/java/com/swingtrade/llm/service/SentimentService.java`
- `analyzeStockSentiment()`: calls `fetchArticlesAndFilings()`, extracts filings
- `performSentimentAnalysis()`: now accepts `filingsPrompt` parameter
- Filings injected into LLM prompt via new `createSentimentAnalysisPrompt(symbol, news, filings)`

### 16. SentimentAnalyzer Prompt Updated
**File:** `backend/llm/src/main/java/com/swingtrade/llm/service/SentimentAnalyzer.java`
- New overloaded `createSentimentAnalysisPrompt(symbol, newsContent, filingsSection)`
- Filings section injected between news headlines and task instruction

### 17. Config Properties
**File:** `backend/llm/src/main/resources/application.properties`
- Added: `news.source.timeout`, `news.source.{moneycontrol,et,google,nse,bse,reddit}.enabled`
- Added: `news.source.{moneycontrol,et,google,reddit}.max-articles`
- Added: `news.reddit.client-id`, `news.reddit.client-secret`

### 18. Stub Removed
- Deleted `backend/llm/src/main/java/com/swingtrade/llm/impl/NewsIngestionService.java`

## Section 2: What Needs to Be Done

### 19. Fix Remaining Import References (DONE)
All ~15 files updated from `com.swingtrade.llm.service.NewsIngestionService.NewsArticle` to `com.swingtrade.domain.NewsArticle`:
- NewsIngestionService.java — removed inner records, added import
- MoneycontrolNewsSource.java, EconomicTimesNewsSource.java, GoogleNewsSource.java
- NseAnnouncementsSource.java, BseAnnouncementsSource.java, RedditIndiaInvestmentsSource.java
- NewsFilterService.java — replaced all `NewsIngestionService.NewsArticle` references
- NewsArticleStoreImpl.java, NewsArticleStore.java — updated imports
- SentimentService.java, SentimentAnalysisLiveE2ETest.java — updated references
- SentimentApiController.java, SignalFilterService.java — updated API layer references
- NewsSource.java — updated interface to use `List<NewsArticle>`

### 20. Remove NewsArticle from NewsIngestionService.java (DONE)
- Deleted inner `public record NewsArticle(...)` + builder (lines 754-819)
- Replaced with `private record NewsItem(...)` (internal use only, lines 754-762)
- Eliminates duplicate definition

### 21. Additional Fixes (DONE — pre-existing build issues)
- SynthesisService.java — moved from `llm/service/` to `api/service/` to fix circular dependency (llm → api → llm)
- NseAnnouncementsSource.java — added `Locale` import, fixed `toZonedDateTime()` call
- BseAnnouncementsSource.java — fixed `toZonedDateTime()` call
- AnalysisOrchestratorService.java — fixed `SynthesisResult` import to `com.swingtrade.api.dto.SynthesisResult`

### 22. Write Unit Tests with WireMock (PENDING)
**File:** `backend/llm/src/test/java/com/swingtrade/llm/service/MoneycontrolNewsSourceTest.java`
- Mock RSS response, verify article extraction, symbol filtering, date parsing

**File:** `backend/llm/src/test/java/com/swingtrade/llm/service/EconomicTimesNewsSourceTest.java`
- Same pattern as Moneycontrol

**File:** `backend/llm/src/test/java/com/swingtrade/llm/service/GoogleNewsSourceTest.java`
- Mock XML response, verify DOM parsing, dedup

**File:** `backend/llm/src/test/java/com/swingtrade/llm/service/NseAnnouncementsSourceTest.java`
- Mock JSON API response, verify filing extraction
- Mock HTML fallback, verify scraping

**File:** `backend/llm/src/test/java/com/swingtrade/llm/service/BseAnnouncementsSourceTest.java`
- Mock HTML page, verify table scraping

**File:** `backend/llm/src/test/java/com/swingtrade/llm/service/RedditIndiaInvestmentsSourceTest.java`
- Mock Reddit JSON response, verify post parsing

### 23. Write Integration Test (PENDING)
**File:** `backend/llm/src/test/java/com/swingtrade/llm/service/NewsIngestionServiceTest.java`
- Update existing test class to test parallel fetch with WireMock
- Verify: all 6 sources called, timeout handling, partial failure (some sources fail, others succeed)
- Verify: dedup across sources, DB persistence

### 24. Build and Verify (DONE)
```bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
cd backend && rm -rf core/target data/target llm/target api/target strategy/target broker/target
mvn compile -Dpmd.skip=true -Dcheckstyle.skip=true
```
- Compile: **SUCCESS** (all 7 modules — core, data, llm, strategy, broker, api)
- Note: Required a thorough `rm -rf target/` clean to resolve corrupt core JAR from stale incremental compilation artifacts.

### 25. Dashboard Verification (PENDING)
- Verify dashboard still loads correctly after SentimentService/SynthesisService changes
- Check that news-related UI components render properly
- Confirm no regressions in sentiment display or signal generation views

## Section 3: Style Guide

### New Source Implementation Pattern
- Implement `NewsSource` interface
- `@Component` annotation
- Constructor injection with `@Value` for config
- Use `java.net.http.HttpClient` (Java 21 built-in) for HTTP calls
- Jsoup for HTML parsing (not DOM — Jsoup is more forgiving)
- Return `List<NewsArticle>` with symbol field set
- Return empty list on failure (never throw)
- Log at warn level on failure

### Config Properties
- `news.source.timeout` — shared timeout for all sources (seconds)
- `news.source.{name}.enabled` — per-source toggle
- `news.source.{name}.max-articles` — per-source limit
- `news.reddit.client-id` / `news.reddit.client-secret` — OAuth credentials (optional)

### Error Handling
- Source timeout: log warning, skip source, continue
- Source returns 0 articles: log info, continue
- All sources fail: SentimentService returns neutral sentiment (existing fallback)
- DB insert fails: log warning, don't fail sentiment analysis

## Section 4: Verification

1. Fix imports (task 19) — DONE
2. Remove duplicate NewsArticle record (task 20) — DONE
3. Additional fixes (SynthesisService move, etc.) (task 21) — DONE
4. `cd backend && mvn compile -Dpmd.skip=true` — **PASS** (all 7 modules)
5. `mvn test` — all existing tests + new tests pass — PENDING (tasks 22-23)
6. `mvn spring-boot:run -Dspring-boot.run.profiles=local,fyers` — app starts without errors — **PASS** (API running, /api/health UP, /api/signals/sentiment/RELIANCE returns POSITIVE)
7. Dashboard verification — PENDING (task 25)