# Fyers v3 Data Integration — Deep Implementation Plan

> Created 2026-07-04. Decisions locked: verify Fyers E2E first; paper trading only (order code stays dormant); symbol master via Fyers CSV → PostgreSQL; review + split-commit the working diff before new work.

## 0. Verified current state (corrections to earlier assumptions)

Exploration surfaced five facts that change the plan details — three of them are latent bugs the E2E verification would hit immediately:

1. **Migrations are V1–V5, not V1–V3** (`backend/data/src/main/resources/db/migration/` contains `V4__fix_sentiment_results.sql`, `V5__add_trade_labels.sql`). The symbol-master migration must be **V6**, not V4.

2. **Broker-key mismatch bug (blocker).** `MarketDataClientProvider` (`backend/data/src/main/java/com/swingtrade/data/service/MarketDataClientProvider.java`) is injected with `Map<String, MarketDataClient>` keyed by **bean names** — `yahooFinanceClient`, `fyersServiceClient`, `upstoxServiceClient` (from `MarketDataClientConfig` @Bean method names) — but looks up `"yahoo"` / receives `"fyers"` / `"upstox"` from the dashboard (`SettingsView.vue` brokers array). Today `setActiveBroker("fyers")` is refused and `getClient()` falls back to `clients.get("yahoo")` → `null` → `IllegalStateException`. Every ingestion call through the provider is broken. Fix in Step 1 (bean naming).

3. **`FyersServiceClientTest` already exists and is stale/broken.** It asserts `Authorization: token test-fyers-token` (old format; new code sends `{appId}:{token}`), and — worse — the new uncommitted `FyersServiceClient` constructor calls `webClientBuilder.baseUrl(BASE_URL)` which **overrides the MockWebServer URL the test injects**, so tests now hit `https://api-t1.fyers.in` over the network. `mvn -pl data test` is almost certainly red right now, which blocks Step 0's "tests green before commit" gate. A minimal testability fix must ride in the Step 0 fyers commit.

4. **Token refresh is missing the mandatory `pin` field.** `docs/fyers-api-v3.md` (section "3. Refresh Access Token") documents `pin` (4-digit Fyers PIN) as **required** in the `validate-refresh-token` body. `FyersAuthService.refreshToken()` sends only `grant_type`/`refresh_token`/`appIdHash`, and `FyersConfig` has no pin property. The 401-refresh-retry path will fail E2E until this is added.

5. **Quotes go through the SDK singleton (`FyersClass.getInstance().GetStockQuotes`)**, which hits the real host with its own internal HTTP stack — unmockable with MockWebServer and stateful across tests. The candle path uses WebClient. Recommendation (Step 1): reimplement `fetchQuote`/`fetchQuotes` with WebClient against `GET /data/quotes?symbols=...` (fully documented in `docs/fyers-api-v3.md`), sharing the 401-refresh-retry helper with the candle path. The SDK stays as the (dormant) order-management transport only.

Other notes:
- The junk dir `backend/null object or invalid expression/` is a botched Maven repo layout of `fyersjavasdk-1.9.0` (from a `mvn install:install-file` with an unevaluated variable). The artifact also exists in `~/.m2/repository/com/tts/in/fyersjavasdk/1.9.0/`, so deleting the junk dir is safe locally. (Portability risk noted in Risks.)
- `DataIngestionService.autoIngestData()` and `EodIngestionScheduler.ingestLatestForAll()` **both** run `@Scheduled(cron = "0 30 16 * * MON-FRI")` — duplicate EOD jobs (idempotent, but doubles API load). Cleanup in Step 3.
- `FyersAuthController` still carries `@Profile("fyers")` (only the data-module beans were de-profiled in commit 9c2f11a). Harmless in dev (dev-stack always activates `fyers`), but inconsistent with runtime broker switching — address in Step 3.
- `backend/api/logs/*.gz` churn in git status; `logs/` and the token file `backend/api/data/fyers-tokens.json` (default `fyers.tokenStorePath=data/fyers-tokens.json`, relative to `backend/api/`) are not gitignored.
- Fyers history API caps a `resolution=D` request at ~366 days. `backfillStockData(symbol, 3)` does one `fetchCandles(from, to)` call — Fyers would silently truncate a 3-year backfill. Chunking needed (Step 1).

---

## Step 0 — Git hygiene: cleanup, green build, split commits

