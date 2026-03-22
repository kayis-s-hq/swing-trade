---
name: Phase 9 Notification Migration
description: Phase 9 replaces Telegram with Signal/Signl4 webhook integration for all notifications
type: project
---

## Phase 9: Replace Telegram with Signal

**Decision:** Replace Telegram Bot API with Signal/Signl4 webhook integration for all notification features.

### Key Decisions

1. **Full feature parity** - Signal gets all Telegram features: signals (BUY/SELL/HOLD), position entries/exits, stop loss/target hits, kill switch commands, daily summaries, risk warnings, error alerts

2. **Cutover approach** - Disable Telegram, enable Signal (clean break, one config change). No parallel running period.

3. **Signal/Signl4 platform** - Use existing webhook-based integration already in `SignalNotificationService`. REST API with Bearer token authentication.

4. **Configuration migration** - Direct replacement: `telegram.bot.token` → `signal.api.token`, `TELEGRAM_CHAT_IDS` → `SIGNAL_CHAT_ID`. Deprecate all `telegram.*` config.

5. **Phase scope** - Include all: replace Telegram with Signal + add tests + remove Telegram code entirely.

### Files Involved

- `broker/src/main/java/com/swingtrade/broker/telegram/TelegramNotificationService.java` - To be replaced
- `broker/src/main/java/com/swingtrade/broker/telegram/SignalNotificationService.java` - Already exists, needs feature expansion
- `broker/src/main/java/com/swingtrade/broker/telegram/SignalMessageFormatter.java` - Message formatting
- `broker/src/main/java/com/swingtrade/broker/telegram/BrokerNotificationIntegration.java` - Integration layer

### Why

Telegram notifications are being replaced with Signal/Signl4 for better event-driven alerting. The Signal/Signl4 webhook integration already exists in the codebase and supports REST API with Bearer token authentication.

### How to Apply

When planning Phase 9:
1. Expand `SignalNotificationService` to handle all notification types (signals, commands, summaries)
2. Remove `TelegramNotificationService` and all Telegram-specific code
3. Update configuration to use Signal/Signl4 settings only
4. Add comprehensive tests for Signal integration
