# Data Integrity Remediation and Stage Validation

## Status

**Proposed — no production code, database data, or stage environment has been changed by this plan. The unrelated untracked repository-root `payload.json` was removed with explicit user approval.**

## Objective

Restore trustworthy daily OHLCV data, make signal retrieval deterministic, remove the obsolete HDFC Ltd instrument, validate the deployed application in stage, and close the identified development-environment housekeeping items.

The intended outcome is that every active watchlist symbol has one valid candle per expected NSE trading session, signal generation never fails because multiple rows exist, and the same checks are proven against stage before any pilot decision.

## Decisions already made

| Topic | Decision |
|---|---|
| HDFC | Permanently remove `HDFC` from the active watchlist. Do not alias or rename it to `HDFCBANK`; these are distinct instruments and `HDFCBANK` already exists. |
| Candle repair | Use a bounded reconciliation operation, not the existing catch-up backfill endpoint. |
| Saturday artifact | Remove verified `2026-08-15` candles and prevent non-trading-day ingestion. |
| Signal ties | Select a deterministic single latest signal; do not impose a uniqueness rule that could prohibit different strategies on the same date. |
| Stage checks | Begin with read-only functional validation. Any stage operation that creates signals, backfills data, or creates paper positions remains explicitly opt-in. |
| Dev cleanup | Produce a dry-run inventory first. Delete or repair only confirmed rows by primary key. |

## Findings that drive the design

### Existing `backfill-all` cannot repair historical gaps

`POST /api/ingestion/backfill-all` delegates to `WatchlistService.startPullAll()`. For a symbol that has candles after a gap, it sets the fetch start date to `latestCandle.date + 1`; it never revisits earlier dates. Consequently, the reported missing sessions on 2026-08-20, 2026-08-21, 2026-08-24, and possibly 2026-08-25 will remain missing if this endpoint is run today.

The endpoint remains useful as a normal forward catch-up operation, but it must not be advertised or used as a historical-gap repair tool.

### Candle idempotency is not enforced by PostgreSQL

`ohlcv_candles` has an `id` primary key and a non-unique `(symbol, date)` index. Ingestion first calls `existsBySymbolAndDate()` and then inserts, which is vulnerable to concurrent fetches and cannot prevent duplicate historical rows. The database must ultimately enforce one daily candle per symbol/date.

### Yahoo data needs source-boundary validation

The Yahoo client maps timestamps using UTC and accepts every otherwise-valid row returned by the chart endpoint. It does not verify that a returned date is inside the requested interval or an NSE trading session. The ingestion boundary must reject dates outside the requested range and dates on weekends or stored NSE holidays before persistence.

### The signal 500 is an unrestricted one-row query

`SignalRepository.findLatestBySymbol()` returns `Optional<SignalEntity>` from an ordered query with no limit. Spring Data/JPA expects zero or one row for `Optional`; two latest candidates therefore produce `NonUniqueResultException`. A date and `created_at` tie makes this likely during repeated generation.

### Bulk signal clearing is also over-broad

The bulk generation endpoints loop by symbol but call `deleteByDate(latest.date())`, which deletes all symbols' signals on that date. This is independent of the single-symbol 500 but must be fixed in the same signal-safety change.

## Scope and non-goals

### In scope

- Remove `HDFC` from active watchlists in current and newly created environments.
- Add candle reconciliation, validation, persistence guarantees, and data-quality reporting sufficient to detect this failure class.
- Repair the known candle gaps and Saturday rows in development after code changes are verified.
- Make latest-signal selection deterministic and scope signal deletion to the requested symbol.
- Add stage smoke validation to deployment automation.
- Inventory and clean only confirmed development test positions, including the legacy RELIANCE row with a null `position_id`.
- Archive the obsolete native-image plan and resolve the untracked payload file.

### Out of scope

- Migrating HDFC Ltd market history to HDFC Bank.
- Replacing Yahoo Finance as a provider.
- Creating a full alerting platform or new dashboard for data quality. The reconciliation report and stage check are the first enforcement point.
- Altering real-trading behavior or enabling stage signal execution.

## Implementation plan

### Phase 0 — Capture a read-only baseline

Before changing data, add and run a read-only diagnostic that records the exact remediation target. It must be runnable against development and stage without mutation.

For each active symbol, the diagnostic must return:

