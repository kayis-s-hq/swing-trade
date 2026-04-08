---
phase: 08-vue-dashboard-monitoring-ui
plan: 02
type: execute
wave: 1
depends_on:
  - "08-vue-dashboard-monitoring-ui-01"
files_modified:
  - swing-trade-dashboard/src/views/DashboardView.vue
  - swing-trade-dashboard/src/views/PositionsView.vue
  - swing-trade-dashboard/src/views/SignalsView.vue
  - swing-trade-dashboard/src/views/PortfolioView.vue
  - swing-trade-dashboard/src/components/PositionCard.vue
  - swing-trade-dashboard/src/components/SignalCard.vue
  - swing-trade-dashboard/src/components/PerformanceMetrics.vue
  - swing-trade-dashboard/src/components/SectorAllocation.vue
autonomous: true
requirements:
  - DASH-03
  - DASH-04
  - DASH-05
user_setup: []

must_haves:
  truths:
    - "Dashboard shows system health status and quick metrics"
    - "Positions view lists all open positions with P&L"
    - "Signals view displays latest trading signals"
    - "Portfolio view shows performance metrics"
  artifacts:
    - path: "swing-trade-dashboard/src/views/DashboardView.vue"
      provides: "Main dashboard with health and metrics"
      min_lines: 100
    - path: "swing-trade-dashboard/src/views/PositionsView.vue"
      provides: "Positions list with filtering"
      min_lines: 150
    - path: "swing-trade-dashboard/src/views/SignalsView.vue"
      provides: "Signals feed with generation"
      min_lines: 100
    - path: "swing-trade-dashboard/src/views/PortfolioView.vue"
      provides: "Performance metrics and charts"
      min_lines: 120
  key_links:
    - from: "swing-trade-dashboard/src/views/PositionsView.vue"
      to: "swing-trade-dashboard/src/api/client.ts"
      via: "positionService.getPositions()"
      pattern: "positionService\\.getPositions"
    - from: "swing-trade-dashboard/src/views/PortfolioView.vue"
      to: "swing-trade-dashboard/src/api/client.ts"
      via: "tradeService.getPerformance()"
      pattern: "tradeService\\.getPerformance"
---

<objective>
Build core dashboard views for monitoring trading system

Purpose: Create the main user-facing views that display positions, signals, and portfolio performance using the API client

Output:
- Dashboard view with system overview
- Positions view with filtering and search
- Signals view with signal generation
- Portfolio view with performance metrics
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/ROADMAP.md
@.planning/phases/08-vue-dashboard-monitoring-ui/08-vue-dashboard-monitoring-ui-01-PLAN.md

# PositionResponse DTO structure
{
  symbol: string,
  entryPrice: number,
  quantity: number,
  stopLoss: number,
  target: number,
  status: 'OPEN' | 'CLOSED' | 'STOPPED' | 'TARGET_HIT',
  currentPrice: number,
  unrealizedPnL: number,
  unrealizedPnLPercent: number,
  totalValue: number,
  entryDate: string,
  entryReason: string
}

# PerformanceResponse DTO structure
{
  totalReturn: number,
  sharpeRatio: number,
  maxDrawdown: number,
  totalTrades: number,
  winRate: number,
  averageWin: number,
  averageLoss: number,
  totalPnL: number
}

# SignalResponse DTO structure
{
  symbol: string,
  signalType: 'BUY' | 'SELL' | 'HOLD',
  confidence: number,
  generatedAt: string,
  reasoning: string
}
</context>

<interfaces>
<!-- Interface contracts from API client -->
<!-- These are consumed by all view components -->

positionService.getPositions(): Promise<PositionResponse[]>
positionService.getPositionStats(): Promise<PositionStats>
positionService.getSectorAllocation(): Promise<SectorAllocation>

tradeService.getPerformance(): Promise<PerformanceResponse>
tradeService.createPosition(data): Promise<PositionResponse>
tradeService.closePosition(symbol): Promise<PositionResponse>

