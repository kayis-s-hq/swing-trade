# Dashboard Error Management

## Context

Frontend failures currently use incompatible contracts and presentation patterns. `dashboard/src/api/shared.ts` resolves most failures as `{ success: false }`, while streaming APIs throw. Several mutation callers ignore the resolved failure and report success anyway—most critically position create/close, signal execution, and data-pull cancellation. Other failures appear as raw backend text, native browser alerts, stale data, empty states, permanent spinners, or no message.

Goal: every failure produces a clear, contextual message and correct recovery action without leaking raw HTML/internal details or claiming a failed trading action succeeded. Implement dashboard-wide, but stage work so trading correctness lands first and each change remains testable.

Recommended contract: public domain API methods resolve only confirmed success values and throw a typed `AppError` for every failure. This prevents callers from accidentally treating `{ success: false }` as success. A temporary compatibility helper supports endpoint-by-endpoint migration, then is removed.

At implementation start, mirror this approved plan into `docs/plans/2026-08-26-dashboard-error-management.md` per project convention.

## What’s already done

- `dashboard/src/api/shared.ts` centralizes base URL, headers, timeout, parsing, and retry behavior.
- `dashboard/src/composables/useAsyncData.ts` provides basic loading/error state.
- `dashboard/src/components/ErrorMessage.vue`, `Toast.vue`, and `BackendDownBanner.vue` provide reusable UI foundations.
- `dashboard/src/stores/appState.ts` owns backend health polling.
- `dashboard/src/errors/errorClasses.ts` provides reusable typed specializations of `AppError` for network, timeout, cancellation, malformed-response, and runtime failures. Shared transport, SSE parsing, and runtime reporting use these classes instead of repeating error-kind defaults.
- Existing Vitest patterns cover hoisted API mocks, `flushPromises`, deferred promises, memory routers, and fake timers.
- Existing tests cover Toast behavior, settings-load warnings, job error preservation, and orchestrator start failure.

## What needs to be done

### 1. Define typed failures and one API contract

Critical files:

- Create `dashboard/src/errors/appError.ts` and `appError.test.ts`.
- Create `dashboard/src/errors/errorClasses.ts` and `errorClasses.test.ts`.
- Modify `dashboard/src/api/shared.ts`, `types.ts`, `client.ts`, and `index.ts`.
- Create `dashboard/src/api/shared.test.ts`.

Implement `AppError` with:

- `kind`: network, timeout, authentication, permission, validation, not-found, conflict, rate-limit, server, malformed-response, cancelled, runtime, or unknown.
- Optional HTTP status, backend code, correlation/request ID, retry-after value, technical detail, cause, and `retryable`.
- `outcomeUnknown` for interrupted non-idempotent operations where completion cannot be confirmed.
- Helpers such as `asAppError()`, `isAppError()`, and one operation-aware formatter returning a human title, message, and safe recovery action.

Add reusable error utility classes on top of the common `AppError` contract:

- `NetworkError`, `TimeoutError`, `CancelledError`, `MalformedResponseError`, and `RuntimeAppError` must provide safe defaults for their stable `kind`, message, and retry behavior while preserving optional metadata/cause fields.
- `isErrorKind(error, kind)` must provide a readable, type-safe guard for consumers that need kind-specific recovery.
- Transport, SSE parsing, runtime reporting, and view-level recovery must construct these utility classes (or use `asAppError()` for unknown failures) rather than duplicating `new AppError({ kind: ... })` boilerplate.
- Re-export the utility classes from `api/index.ts` during migration so domain consumers have one supported import surface.

Refactor transport behavior:

- Public domain API methods return `Promise<T>`; resolved means confirmed success, failure throws `AppError`.
- Require each adapter to declare whether the wire payload is a direct value or backend `{ success, data, error }` envelope. Treat HTTP 200 `success:false` as failure.
- Parse backend `error` first, then legacy `message`; retain safe code/correlation metadata.
- Never use raw HTML, stack traces, generic 5xx bodies, or browser-native exception text as primary UI copy.
- Classify invalid JSON, unexpected content type, malformed envelopes, and missing required fields as malformed responses.
- Retry GET/HEAD once only for network failures and 502/503/504. Do not retry timeouts or POST/PUT/PATCH/DELETE automatically.
- For failed mutations with an ambiguous outcome, offer status refresh—not blind mutation retry.
- Consolidate signal/analysis SSE parsing: typed HTTP/network errors, abort support, missing-body checks, malformed-event handling, and required terminal completion event.
- Keep a temporary result-returning compatibility path only while adapters migrate; remove it in Step 7.

User-facing message rules:

- Network: “Can’t reach the backend. Check the service, then retry.”
- Timeout: “The request took too long. Try again.” Reads may retry; ambiguous mutations must refresh status.
- Validation/auth/permission/not-found/conflict/rate-limit: contextual safe guidance using status/code metadata.
- Server/malformed response: generic explanation plus optional status, safe code, and correlation ID under Details.

