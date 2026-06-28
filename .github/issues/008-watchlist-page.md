# feat(dashboard): add watchlist page

**Labels:** `enhancement` `tier-2-frontend` `dashboard`
**Estimated effort:** 2 days

## Problem

The dashboard has no watchlist UI. Users cannot view or manage their list of monitored stocks from the frontend.

## Proposed Solution

Create a dedicated watchlist page with add/remove functionality and live signal status for each watched stock.

## Routes

Add to `src/router/index.ts`:
```typescript
{
  path: '/watchlist',
  name: 'Watchlist',
  component: () => import('../views/WatchlistView.vue'),
}
```

Add to `src/components/Sidebar.vue`:
- New sidebar link: "Watchlist" with icon (star/bell)

## Pages & Components

### WatchlistView.vue
Main page with:
- Header: "Watchlist" + "Add Stock" button
- Search input for adding new stocks (debounced)
- Table of watched stocks with:
  - Symbol, Name, Sector
  - Latest Price (from candle)
  - Signal (BUY/SELL/HOLD with color badge)
  - Confidence score (bar or number)
  - Last Signal date
  - Actions: remove button, view chart button
- Empty state: "No stocks in watchlist. Add one to get started."

### WatchlistCard.vue
Individual stock card (alternative grid view):
```vue
<template>
  <div class="rounded-xl border p-4 hover:border-indigo-300 transition-colors">
    <div class="flex justify-between items-start">
      <div>
        <h4 class="font-bold">{{ stock.symbol }}</h4>
        <p class="text-sm text-gray-500">{{ stock.name }}</p>
      </div>
      <button @click="remove" class="text-gray-400 hover:text-red-500">
        <svg>...trash icon...</svg>
      </button>
    </div>
    <div class="mt-2 flex items-center gap-2">
      <span class="signal-badge">{{ stock.signal }}</span>
      <span class="confidence-bar">{{ stock.confidence }}%</span>
    </div>
  </div>
</template>
```

### AddStockDialog.vue
Modal for searching and adding stocks:
```vue
<template>
  <div class="modal-overlay">
    <div class="modal-content">
      <h3>Add to Watchlist</h3>
      <input v-model="query" @input="searchStocks" placeholder="Enter symbol..." />
      <ul v-if="results.length">
        <li v-for="stock in results" @click="addToWatchlist(stock)">
          {{ stock.symbol }} - {{ stock.name }}
        </li>
      </ul>
      <p v-else-if="query">No results</p>
    </div>
  </div>
</template>
```

## API Calls

```typescript
// In client.ts
export const getWatchlist = (): Promise<ApiResponse<WatchlistItem[]>> =>
  request('/watchlist')

export const addToWatchlist = (symbol: string): Promise<ApiResponse<void>> =>
  request('/watchlist', { method: 'POST', body: JSON.stringify({ symbol }) })

export const removeFromWatchlist = (symbol: string): Promise<ApiResponse<void>> =>
  request(`/watchlist/${symbol}`, { method: 'DELETE' })

export const getWatchlistSignals = (): Promise<ApiResponse<Signal[]>> =>
  request('/watchlist/signals')
```

## TypeScript Types

Add to `src/api/types.ts`:
```typescript
export interface WatchlistItem {
  symbol: string
  name?: string
  sector?: string
  addedAt: string
  notes?: string
  latestSignal?: Signal
  currentPrice?: number
}
```

## Styling

- Grid layout: 3 columns on desktop, 1 on mobile
- Add stock button: primary (indigo) in header
- Remove button: red on hover
- Signal badges: same colors as SignalsView
- Search input: debounce 300ms

## Acceptance Criteria

- [ ] `/watchlist` route added to router
- [ ] Sidebar has Watchlist navigation link
- [ ] WatchlistView renders watchlist items from API
- [ ] Add stock dialog searches and adds stocks
- [ ] Remove button deletes from watchlist
- [ ] Signal badge shows latest signal per stock
- [ ] Empty state displayed when watchlist is empty
- [ ] Loading and error states handled
- [ ] Responsive grid layout
- [ ] Dark mode support

## Notes

- Reuse `SignalCard.vue` component for signal display within watchlist items
- The search should use the existing stocks data or call a search endpoint
- Consider adding keyboard shortcut (e.g., `W` for watchlist)
