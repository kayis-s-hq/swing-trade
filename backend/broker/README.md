# Broker module

The broker module provides broker-agnostic paper-trading behavior: orders, positions, portfolio state, risk controls, and the paper-trading engine. It does not call external broker APIs; those integrations live in `data`.

Run from `backend/`:

```bash
./gradlew :broker:test
```

Paper-trading mode is selected by the API application's broker configuration. Position lifecycle behavior and pilot verification are tracked in [`docs/status.md`](../../docs/status.md).