### 2. Build shared request-state and notification UI

Files:

- Modify and test `dashboard/src/composables/useAsyncData.ts`.
- Modify and test `dashboard/src/components/ErrorMessage.vue` and `Toast.vue`.
- Create and test `dashboard/src/stores/notifications.ts` and `components/NotificationHost.vue`.
- Mount `NotificationHost` in `dashboard/src/App.vue`.

Behavior:

- `useAsyncData` exposes typed error, initial loading, refreshing, stale state, last successful update, retry, and cancellation/latest-request protection.
- Initial failure without usable data shows persistent inline `ErrorMessage` and no misleading empty state.
- Refresh failure preserves prior data, marks it stale, and shows “Last updated …; refresh failed” with Retry.
- Superseded/navigation cancellations remain silent; stale responses cannot overwrite newer data.
- `ErrorMessage` accepts title, message, optional details, and contextual action; add `role="alert"`, `aria-busy`, and optional focus for blocking/form failures.
- Make `Toast` presentational. Notification store owns queue, timers, deduplication, dismissal, and actions.
- Errors use `role="alert"`; success/warning/info use `role="status"`. Dismiss buttons have accessible labels. Do not show duplicate inline and toast errors for the same failure.

### 3. Fix trading and operational mutation correctness first

API/view groups:

- `api/positions.ts` + `views/PositionsView.vue`
- `api/signals.ts` + `views/SignalsView.vue`
- `api/ingestion.ts` + `views/DataIngestionView.vue`
- `api/watchlist.ts` + `views/WatchlistView.vue`
- `api/job.ts` + mutation paths in `views/OrchestratorView.vue`

Required outcomes:

- Position create/close closes and resets its modal only after confirmed success. Failure preserves fields/target and shows a focused inline message.
- Ambiguous trade outcome says it could not be confirmed and offers “Refresh positions,” never “Retry order.”
- Confirmed mutation plus failed list refresh shows success and a stale-list warning.
- Signal batch execution counts only confirmed successes. Retain failed/unknown signals selected and show each symbol’s reason. Clear operations preserve failed items/selections.
- Data-pull cancellation stops polling only after confirmation. Failed polling preserves last progress, marks it stale, deduplicates warnings, and offers “Retry now.”
- Watchlist add/remove/toggle preserves form or row state on failure. Replace native `alert()` calls.
- Orchestrator start/cancel never invents run state; ambiguous outcomes direct users to refresh current status.

Add/extend colocated tests for `PositionsView`, `SignalsView`, `DataIngestionView`, `WatchlistView`, `OrchestratorView`, and `api/job.test.ts`. Assert form/modal retention, no false success, partial batch reasons, retained failed selections, and unknown-outcome recovery.

### 4. Separate runtime exceptions from expected request failures

Files:

- Create/test `dashboard/src/components/RuntimeErrorBoundary.vue`.
- Create/test `dashboard/src/stores/runtimeErrors.ts`.
- Modify `dashboard/src/App.vue`, `main.ts`, and `router/index.ts`.
- Retire request-state use of `components/ErrorBoundary.vue`.

Implementation:

- `RuntimeErrorBoundary` uses Vue `onErrorCaptured`, replaces failing route content, and never re-renders the failing child while fallback is active.
- Use scoped `router-view`; key/reset the boundary on route change. Keep sidebar, header, backend banner, and notification host operational.
- Report the captured error once, then return `false` to prevent duplicate global handling.
- Register `app.config.errorHandler` for uncaught Vue errors and `router.onError` for navigation/lazy-chunk failures.
- Safe fallback copy: “This page couldn’t be displayed,” with “Try this page again” and “Reload dashboard.” Raw exception text stays out of primary UI.
- Expected API failures remain explicit local state; they must not depend on runtime exception capture.

### 5. Correct backend-health behavior

Modify/test `dashboard/src/stores/appState.ts`, `components/BackendDownBanner.vue`, `App.vue`, and status rendering in `components/Sidebar.vue`.

- Initial state is `checking`, not healthy.
- Track typed health error, checking flag, last attempt/success, and banner dismissal separately from connectivity.
- Network/timeout means unavailable; reachable server with unhealthy response means degraded.
- Export an immediate deduplicated health check. Banner Retry calls it even when polling is active.
- Dismiss hides only the current banner occurrence; it never marks the backend healthy.
- Use accurate Checking/Healthy/Degraded/Unavailable wording, `role="alert"`, busy Retry state, and labeled dismiss button.

### 6. Give every remaining view explicit section states

Migrate the remaining `dashboard/src/api/**` adapters, then update representative view groups:

- Data-heavy: `DashboardView.vue`, `PortfolioView.vue`, `MonitoringView.vue`, `OrchestratorView.vue`.
- Settings/status: `stores/settings.ts`, `SettingsView.vue`, `Header.vue`.
- Symbol/content: `NewsView.vue`, `SentimentView.vue`, `BacktestView.vue`.

