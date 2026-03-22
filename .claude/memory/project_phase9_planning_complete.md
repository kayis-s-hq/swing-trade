---
name: Phase 9 Planning Complete
description: Phase 9 planning completed with 3 plans for Telegram to Signal migration
type: project
---

## Phase 9: Replace Telegram with Signal - Planning Complete

**Date:** 2026-03-22
**Status:** 3 plans created, verification passed

### Plans Created

| Plan | Objective | Wave | Tasks | Files |
|------|-----------|------|-------|-------|
| 09-01 | Signal service expansion | 1 | 5 | SignalNotificationService.java, SignalMessageFormatter.java, SignalConfig.java, application.properties |
| 09-02 | Telegram deprecation | 2 | 5 | TelegramNotificationService.java, TelegramMessageFormatter.java, TelegramConfig.java, BrokerNotificationIntegration.java, application.properties |
| 09-03 | Signal test suite | 3 | 5 | 4 test classes (80+ tests total) |

### Key Features

**Plan 01 - Signal Service Expansion:**
- Expand SignalNotificationService with 17+ notification methods (signals, positions, trades, commands, summaries, warnings)
- Expand SignalMessageFormatter with 15+ formatting methods
- Create SignalConfig.java with signal.* configuration properties
- Update application.properties with signal.* configuration
- Wire BrokerNotificationIntegration to SignalService

**Plan 02 - Telegram Deprecation:**
- Mark TelegramNotificationService as @Deprecated
- Mark TelegramMessageFormatter as @Deprecated
- Mark TelegramConfig as @Deprecated
- Wire BrokerNotificationIntegration to use Signal by default
- Update application.properties to enable Signal (signal.enabled=true), disable Telegram (telegram.enabled=false)

**Plan 03 - Signal Test Suite:**
- SignalMessageFormatterTest: 20+ tests
- SignalNotificationServiceTest: 25+ tests
- SignalConfigTest: 13+ tests
- BrokerNotificationIntegrationTest: 21+ tests
- Total: 80+ tests

### Verification Status

All 8 dimensions passed:
- Requirement Coverage ✅
- Task Completeness ✅
- Dependency Correctness ✅
- Key Links Planned ✅
- Scope Sanity ✅
- Verification Derivation ✅
- Context Compliance ✅
- Nyquist Compliance (Skipped - no RESEARCH.md)

### Next Action

Execute: `/gsd:execute-phase 09`

### User Setup Required

- `SIGNAL_API_URL`: Signal/Signl4 webhook URL from dashboard
- `SIGNAL_API_TOKEN`: API token from Signal/Signl4 settings
- `SIGNAL_CHAT_ID`: Your Signal chat ID