### Task 0.1 — Delete junk + ignore runtime artifacts
- Delete `backend/null object or invalid expression/` (whole directory).
- Add to `.gitignore`:
  ```
  backend/api/logs/
  backend/api/data/fyers-tokens.json
  ```
- Stage the already-deleted `backend/api/logs/swing-trade-local.log.2026-06-26.0.gz` and `git rm -r --cached backend/api/logs` so the new `.gz` files stop appearing.

Verification:
```bash
git status --short   # no "null object" dir, no logs/*.gz noise
```

### Task 0.2 — Make the working tree build and tests pass
The stale `FyersServiceClientTest` must be repaired before any commit (two changes, folded into commit 4 below):
- **Testable constructor** in `FyersServiceClient.java` — mirror `UpstoxServiceClient`'s pattern:
  ```java
  public FyersServiceClient(WebClient.Builder b, FyersAuthService auth) {
      this(b, auth, BASE_URL);
  }
  // package-private for tests
  FyersServiceClient(WebClient.Builder b, FyersAuthService auth, String baseUrl) {
      this.webClient = b.baseUrl(baseUrl)...
  }
  ```
- Update `FyersServiceClientTest`: use the package-private ctor with the MockWebServer URL; stub `when(mockAuthService.getClientId()).thenReturn("TESTAPP-100")`; change the auth-header assertion to `"TESTAPP-100:test-fyers-token"`.
- Note: `fetchCandleReturnsEmptyWhenNoToken` currently passes `null` token — new code logs and returns empty before any HTTP call; keep.

Verification:
```bash
cd backend
mvn -q -pl data -am test          # data module green
mvn -q -DskipTests clean install  # full reactor compiles (proves SDK resolves from ~/.m2 after junk-dir delete)
```

### Task 0.3 — Commit plan (5 commits)

Whole-file staging means commits 2–3 will not compile in isolation (the interface change forces all three implementers); only HEAD must be green. If per-commit compilability matters, make the three new `MarketDataClient` methods `default` (returning `null`/empty) in commit 2 — otherwise proceed as below.

| # | Files | Message |
|---|-------|---------|
| 1 | delete `backend/null object or invalid expression/`, `.gitignore`, staged deletion of `backend/api/logs/*.gz` | `chore: remove stray maven artifact dir, ignore runtime logs and fyers token file` |
| 2 | `backend/data/.../service/MarketDataClient.java`, `QuoteData.java`, `SearchResult.java`, `UpstoxServiceClient.java` (stub impls) | `feat(data): add quote and symbol-search contract to MarketDataClient` |
| 3 | `backend/data/.../client/YahooFinanceClient.java`, `backend/data/src/test/.../client/YahooFinanceClientTest.java`, `docs/yahoo-finance-api.md` | `feat(data): implement quotes, search and rate limiting in YahooFinanceClient` |
| 4 | `backend/data/.../service/FyersServiceClient.java` (incl. Task 0.2 ctor), `backend/data/src/test/.../service/FyersServiceClientTest.java` (repaired), `docs/fyers-api-v3.md` | `feat(data): Fyers v3 candles, quotes and dormant order-management client` |
| 5 | `dashboard/src/views/SettingsView.vue`, `CLAUDE.md`, `docs/superpowers/plans/2026-07-01-dev-stack-skill-and-cli.md` | `feat(dashboard): fyers auth popup flow with status polling; docs port fixes` |

Final gate: `mvn -q test` at `backend/` root, `cd dashboard && npm run typecheck`.

---

## Step 1 — Fyers E2E verification (auth, candles, quotes, tests)

Ordering: 1.1 → 1.2 → 1.3 → 1.4 → 1.5 (all local/unit) → 1.6 (live E2E).

### Task 1.1 — Fix broker registry keys (prerequisite for everything provider-routed)
File: `backend/data/src/main/java/com/swingtrade/data/config/MarketDataClientConfig.java`
```java
@Bean(name = "yahoo")  public MarketDataClient yahooFinanceClient() {...}
@Bean(name = "fyers")  public MarketDataClient fyersServiceClient(...) {...}
@Bean(name = "upstox") @Profile("upstox") public MarketDataClient upstoxServiceClient(...) {...}
```
Add `backend/data/src/test/java/com/swingtrade/data/service/MarketDataClientProviderTest.java` (plain unit test: map of mocks keyed `yahoo`/`fyers`, assert default routing, switch, unknown-broker fallback).