Pattern:

- Use independent request states/`Promise.allSettled` for independent sections; one endpoint failure must not block successful cards.
- Replace permanent spinners, zero placeholders, stale run stages, and false empty states with section-level errors and Retry.
- Commit multi-part snapshots atomically where values must stay consistent.
- Label retained data stale with last-success time.
- For a new symbol/report/run request, do not relabel old data as the new selection; commit selection-specific data only after success.
- Settings health/Fyers/Pi failures show “Status unavailable,” not stopped/disconnected. Failed sections identify unconfirmed defaults and cannot be silently saved over stored values.
- Backtest report-list failures differ from “No saved reports.” Header auxiliary failures remain compact but visible.
- Replace remaining native `alert()` and raw technical strings with shared inline/notification presentation.

Add focused tests for each migrated error state, especially Monitoring partial failures, Orchestrator stale polling, Settings unavailable status, News/Sentiment latest-request wins, and Backtest report failures.

### 7. Remove transitional paths and enforce the contract

- Remove `rawFetchResult`, `RawFetchResult`, `errResponse`, `unwrap`, public result-style `ApiResponse<T>`, and all `res.success` call-site branches after migration.
- Keep a private wire-envelope type only.
- Delete the old prop-controlled `components/ErrorBoundary.vue` once all request UIs use `ErrorMessage`.
- Add ESLint restrictions against native `alert()` and direct `fetch()` outside shared transport/stream infrastructure.
- Confirm all refresh wrapper functions return their promise so `await refresh()` is real.

### 8. Add deterministic failure E2E coverage

Create:

- `dashboard/tests/e2e/fixtures/api.ts`
- `dashboard/tests/e2e/errors/error-management.spec.ts`

Use Playwright route interception for direct payloads, envelopes, response sequences, delays, aborts, HTML errors, and SSE streams. Cover:

1. Initial GET failure → clear alert + Retry, not empty state.
2. Failed refresh → retained stale values + last-updated context.
3. HTTP 200 `success:false` position mutation → modal remains open.
4. Interrupted mutation → unknown-outcome message + status refresh, no blind repeat.
5. Mixed signal execution → exact counts/reasons + failed selection retained.
6. HTML 500 and SSE failures → sanitized understandable copy.
7. Backend unavailable → accurate banner + immediate recovery.
8. Lazy-route/runtime failure → safe fallback.
9. Alert/status roles, dismiss labels, keyboard focus, and busy states.

## Style guide

- Reuse existing `card-panel`, semantic color tokens, compact typography, and `LoadingSpinner`.
- Use action-first copy: “Couldn’t load positions,” “Cancellation was not confirmed,” “Saved reports couldn’t be loaded.”
- Never show success before confirmation; never replace a failure with zeros or an empty-state message.
- Keep mutation forms open on failure. Unknown mutation outcomes require verification before another mutation.
- Put only safe status/code/correlation metadata under Details; never primary raw payloads or stack traces.
- Blocking/form errors may receive focus after user action. Refresh warnings and toasts do not steal focus.
- Follow Vue docs: captured fallback must not render the failing original content; returning `false` stops duplicate propagation to `app.config.errorHandler`.

## Verification

During implementation, use test-first changes for each stage and fetch current Vue/Pinia/Vue Router/Playwright documentation before editing framework-dependent files.

Run from `dashboard/` after each stage:

```bash
yarn test:run
yarn format:check
yarn typecheck
yarn lint
yarn build
yarn playwright test tests/e2e/errors/error-management.spec.ts
yarn playwright test
```

Static checks:

- No native `alert(`.
- No deprecated result helpers or unchecked `success` branches.
- No direct component/view `fetch()`.
- Unit coverage includes error classification, envelope failure, retry policy, cancellation, race ordering, stale data, correlation IDs, and mutation ambiguity.
- Browser console has no uncaught request failures or duplicate polling notifications.

Required UI verification per project rules:

- Run relevant Playwright E2E tests.
- Use deterministic behavioral assertions for blocking load, stale refresh, unknown mutation outcome, partial batch failure, backend banner, runtime fallback, accessible roles, focus, and busy states.
- Do not use screenshot/image-driven assertions for this plan; route interception and DOM/accessibility assertions are the supported E2E approach.

## Critical files

- `dashboard/src/api/shared.ts`
- `dashboard/src/errors/appError.ts`
- `dashboard/src/composables/useAsyncData.ts`
- `dashboard/src/components/ErrorMessage.vue`
- `dashboard/src/components/RuntimeErrorBoundary.vue`
- `dashboard/src/stores/notifications.ts`
- `dashboard/src/stores/appState.ts`
- `dashboard/src/App.vue`
- `dashboard/src/views/PositionsView.vue`
- `dashboard/src/views/SignalsView.vue`
- `dashboard/src/views/DataIngestionView.vue`
