# Indian Market News Sources Integration Design

**Date:** 2026-07-25
**Status:** Approved
**Scope:** Integrate 6 free-tier Indian market news sources into the sentiment pipeline

## Decisions

| Decision | Choice |
|----------|--------|
| Sources | All 6 free tiers: NSE, BSE, Google News, Moneycontrol, ET, Reddit r/IndiaInvestments |
| Architecture | Separate `@Component` classes implementing `NewsSource` interface |
| Storage | Persist articles to DB (`news_articles` table) |
| Dedup | None — keep all sources, LLM sees everything |
| NSE/BSE | Structured filing injection into LLM prompt |
| Reddit | OAuth personal use script flow |
| Approach | Lean parallel fetchers (no circuit breakers, no registry pattern) |

## Architecture

```
NewsIngestionService (orchestrator)
  ├── MoneycontrolNewsSource
  ├── EconomicTimesNewsSource
  ├── GoogleNewsSource (enhanced)
  ├── NseAnnouncementsSource
  ├── BseAnnouncementsSource
  ├── RedditIndiaInvestmentsSource
  └── NewsFilterService (existing)
```

Each source implements `NewsSource`:
```java
public interface NewsSource {
    String type();
    List<NewsArticle> fetch(String symbol);
    List<StructuredFiling> fetchFilings(String symbol); // NSE/BSE only
}
```

## Components

### New Files

| File | Purpose |
|------|---------|
| `NewsSource.java` | Interface: `type()`, `fetch(symbol)`, `fetchFilings(symbol)` |
| `MoneycontrolNewsSource.java` | Parses Moneycontrol RSS |
| `EconomicTimesNewsSource.java` | Parses ET RSS |
| `GoogleNewsSource.java` | Enhanced Google News (existing logic moved from NewsIngestionService) |
| `NseAnnouncementsSource.java` | NSE corporate announcements API → structured filings |
| `BseAnnouncementsSource.java` | BSE filings API → structured filings |
| `RedditIndiaInvestmentsSource.java` | Reddit OAuth + API from r/IndiaInvestments |
| `NewsArticleEntity.java` | JPA entity for `news_articles` table |
| `NewsArticleRepository.java` | Spring Data JPA repository |
| `StructuredFiling.java` | Record for NSE/BSE filing data |

### DB Migration (V4__add_news_articles_table.sql)

```sql
CREATE TABLE news_articles (
    id          BIGSERIAL PRIMARY KEY,
    symbol      VARCHAR(20) NOT NULL,
    source      VARCHAR(50) NOT NULL,
    title       TEXT NOT NULL,
    link        TEXT,
    summary     TEXT,
    published_at TIMESTAMPTZ,
    raw_content TEXT,
    created_at  TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX idx_news_articles_symbol ON news_articles(symbol);
CREATE INDEX idx_news_articles_source ON news_articles(source);
CREATE INDEX idx_news_articles_published ON news_articles(published_at);
```

## Data Flow

1. `SentimentEvaluationJob` → `SentimentService.analyzeStockSentiment(symbol)`
2. `SentimentService` → `NewsIngestionService.fetchStockNews(symbol)`
3. `NewsIngestionService` spawns 6 `CompletableFuture` tasks (parallel)
4. All articles + filings collected → saved to DB
5. Articles cleaned → combined into prompt context
6. Structured filings formatted → injected as separate prompt section
7. Combined prompt → LLM → `SentimentOutput` → `SentimentResult` → persisted

### Prompt structure

```
Recent news headlines (last 7 days):
[Moneycontrol] Title 1...
[ET] Title 2...
[Google] Title 3...
[Reddit] Title 4...

CORPORATE FILINGS for {symbol}:
- Board Meeting: 2026-08-15 | Purpose: Quarterly Results
- Shareholding: FII 12.5% -> 13.2%
```

## Error Handling

| Scenario | Behavior |
|----------|----------|
| Source timeout (10s) | Log warning, skip source, continue |
| Source returns 0 articles | Log info, continue |
| NSE/BSE no filings | Log debug, no filings section |
| Reddit OAuth expired | Refresh token, retry once. If fails, skip Reddit. |
| All 6 sources fail | Return neutral sentiment (existing fallback) |
| DB insert fails | Log warning, don't fail sentiment analysis |

## Config Properties

```properties
news.source.timeout=10
news.source.moneycontrol.enabled=true
news.source.et.enabled=true
news.source.google.enabled=true
news.source.nse.enabled=true
news.source.bse.enabled=true
news.source.reddit.enabled=true

# Reddit OAuth (personal use script)
news.reddit.client-id=
news.reddit.client-secret=
news.reddit.username=
news.reddit.password=
news.reddit.app-id=
news.reddit.app-secret=
```

## Testing

| Test | Type | Coverage |
|------|------|----------|
| `MoneycontrolNewsSourceTest` | Unit | RSS parsing, article extraction |
| `EconomicTimesNewsSourceTest` | Unit | RSS parsing |
| `NseAnnouncementsSourceTest` | Unit | Filing parsing, structured output |
| `BseAnnouncementsSourceTest` | Unit | Filing parsing |
| `RedditIndiaInvestmentsSourceTest` | Unit | OAuth flow, post parsing |
| `NewsIngestionServiceTest` | Integration | Parallel fetch, timeout, partial failure |
| WireMock tests | Unit | Mock NSE/BSE/Reddit APIs |