### Task 1.2 — Token refresh with PIN
Files: `FyersConfig.java` (add `private String pin;` + accessors), `FyersAuthService.java` (add `"pin", fyersConfig.getPin()` to the refresh body; guard: if pin is blank, log a clear error "fyers.pin required for token refresh" instead of sending a doomed request), `backend/api/src/main/resources/application-fyers.properties` (`fyers.pin=${FYERS_PIN:}`), plus `FYERS_PIN` in `backend/.env` (untracked).
Extend `FyersAuthServiceTest`: refresh request body contains `pin`; refresh skipped with warning when pin missing.

### Task 1.3 — Rework quotes onto WebClient; unify auth-retry
File: `FyersServiceClient.java`
- Extract a helper used by both candles and quotes:
  ```java
  private String getWithAuthRetry(String path) {
      // GET path with "Authorization: {appId}:{token}"
      // on WebClientResponseException 401 (or body {"s":"error","code":-16/-15/-17}):
      //   authService.refreshToken(); retry once with new token
  }
  ```
- `fetchQuote`/`fetchQuotes` → `GET /data/quotes?symbols=NSE:X-EQ,NSE:Y-EQ` parsed with Jackson (response shape documented in `docs/fyers-api-v3.md` "Stock Quotes": `d[].v.{lp,ch,chp,high_price,low_price,prev_close_price,volume,short_name}` — note these are JSON **numbers**, so parse with `v.path("lp").decimalValue()` rather than the current `getString(...)` + `new BigDecimal(String)` which throws on numeric JSON; this is the parse bug class the current SDK-path code has).
- Remove `FyersClass` initialization from the constructor (keep `getSdk()` only inside order-management methods; they stay dormant).
- Add ~365-day chunking in `fetchCandlesList` (loop windows, concatenate, dedupe by date).
- URL building: switch from hand-escaped `String.format` to `UriComponentsBuilder`/`uriBuilder` with `queryParam("date[from]", ...)` to avoid double-encoding surprises.

### Task 1.4 — Full mocked unit-test suite
File: `backend/data/src/test/java/com/swingtrade/data/service/FyersServiceClientTest.java` (extend, MockWebServer style like `YahooFinanceClientTest`/`UpstoxServiceClientTest`):
- candle parsing (exists), epoch→IST date correctness, error body, empty candles
- **401 then success → refresh called once, candles returned** (`verify(mockAuthService).refreshToken()`)
- 401 then 401 → empty list, no infinite retry
- `fetchQuote` single: correct path `/data/quotes?symbols=NSE:RELIANCE-EQ`, numeric-field parsing
- `fetchQuotes` batch of 3: order/symbol mapping (strip `NSE:`/`-EQ`)
- quotes error body `{"s":"error"}` → empty
- chunking: range of 2 years issues 3 requests (assert `mockWebServer.getRequestCount()`)

### Task 1.5 — Real OAuth + live data E2E (manual, scripted checks)
```bash
./dev-stack.sh start fyers                       # infra on pi-node + Spring Boot :8080
curl -s localhost:8080/api/fyers/status | jq     # {"connected":false,...} initially
# Dashboard: cd dashboard && npm run dev → http://localhost:3003/settings
#   select Fyers → Connect → popup login → poll flips to connected
curl -s localhost:8080/api/fyers/status | jq '.connected'   # true
ls -la backend/api/data/fyers-tokens.json        # tokens persisted
# switch active broker to fyers (now works after Task 1.1):
curl -s -X POST localhost:8080/api/settings/broker -H 'Content-Type: application/json' -d '{"broker":"fyers"}'
# live candles for 2-3 Nifty names:
curl -s -X POST "localhost:8080/api/ingestion/backfill?symbol=RELIANCE&years=1" | jq
curl -s -X POST "localhost:8080/api/ingestion/backfill?symbol=TCS&years=1" | jq
curl -s "localhost:8080/api/ingestion/status" | jq
# DB spot-check:
PGPASSWORD=swingtrade_password psql -h 192.168.0.100 -p 5435 -U swingtrade_user -d swingtrade_db \
  -c "select symbol, count(*), max(date) from ohlcv_candles where symbol in ('RELIANCE','TCS') group by 1;"
```
Refresh-path check: edit `fyers-tokens.json` to corrupt the access token, restart, trigger `POST /api/ingestion/latest?symbol=RELIANCE`, confirm log shows refresh + retry success (requires `FYERS_PIN` set).
Quote verification vehicle: add the small `GET /api/quotes?symbols=RELIANCE,TCS` controller here (see Step 3.2 sketch — pull it forward; it is ~30 lines and is the only clean way to verify quotes E2E).