signalService.getLatestSignals(): Promise<SignalResponse[]>
signalService.getSignalsBySymbol(symbol): Promise<SignalResponse[]>
signalService.generateSignal(symbol): Promise<SignalResponse>

healthService.getFullHealth(): Promise<HealthStatus>
</interfaces>

<tasks>

<task type="auto">
  <name>Task 1: Create DashboardView with system overview</name>
  <files>swing-trade-dashboard/src/views/DashboardView.vue</files>
  <action>
Create main dashboard view showing system health and key metrics:

1. Layout structure:
   - Grid layout with responsive columns (2x2 on desktop, stacked on mobile)
   - 4 metric cards at top: Open Positions, Total P&L, Win Rate, Active Signals
   - System health card on right
   - Latest signals feed below

2. Data fetching:
   - Use Vue Composition API with ref/reactive
   - onMounted() to fetch data from API
   - Computed properties for aggregated metrics
   - Auto-refresh every 30 seconds with refresh button

3. Metric cards:
   - Open Positions: Count from positionService.getPositions()
   - Total P&L: Sum of unrealizedPnL from positions
   - Win Rate: From performanceService.getWinRate()
   - Active Signals: Count of high-confidence signals

4. Health card:
   - Display status from healthService.getFullHealth()
   - Color-coded: green (UP), yellow (DEGRADED), red (DOWN)
   - Show component status details on hover

5. Latest signals section:
   - Call signalService.getLatestSignals()
   - Display 5 most recent signals
   - Show signal type icon (green BUY, red SELL, yellow HOLD)
  </action>
  <verify>
    <automated>cd swing-trade-dashboard && grep -c "onMounted" src/views/DashboardView.vue && grep -c "positionService" src/views/DashboardView.vue</automated>
    <manual>Verify DashboardView loads and displays metrics from API</manual>
  </verify>
  <done>
    - DashboardView displays 4 metric cards with live data
    - System health status shown with component breakdown
    - Latest signals feed displays recent signals
    - Auto-refresh working with 30-second interval
    - All data fetched from API services
  </done>
</task>

<task type="auto">
  <name>Task 2: Create PositionsView with filtering</name>
  <files>swing-trade-dashboard/src/views/PositionsView.vue, swing-trade-dashboard/src/components/PositionCard.vue</files>
  <action>
Create positions view with list, filtering, and search:

1. Layout structure:
   - Header with action buttons (New Position, Filter, Search)
   - Filter bar: Status dropdown (OPEN/CLOSED/ALL), Sector dropdown
   - Search input for symbol filtering
   - Grid of PositionCard components

2. PositionCard component:
   - Symbol, entry price, current price
   - P&L display with color coding (green profit, red loss)
   - Stop loss and target levels
   - Status badge
   - Quick actions: Close Position button
   - Responsive layout (3 cols on lg, 1 on mobile)

3. Filtering logic:
   - statusFilter ref for dropdown selection
   - symbolSearch ref for text search
   - computed filteredPositions combining both
   - Debounce search input (300ms)

4. Data management:
   - fetchPositions() to load from API
   - PositionStats and SectorAllocation displayed in sidebar
   - Close position action calls tradeService.closePosition()

5. Empty states:
   - "No open positions" message when empty
   - Loading skeleton during fetch
  </action>
  <verify>
    <automated>cd swing-trade-dashboard && grep -c "filteredPositions" src/views/PositionsView.vue && grep -c "PositionCard" src/views/PositionsView.vue</automated>
  </verify>
  <done>
    - PositionsView shows all positions in card grid
    - Filtering by status and sector works
    - Search by symbol filters the list
    - PositionCard displays complete position info with P&L
    - Close position action functional
  </done>
</task>

<task type="auto">
  <name>Task 3: Create SignalsView with signal generation</name>
  <files>swing-trade-dashboard/src/views/SignalsView.vue, swing-trade-dashboard/src/components/SignalCard.vue</files>
  <action>
Create signals view for displaying and generating trading signals:

