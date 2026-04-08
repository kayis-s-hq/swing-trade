---
phase: 08-vue-dashboard-monitoring-ui
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - swing-trade-dashboard/package.json
  - swing-trade-dashboard/vue.config.js
  - swing-trade-dashboard/src/main.ts
  - swing-trade-dashboard/index.html
  - swing-trade-dashboard/src/api/client.ts
  - swing-trade-dashboard/src/App.vue
  - swing-trade-dashboard/src/styles/main.css
autonomous: true
requirements:
  - DASH-01
  - DASH-02
user_setup:
  - service: Node.js
    why: "Dashboard build tooling and dependencies"
    env_vars: []
    dashboard_config: []

must_haves:
  truths:
    - "Dashboard application starts and serves on port 3000"
    - "API client successfully connects to Spring Boot backend at http://localhost:8080"
    - "Dashboard layout loads with sidebar navigation and main content area"
  artifacts:
    - path: "swing-trade-dashboard/package.json"
      provides: "Vue.js project configuration"
      min_lines: 40
    - path: "swing-trade-dashboard/src/main.ts"
      provides: "Vue application bootstrap"
      exports: ["createApp"]
    - path: "swing-trade-dashboard/src/api/client.ts"
      provides: "REST API client with error handling"
      exports: ["apiClient", "useAuth"]
  key_links:
    - from: "swing-trade-dashboard/src/api/client.ts"
      to: "http://localhost:8080/api/*"
      via: "axios HTTP client"
      pattern: "axios\\.(get|post)"
    - from: "swing-trade-dashboard/src/main.ts"
      to: "swing-trade-dashboard/src/App.vue"
      via: "createApp(App).mount('#app')"
      pattern: "createApp.*mount"
---

<objective>
Initialize Vue.js dashboard project with build tooling, API client, and base layout

Purpose: Create the foundation for the monitoring dashboard with proper project structure, dependencies, and API integration capabilities

Output:
- Vue 3 project with Vite build tooling
- REST API client for Spring Boot integration
- Base application layout with sidebar navigation
- Development server configured
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/ROADMAP.md
@.planning/STATE.md

# API Endpoints to Consume
From api/src/main/java/com/swingtrade/api/controller/:

## Health Endpoints (HealthController)
GET /api/health - Basic health check
GET /api/health/details - Detailed health with components
GET /api/health/full - Full system health

## Position Endpoints (PositionController)
GET /api/positions - List open positions
GET /api/positions/{symbol} - Get specific position
GET /api/positions/closed - List closed positions
GET /api/positions/status/{status} - Filter by status
GET /api/positions/sector/{sector} - Filter by sector
GET /api/positions/stats - Position statistics
GET /api/positions/sector-allocation - Sector allocation

## Trading Endpoints (TradingController)
POST /api/trades - Create new position
GET /api/trades - List open positions
GET /api/trades/{symbol} - Get specific position
POST /api/trades/{symbol}/close - Close position
GET /api/trades/{symbol}/history - Trade history
GET /api/trades/performance - Portfolio performance
GET /api/trades/risk-summary - Risk summary

## Signal Endpoints (SignalController)
GET /api/signals/latest - Latest signals
GET /api/signals/symbol/{symbol} - Signals by symbol
GET /api/signals/date-range - Signals by date range
GET /api/signals/type/{type} - Signals by type
GET /api/signals/high-confidence - High confidence signals
POST /api/signals/generate - Generate new signal
POST /api/signals/scan - Trigger scan
GET /api/signals/scan/history - Scan history
GET /api/signals/analysis/{symbol} - Technical analysis
GET /api/signals/sentiment/{symbol} - Sentiment analysis
GET /api/signals/combined/{symbol} - Combined signal

# API Response DTOs
From api/src/main/java/com/swingtrade/api/dto/:
- PositionResponse - Position data with P&L
- TradeResponse - Completed trade data
- PerformanceResponse - Portfolio metrics
- SignalResponse - Trading signals
- HealthStatus - System health
- ScanResponse - Scan results
- RiskSummary - Risk metrics
</context>

<interfaces>
<!-- Key types and contracts the executor needs. Extracted from codebase. -->

From api/src/main/java/com/swingtrade/api/dto/PositionResponse.java:
```typescript
// TypeScript interface for PositionResponse
interface PositionResponse {
  id?: number;
  symbol: string;
  entryPrice: number;
  entryDate: string;
  quantity: number;
  stopLoss: number;
  target: number;
  status: 'OPEN' | 'CLOSED' | 'STOPPED' | 'TARGET_HIT';
  entryReason: string;
  currentPrice: number;
  unrealizedPnL: number;
  unrealizedPnLPercent: number;
  averagePrice: number;
  totalValue: number;
}
```