Commits for Step 1: one per task (`fix(data): register market data clients under broker keys`, `fix(data): send Fyers PIN on token refresh`, `refactor(data): quotes via REST with shared 401-refresh retry; chunk history >1y`, `test(data): mocked FyersServiceClient suite`).

---

## Step 2 — Symbol master in PostgreSQL (V6)

### Task 2.1 — Migration
New file: `backend/data/src/main/resources/db/migration/V6__add_fyers_symbol_master.sql`
```sql
CREATE TABLE IF NOT EXISTS fyers_symbol_master (
    id             BIGSERIAL PRIMARY KEY,
    fy_token       VARCHAR(32)  NOT NULL UNIQUE,
    fyers_symbol   VARCHAR(64)  NOT NULL,          -- e.g. NSE:RELIANCE-EQ
    trading_symbol VARCHAR(32)  NOT NULL,          -- e.g. RELIANCE
    name           TEXT,
    exchange       VARCHAR(16)  NOT NULL DEFAULT 'NSE',
    segment        VARCHAR(16),
    lot_size       INTEGER,
    tick_size      NUMERIC(10,4),
    isin           VARCHAR(13),
    updated_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_fyers_symbol_master_symbol ON fyers_symbol_master (fyers_symbol);
CREATE INDEX IF NOT EXISTS ix_fyers_symbol_master_trading ON fyers_symbol_master (trading_symbol);
CREATE INDEX IF NOT EXISTS ix_fyers_symbol_master_name ON fyers_symbol_master (LOWER(name) text_pattern_ops);
```

### Task 2.2 — Entity + repository
New: `backend/data/src/main/java/com/swingtrade/data/entity/FyersSymbolEntity.java`, `backend/data/src/main/java/com/swingtrade/data/repository/FyersSymbolRepository.java`
```java
Optional<FyersSymbolEntity> findByTradingSymbolIgnoreCase(String tradingSymbol);
@Query("select f from FyersSymbolEntity f where upper(f.tradingSymbol) like upper(concat(:q,'%')) " +
       "or upper(f.name) like upper(concat('%',:q,'%')) order by f.tradingSymbol")
List<FyersSymbolEntity> search(@Param("q") String q, Pageable page);
long count();
```

### Task 2.3 — Ingestion service
New: `backend/data/src/main/java/com/swingtrade/data/service/FyersSymbolMasterService.java` (pattern reference: `NseInstrumentService`, but DB-backed, no `@Profile`).
```java
@Service
public class FyersSymbolMasterService {
    private static final String CSV_URL = "https://public.fyers.in/sym_details/NSE_CM.csv";
    // Headerless CSV; expected columns (VERIFY against a live download before coding, see Risks):
    // 0 fyToken | 1 name/description | 2 exch instrument type | 3 lot size | 4 tick size
    // 5 ISIN | 6 trading session | 7 last update | 8 expiry | 9 fyers symbol (NSE:X-EQ)
    // 10 exchange id | 11 segment id | 12 scrip code | 13 trading symbol (X) | ...

    @Transactional
    public int refresh() {
        String csv = webClient.get().uri(CSV_URL).retrieve().bodyToMono(String.class)
                     .block(); // ~2-4 MB; raise WebClient max in-memory size to 16MB via ExchangeStrategies
        List<FyersSymbolEntity> rows = parse(csv);   // keep only symbols ending "-EQ" (optionally "-BE")
        repository.deleteAllInBatch();               // simple replace; table is small (~2k rows)
        repository.saveAll(rows);
        return rows.size();
    }

    // parse(): split lines; use commons-csv (add org.apache.commons:commons-csv to data/pom.xml)
    // because company names may contain commas/quotes; skip malformed rows with a warn counter.

    @Scheduled(cron = "0 45 8 * * MON-FRI", zone = "Asia/Kolkata")  // pre-market daily
    public void scheduledRefresh() { ... refresh(); ... }

    @EventListener(ApplicationReadyEvent.class)
    public void refreshIfEmpty() { if (repository.count() == 0) refresh(); }  // async-safe, log-only on failure
}
```
Note: no auth required — `public.fyers.in` is unauthenticated, so this also works before OAuth login.

