# Fix: LLM settings 500 error (NPE on unset PDF base-url)

**Note:** Per this repo's CLAUDE.md convention, this plan will also be saved to
`docs/plans/fix-llm-settings-pdf-nullpointer.md` as the first execution step, before making any code
changes.

## Context

The dashboard's Settings page shows "Failed to load: LLM settings. Showing defaults." with 4 repeated
500s in the browser console for `GET /api/settings/llm`. This started after merging PR #96
("harden orchestration and LLM settings" work), which added PDF-extraction settings
(`llm.pdf.base_url`, `llm.pdf.model`) to `SettingsController.getLlmSettings()` without the same
null-safety the other providers already have.

**Root cause** (confirmed via live backend log `backend/logs/swing-trade-local.log` and `curl`):

```
java.lang.NullPointerException: Cannot invoke "java.net.URI.toString()" because the return value of
"com.swingtrade.llm.config.LlmProperties$Pdf.getBaseUrl()" is null
```

`llm.pdf.base-url` is intentionally optional and defaults to an **empty string** in every properties
file (`${LLM_PDF_BASE_URL:}`), unlike every other provider's `base-url`, which always has a concrete
`http://...` fallback (e.g. `llm.base-url`, `llm.providers.openai.base-url`). Spring's relaxed
`Binder` treats an empty string bound to a non-`String` type (`java.net.URI`) as "no value" rather
than `URI.create("")`, so `LlmProperties.Pdf.baseUrl` stays `null` when `LLM_PDF_BASE_URL` isn't set
— which is the case in the local dev environment. The unguarded
`llmProperties.getPdf().getBaseUrl().toString()` call at
`backend/api/src/main/java/com/swingtrade/api/controller/SettingsController.java:120-121` then NPEs,
and `GlobalExceptionHandler` turns that into a generic 500.

This is the only unguarded `.toString()` on a `URI` field in the controller — every other provider
has a real default, so this codepath was never exercised there. This also breaks
`POST /api/settings/save`, which calls `getLlmSettings()` internally.

The existing regression test (`SettingsControllerDefaultsTest.properties()`) always sets a non-null
PDF base URL in its fixture, so it never exercised the unset case — that's the coverage gap that let
this ship.

## Fix

Since `llm.pdf.base-url` is legitimately optional (PDF extraction may not be configured), the fix is
null-safety in the controller, not forcing a fake non-blank default in properties files.

**`backend/api/src/main/java/com/swingtrade/api/controller/SettingsController.java`** (`getLlmSettings()`,
around line 120-121): replace the unguarded call with a null-safe default of `""`, mirroring how the
rest of the method already treats "not configured" as an empty string:

```java
settings.put("llm.pdf.base_url", appSettingsService.get(
    "llm.pdf.base_url",
    llmProperties.getPdf().getBaseUrl() != null ? llmProperties.getPdf().getBaseUrl().toString() : ""));
```

## Test coverage

**`backend/api/src/test/java/com/swingtrade/api/controller/SettingsControllerDefaultsTest.java`**:
add a test in the `GetLlmSettings` nested class that builds an `LlmProperties` fixture with
`pdf.baseUrl` left `null` (i.e. don't call `properties.getPdf().setBaseUrl(...)`) and asserts
`getLlmSettings()` returns `"llm.pdf.base_url" -> ""` instead of throwing. This requires either a
second properties-builder helper (a `properties()` variant without the PDF base URL set) or
constructing a fresh `SettingsController` with a custom `LlmProperties` fixture inline in the test —
follow the existing pattern in the file (`properties()` + `configureProvider()` helpers).

## Verification

1. `cd backend && ./gradlew :api:test --tests "*SettingsControllerDefaultsTest*"` — new + existing
   tests pass.
2. Restart the local backend (`./dev-stack.sh stop && ./dev-stack.sh start`, or just rebuild the
   `api` module and restart the running jar) so the fix is picked up.
3. `curl -s http://localhost:8080/api/settings/llm` — should return `200` with
   `"llm.pdf.base_url":""` instead of a 500 error envelope.
4. Reload the dashboard Settings page — the "Failed to load: LLM settings" toast should no longer
   appear, and the LLM & Intelligence tab should render normally.
