# feat(dashboard): add stock chart component

**Labels:** `enhancement` `tier-2-frontend` `dashboard` `charting`
**Estimated effort:** 2-3 days

## Problem

The dashboard has no charting capability. Users cannot view OHLC candlestick charts or technical indicator overlays for stocks. The `PortfolioView.vue` has a placeholder for "Equity Curve" but no actual charting implementation.

## Proposed Solution

Add a candlestick chart component using TradingView's lightweight-charts library with EMA overlays, volume bars, and time range selector.

## Components to Create

### CandlestickChart.vue
Main chart component displaying OHLC data.

```vue
<template>
  <div ref="chartContainer" class="w-full h-96 rounded-lg border border-gray-200 dark:border-gray-700"></div>
</template>

<script setup lang="ts">
interface Props {
  data: OhlcvCandle[]
  showEma?: boolean
  showVolume?: boolean
  symbol?: string
}
```

Features:
- Candlestick series (green/red coloring)
- EMA 12 (blue line) overlay
- EMA 26 (red line) overlay
- Volume histogram at bottom (transparent bars)
- Crosshair cursor with price/time tooltip
- Responsive sizing

### TimeRangeSelector.vue
Small component for switching between 7d / 30d / 90d / 1y / all.

```vue
<template>
  <div class="flex gap-1">
    <button v-for="range in ranges" :key="range" @click="emit('select', range)"
      :class="{'bg-indigo-100 dark:bg-indigo-900': range === selected}">
      {{ range }}
    </button>
  </div>
</template>
```

### useChart.ts (composable)
Manages lightweight-charts instance lifecycle.

```typescript
export function useChart(container: Ref<HTMLDivElement | null>) {
  const chart = ref<LightweightCharts.Chart | null>(null)
  const candleSeries = ref<LightweightCharts.ICandlestickSeries | null>(null)
  const emaFastSeries = ref<LightweightCharts.ISeriesSeries<"line"> | null>(null)
  const emaSlowSeries = ref<LightweightCharts.ISeriesSeries<"line"> | null>(null)

  function updateData(candles: OhlcvCandle[]): void
  function applyRange(range: string): void
  function destroy(): void
}
```

## Files to Create

- `src/components/CandlestickChart.vue` - Main chart component
- `src/components/TimeRangeSelector.vue` - Time range buttons
- `src/composables/useChart.ts` - Chart lifecycle management
- `src/api/types.ts` - Add `OhlcvCandle` interface

## Dependencies

```bash
npm install lightweight-charts
npm install -D @types/lightweight-charts  # if needed
```

## Data Flow

```
DashboardView/StockDetailView
  └── GET /api/stocks/{symbol}/chart?period=30d
      └── CandlestickChart
          ├── candleSeries (OHLC)
          ├── emaFastSeries (EMA 12)
          ├── emaSlowSeries (EMA 26)
          └── volumeSeries (histogram)
```

## Styling

- Dark mode support via CSS variables
- Chart colors:
  - Bullish candle: `#26a69a` (green)
  - Bearish candle: `#ef5350` (red)
  - EMA fast: `#2196F3` (blue)
  - EMA slow: `#FF9800` (orange)
  - Grid lines: `rgba(0,0,0,0.05)` / `rgba(255,255,255,0.05)` dark
- Height: `h-96` (384px) default, responsive
- Font: system font stack

## Acceptance Criteria

- [ ] `lightweight-charts` installed as dependency
- [ ] `CandlestickChart.vue` renders OHLC candles correctly
- [ ] EMA 12 and EMA 26 overlays display on chart
- [ ] Volume histogram shows at bottom (toggleable)
- [ ] `TimeRangeSelector.vue` switches data range
- [ ] Chart is responsive (resizes on window resize)
- [ ] Dark mode styling works
- [ ] Crosshair shows price and date tooltip
- [ ] Used in DashboardView for top-positioned stocks
- [ ] Used in new StockDetailView (future issue)
- [ ] No console warnings or errors

## Notes

- lightweight-charts v5+ is the current version
- Chart data must be sorted ascending by time
- Handle empty data gracefully (show "No data" message)
- Consider adding a resize observer for responsive behavior