### Task 2.4 — Back the three stubbed methods
`FyersServiceClient.java` constructor gains `FyersSymbolMasterService symbolMaster` (update `MarketDataClientConfig.fyersServiceClient(...)` bean):
- `searchSymbols(q)` → `symbolMaster.search(q, 20)` mapped to `SearchResult.of(tradingSymbol, name, name, "EQUITY", "NSE", "NSE")`
- `fetchAllStockSymbols()` → all trading symbols
- `fetchInstrumentDetails(symbol)` → lookup; fall back to current hardcoded stub with a warn if missing
- `fetchChartMeta(symbol)` (optional, low priority — no current consumers): compose from `fetchQuote` + master (price, prevClose, exchange, shortName; 52-week fields null).

### Task 2.5 — Manual refresh endpoint
`backend/api/src/main/java/com/swingtrade/api/controller/AdminController.java`: add
`POST /api/admin/symbols/refresh` → `{"count": n}` and `GET /api/admin/symbols/status` → `{count, lastUpdatedAt}`.

### Task 2.6 — Tests
- `FyersSymbolMasterServiceTest`: parse() against a 5-line fixture string (EQ row, index row filtered out, malformed row skipped, quoted-comma name); refresh() with MockWebServer + mocked repository.
- Extend `FyersServiceClientTest`: search/instrument-details backed by a mocked `FyersSymbolMasterService`.

Verification:
```bash
cd backend && mvn -q -pl data -am test
./dev-stack.sh start fyers        # Flyway applies V6 (watch log: "Migrating schema ... to version 6")
curl -s -X POST localhost:8080/api/admin/symbols/refresh | jq   # count ~1900-2200
PGPASSWORD=swingtrade_password psql -h 192.168.0.100 -p 5435 -U swingtrade_user -d swingtrade_db \
  -c "select count(*), min(updated_at) from fyers_symbol_master; select * from fyers_symbol_master where trading_symbol='RELIANCE';"
```

Commits: `feat(data): fyers symbol master table, ingestion service and daily refresh (V6)`, `feat(data): back Fyers search/instrument lookups with symbol master`, `feat(api): admin endpoints for symbol master refresh`.

---

## Step 3 — Wire into app flow

