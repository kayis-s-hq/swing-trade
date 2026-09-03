# Data module

The data module owns persistence, Flyway migrations, market-data clients, ingestion, watchlists, and data-quality services. Supported clients include Yahoo Finance (default for local development), Fyers, and Upstox where configured.

Run from `backend/`:

```bash
./gradlew :data:test
./gradlew :data:test --tests '*YahooFinanceClientTest'
```

The local database target and stack commands are documented in [`AGENTS.md`](../../AGENTS.md). Yahoo-specific behavior and rate-limit handling are documented in [`docs/yahoo-finance-api.md`](../../docs/yahoo-finance-api.md).
