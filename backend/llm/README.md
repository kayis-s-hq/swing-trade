# LLM Module

This module provides Large Language Model integration for the swing trading system using LangChain4j.

## Features

1. **vLLM Integration**: Connects to local vLLM endpoint with configurable base URL
2. **Structured Output Parsing**: Supports POSITIVE/NEUTRAL/NEGATIVE sentiment classification with reasoning
3. **News Ingestion Services**: 
   - NSE Corporate Announcements
   - Google News RSS feeds
4. **Sentiment Analysis Pipeline**: 
   - Fetches news for technical BUY signals
   - Processes sentiment for trading decisions

## Architecture

### Components

- `LlmClient`: Main interface for LLM interactions
- `LlmService`: Service layer for news processing
- `LangChain4jLlmClient`: Implementation using LangChain4j
- `NewsIngestionService`: News fetching and processing service

### Configuration

All configurations are managed through `application.properties`:
```
llm.vllm.base-url=http://localhost:8000
llm.vllm.model-name=meta-llama/Llama-3.2-3B-Instruct
llm.vllm.temperature=0.0
news.nse.endpoint=https://nseindia.com/api/corporate-announcements
news.google-rss.url=https://news.google.com/rss
```

## Usage

```java
// Initialize LLM client
LlmClient llmClient = new LangChain4jLlmClient("http://localhost:8000");

// Analyze sentiment
SentimentAnalysisResult result = llmClient.analyzeSentiment("Company XYZ shows strong performance");

// Process news for signals
List<TechnicalSignal> signals = llmClient.processNewsForSignals(newsArticles);
```

## Dependencies

- LangChain4j v0.31.0
- LangChain4j vLLM integration
- Spring Boot Starter
