# Phase 9: Replace Telegram with Signal - Context

**Gathered:** 2026-03-22
**Status:** Ready for planning

<domain>
## Phase Boundary

Replace Telegram notifications with Signal/Signl4 webhook integration for all notification features. Migrate from Telegram Bot API to Signal/Signl4 REST API, including full feature parity (signals, positions, trades, kill switch commands, daily summaries, risk warnings, error alerts).

</domain>

<decisions>
## Implementation Decisions

### Notification scope
- **Full feature parity** — Signal gets all Telegram features: signals (BUY/SELL/HOLD), position entries/exits, stop loss/target hits, kill switch commands, daily summaries, risk warnings, error alerts
- No features deferred — complete migration of all notification functionality

### Migration approach
- **Cutover** — Disable Telegram, enable Signal (clean break, one config change)
- No parallel running period — direct replacement
- Telegram disabled immediately when Signal enabled

### Signal integration platform
- **Signal/Signl4** — Use existing webhook-based integration already in `SignalNotificationService`
- REST API with Bearer token authentication
- Webhook URL configurable via `SIGNAL_API_URL` environment variable

### Configuration migration
- **Direct replacement** — `telegram.bot.token` → `signal.api.token`, `TELEGRAM_CHAT_IDS` → `SIGNAL_CHAT_ID`
- **Deprecate** — Remove `telegram.*` config entirely, keep only `signal.*` config
- No adapter layer — clean break from Telegram configuration

### Phase scope
- **Include all** — Replace Telegram with Signal + add tests + remove Telegram code
- Complete migration in single phase
- No deferred work

### Claude's Discretion
- Exact refactoring approach (extract common notification interface or direct replacement)
- Test coverage strategy (unit vs integration tests)
- Config migration helper utilities (if any)

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- **SignalNotificationService** (`broker/src/main/java/com/swingtrade/broker/telegram/SignalNotificationService.java`) — Already implements Signal/Signl4 webhook integration, REST API with RestTemplate
- **SignalMessageFormatter** (`broker/src/main/java/com/swingtrade/broker/telegram/SignalMessageFormatter.java`) — Message formatting for trade events
- **TelegramNotificationService** (`broker/src/main/java/com/swingtrade/broker/telegram/TelegramNotificationService.java`) — Reference implementation for all notification types (signals, positions, commands, summaries)
- **TelegramMessageFormatter** — Reference for message formatting patterns

### Established Patterns
- **RestTemplate** — HTTP client already used in both services
- **@Value annotations** — Configuration pattern for Signal settings
- **Command handlers** — Telegram's command pattern (`/stop`, `/resume`, `/status`, `/help`) can be adapted for Signal
- **ErrorSeverity enum** — Severity levels (LOW, MEDIUM, HIGH, CRITICAL) reusable

### Integration Points
- **BrokerNotificationIntegration** — Currently routes to SignalNotificationService for trade events
- **PaperTradingEngine** — Calls notification services on trade events
- **RiskControlsService** — Triggers error/warning notifications
- **application.properties** — Need to update config properties

</code_context>

<specifics>
## Specific Ideas

- Signal/Signl4 webhook URL pattern: `https://your-signl4-webhook-url.com/webhook` (already in code)
- Bearer token authentication: `Authorization: Bearer {apiToken}`
- Chat ID via header: `X-Chat-Id: {chatId}`
- Quiet hours support already implemented in SignalNotificationService
- Command handling pattern from Telegram can be adapted (commands via Signal messages)

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 09-replace-telegram-with-signal*
*Context gathered: 2026-03-22*
