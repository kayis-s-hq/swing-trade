# feat(api): add system settings/configuration endpoints

**Labels:** `enhancement` `tier-1-backend` `api`
**Estimated effort:** 2-3 days

## Problem

System configuration (risk limits, broker mode, strategy parameters) is hardcoded in `application.properties` or Java constants. There is no runtime way to adjust trading parameters without code changes and restarts.

## Proposed Solution

Create a settings management system with database-backed storage and REST endpoints for runtime configuration.

## API Endpoints

```
GET    /api/settings/risk              - Get risk settings
PUT    /api/settings/risk              - Update risk settings
GET    /api/settings/broker            - Get broker settings
PUT    /api/settings/broker            - Update broker settings
GET    /api/settings/strategy          - Get strategy settings
PUT    /api/settings/strategy          - Update strategy settings
GET    /api/settings                   - Get all settings
PUT    /api/settings                   - Update all settings (atomic)
```

## Database Schema

```sql
CREATE TABLE system_settings (
    id SERIAL PRIMARY KEY,
    category VARCHAR(50) NOT NULL,  -- risk, broker, strategy
    key VARCHAR(100) NOT NULL,
    value TEXT NOT NULL,
    description TEXT,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(50) DEFAULT 'system',
    UNIQUE(category, key)
);

CREATE INDEX idx_system_settings_category ON system_settings(category);
```

## Settings Categories

### Risk Settings
| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `max_positions` | int | 10 | Maximum concurrent open positions |
| `position_size_pct` | decimal | 10.0 | Max portfolio value per position (%) |
| `daily_loss_limit` | decimal | 5.0 | Max daily loss as % of portfolio |
| `max_position_pct` | decimal | 25.0 | Max single position as % of portfolio |
| `circuit_breaker_trades` | int | 3 | Consecutive losses before circuit breaker |

### Broker Settings
| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `broker_mode` | string | PAPER | paper, live, dry_run |
| `upstox_api_key` | string | - | Upstox API key (encrypted) |
| `upstox_api_secret` | string | - | Upstox API secret (encrypted) |

### Strategy Settings
| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `ema_fast_period` | int | 12 | Fast EMA period |
| `ema_slow_period` | int | 26 | Slow EMA period |
| `rsi_period` | int | 14 | RSI period |
| `rsi_oversold` | decimal | 30 | RSI oversold threshold |
| `rsi_overbought` | decimal | 70 | RSI overbought threshold |
| `atr_multiplier_sl` | decimal | 2.0 | ATR multiplier for stop loss |
| `volume_multiplier` | decimal | 1.5 | Volume average multiplier |

## Files to Create/Modify

### New files
- `data/src/main/resources/db/migration/V7__create_system_settings_table.sql`
- `data/src/main/java/com/swingtrade/data/entity/SystemSettingsEntity.java`
- `data/src/main/java/com/swingtrade/data/repository/SystemSettingsRepository.java`
- `api/src/main/java/com/swingtrade/api/dto/RiskSettingsRequest.java`
- `api/src/main/java/com/swingtrade/api/dto/BrokerSettingsRequest.java`
- `api/src/main/java/com/swingtrade/api/dto/StrategySettingsRequest.java`
- `api/src/main/java/com/swingtrade/api/service/SettingsService.java`
- `api/src/main/java/com/swingtrade/api/controller/SettingsController.java`

### Modified files
- `broker/src/main/java/com/swingtrade/broker/config/BrokerMode.java` - Load from settings
- `strategy/src/main/java/com/swingtrade/strategy/impl/DefaultStrategy.java` - Load params from settings
- `broker/src/main/java/com/swingtrade/broker/risk/RiskControlsService.java` - Load limits from settings

## Security

- `upstox_api_key` and `upstox_api_secret` must be encrypted at rest
- Use `@JdbcTypeCode(SqlTypes.VARBINARY)` with AES encryption
- Never return encrypted values in GET responses (strip sensitive fields)
- PUT endpoints for broker settings require admin role

## Acceptance Criteria

- [ ] `system_settings` table created with default values seeded
- [ ] All 3 categories (risk, broker, strategy) have GET/PUT endpoints
- [ ] GET /api/settings returns all categories in one response
- [ ] PUT /api/settings/risk validates all fields before saving
- [ ] `max_positions` > 0, `position_size_pct` between 1-50
- [ ] Broker API keys are encrypted at rest
- [ ] Sensitive fields (API keys) are stripped from GET responses
- [ ] Changes to risk settings apply immediately (no restart)
- [ ] Strategy params update triggers strategy recreation
- [ ] Unit tests for validation and CRUD
- [ ] Integration test with database
- [ ] Code coverage >= 80%

## Notes

- Use Redis cache with 1-minute TTL for settings lookups
- Log all setting changes: `logger.info("Settings updated: category={}, key={}, oldValue={}, newValue={}")`
- Consider adding an audit trail table for settings changes
