---
status: testing
phase: 04-llm-sentiment-layer
source: [04-llm-sentiment-layer-01-SUMMARY.md, 04-llm-sentiment-layer-02-SUMMARY.md, 04-llm-sentiment-layer-03-SUMMARY.md, 04-llm-sentiment-layer-04-SUMMARY.md]
started: 2026-03-23T12:30:00Z
updated: 2026-03-23T12:30:00Z
---

## Current Test

<!-- OVERWRITE each test - shows where we are -->

number: 1
name: SignalEngine Sentiment Filtering
expected: |
  When SignalEngine generates a BUY signal:
  - If sentiment is NEGATIVE: signal is NOT saved to database (suppressed)
  - If sentiment is NEUTRAL: signal IS saved with warning_flag = 'NEUTRAL_SENTIMENT'
  - If sentiment is POSITIVE: signal IS saved with warning_flag = 'WARNING_NONE'
  - If sentiment check throws exception: signal IS saved anyway (graceful degradation)
awaiting: user response

## Tests

### 1. SignalEngine Sentiment Filtering
expected: |
  When SignalEngine generates a BUY signal:
  - If sentiment is NEGATIVE: signal is NOT saved to database (suppressed)
  - If sentiment is NEUTRAL: signal IS saved with warning_flag = 'NEUTRAL_SENTIMENT'
  - If sentiment is POSITIVE: signal IS saved with warning_flag = 'WARNING_NONE'
  - If sentiment check throws exception: signal IS saved anyway (graceful degradation)
result: pending

### 2. Weekly Sector Digest Generation
expected: |
  SentimentAnalysisService.generateSectorDigest(startDate, endDate) returns formatted digest with:
  - "Weekly Sector Sentiment Digest" header
  - Date range (e.g., "Week of: 2026-03-16 to 2026-03-22")
  - Top 3 Positive Sectors section with counts (e.g., "BANK - 45 POS, 12 NEU, 8 NEG")
  - Top 3 Negative Sectors section with counts
  - Summary Statistics (total stocks analyzed, sentiment distribution)
result: pending

### 3. Weekly Digest Scheduled Job
expected: |
  WeeklySectorDigestScheduler runs every Sunday at 17:00 IST and:
  - Fetches digest from SentimentAnalysisService.generateSectorDigestForLastWeek()
  - Sends via TelegramNotificationService.sendMessage(digest)
  - Broadcasts to all configured chat IDs
  - Logs start and completion
result: pending

### 4. vLLM Client HTTP Integration
expected: |
  VLLMClient correctly formats requests to /chat/completions endpoint:
  - Includes model, messages, max_tokens, temperature parameters
  - Parses response extracting content from choices[0].message.content
  - Handles timeout and errors gracefully
result: pending

### 5. News Ingestion from RSS Feeds
expected: |
  NewsIngestionService fetches and parses RSS feeds:
  - Parses XML correctly for Google News and NSE corporate announcements
  - Filters articles by stock symbol
  - Removes HTML tags and normalizes whitespace
  - Handles invalid XML gracefully
result: pending

### 6. Sentiment Analysis Pipeline
expected: |
  SentimentAnalyzer creates correct prompts and parses responses:
  - Prompt includes stock symbol and market context
  - Request uses JSON format with sentiment, confidence, reasoning, keyFactors
  - Parses POSITIVE, NEUTRAL, NEGATIVE sentiment types correctly
  - Falls back to NEUTRAL for malformed JSON or empty responses
result: pending

### 7. Sentiment Result Persistence
expected: |
  SentimentAnalysisService persists results to database:
  - SentimentResultEntity created with proper JPA annotations
  - SentimentResultRepository.save() called after analysis
  - Results can be queried by symbol and date
result: pending

### 8. Sector Digest Service Logic
expected: |
  SentimentAnalysisService.groupBySectorAndSentiment() and getTopSectors() work correctly:
  - Groups sentiment results by stock sector
  - Counts POSITIVE/NEUTRAL/NEGATIVE per sector
  - Ranks sectors by positive count (top performers)
  - Ranks sectors by negative count (underperformers)
result: pending

## Summary

total: 8
passed: 0
issues: 0
pending: 8
skipped: 0

## Gaps

[none yet]
