# feat(dashboard): add signal detail modal

**Labels:** `enhancement` `tier-2-frontend` `dashboard`
**Estimated effort:** 1-2 days

## Problem

The `SignalsView.vue` shows signal cards but clicking on them does nothing. Users cannot see the full analysis behind a signal (technical indicators, sentiment score, combined reasoning).

## Proposed Solution

Add a modal that opens when a signal card is clicked, showing complete signal analysis details.

## Interaction

```
SignalsView.vue
  └── SignalCard.vue (clickable)
      └── SignalDetailModal.vue (dialog)
          ├── Technical Analysis section
          ├── Sentiment Analysis section
          ├── Combined Signal section
          └── Action buttons (View Chart, Add to Watchlist)
```

## Components

### SignalDetailModal.vue

```vue
<template>
  <Teleport to="body">
    <div v-if="isOpen" class="modal-overlay" @click.self="close">
      <div class="modal-content max-h-[90vh] overflow-y-auto">
        <!-- Header -->
        <div class="flex justify-between items-start mb-6">
          <div>
            <h2 class="text-xl font-bold">{{ signal.symbol }}</h2>
            <p class="text-sm text-gray-500">{{ signal.generatedAt }}</p>
          </div>
          <button @click="close" class="text-gray-400 hover:text-gray-600">
            <svg>...X icon...</svg>
          </button>
        </div>

        <!-- Signal Badge -->
        <div class="mb-6">
          <span class="signal-badge" :class="signalTypeClass">
            {{ signal.signalType }}
          </span>
          <span class="ml-2 text-sm">Confidence: {{ signal.confidence }}%</span>
        </div>

        <!-- Technical Analysis -->
        <div v-if="technicalAnalysis" class="mb-6">
          <h3 class="font-semibold mb-3">Technical Analysis</h3>
          <div class="grid grid-cols-2 gap-3">
            <div v-for="(value, key) in technicalAnalysis.indicators" :key="key"
              class="rounded-lg bg-gray-50 dark:bg-gray-800 p-3">
              <div class="text-xs text-gray-500">{{ key }}</div>
              <div class="font-medium">{{ value }}</div>
            </div>
          </div>
        </div>

        <!-- Sentiment Analysis -->
        <div v-if="sentimentAnalysis" class="mb-6">
          <h3 class="font-semibold mb-3">Sentiment Analysis</h3>
          <div class="flex items-center gap-3 mb-2">
            <span class="sentiment-badge" :class="sentimentClass">
              {{ sentimentAnalysis.score }}
            </span>
            <span class="text-sm text-gray-500">Score: {{ sentimentAnalysis.scoreValue }}</span>
          </div>
          <p class="text-sm">{{ sentimentAnalysis.summary }}</p>
        </div>

        <!-- Combined Signal -->
        <div v-if="combinedSignal" class="mb-6">
          <h3 class="font-semibold mb-3">Combined Signal</h3>
          <p class="text-sm">{{ combinedSignal.reasoning }}</p>
        </div>

        <!-- Reasoning -->
        <div v-if="signal.reasoning" class="mb-6">
          <h3 class="font-semibold mb-3">Reasoning</h3>
          <p class="text-sm text-gray-700 dark:text-gray-300">{{ signal.reasoning }}</p>
        </div>

        <!-- Action Buttons -->
        <div class="flex gap-3">
          <button @click="viewChart" class="btn-secondary">
            View Chart
          </button>
          <button @click="addToWatchlist" class="btn-primary">
            Add to Watchlist
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>
```

## Signal Data Structure

The modal receives a signal prop with all available data:

```typescript
export interface SignalDetail {
  id: string
  symbol: string
  signalType: 'BUY' | 'SELL' | 'HOLD'
  confidence: number
  reasoning: string
  generatedAt: string
  indicators?: Record<string, string>  // e.g., { RSI: '32.5 (Oversold)' }
  technicalAnalysis?: {
    indicators: Record<string, string>
    date: string
  }
  sentimentAnalysis?: {
    score: 'POSITIVE' | 'NEGATIVE' | 'NEUTRAL'
    scoreValue: number
    summary: string
    analyzedAt: string
  }
  combinedSignal?: {
    finalSignal: 'BUY' | 'SELL' | 'HOLD'
    reasoning: string
    date: string
  }
}
```

## Integration with SignalsView

```vue
<!-- In SignalsView.vue -->
<SignalCard
  v-for="signal in signals"
  :key="signal.id"
  :signal="signal"
  @click="openDetail(signal)"
/>

<SignalDetailModal
  v-if="selectedSignal"
  :signal="selectedSignal"
  @close="selectedSignal = null"
/>
```

## Styling

- Modal overlay: `bg-black/50 backdrop-blur-sm`
- Modal content: `bg-white dark:bg-gray-900 rounded-2xl max-w-2xl w-full max-h-[90vh]`
- Signal badges: same colors as existing signal cards
  - BUY: green (`bg-success-50 text-success-600`)
  - SELL: red (`bg-error-50 text-error-600`)
  - HOLD: yellow (`bg-warning-50 text-warning-600`)
- Sentiment badges:
  - POSITIVE: green
  - NEGATIVE: red
  - NEUTRAL: gray
- Indicators grid: 2 columns, light gray background cards

## Acceptance Criteria

- [ ] Clicking a signal card opens the detail modal
- [ ] Modal shows all available signal data sections
- [ ] Technical indicators displayed in grid layout
- [ ] Sentiment score shown with color badge
- [ ] Combined signal reasoning displayed
- [ ] "View Chart" navigates to stock chart (or placeholder)
- [ ] "Add to Watchlist" calls watchlist API
- [ ] Clicking overlay or X button closes modal
- [ ] ESC key closes modal
- [ ] Modal is accessible (focus trap, aria attributes)
- [ ] Dark mode support

## Notes

- The modal should use `<Teleport to="body">` to avoid z-index issues
- Consider using a composable `useModal()` for open/close state management
- If backend data is missing for a section (e.g., no sentiment), hide that section entirely
- Reuse `SignalCard.vue` styling for consistency
