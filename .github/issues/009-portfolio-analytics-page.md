# feat(dashboard): add portfolio analytics page

**Labels:** `enhancement` `tier-2-frontend` `dashboard` `analytics`
**Estimated effort:** 3 days

## Problem

The `PortfolioView.vue` has a placeholder "Equity Curve Chart" div with no actual charting. There is no analytics page showing performance metrics, win rate charts, or monthly returns.

## Proposed Solution

Create a full analytics page with equity curve chart, win rate donut chart, P&L distribution, and monthly returns heatmap.

## Routes

Add to `src/router/index.ts`:
```typescript
{
  path: '/analytics',
  name: 'Analytics',
  component: () => import('../views/AnalyticsView.vue'),
}
```

Add to `src/components/Sidebar.vue`:
- New sidebar link: "Analytics" with icon (chart/bar)

## Pages & Components

### AnalyticsView.vue
Main page with:
- Summary metrics cards (top row)
- Equity curve chart (large, full width)
- Grid of smaller charts below:
  - Win rate donut
  - P&L distribution histogram
  - Monthly returns heatmap
  - Sector allocation pie

```vue
<template>
  <div class="p-4">
    <div class="flex justify-between items-center mb-6">
      <h1 class="text-2xl font-bold">Analytics</h1>
      <TimeRangeSelector @select="onRangeChange" />
    </div>

    <!-- Summary Cards -->
    <div class="grid grid-cols-2 md:grid-cols-4 gap-4 mb-6">
      <MetricCard title="Total P&L" :value="formatCurrency(metrics.totalPnl)" />
      <MetricCard title="Win Rate" :value="`${metrics.winRate}%`" />
      <MetricCard title="Sharpe Ratio" :value="metrics.sharpeRatio?.toFixed(2)" />
      <MetricCard title="Max Drawdown" :value="`${metrics.maxDrawdown}%`" />
    </div>

    <!-- Equity Curve -->
    <div class="rounded-xl border p-4 mb-6">
      <h3 class="font-semibold mb-4">Equity Curve</h3>
      <EquityCurveChart :data="equityCurveData" />
    </div>

    <!-- Chart Grid -->
    <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
      <WinRateDonut :data="winRateData" />
      <PnlDistribution :data="pnlData" />
      <MonthlyHeatmap :data="monthlyData" />
      <SectorAllocation :data="sectorData" />
    </div>
  </div>
</template>
```

### EquityCurveChart.vue
Line chart showing cumulative portfolio value over time.
- Uses lightweight-charts (from issue #007)
- Green area fill above baseline, red below
- X-axis: dates, Y-axis: portfolio value

### WinRateDonut.vue
Donut chart showing win/loss ratio.
- Green slice: wins, Red slice: losses
- Center text: win rate percentage
- Library: chart.js or custom SVG

### PnlDistribution.vue
Histogram of trade P&L amounts.
- Green bars: winning trades
- Red bars: losing trades
- X-axis: P&L amount, Y-axis: count

### MonthlyHeatmap.vue
Grid of months colored by return performance.
- Color scale: green (profit) to red (loss)
- Intensity proportional to magnitude
- Cells: month label + P&L value

### SectorAllocation.vue
Pie/donut chart of portfolio by sector.
- One slice per sector
- Percentage labels
- Legend on side

## Dependencies

```bash
npm install chart.js @chartjs/plugin-doughnut-tooltip
npm install chartjs-adapter-date-fns
```

## API Calls

```typescript
export const getPortfolioMetrics = (range?: string): Promise<ApiResponse<PortfolioMetricsResponse>> =>
  request(`/portfolio/metrics${range ? `?range=${range}` : ''}`)

export const getEquityCurve = (): Promise<ApiResponse<EquityPoint[]>> =>
  request('/portfolio/equity-curve')
```

## Styling

- Summary cards: same style as DashboardView MetricCard
- Equity curve: full width, `h-80`
- Chart grid: 2 columns on desktop, 1 on mobile
- Heatmap: green-red color scale `#ef4444` to `#22c55e`
- Dark mode: adjust all colors

## Acceptance Criteria

- [ ] `/analytics` route added to router
- [ ] Sidebar has Analytics navigation link
- [ ] Equity curve chart renders from API data
- [ ] Win rate donut chart displays correctly
- [ ] P&L distribution histogram shows trade data
- [ ] Monthly heatmap renders with color scale
- [ ] Sector allocation pie chart works
- [ ] Time range selector filters all charts
- [ ] Loading and error states handled
- [ ] Responsive layout
- [ ] Dark mode support

## Notes

- chart.js is easier for donut/pie charts than lightweight-charts
- Consider using the same chart library throughout for consistency
- The heatmap color scale should be diverging (red-white-green)
- Equity curve data may need a new backend endpoint if not covered by issue #002
