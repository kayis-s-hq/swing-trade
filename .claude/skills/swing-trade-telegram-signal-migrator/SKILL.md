---
name: swing-trade-telegram-signal-migrator
description: Migrate from Telegram to Signal/Signl4 webhook integration for all notifications in the swing-trade system. Use this skill when setting up new notification channels, migrating from Telegram due to reliability issues, configuring alternative alerting systems, or when updating notification infrastructure. This skill is critical for maintaining notification reliability and should be triggered proactively when Telegram integration fails or when implementing more robust alerting.
---

# SwingTrade Telegram to Signal Migration Skill

## Overview

This skill automates the migration from Telegram bot notifications to Signal/Signl4 webhook integration for all trading notifications in the swing-trade system.

## When to Use This Skill

Trigger this skill when:
- Setting up new notification channels
- Migrating from Telegram due to reliability issues
- Configuring alternative alerting systems
- Updating notification infrastructure
- Before implementing new notification features
- When Telegram API changes or rate limits

## Migration Components

### 1. Notification Types

```java
public class NotificationTypes {
    // Trade events
    public static final String TRADE_OPEN = "trade_open";
    public static final String TRADE_CLOSE = "trade_close";
    public static final String TRADE_STOP_LOSS = "trade_stop_loss";
    public static final String TRADE_TARGET_HIT = "trade_target_hit";

    // System events
    public static final String SIGNAL_GENERATED = "signal_generated";
    public static final String DATA_INGESTION_FAILURE = "data_ingestion_failure";
    public static final String RISK_ALERT = "risk_alert";
    public static final String PERFORMANCE_REPORT = "performance_report";
}
```

### 2. Telegram Implementation (Legacy)

```java
@Component
public class TelegramNotificationService {
    private final TelegramBot bot;

    public void sendTradeOpenNotification(Trade trade) {
        String message = String.format(
            "Trade Open: %s\n" +
            "Entry: %.2f\n" +
            "Quantity: %d\n" +
            "Stop Loss: %.2f\n" +
            "Target: %.2f",
            trade.getSymbol(),
            trade.getEntryPrice(),
            trade.getQuantity(),
            trade.getStopLoss(),
            trade.getTarget()
        );

        bot.execute(new SendMessage(CHANNEL_ID, message));
    }
}
```

### 3. Signal Implementation (Target)

```java
@Component
public class SignalNotificationService {
    private final WebClient webClient;
    private final String webhookUrl;

    public SignalNotificationService(@Value("${signal.webhook.url}") String webhookUrl) {
        this.webhookUrl = webhookUrl;
        this.webClient = WebClient.create();
    }

    public Mono<Void> sendTradeOpenNotification(Trade trade) {
        SignalMessage message = SignalMessage.builder()
            .title("Trade Open")
            .body(String.format(
                "**Trade Open**: %s\n" +
                "Entry: %.2f\n" +
                "Quantity: %d\n" +
                "Stop Loss: %.2f\n" +
                "Target: %.2f",
                trade.getSymbol(),
                trade.getEntryPrice(),
                trade.getQuantity(),
                trade.getStopLoss(),
                trade.getTarget()
            ))
            .priority("high")
            .tags(List.of("trade", "open"))
            .build();

        return webClient.post()
            .uri(webhookUrl)
            .bodyValue(message)
            .retrieve()
            .toBodilessEntity()
            .then();
    }
}
```

## Migration Workflow

```
1. Assess current Telegram setup
   ↓
2. Configure Signal/Signl4 webhook
   ↓
3. Update notification service implementation
   ↓
4. Test notification delivery
   ↓
5. Update configuration files
   ↓
6. Deploy changes
   ↓
7. Monitor notification delivery
   ↓
8. Archive Telegram integration
```

### Migration Steps

