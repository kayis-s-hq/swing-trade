# Sentiment Analysis Dashboard Design

## Problem

The SentimentView shows only the parsed LLM output (score, summary, red flags, catalysts). No way to verify what news articles the LLM analyzed or how the reasoning was derived. Cannot validate the LLM's decision quality.

## Solution

Add a story-flow detail view: news sources → LLM analysis → context panel. Single scrollable page per analysis.

## Design

### Detail View Layout (4 sections)

**1. Symbol selector** — Existing: input + View/Refresh buttons.

**2. News Sources** — Fetches `GET /api/news/{symbol}/latest`. Each article card:
- Title (clickable → source URL)
- Source name + published date
- Short snippet (first 120 chars)
- Source badge

**3. LLM Analysis** — Fetches `GET /api/sentiment/{symbol}/latest`. Shows:
- Score badge + confidence bar
- Summary text
- Red flags list
- Catalysts list
- Analysis date

**4. Context Panel** — New section:
- **Technical signal agreement**: Current technical signal (BUY/SELL/HOLD) from TA4j. Green check if sentiment matches, yellow warning if conflicting.
- **Confidence breakdown**: Confidence score, article count, LLM response length.
- **Historical comparison**: Mini trend of last 5 sentiment results for this symbol.

### Data Flow

On "View" for a symbol:
1. Fetch sentiment latest (existing API)
2. Fetch news latest (existing API, already in client)
3. Fetch latest technical signal (new endpoint)
4. Fetch sentiment history for trend (existing API)
5. All 4 results rendered in single page

### New API Endpoint

`GET /api/signals/{symbol}/latest` — Returns the current technical signal for the symbol. Consumes existing `SignalRepository.findLatestBySymbol()`.

### New Frontend Components

- `NewsSourceCard.vue` — Article display (title, source, date, snippet, link)
- `ContextPanel.vue` — Technical agreement + confidence breakdown + historical trend
- Extend `SentimentView.vue` to render all 4 sections

### Types

Extend `SentimentResult` or add a `SentimentDetail` type that includes article count and response length metadata. Add `TechnicalSignal` type for the signal agreement display.

### No Changes to Backend LLM Pipeline

The existing sentiment analysis flow (news fetch → clean → prompt → vLLM → parse → persist) remains unchanged. This only adds display of data already available through existing APIs.