- expected NSE sessions in the supplied `[from, to]` range, excluding weekends and `nse_holidays`;
- stored dates, missing dates, and non-trading-day dates;
- duplicate `(symbol, date)` groups, including IDs, `created_at`, and OHLCV values;
- earliest/latest dates and count;
- data-quality status: `GOOD`, `MISSING`, `INVALID_DATE`, or `DUPLICATE`.

For positions, produce a separate development-only inventory containing each candidate row's primary key, symbol, broker type, status, position ID, linked trades/orders, and reason it was classified as a test/legacy candidate. The inventory is a review artifact, not authorization to delete.

**Acceptance criteria**

- The report reproduces the known 2026-08-20/21/24(/25) gaps and 2026-08-15 invalid rows.
- The baseline identifies whether HDFC is currently active. After Phase 1, the same report confirms that HDFC is excluded from active-symbol checks and HDFCBANK remains included.
- The position inventory identifies exact IDs; no broad symbol- or status-based delete is proposed.

### Phase 1 — Remove HDFC from the active watchlist

1. Add a Flyway migration (next version after `V27`) that sets `watchlist.is_active = false` for `symbol = 'HDFC'`. This applies to every database that has applied the project's Flyway history (development, stage, and any future environment); it is not a claim about unmanaged databases.
2. Retain the `stocks` record and any historic rows; this is a watchlist policy change, not a data rewrite.
3. Update the consolidated seed schema so a fresh database does not seed HDFC as active. Prefer removing the seed row entirely if no FK-dependent bootstrap path needs it; otherwise seed it with `is_active = false`.
4. Add migration and repository/service tests verifying HDFC is excluded from active ingestion, orchestration, and signal generation while HDFCBANK remains active.
5. Update `docs/status.md` to state that the active universe contains 14 symbols and HDFC Ltd is deliberately retired.

**Rollback**: set `is_active = true` in an explicit follow-up migration or manually in a non-production environment. Do not restore it by remapping symbols.

### Phase 2 — Make candle persistence and provider parsing safe

#### 2.1 Define the market-session rule

Create one reusable market-calendar component in the data module. Given a date and exchange, it must determine whether a daily NSE candle may exist:

- Monday through Friday only;
- exclude full-day entries in `nse_holidays`; this table is the application's authoritative operational calendar;
- use `Asia/Kolkata` for NSE calendar calculations;
- allow the service to receive an explicit `asOfDate` in tests rather than relying only on system time.

Keep the component independent of the API module so ingestion, the scheduler, and the reconciliation report share exactly the same rule.

Add an `nse_calendar_coverage` table with one row per calendar year: `calendar_year` (primary key), `status` (`VERIFIED` or `PENDING`), `source`, and `verified_at`. The holiday-maintenance procedure marks a year `VERIFIED` only after the published NSE calendar has been loaded and reviewed; it creates/retains `PENDING` otherwise. The calendar component considers a range covered only when every year it spans has a `VERIFIED` row. If coverage is absent or pending, reconciliation returns `CALENDAR_INCOMPLETE` and refuses `apply=true`; it must not infer that every weekday is tradable. The Phase 2 migration seeds the already-reviewed 2026/2027 coverage records from the existing holiday data, and the stage runbook defines the annual update/review step.

#### 2.2 Harden Yahoo response parsing

In `YahooFinanceClient.fetchCandles()`:

1. Obtain the response's exchange timezone from chart metadata when available; use `Asia/Kolkata` for NSE as the safe fallback.
2. Convert each timestamp in that exchange timezone, not unconditionally with `ZoneOffset.UTC`.
3. Reject rows whose converted date is before `startDate` or after `endDate`.
4. Reject null/zero OHLCV data as today, then pass the date through the market-session rule before returning a `CandleData` record.
5. Log a structured warning for each rejected timestamp with symbol, source timestamp, resolved date, and rejection reason. Do not log price payloads unnecessarily.

The date rule belongs at the client/ingestion boundary; database repair must not rely on display-layer filtering.

#### 2.3 Enforce one candle per symbol/date

After Phase 0 identifies the duplicate state, create a data migration that:

1. deletes the verified Saturday 2026-08-15 rows for active NSE instruments;
2. handles any same-date duplicate groups explicitly and deterministically;
3. adds `UNIQUE (symbol, date)` to `ohlcv_candles`.

