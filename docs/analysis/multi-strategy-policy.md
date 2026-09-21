# Multi-strategy candidate and champion policy

Effective 2026-09-21, the local implementation uses the following deliberately
conservative policy:

- Candidate Explorer evaluates every current `SHADOW` and `CHAMPION` variant for
  each symbol. A candidate must meet the configured strategy BUY threshold
  (default: two strategies) before the common candidate backtest and
  walk-forward gates run.
- Per-strategy backtest and out-of-sample metrics are persisted for attribution
  and displayed in Candidate Explorer. They are informational for qualification;
  qualification currently uses the common performance gates after consensus.
- The policy does not yet require every participating strategy to pass its own
  performance gates. That is a separate product decision because it can reduce
  the candidate set substantially.
- Exactly one current variant may be `CHAMPION`. The strategy configuration API
  rejects a second promotion; it never demotes an existing champion implicitly.
  Zero champions is allowed for shadow-only evaluation, but the orchestrator
  reports a warning and does not mark any signal tradeable until a champion is
  deliberately promoted.
- No local or automated scan promotes a strategy. Promotion remains an explicit
  operator action from the Strategies view/API.

This keeps signal discovery, performance attribution, and live authority
separate while the historical data coverage and per-strategy qualification
policy are still being evaluated.