```bash
# Step 1: Export current Telegram settings
/skill: swing-trade-telegram-signal-migrator --export-telegram

# Step 2: Configure Signal webhook
/skill: swing-trade-telegram-signal-migrator --configure-signal --webhook-url https://your-signl4-webhook.com/notify

# Step 3: Update notification service
/skill: swing-trade-telegram-signal-migrator --migrate-service

# Step 4: Test notifications
/skill: swing-trade-telegram-signal-migrator --test

# Step 5: Deploy to production
/skill: swing-trade-telegram-signal-migrator --deploy

# Step 6: Monitor delivery
/skill: swing-trade-telegram-signal-migrator --monitor
```

## Configuration Migration

### Telegram Configuration (Legacy)

```yaml
# application-telegram.yml
telegram:
  bot-token: ${TELEGRAM_BOT_TOKEN}
  channel-id: ${TELEGRAM_CHANNEL_ID}
  enabled: true
```

### Signal Configuration (Target)

```yaml
# application-signal.yml
signal:
  webhook:
    url: ${SIGNAL_WEBHOOK_URL}
    timeout: 30s
    retry:
      max-attempts: 3
      delay: 5s
  enabled: true
  notification:
    trade-events: true
    system-events: true
    performance-reports: true
```

## Notification Message Templates

### Trade Open

```
**Trade Open**
Symbol: RELIANCE
Direction: LONG
Entry: 2500.00
Quantity: 100
Stop Loss: 2400.00
Target: 2750.00
Risk/Reward: 1:2.5
Capital Deployed: ₹250,000
Potential Risk: ₹10,000
Potential Profit: ₹25,000
```

### Trade Close

```
**Trade Closed**
Symbol: RELIANCE
Exit: 2750.00
P&L: +₹25,000 (+10%)
Exit Reason: Target Hit
Holding Period: 5 days
```

### Stop Loss Hit

```
**Stop Loss Hit**
Symbol: TATASTEEL
Exit: 1164.00
P&L: -₹3,600 (-3%)
Exit Reason: Stop Loss
Holding Period: 2 days
```

## Migration Report Format

```
# Telegram to Signal Migration Report - [Timestamp]

## Migration Summary
- Start Time: [timestamp]
- End Time: [timestamp]
- Status: [SUCCESS/PARTIAL/FAILED]

## Telegram Configuration Exported
- Bot Token: [REDACTED]
- Channel ID: [REDACTED]
- Notifications Configured: [X]

## Signal Configuration
- Webhook URL: [REDACTED]
- Timeout: [X]s
- Retry Policy: [X] attempts
- Notification Types: [list]

## Migration Results
| Component | Status | Details |
|-----------|--------|---------|
| Notification Service | MIGRATED | Updated to Signal |
| Configuration | UPDATED | Signal config added |
| Tests | PASSED | All tests passing |
| Deployment | SUCCESS | Deployed to prod |

## Notification Testing
| Type | Test Result | Delivery Time |
|------|-------------|---------------|
| Trade Open | SUCCESS | 1.2s |
| Trade Close | SUCCESS | 0.8s |
| Stop Loss | SUCCESS | 1.0s |
| Signal Generated | SUCCESS | 0.9s |

## Recommendations
1. [Archive Telegram bot]
2. [Update documentation]
3. [Monitor delivery for 1 week]
4. [Set up notification analytics]
```

## Example Usage

```bash
# Full migration
/skill: swing-trade-telegram-signal-migrator

# Migration to specific webhook
/skill: swing-trade-telegram-signal-migrator --webhook-url https://your-webhook.com

# Test migration
/skill: swing-trade-telegram-signal-migrator --test

# Generate migration report
/skill: swing-trade-telegram-signal-migrator --report

# Rollback to Telegram
/skill: swing-trade-telegram-signal-migrator --rollback

# Export current Telegram config
/skill: swing-trade-telegram-signal-migrator --export
```

## Dependencies

- Signal/Signl4 webhook endpoint
- WebClient for HTTP requests
- Configuration management
- Notification testing framework

## Performance Considerations

- Webhook requests should complete within 5 seconds
- Implement retry logic for failures
- Use async notification sending
- Monitor delivery success rates