An **exact duplicate** has equal `open_price`, `high_price`, `low_price`, `close_price`, `volume`, and `adj_close_price`, treating two null values as equal; `id` and creation timestamps do not affect equality. For exact duplicates, retain the row with the earliest `created_at`, then lowest `id`. Do not silently choose a winner for records with different OHLCV values. The preflight report must flag those conflicts for source comparison and an operator-selected repair; the migration must fail loudly if unresolved conflicting groups remain.

The constraint applies to all symbols, not only active ones. Therefore the preflight and conflicting-row resolution cover the entire `ohlcv_candles` table. The known Saturday deletion is intentionally limited to active NSE watchlist rows; invalid data belonging to inactive instruments is reported for separate, approved maintenance rather than silently deleted.

Replace the application-level check-then-insert path with a repository-level PostgreSQL `INSERT ... ON CONFLICT (symbol, date) DO NOTHING` (or an equivalent atomic upsert). Treat the affected-row count as the saved/skipped result. The pre-check may be removed once callers no longer depend on it.

**Tests**

- Yahoo unit tests for: timezone conversion, row outside range, Saturday row, NSE holiday row, and valid Friday row.
- Ingestion service tests proving invalid rows are not persisted and concurrent/repeated processing does not create a second candle.
- PostgreSQL integration test proving the unique constraint rejects a duplicate insert.
- Migration test using exact duplicate and conflicting-duplicate fixtures.

### Phase 3 — Add a bounded reconciliation operation and repair development data

Add a distinct, explicit API rather than changing the meaning of normal catch-up backfill:

```
GET  /api/ingestion/reconcile?from=YYYY-MM-DD&to=YYYY-MM-DD&symbol=optional
POST /api/ingestion/reconcile?from=YYYY-MM-DD&to=YYYY-MM-DD&symbol=SYMBOL&apply=true
```

Rules:

- `GET` is always dry-run and returns the Phase 0 report shape. With no `symbol`, it reports every active symbol; inactive symbols are visible only through the separate maintenance audit.
- `POST` requires `apply=true` and exactly one active `symbol`; otherwise return validation failure. Bulk mutation is deliberately excluded from this first implementation.
- `POST` is enabled only in `local` and `stage` when `INGESTION_RECONCILE_APPLY_ENABLED=true`. In stage it additionally requires a configured `RECONCILE_APPLY_TOKEN` header; production profiles do not expose the apply operation. This feature gate is required because the application has no general authentication work in this scope.
- The request range is bounded to 10 calendar days, which covers the known incident while preventing accidental multi-year provider pulls. The client uses its existing per-request timeout, and the controller has an explicit overall 60-second request deadline; a timeout returns an incomplete per-symbol result and performs no retry in-process.
- For the named symbol, compute expected sessions; fetch the bounded provider range once; persist only missing valid sessions with the atomic insert path. `SOURCE_INCOMPLETE` means one or more expected missing sessions has no valid Yahoo candle after parsing and market-session validation. In that case the request returns every omitted date and performs no insert or delete: repair is all-or-nothing per symbol/range. The database transaction covers the Saturday-row deletion and all inserts only after the provider response is complete.
- Delete only non-trading-day candles explicitly listed by the dry-run report and confirmed by the reconcile apply operation.
- Return counts for missing, fetched, inserted, already-present, invalid-source, invalid-stored-removed, and failures, plus a machine-readable terminal status: `COMPLETED`, `CALENDAR_INCOMPLETE`, `SOURCE_INCOMPLETE`, or `TIMED_OUT`. Persist an audit record only after an apply request reaches a terminal state.
- Permit retry: a successful second apply must insert/delete zero rows.

Do not reuse the existing in-memory `backfill-all` thread/progress semantics for this repair until cancellation and durable job-state behavior are established. Operators run the explicit request once per approved symbol; if bulk repair is later needed, it must use the persisted job-run model rather than a daemon thread.

**Development execution runbook**

