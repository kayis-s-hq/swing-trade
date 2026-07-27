# Sentiment Rename + Dashboard Integration — Continuation Plan

## What's already done (backend — completed in prior session)

- `SentimentAnalysisService` → `SentimentService` (file renamed, class renamed, all refs updated across llm/api/strategy/data modules)
- `SentimentAccuracyTracker` → `SentimentAccuracyService` (file renamed, class renamed)
- `SentimentAnalysisResult` → `SentimentOutput` (file renamed, class renamed)
- Dead code `generateSignalFromSentiment()` deleted (never called)
- `SignalController` placeholder endpoints `/api/signals/sentiment/{symbol}` and `/api/signals/combined/{symbol}` wired to real `SentimentResultRepository` data
- All test files updated with new class names

## What needs to be done (dashboard)

### 1. Add types to `dashboard/src/api/types.ts`

```ts
export interface SentimentResult {
  id: number
  symbol: string
  date: string
  score: 'POSITIVE' | 'NEUTRAL' | 'NEGATIVE'
  summary: string
  confidence: number
  analyzedAt: string
  redFlags: string[]
  catalysts: string[]
}

export interface SentimentHistoryItem {
  id: number
  symbol: string
  date: string
  score: 'POSITIVE' | 'NEUTRAL' | 'NEGATIVE'
  summary: string
  confidence: number
  analyzedAt: string
  redFlags: string[]
  catalysts: string[]
}

export interface SentimentAccuracyStats {
  total: number
  correct: number
  accuracy_pct: number
  by_sentiment: Record<string, number>
  by_symbol: Record<string, number>
}
```

### 2. Add API functions to `dashboard/src/api/client.ts`

```ts
// Sentiment
export async function getSentimentLatest(symbol: string): Promise<ApiResponse<SentimentResult>> {
  const raw = await rawFetch(`/sentiment/${symbol}/latest`)
  if (!raw.ok) return errResponse(raw.error!)
  return { success: true, data: (raw.data as any).data as SentimentResult }
}

export async function getSentimentHistory(symbol: string, page = 0, size = 20): Promise<ApiResponse<SentimentHistoryItem[]>> {
  const raw = await rawFetch(`/sentiment/${symbol}/history?page=${page}&size=${size}`)
  if (!raw.ok) return errResponse(raw.error!)
  return { success: true, data: (raw.data as any).data as SentimentHistoryItem[] }
}

export async function getAccuracyStats(): Promise<ApiResponse<SentimentAccuracyStats>> {
  const raw = await rawFetch('/sentiment/accuracy')
  if (!raw.ok) return errResponse(raw.error!)
  return { success: true, data: (raw.data as any).data as SentimentAccuracyStats }
}

export async function triggerSentimentAnalysis(symbol: string): Promise<ApiResponse<SentimentResult>> {
  const raw = await rawFetch(`/sentiment/${symbol}/analyse`, { method: 'POST' })
  if (!raw.ok) return errResponse(raw.error!)
  return { success: true, data: (raw.data as any).data as SentimentResult }
}

export async function getLatestNews(symbol: string): Promise<ApiResponse<any[]>> {
  const raw = await rawFetch(`/news/${symbol}/latest`)
  if (!raw.ok) return errResponse(raw.error!)
  return { success: true, data: (raw.data as any).data as any[] }
}

export async function getLatestEarnings(symbol: string): Promise<ApiResponse<any>> {
  const raw = await rawFetch(`/pdf/${symbol}/latest`)
  if (!raw.ok) return errResponse(raw.error!)
  return { success: true, data: (raw.data as any).data as any }
}
```

Also add the imports for the new types at the top of client.ts.

### 3. Add icon to `dashboard/src/components/Icons.ts`

Add a brain/lightbulb icon:
```ts
intelligence: '<path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M9.663 17h4.673M12 3v1m6.364 1.636l-.707.707M21 12h-1M4 12H3m3.343-5.657l-.707-.707m2.828 9.9a5 5 0 117.072 0l-.548.547A3.374 3.374 0 0014 18.469V19a2 2 0 11-4 0v-.531c0-.895-.356-1.754-.988-2.386l-.548-.547z" />',
```