From api/src/main/java/com/swingtrade/api/dto/PerformanceResponse.java:
```typescript
interface PerformanceResponse {
  totalReturn: number;
  annualizedReturn: number;
  sharpeRatio: number;
  maxDrawdown: number;
  totalTrades: number;
  winningTrades: number;
  losingTrades: number;
  winRate: number;
  averageWin: number;
  averageLoss: number;
  totalPnL: number;
}
```

From api/src/main/java/com/swingtrade/api/dto/HealthStatus.java:
```typescript
interface HealthStatus {
  status: 'UP' | 'DOWN' | 'DEGRADED';
  timestamp: string;
  components: {
    [key: string]: {
      name: string;
      status: 'UP' | 'DOWN' | 'DEGRADED';
      description: string;
      details: { [key: string]: any };
    };
  };
}
```

From api/src/main/java/com/swingtrade/api/dto/SignalResponse.java:
```typescript
interface SignalResponse {
  symbol: string;
  signalType: 'BUY' | 'SELL' | 'HOLD';
  confidence: number;
  generatedAt: string;
  reasoning: string;
}
```
</interfaces>

<tasks>

<task type="auto">
  <name>Task 1: Initialize Vue 3 project with Vite</name>
  <files>swing-trade-dashboard/package.json, swing-trade-dashboard/vue.config.js, swing-trade-dashboard/index.html</files>
  <action>
Create Vue 3 dashboard project using Vite as build tool:

1. Create project directory structure: swing-trade-dashboard/
   - src/components/ - Reusable Vue components
   - src/views/ - Page components (Dashboard, Positions, Signals, Portfolio)
   - src/api/ - API client and types
   - src/router/ - Vue Router configuration
   - src/styles/ - Global CSS
   - src/types/ - TypeScript interfaces

2. Create package.json with dependencies:
   - vue@3.4 (core framework)
   - vue-router@4.3 (routing)
   - axios@1.6 (HTTP client)
   - tailwindcss@3.4 (utility-first CSS)
   - @heroicons/vue@2.1 (icon library)
   - echarts@5.4 (charts library)
   - typescript@5.3 (type checking)
   - vite@5.0 (build tool)
   - @vitejs/plugin-vue@4.5 (Vite plugin)

3. Create index.html with #app root element
   - Include Tailwind CDN for development
   - Set viewport meta tags for responsive design

4. Create vue.config.js with:
   - Proxy configuration to forward /api requests to http://localhost:8080
   - Development server port: 3000
   - Build output configuration