1. Deploy Phases 1–3 locally and run automated tests.
2. Call the dry-run endpoint for `2026-08-14` through `2026-08-23`, then `2026-08-24` through `2026-08-25`, for all active symbols; save its JSON response with the work record, not in the repository root. The split respects the 10-calendar-day maximum.
3. Review that 14 active symbols show the expected missing sessions and that every 2026-08-15 row is invalid.
4. Call `POST .../reconcile?...&symbol=<approved symbol>&apply=true` once for each approved active symbol, recording every response.
5. Call dry-run again. Require no missing expected sessions, no invalid session dates, and no duplicate groups.
6. Run the relevant indicator/backtest and signal tests after the repaired data is present. Do not use the result to retune thresholds in this change.

### Phase 4 — Make signal generation deterministic and symbol-scoped

1. Replace `SignalRepository.findLatestBySymbol()` with a query that has an explicit one-row limit. Preferred design: return `List<SignalEntity>` with `PageRequest.of(0, 1, Sort.by(DESC, "date", "createdAt", "id"))`, then let `SignalStoreImpl` take the first result. A native/JPQL query with `LIMIT 1` is acceptable if it remains portable within the project conventions.
2. Include `id DESC` as the final tie-breaker, so equal dates and equal `created_at` values are deterministic.
3. Make the equivalent strategy-aware latest query use the same ordering and one-row semantics where it is used.
4. Keep multiple signals on the same symbol/date permissible when strategy or intended generation semantics differ. Do not add a blanket unique signal constraint as a workaround.
5. Add `deleteBySymbolAndDateAndStrategy(symbol, date, strategy)` and use it in both bulk price-action generation paths, which regenerate only `PRICE_ACTION` signals. Preserve any other strategy's signal for that symbol/date.
6. Reserve `deleteBySymbolAndDate(symbol, date)` for the explicit clear-all-strategies endpoint only, and name/document that behavior accordingly. Eliminate the global `deleteByDate(date)` call from controller flows.
7. Verify that price-action persistence writes `strategy = PRICE_ACTION` rather than accepting the entity default. Add a regression test that creates DEFAULT and PRICE_ACTION rows for the same symbol/date, regenerates price action, and proves only the prior PRICE_ACTION row was replaced.
8. Review whether same-strategy/same-symbol/same-date generation should update the existing signal rather than create another row. If so, make that behavior an explicit follow-up schema/API decision, not an accidental side effect of latest retrieval.

**Tests**

- Repository/data test with two signals tied on `date` and `created_at`; assert the larger ID is returned without `NonUniqueResultException`.
- Service/controller test for `POST /api/signals/generate` using that fixture; assert HTTP 200 and deterministic response.
- Bulk-generation test with two symbols on the same date; assert regeneration/clearing for one does not delete the other symbol's signal.
- Existing integration tests must not rely on non-deterministic insertion order.

### Phase 5 — Stage validation gate

Extend the stage deployment workflow after the current health check with a named, failure-gating `Stage smoke validation` step. It must use the deployed stage URL, not a locally started test server.

Read-only checks:

1. `GET /actuator/health` returns `UP`.
2. Query the Spring Boot Actuator Flyway endpoint, enabled only on the private stage network, and compare its highest successful migration version with the version embedded in the built artifact/smoke-script expectation. The check fails on any failed or pending migration; it does not infer migration state from application health.
3. `GET /api/ingestion/status` succeeds, includes 14 active symbols, and does not include active HDFC.
4. Calculate the latest completed NSE session range in `Asia/Kolkata`: end at the prior completed market session (never the current incomplete session), start nine calendar days earlier, and use the same `nse_holidays` table coverage check. `GET /api/ingestion/reconcile` for that exact range reports no missing, invalid, or duplicate candles. A sparse or intentionally reset stage database is a deliberate failure until it is seeded and reconciled; the smoke test must not mask it.
5. `GET /api/signals/latest`, `GET /api/positions`, and the dashboard's required read endpoints return successful, schema-valid responses.
6. Run a browser smoke test against the deployed dashboard URL: dashboard loads, navigation works, data-ingestion page renders status, and Signals/Positions views render an empty state or data without a frontend exception.
7. Verify Prometheus can scrape the stage application and the expected target is healthy.

The workflow must define the deployed API/dashboard base URLs, test account/auth setup if authentication is added later, Prometheus target identity, and log/screenshot artifact paths and retention. It prints sanitized response summaries on failure and preserves logs/artifacts. It must not call signal generation, reconciliation apply, backfill, order placement, or any endpoint that mutates stage data. Those remain separately authorized manual checks.