### Task 3.1 — Runtime broker consistency
- Remove `@Profile("fyers")` from `FyersAuthController.java` (matches commit 9c2f11a's intent; the controller only needs `FyersConfig` to be populated).
- Remove the duplicate `@Scheduled` from `DataIngestionService.autoIngestData()` (keep `EodIngestionScheduler` as the single EOD entry point — it has rate limiting).

### Task 3.2 — Quote + search API endpoints (provider-routed, broker-agnostic)
New: `backend/api/src/main/java/com/swingtrade/api/controller/MarketDataController.java`
```java
@RestController @RequestMapping("/api")
public class MarketDataController {
    // GET /api/quotes?symbols=RELIANCE,TCS  -> provider.getClient().fetchQuotes(list)
    // GET /api/symbols/search?q=reli        -> provider.getClient().searchSymbols(q)
}
```
(If the quotes endpoint was already added in Task 1.5, this task only adds search.)

### Task 3.3 — Dashboard
- `dashboard/src/api/client.ts`: add `searchSymbols(q)` and `getQuotes(symbols)`.
- Watchlist add-flow: wire symbol search autocomplete into the existing add-symbol input (`WatchlistView`), falling back to free-text entry.
- Settings: verify broker persistence display (`GET /api/settings` reflects switch).

### Task 3.4 — End-to-end regression with Fyers active
```bash
./dev-stack.sh start fyers
curl -s -X POST localhost:8080/api/settings/broker -d '{"broker":"fyers"}' -H 'Content-Type: application/json'
curl -s "localhost:8080/api/symbols/search?q=TATA" | jq '.data | length'      # >1
curl -s "localhost:8080/api/quotes?symbols=RELIANCE,TCS,INFY" | jq '.data[].regularMarketPrice'
curl -s -X POST "localhost:8080/api/ingestion/latest?symbol=INFY" | jq
curl -s -X POST "localhost:8080/api/ingestion/backfill-all?years=1" | jq      # watchlist batch through Fyers
curl -s "localhost:8080/api/data/pull/progress" | jq
# then switch back and confirm yahoo still works:
curl -s -X POST localhost:8080/api/settings/broker -d '{"broker":"yahoo"}' -H 'Content-Type: application/json'
curl -s -X POST "localhost:8080/api/ingestion/latest?symbol=RELIANCE" | jq
```
Dashboard checks on http://localhost:3003: Settings broker toggle, Fyers connected badge, watchlist search-add, data pull progress.

Commits: `fix(data): single EOD scheduler; fyers auth controller available in all profiles`, `feat(api): broker-agnostic quotes and symbol search endpoints`, `feat(dashboard): symbol search in watchlist, quotes via active broker`.

---

## Out of scope (this phase)

Live order placement, broker-module wiring of the Fyers order-management methods, Phase 9 Signal notifications, strategy/backtesting work.

---

## Risks / unknowns and mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| **NSE_CM.csv column layout** is undocumented and headerless; column map above is from known Fyers conventions | Parser maps wrong fields | Before writing the parser: `curl -s https://public.fyers.in/sym_details/NSE_CM.csv \| head -3` and pin the layout in a comment + fixture. Parser validates: col 9 matches `^NSE:.+-EQ$`, tick_size numeric; skip-and-count otherwise. Fyers also publishes `NSE_CM_sym_master.json` — if the CSV proves ambiguous, switch to the JSON (self-describing keys). |
| **`api-t1` vs `api` host** — SDK/doc use `api-t1.fyers.in`; some Fyers deployments use `api.fyers.in` for v3 | Auth or data calls 404/redirect | Both `FyersAuthService` and `FyersServiceClient` hardcode BASE_URL. Move to `fyers.apiBaseUrl` config property (default `https://api-t1.fyers.in`) during Task 1.3 so a host flip is a config change; verify during Task 1.5 with the real login. |
| **Refresh PIN semantics** — refresh token valid 15 days, access token ~1 day; wrong/missing PIN returns success=false | Nightly jobs die silently after day 1 | Task 1.2 guard + explicit log; E2E test the refresh path deliberately (corrupt token trick). Document in `docs/fyers-api-v3.md` that a full re-login is needed every 15 days. |
| **Fyers rate limits** (~10 req/s, 200/min, 100k/day per app — not officially documented) | 429s during backfill-all of Nifty-500 | `EodIngestionScheduler` already sleeps `yahoo.finance.rate-limit-ms=500`; generalize property name (`marketdata.rate-limit-ms`) and apply the same delay inside `WatchlistService.startPullAll`. Treat 429 as retry-after-sleep in `getWithAuthRetry`. |
| **Fyers returns HTTP 200 with `{"s":"error","code":-16}`** for bad tokens on some endpoints instead of 401 | Refresh-retry never triggers | In `getWithAuthRetry`, trigger refresh on body codes -15/-16/-17 as well as HTTP 401 (unit-tested). |
| **SDK singleton (`FyersClass.getInstance()`) shares mutable static state** | Test pollution; wrong credentials after broker switch | Task 1.3 removes SDK from the data path entirely; order-management keeps `getSdk()` which re-sets credentials per call. No SDK calls in unit tests. |
| **`fyersjavasdk` only exists in local `~/.m2`** (manually installed; junk dir was a failed install) | Fresh clone/CI cannot build | Out of scope to fix now; note in README/CLAUDE.md the `mvn install:install-file` command, or later commit a project-local `file://` repo. Verify Step 0 full build after junk-dir deletion. |
| **Flyway V6 on TimescaleDB pi-node** — shared DB at 192.168.0.100:5435 | Failed migration blocks startup | DDL is additive-only, `IF NOT EXISTS`; rollback = `drop table fyers_symbol_master; delete from flyway_schema_history where version='6';` |
| **Quote field types** (numbers vs strings) in `/data/quotes` | `BigDecimal(String)` parse crashes | Parse via Jackson `decimalValue()`/`asLong()`; unit fixtures copied verbatim from `docs/fyers-api-v3.md` sample response. |
| Symbol-master refresh vs 08:45 job on holidays/weekends | Stale data (harmless) | Replace-on-refresh is idempotent; `refreshIfEmpty` covers cold starts. |

---

## Critical files

- `backend/data/src/main/java/com/swingtrade/data/service/FyersServiceClient.java`
- `backend/data/src/main/java/com/swingtrade/data/config/MarketDataClientConfig.java`
- `backend/data/src/main/java/com/swingtrade/data/service/FyersAuthService.java`
- `backend/data/src/test/java/com/swingtrade/data/service/FyersServiceClientTest.java`
- `backend/data/src/main/resources/db/migration/V6__add_fyers_symbol_master.sql` (new)