5. Create tsconfig.json with Vue 3 + TypeScript settings
   - Enable strict mode
   - Configure path aliases (@/* -> src/*)
  </action>
  <verify>
    <automated>cd swing-trade-dashboard && npm list vue vue-router axios tailwindcss 2>&1 | grep -E "vue@|axios@|tailwindcss@" && cat package.json | jq -r '.dependencies | keys[]' | sort</automated>
    <manual>Verify package.json contains all required dependencies with correct versions</manual>
  </verify>
  <done>
    - package.json created with Vue 3, Router, Axios, Tailwind, ECharts dependencies
    - vue.config.js configured with proxy to backend at localhost:8080
    - index.html with root element and viewport configuration
    - Directory structure created (src/components, src/views, src/api, src/router, src/styles, src/types)
  </done>
</task>

<task type="auto">
  <name>Task 2: Create API client with TypeScript types</name>
  <files>swing-trade-dashboard/src/api/client.ts, swing-trade-dashboard/src/types/index.ts</files>
  <action>
Create REST API client with comprehensive TypeScript interfaces:

1. Create src/types/index.ts with all API response interfaces:
   - PositionResponse (from Java DTO)
   - TradeResponse
   - PerformanceResponse
   - SignalResponse
   - HealthStatus
   - ScanResponse
   - RiskSummary
   - SectorAllocation
   - PositionStats

2. Create src/api/client.ts:
   - Initialize axios instance with baseURL from environment
   - Add request interceptor for logging
   - Add response interceptor for error handling
   - Create service functions for each endpoint group:
     * healthService: getHealth(), getDetailedHealth(), getFullHealth()
     * positionService: getPositions(), getPosition(), getClosedPositions(), getPositionStats()
     * tradeService: createPosition(), closePosition(), getPerformance()
     * signalService: getLatestSignals(), getSignalsBySymbol(), generateSignal()
     * scanService: triggerScan(), getScanHistory()

3. Implement typed error handling:
   - Distinguish between network errors and API errors
   - Return descriptive error messages for UI

4. Add request cancellation for rapid queries
  </action>
  <verify>
    <automated>cd swing-trade-dashboard && grep -c "export.*Service" src/api/client.ts && grep -c "interface.*Response" src/types/index.ts</automated>
  </verify>
  <done>
    - API client exports healthService, positionService, tradeService, signalService, scanService
    - All 8+ TypeScript interfaces defined matching Java DTOs
    - Axios instance configured with error handling
    - Each service has typed methods matching API endpoints
  </done>
</task>

<task type="auto">
  <name>Task 3: Build base application layout</name>
  <files>swing-trade-dashboard/src/App.vue, swing-trade-dashboard/src/main.ts, swing-trade-dashboard/src/styles/main.css</files>
  <action>
Create base Vue application with layout structure:

1. Create src/styles/main.css:
   - Import Tailwind base, components, utilities
   - Add custom scrollbar styles
   - Add responsive container utilities
   - Define CSS variables for theme colors

2. Create src/main.ts:
   - Import Vue and createApp
   - Import Tailwind styles
   - Import App.vue
   - Configure Vue 3 devtools
   - Mount to #app element

3. Create src/App.vue:
   - Layout wrapper with flex container
   - Sidebar navigation (left, 250px width, collapsible on mobile)
   - Main content area (flexible width)
   - Header bar with system status indicator
   - Router-view for page content

4. Create sidebar navigation component structure:
   - Logo/header section
   - Navigation links: Dashboard, Positions, Signals, Portfolio, Settings
   - Active route highlighting
   - Collapsible on screens < 768px

5. Create header component:
   - System health status indicator (green/up, red/down)
   - Last updated timestamp
   - Refresh button to reload data
  </action>
  <verify>
    <automated>cd swing-trade-dashboard && grep -c "createApp" src/main.ts && grep -c "router-view" src/App.vue && ls -la src/components/ src/views/</automated>
  </verify>
  <done>
    - main.ts bootstraps Vue app and mounts to #app
    - App.vue has sidebar + main content layout structure
    - Navigation links for all major sections
    - System health status indicator in header
    - Tailwind styles imported and configurable
  </done>
</task>

<task type="auto">
  <name>Task 4: Create router configuration</name>
  <files>swing-trade-dashboard/src/router/index.ts</files>
  <action>
Configure Vue Router for navigation:

1. Create src/router/index.ts:
   - Initialize Vue Router with createRouter + createWebHistory
   - Define routes:
     * '/' -> Dashboard view (redirect to '/dashboard')
     * '/dashboard' -> Dashboard view
     * '/positions' -> Positions view
     * '/signals' -> Signals view
     * '/portfolio' -> Portfolio view
     * '/settings' -> Settings view

2. Add route guards:
   - Navigation guard for authenticated checks (future)

3. Configure route metadata:
   - title for each route
   - icon for sidebar display
   - requiresAuth flag
  </action>
  <verify>
    <automated>cd swing-trade-dashboard && grep -c "RouteConfig\|defineRoute" src/router/index.ts && grep -c "createRouter" src/router/index.ts</automated>
  </verify>
  <done>
    - Router defined with 6 routes (dashboard, positions, signals, portfolio, settings)
    - Routes have metadata for navigation
    - Navigation guard infrastructure in place
    - History mode configured for clean URLs
  </done>
</task>

</tasks>

<verification>
<automated>cd swing-trade-dashboard && npm run dev -- --host 0.0.0.0 --port 3000 & sleep 5 && curl -s http://localhost:3000 | grep -q "app" && echo "Dashboard serves successfully"</automated>
<manual>
1. Visit http://localhost:3000
2. Verify sidebar navigation is visible
3. Verify main content area is present
4. Check browser console for errors
</manual>
</verification>

<success_criteria>
- Vue 3 project scaffolding complete with Vite
- package.json has all required dependencies (vue, router, axios, tailwind, echarts)
- API client defined with TypeScript interfaces for all endpoints
- Base layout renders with sidebar navigation
- Development server starts on port 3000
- No TypeScript compilation errors
</success_criteria>

<output>
After completion, create `.planning/phases/08-vue-dashboard-monitoring-ui/08-vue-dashboard-monitoring-ui-01-SUMMARY.md`
</output>