Add a short stage-validation runbook documenting required environment variables, URLs, expected test account state, and an operator checklist for an opt-in mutation smoke test if one is later needed.

### Phase 6 — Controlled development housekeeping

#### Test positions

1. Run the Phase 0 position inventory.
2. Review exact IDs and linked records with the user/operator.
3. Delete only approved test position primary keys in one transaction, handling dependent orders/trades according to their foreign keys.
4. Re-run the inventory and verify portfolio totals/open-position counts reconcile.

#### RELIANCE row with null `position_id`

1. Inspect the row and linked trades/orders by database primary key.
2. If it is a confirmed legacy row to retain, assign a reserved, non-colliding identifier such as `LEGACY_<database-id>`; do not guess a `POS_n` value that could interfere with the paper-trading counter.
3. If it is a confirmed test row, remove it through the approved test-position cleanup path instead.
4. Add a regression test that the startup ID reseed logic ignores the reserved legacy prefix and still prevents collisions for generated `POS_n` identifiers.

#### Documentation and local artifact

1. Move `docs/plans/2026-08-08-native-image-upgrade.md` to `docs/plans/archive/` with a brief archive note linking to the superseding approach, if any.
2. Repository-root `payload.json` was confirmed untracked and unrelated to the project before explicit deletion. Do not add a broad ignore rule unless the project deliberately adopts a local scratch-payload convention.
3. Update `docs/status.md` only after the associated verification actually passes; do not mark items complete from code review alone.

## Delivery sequence

1. Phase 0 baseline and Phase 1 HDFC retirement.
2. Phase 2 parser/persistence protection, including migrations and tests.
3. Phase 3 reconciliation API, local repair, and post-repair evidence.
4. Phase 4 signal deterministic retrieval and symbol-scoped deletion.
5. Phase 5 stage deployment validation and a successful stage run.
6. Phase 6 approved development cleanup and documentation archive.

This order ensures that no new bad candles are written before repair, that repaired data is protected by the database, and that signal regeneration does not mutate unrelated symbols.

## Verification matrix

| Area | Automated verification | Operational verification |
|---|---|---|
| HDFC retirement | Migration/service tests | HDFC absent from active status; HDFCBANK present |
| Yahoo parsing | Unit tests with date-boundary fixtures | No invalid weekend/holiday dates in reconcile report |
| Candle uniqueness | PostgreSQL integration/migration tests | Reconcile re-run changes zero rows |
| Missing sessions | Reconciliation unit and integration tests | 2026-08-20/21/24(/25) restored for each active symbol |
| Signal 500 | Tied-row repository/service/controller tests | Single-symbol generate returns 200 on repaired development data |
| Bulk signal isolation | Two-symbol deletion regression test | One symbol's regeneration preserves the other's signal |
| Stage | CI smoke script/unit contract tests | One passing deployed-stage smoke run with artifacts |
| Dev cleanup | Startup ID/reconciliation tests | Approved IDs removed/repaired; second inventory clean |

## Risks and safeguards

| Risk | Safeguard |
|---|---|
| Deleting a valid historical candle | Dry-run report, session-calendar confirmation, and explicit apply operation; no blanket weekend cleanup without recorded evidence. |
| Unique-constraint migration fails on unknown duplicates | Preflight group report; collapse only exact duplicates; fail on conflicting OHLCV rows. |
| Provider returns incomplete/incorrect repair data | Reconcile reports source rows and failures per symbol; preserve existing valid rows; rerun is idempotent. |
| Stage smoke changes trading state | Restrict pipeline to GET/read-only endpoints. |
| Cleanup removes real paper-trading history | Require exact primary-key approval and linked-record review before any DELETE. |
| HDFC reappears in fresh DBs | Update both the migration and consolidated seed schema. |

## Completion criteria

- HDFC is inactive in every Flyway-managed environment and HDFCBANK is unaffected.
- All 14 active symbols have no missing expected NSE sessions, no 2026-08-15 Saturday candle, and no duplicate `(symbol, date)` rows in the repaired range.
- Database uniqueness and ingestion behavior prevent recurrence.
- `POST /api/signals/generate` succeeds with tied historical signals and bulk operations never delete another symbol's rows.
- A stage deployment has passed the new functional smoke suite.
- Development test/legacy position actions are evidenced, approved, and reconciled.
- `docs/status.md` reflects verified—not merely implemented—completion.
