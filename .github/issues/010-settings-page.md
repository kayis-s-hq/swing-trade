# feat(dashboard): add settings page

**Labels:** `enhancement` `tier-2-frontend` `dashboard`
**Estimated effort:** 2 days

## Problem

There is no UI for adjusting system settings. Users must edit `application.properties` or Java constants to change risk limits, broker mode, or strategy parameters.

## Proposed Solution

Create a settings page with forms for each settings category, backed by the settings API endpoints (issue #005).

## Routes

Add to `src/router/index.ts`:
```typescript
{
  path: '/settings',
  name: 'Settings',
  component: () => import('../views/SettingsView.vue'),
}
```

Add to `src/components/Sidebar.vue`:
- New sidebar link: "Settings" with icon (gear)

## Pages & Components

### SettingsView.vue
Main page with tabbed interface:
```vue
<template>
  <div class="p-4">
    <h1 class="text-2xl font-bold mb-6">Settings</h1>

    <div class="flex gap-2 mb-6 border-b">
      <button v-for="tab in tabs" :key="tab.id" @click="activeTab = tab.id"
        :class="{'border-indigo-500 text-indigo-600': activeTab === tab.id}">
        {{ tab.label }}
      </button>
    </div>

    <div v-show="activeTab === 'risk'">
      <RiskSettingsForm @save="saveRiskSettings" />
    </div>
    <div v-show="activeTab === 'broker'">
      <BrokerSettingsForm @save="saveBrokerSettings" />
    </div>
    <div v-show="activeTab === 'strategy'">
      <StrategySettingsForm @save="saveStrategySettings" />
    </div>
  </div>
</template>
```

### RiskSettingsForm.vue
Form for risk parameters:
```
┌─────────────────────────────────────┐
│ Risk Management                     │
├─────────────────────────────────────┤
│ Max Positions          [10    ]     │
│ Position Size (%)      [10.0  ]     │
│ Daily Loss Limit (%)   [5.0   ]     │
│ Max Position (%)       [25.0  ]     │
│ Circuit Breaker Trades [3     ]     │
│                           [Save]    │
└─────────────────────────────────────┘
```

### BrokerSettingsForm.vue
Form for broker configuration:
```
┌─────────────────────────────────────┐
│ Broker Configuration                │
├─────────────────────────────────────┤
│ Broker Mode        [Paper ▼]        │
│ Upstox API Key     [••••••••]       │
│ Upstox API Secret  [••••••••]       │
│                           [Save]    │
└─────────────────────────────────────┘
```

### StrategySettingsForm.vue
Form for strategy parameters:
```
┌─────────────────────────────────────┐
│ Strategy Parameters                 │
├─────────────────────────────────────┤
│ EMA Fast Period    [12    ]         │
│ EMA Slow Period    [26    ]         │
│ RSI Period         [14    ]         │
│ RSI Oversold       [30    ]         │
│ RSI Overbought     [70    ]         │
│ ATR Stop Loss Mult [2.0   ]         │
│ Volume Multiplier  [1.5   ]         │
│                           [Save]    │
└─────────────────────────────────────┘
```

## API Calls

```typescript
export const getRiskSettings = (): Promise<ApiResponse<RiskSettings>> =>
  request('/settings/risk')

export const updateRiskSettings = (settings: RiskSettings): Promise<ApiResponse<void>> =>
  request('/settings/risk', { method: 'PUT', body: JSON.stringify(settings) })

// Similar for broker and strategy settings
```

## TypeScript Types

```typescript
export interface RiskSettings {
  maxPositions: number
  positionSizePct: number
  dailyLossLimit: number
  maxPositionPct: number
  circuitBreakerTrades: number
}

export interface BrokerSettings {
  brokerMode: 'PAPER' | 'LIVE' | 'DRY_RUN'
  upstoxApiKey?: string
  upstoxApiSecret?: string
}

export interface StrategySettings {
  emaFastPeriod: number
  emaSlowPeriod: number
  rsiPeriod: number
  rsiOversold: number
  rsiOverbought: number
  atrMultiplierSl: number
  volumeMultiplier: number
}
```

## Validation

| Field | Min | Max | Required | Error Message |
|-------|-----|-----|----------|---------------|
| maxPositions | 1 | 50 | Yes | Must be 1-50 |
| positionSizePct | 1 | 50 | Yes | Must be 1-50% |
| dailyLossLimit | 1 | 20 | Yes | Must be 1-20% |
| maxPositionPct | 5 | 50 | Yes | Must be 5-50% |
| circuitBreakerTrades | 1 | 10 | Yes | Must be 1-10 |
| rsiOversold | 10 | 40 | Yes | Must be < rsiOverbought |
| rsiOverbought | 60 | 90 | Yes | Must be > rsiOversold |

## Features

- Form validation with inline error messages
- Save confirmation toast/notification
- "Reset to defaults" button per tab
- Dirty form detection (unsaved changes warning)
- Loading state during API calls
- Auto-refresh on mount

## Acceptance Criteria

- [ ] `/settings` route added to router
- [ ] Sidebar has Settings navigation link
- [ ] Three tabs: Risk, Broker, Strategy
- [ ] Each tab has correct form fields
- [ ] Validation rules enforced with error messages
- [ ] Save button calls correct API endpoint
- [ ] Success feedback after save
- [ ] Settings load on page mount
- [ ] Reset to defaults works
- [ ] Dark mode support

## Notes

- Use the same form styling as existing Vue components in the project
- API secret fields should be masked (show `••••••`) and only reveal on focus
- Changing broker mode from PAPER to LIVE should show a confirmation dialog
- Consider adding a "Apply to live system" confirmation for strategy changes