1. Layout structure:
   - Header with "Generate New Signal" button
   - Tabs: Latest, By Symbol, High Confidence
   - SignalCard grid below tabs
   - Symbol selector dropdown for "By Symbol" tab

2. SignalCard component:
   - Signal type icon (BUY/SELL/HOLD) with color
   - Symbol name
   - Confidence score as progress bar (0-100%)
   - Reasoning text (expandable/collapsible)
   - Generated timestamp
   - High-confidence badge for scores > 70%

3. Tab logic:
   - Latest: signalService.getLatestSignals() - last 10
   - By Symbol: signalService.getSignalsBySymbol(symbol)
   - High Confidence: signalService.getHighConfidenceSignals(0.7)

4. Signal generation:
   - Modal/dialog for "Generate New Signal"
   - Symbol input with autocomplete
   - Confirmation before generation
   - Calls signalService.generateSignal(symbol)
   - Shows result in toast notification

5. Refresh:
   - Manual refresh button
   - Auto-refresh every 60 seconds
  </action>
  <verify>
    <automated>cd swing-trade-dashboard && grep -c "tabs" src/views/SignalsView.vue && grep -c "generateSignal" src/views/SignalsView.vue</automated>
  </verify>
  <done>
    - SignalsView shows signals in 3 tab views
    - SignalCard displays confidence and reasoning
    - Signal generation modal works
    - Generated signal shown in feed
    - All API calls use typed services
  </done>
</task>

<task type="auto">
  <name>Task 4: Create PortfolioView with performance metrics</name>
  <files>swing-trade-dashboard/src/views/PortfolioView.vue, swing-trade-dashboard/src/components/PerformanceMetrics.vue, swing-trade-dashboard/src/components/SectorAllocation.vue</files>
  <action>
Create portfolio view with performance charts and allocation:

1. PerformanceMetrics component:
   - Grid of key metrics: Total Return, Sharpe Ratio, Max Drawdown, Win Rate
   - Each metric as a stat card with value and label
   - Comparison vs previous period (if available)
   - Visual indicators for positive/negative values

2. SectorAllocation component:
   - Pie chart using Apache ECharts
   - Data from positionService.getSectorAllocation()
   - Legend showing sector percentages
   - Click handler for sector filtering
   - Responsive sizing

3. Portfolio performance chart:
   - Line chart showing cumulative P&L over time
   - Data from trade history aggregation
   - Date range selector (1W, 1M, 3M, 1Y)
   - Tooltips on hover

4. Position distribution:
   - Bar chart showing positions by sector
   - Horizontal bars for clarity
   - Sortable by allocation percentage

5. Data fetching:
   - tradeService.getPerformance() for metrics
   - positionService.getSectorAllocation() for allocation
   - Handle loading states and errors gracefully
  </action>
  <verify>
    <automated>cd swing-trade-dashboard && grep -c "echarts" src/components/PerformanceMetrics.vue && grep -c "sectorAllocation" src/components/SectorAllocation.vue</automated>
  </verify>
  <done>
    - PortfolioView displays all performance metrics
    - Sector allocation pie chart renders correctly
    - Performance chart shows P&L trend
    - ECharts configured with responsive resizing
    - All data sourced from API services
  </done>
</task>

</tasks>

<verification>
<automated>cd swing-trade-dashboard && npm run build 2>&1 | tail -5</automated>
<manual>
1. Navigate to /dashboard - verify health status and metrics
2. Navigate to /positions - verify filtering and search
3. Navigate to /signals - verify tabs and signal generation
4. Navigate to /portfolio - verify charts render
5. Check browser console for no errors
</manual>
</verification>

<success_criteria>
- All 4 views implemented (Dashboard, Positions, Signals, Portfolio)
- Components properly use API services
- Filtering and search functional in PositionsView
- Signal generation modal works
- ECharts charts render without errors
- Responsive design works on mobile/tablet/desktop
- TypeScript compilation succeeds with no errors
</success_criteria>

<output>
After completion, create `.planning/phases/08-vue-dashboard-monitoring-ui/08-vue-dashboard-monitoring-ui-02-SUMMARY.md`
</output>
