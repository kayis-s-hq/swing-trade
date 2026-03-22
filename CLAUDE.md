# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a swing trading system built with Java 21 and Spring Boot 3.x. It's designed for automated trading of NSE/BSE Indian equities with a multi-factor technical approach enhanced by LLM sentiment analysis.

## Architecture

The system is organized into 6 core modules:
- `core`: Domain models (Stock, OhlcvCandle, Signal, Position, Trade, SentimentResult)
- `data`: Data ingestion, storage, and scheduling with PostgreSQL + TimescaleDB
- `strategy`: Technical analysis with TA4J integration and signal generation
- `llm`: LLM client with sentiment analysis pipeline using LangChain4j
- `broker`: Paper trading engine with order management
- `api`: REST endpoints for system interaction

## Key Technologies

- Java 21 with Spring Boot 3.x
- PostgreSQL with TimescaleDB for time-series data
- TA4J for technical analysis
- LangChain4j for LLM integration
- Docker with docker-compose for infrastructure
- Maven multi-module build system

## Development Commands

### Build
```bash
mvn clean install
```

### Run
```bash
# Start database services
docker-compose up -d

# Run the API module
java -jar api/target/api-1.0.0.jar
```

### Testing
```bash
# Run all tests
mvn test

# Run a specific test class
mvn test -Dtest=DataIngestionServiceTest

# Run a specific test method
mvn test -Dtest=DataIngestionServiceTest#testIngestData
```

## Infrastructure

The system requires PostgreSQL with TimescaleDB extension and Redis for caching. These are managed via docker-compose.yml:
```bash
docker-compose up -d
```

## Database Schema

The database schema is managed with Flyway migrations in `data/src/main/resources/db/migration/V1__swing_trade_schema.sql`. This includes:
- TimescaleDB hypertable for ohlcv_candles
- Tables for stocks, signals, positions, trades, sentiment_results
- Proper indexing for performance

## Documentation Rules

All documentation files must be written in Markdown format and placed in the `docs/` folder.

## Planning Directory

The GSD planning folder is located at `.planning/` (root of the swing-trade repository, NOT in this worktree directory). Always reference planning artifacts (SUMMARY.md, PLAN.md, UAT.md, etc.) from `/Users/kayisrahman/Documents/workspace/ideas/swing-trade/.planning/`. This includes:
- System architecture documentation
- API documentation
- User guides
- Technical specifications
- Development guidelines

Each Markdown file should follow these conventions:
- Use proper heading hierarchy (#, ##, ###)
- Include code blocks with appropriate language syntax highlighting
- Use consistent terminology throughout the documentation
- Link to related documents and code sections where relevant