### 4. Add routes to `dashboard/src/router/index.ts`

```ts
{
  path: '/sentiment',
  name: 'Sentiment',
  component: () => import('../views/SentimentView.vue'),
},
{
  path: '/monitoring',
  name: 'Monitoring',
  component: () => import('../views/MonitoringView.vue'),
},
```

### 5. Update sidebar `dashboard/src/components/Sidebar.vue`

Add nav items under an "Intelligence" group. The navItems array should become:

```ts
const navItems = [
  { path: '/', label: 'Dashboard', icon: iconPaths.dashboard, badge: undefined },
  { path: '/positions', label: 'Positions', icon: iconPaths.positions, badge: undefined },
  { path: '/signals', label: 'Signals', icon: iconPaths.signals, badge: '6' },
  { path: '/sentiment', label: 'Sentiment', icon: iconPaths.intelligence, badge: undefined },
  { path: '/monitoring', label: 'Monitoring', icon: iconPaths.intelligence, badge: undefined },
  { path: '/portfolio', label: 'Portfolio', icon: iconPaths.portfolio, badge: undefined },
  { path: '/watchlist', label: 'Watchlist', icon: iconPaths.watchlist, badge: undefined },
  { path: '/backtest', label: 'Backtest', icon: iconPaths.backtest, badge: undefined },
  { path: '/data', label: 'Data', icon: iconPaths.data, badge: undefined },
  { path: '/settings', label: 'Settings', icon: iconPaths.settings, badge: undefined },
]
```

### 6. Create `dashboard/src/components/SentimentBadge.vue`

A reusable badge component that shows sentiment score with color coding:
- POSITIVE → green background, white text, "🟢 POS"
- NEUTRAL → yellow/amber background, "🟡 NEU"
- NEGATIVE → red background, "🔴 NEG"
- Show confidence percentage below badge
- Props: `score: 'POSITIVE' | 'NEUTRAL' | 'NEGATIVE'`, `confidence: number`

### 7. Create `dashboard/src/components/SentimentTimeline.vue`

A simple vertical timeline showing sentiment history for a symbol:
- Props: `items: SentimentHistoryItem[]`
- Each item shows: date, colored badge, confidence, summary (truncated to 2 lines)
- Uses the SentimentBadge component

### 8. Create `dashboard/src/views/SentimentView.vue`

A page with two tabs: "Detail" and "History"

**Detail tab:**
- Symbol selector (dropdown with watchlist symbols + input for custom)
- Latest sentiment card showing: score badge, confidence, date, summary, red flags, catalysts
- Refresh button that calls `triggerSentimentAnalysis(symbol)`

**History tab:**
- SentimentTimeline component showing all historical sentiment results
- Paginated (20 per page)

### 9. Create `dashboard/src/views/MonitoringView.vue`

LLM accuracy dashboard:
- Overall accuracy: large percentage display with bar (e.g., "3/5 correct — 60%")
- Two-column grid: "By Sentiment" and "By Symbol" breakdowns
- Recent trades table: symbol, direction, date, outcome, correct/wrong indicator
- Calls `getAccuracyStats()` for the stats data

---

## Style guide

- Use the same patterns as existing views: Tailwind CSS, dark/light theme support
- Follow the existing component structure: `<template>`, `<script setup lang="ts">`, `<style scoped>`
- Use the existing API client pattern: `rawFetch` → `ApiResponse<T>` → `{ success, data, error }`
- Use the existing sidebar/nav patterns for consistency
- Empty states should show "No data available" in `text-text-muted`
- Loading states use `LoadingSpinner` component
- Error states use `ErrorMessage` component

## Verification

After building all dashboard files:
1. Run `cd dashboard && npm run typecheck` to verify types
2. Run `cd dashboard && npm run dev` to start the dev server
3. Verify the new routes render correctly