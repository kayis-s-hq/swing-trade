<template>
  <div class="view-shell p-4 sm:p-6 animate-fade-in">
    <!-- Page Header -->
    <div class="mb-6 flex items-center justify-between">
      <div>
        <h1 class="font-display text-2xl font-semibold text-text-primary">Backtest</h1>
        <p class="mt-1 text-sm text-text-muted">
          Replay a strategy against historical candles, or compare strategy variants as a portfolio
        </p>
      </div>
    </div>

    <div
      class="mb-6 inline-flex rounded-md border border-border-subtle bg-bg-primary/40 p-0.5"
      role="tablist"
      aria-label="Backtest mode"
    >
      <button
        v-for="option in TABS"
        :key="option.value"
        role="tab"
        :aria-selected="tab === option.value"
        class="rounded px-3 py-1.5 text-xs font-medium transition-colors"
        :class="
          tab === option.value ? 'bg-brand-subtle text-brand' : 'text-text-muted hover:bg-bg-hover'
        "
        @click="tab = option.value"
      >
        {{ option.label }}
      </button>
    </div>

    <BacktestComparePanel
      v-if="tab === 'compare'"
      v-model:selected="compareSelection"
      show-picker
    />

    <div v-show="tab === 'single'">
    <!-- Run Form -->
    <div class="mb-6 card-panel p-5">
      <h3 class="mb-3 text-sm font-semibold text-text-primary">Run Backtest</h3>
      <form class="flex flex-col sm:flex-row gap-3" @submit.prevent="handleRunSingle">
        <div ref="symbolSelect" class="relative flex-1">
          <label class="mb-1 block text-xs font-medium text-text-muted">Symbol</label>
          <input
            v-model="symbolQuery"
            type="text"
            role="combobox"
            aria-label="Backtest symbol"
            aria-controls="backtest-symbol-options"
            :aria-expanded="symbolMenuOpen"
            autocomplete="off"
            placeholder="Search watchlist symbols"
            required
            class="w-full rounded-md border border-border-subtle bg-bg-primary px-3 py-2 text-sm text-text-primary placeholder:text-text-muted/50 focus:outline-none focus:ring-2 focus:ring-brand/30"
            @focus="openSymbolMenu"
            @input="handleSymbolInput"
            @keydown.down.prevent="moveSymbolFocus(1)"
            @keydown.up.prevent="moveSymbolFocus(-1)"
            @keydown.enter.prevent="selectFocusedSymbol"
            @keydown.esc="symbolMenuOpen = false"
          />
          <div
            v-if="symbolMenuOpen"
            id="backtest-symbol-options"
            role="listbox"
            class="absolute z-20 mt-1 max-h-60 w-full overflow-y-auto rounded-md border border-border-subtle bg-bg-surface py-1 shadow-xl"
          >
            <button
              v-for="(entry, index) in filteredWatchlist"
              :key="entry.symbol"
              type="button"
              role="option"
              :aria-selected="entry.symbol === symbol"
              class="flex w-full items-center justify-between px-3 py-2 text-left text-sm transition-colors hover:bg-bg-hover"
              :class="
                index === focusedSymbolIndex ? 'bg-brand-subtle text-brand' : 'text-text-primary'
              "
              @mousedown.prevent="selectSymbol(entry)"
            >
              <span class="font-medium">{{ entry.symbol }}</span>
              <span class="ml-3 truncate text-xs text-text-muted">{{ entry.name }}</span>
            </button>
            <p v-if="watchlistLoading" class="px-3 py-2 text-xs text-text-muted">
              Loading watchlist...
            </p>
            <p v-else-if="watchlistError" class="px-3 py-2 text-xs text-danger">
              Watchlist could not be loaded.
            </p>
            <p v-else-if="filteredWatchlist.length === 0" class="px-3 py-2 text-xs text-text-muted">
              No matching active watchlist symbols.
            </p>
          </div>
          <p v-if="symbol && !selectedWatchlistSymbol" class="mt-1 text-xs text-danger">
            Select a symbol from the watchlist.
          </p>
        </div>
        <div class="flex-1">
          <label class="mb-1 block text-xs font-medium text-text-muted">Exchange</label>
          <select
            v-model="exchange"
            class="w-full rounded-md border border-border-subtle bg-bg-primary px-3 py-2 text-sm text-text-primary focus:outline-none focus:ring-2 focus:ring-brand/30"
          >
            <option value="NSE">NSE</option>
            <option value="BSE">BSE</option>
          </select>
        </div>
        <div class="flex items-end gap-2">
          <button
            type="submit"
            :disabled="running"
            class="rounded-md bg-brand px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-brand/90 disabled:opacity-50"
          >
            {{ running ? 'Running...' : 'Run Backtest' }}
          </button>
          <button
            type="button"
            :disabled="runningAll"
            class="rounded-md border border-border-subtle px-3 py-2 text-sm text-text-muted transition-colors hover:bg-bg-hover disabled:opacity-50"
            @click="handleRunAll"
          >
            {{ runningAll ? 'Running Watchlist...' : 'Run Whole Watchlist' }}
          </button>
        </div>
      </form>
      <p v-if="runError" class="mt-3 text-xs text-danger">
        {{ runError }}
      </p>
    </div>

    <!-- Single Result -->
    <div v-if="result" class="mb-6 card-panel p-5">
      <div class="mb-4 flex items-center justify-between">
        <h3 class="text-sm font-semibold text-text-primary">{{ result.symbol }} — Result</h3>
      </div>
      <div class="grid grid-cols-2 gap-4 sm:grid-cols-4">
        <MetricCard title="Total Trades" :value="result.totalTrades" />
        <MetricCard title="Win Rate" :value="result.winRate.toFixed(1) + '%'" />
        <MetricCard title="Total Return" :value="result.totalReturn.toFixed(2) + '%'" />
        <MetricCard title="Sharpe Ratio" :value="result.sharpeRatio.toFixed(2)" />
        <MetricCard title="Avg Gain" :value="result.avgGainPct.toFixed(2) + '%'" />
        <MetricCard title="Avg Loss" :value="result.avgLossPct.toFixed(2) + '%'" />
        <MetricCard title="Max Drawdown" :value="result.maxDrawdownPct.toFixed(2) + '%'" />
        <MetricCard title="Expectancy" :value="result.expectancy.toFixed(2) + '%'" />
      </div>

      <div class="mt-5 overflow-x-auto">
        <table class="min-w-full">
          <thead>
            <tr class="border-b border-border-subtle bg-bg-primary/50">
              <th
                class="px-4 py-2 text-left text-xs font-semibold uppercase tracking-wider text-text-muted"
              >
                Entry
              </th>
              <th
                class="px-4 py-2 text-left text-xs font-semibold uppercase tracking-wider text-text-muted"
              >
                Exit
              </th>
              <th
                class="px-4 py-2 text-right text-xs font-semibold uppercase tracking-wider text-text-muted"
              >
                Entry Price
              </th>
              <th
                class="px-4 py-2 text-right text-xs font-semibold uppercase tracking-wider text-text-muted"
              >
                Exit Price
              </th>
              <th
                class="px-4 py-2 text-left text-xs font-semibold uppercase tracking-wider text-text-muted"
              >
                Exit Reason
              </th>
              <th
                class="px-4 py-2 text-right text-xs font-semibold uppercase tracking-wider text-text-muted"
              >
                P&L
              </th>
              <th
                class="px-4 py-2 text-right text-xs font-semibold uppercase tracking-wider text-text-muted"
              >
                P&L %
              </th>
              <th
                class="px-4 py-2 text-right text-xs font-semibold uppercase tracking-wider text-text-muted"
              >
                Days Held
              </th>
            </tr>
          </thead>
          <tbody class="divide-y divide-border-subtle/50">
            <tr
              v-for="(t, i) in result.trades"
              :key="i"
              class="transition-colors hover:bg-bg-hover"
            >
              <td class="px-4 py-2 text-sm text-text-secondary">
                {{ t.entryDate }}
              </td>
              <td class="px-4 py-2 text-sm text-text-secondary">
                {{ t.exitDate }}
              </td>
              <td class="px-4 py-2 text-right text-sm text-text-secondary">
                {{ t.entryPrice.toFixed(2) }}
              </td>
              <td class="px-4 py-2 text-right text-sm text-text-secondary">
                {{ t.exitPrice.toFixed(2) }}
              </td>
              <td class="px-4 py-2 text-sm text-text-muted">
                {{ t.exitReason }}
              </td>
              <td
                class="px-4 py-2 text-right text-sm font-medium"
                :class="t.pnl >= 0 ? 'text-success' : 'text-danger'"
              >
                {{ t.pnl.toFixed(2) }}
              </td>
              <td
                class="px-4 py-2 text-right text-sm font-medium"
                :class="t.pnlPct >= 0 ? 'text-success' : 'text-danger'"
              >
                {{ t.pnlPct.toFixed(2) }}%
              </td>
              <td class="px-4 py-2 text-right text-sm text-text-secondary">
                {{ t.holdingDays }}
              </td>
            </tr>
          </tbody>
        </table>
        <div v-if="result.trades.length === 0" class="py-6 text-center text-sm text-text-muted">
          No trades triggered by the entry rules over the available history.
        </div>
      </div>
    </div>

    <!-- Watchlist Summary -->
    <div v-if="summary" class="mb-6 card-panel p-5">
      <h3 class="mb-4 text-sm font-semibold text-text-primary">
        Watchlist Run — {{ summary.symbolsBacktested }} symbols
      </h3>
      <div class="grid grid-cols-2 gap-4 sm:grid-cols-3">
        <MetricCard title="Overall Win Rate" :value="summary.overallWinRate.toFixed(1) + '%'" />
        <MetricCard title="Overall Sharpe" :value="summary.overallSharpeRatio.toFixed(2)" />
        <MetricCard title="Generated" :value="new Date(summary.generatedAt).toLocaleString()" />
      </div>

      <div class="mt-5 overflow-x-auto">
        <table class="min-w-full">
          <thead>
            <tr class="border-b border-border-subtle bg-bg-primary/50">
              <th
                class="px-4 py-2 text-left text-xs font-semibold uppercase tracking-wider text-text-muted"
              >
                Symbol
              </th>
              <th
                class="px-4 py-2 text-right text-xs font-semibold uppercase tracking-wider text-text-muted"
              >
                Trades
              </th>
              <th
                class="px-4 py-2 text-right text-xs font-semibold uppercase tracking-wider text-text-muted"
              >
                Win Rate
              </th>
              <th
                class="px-4 py-2 text-right text-xs font-semibold uppercase tracking-wider text-text-muted"
              >
                Total Return
              </th>
              <th
                class="px-4 py-2 text-right text-xs font-semibold uppercase tracking-wider text-text-muted"
              >
                Sharpe
              </th>
            </tr>
          </thead>
          <tbody class="divide-y divide-border-subtle/50">
            <tr
              v-for="r in summary.results"
              :key="r.symbol"
              class="transition-colors hover:bg-bg-hover"
            >
              <td class="px-4 py-2 text-sm font-semibold text-text-primary">
                {{ r.symbol }}
              </td>
              <td class="px-4 py-2 text-right text-sm text-text-secondary">
                {{ r.totalTrades }}
              </td>
              <td class="px-4 py-2 text-right text-sm text-text-secondary">
                {{ r.winRate.toFixed(1) }}%
              </td>
              <td
                class="px-4 py-2 text-right text-sm font-medium"
                :class="r.totalReturn >= 0 ? 'text-success' : 'text-danger'"
              >
                {{ r.totalReturn.toFixed(2) }}%
              </td>
              <td class="px-4 py-2 text-right text-sm text-text-secondary">
                {{ r.sharpeRatio.toFixed(2) }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- Saved Reports -->
    <div class="card-panel p-5">
      <div class="mb-3 flex items-center justify-between">
        <h3 class="text-sm font-semibold text-text-primary">Saved Reports</h3>
        <button
          class="text-xs font-medium text-text-muted hover:text-text-primary"
          @click="loadReports"
        >
          Refresh
        </button>
      </div>
      <ErrorMessage
        v-if="reportsError"
        title="Saved reports couldn’t be loaded"
        message="Check the backend connection, then retry."
        action-label="Retry"
        @action="loadReports"
      />
      <ul class="divide-y divide-border-subtle/50">
        <li
          v-for="filename in reports"
          :key="filename"
          class="flex items-center justify-between py-2"
        >
          <span class="text-sm text-text-secondary">{{ filename }}</span>
          <button
            class="text-xs font-medium text-brand hover:underline"
            @click="viewReport(filename)"
          >
            View
          </button>
        </li>
      </ul>
      <div
        v-if="!reportsError && reportsLoaded && reports.length === 0"
        class="py-4 text-center text-sm text-text-muted"
      >
        No saved reports yet.
      </div>
    </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { runBacktest, runBacktestAll, listBacktestReports, getBacktestReport } from '../api/client'
import { getWatchlist } from '../api/watchlist'
import type { BacktestResult, BacktestReportSummary, WatchlistEntry } from '../api/types'
import MetricCard from '../components/MetricCard.vue'
import ErrorMessage from '../components/ErrorMessage.vue'
import BacktestComparePanel from '../components/BacktestComparePanel.vue'

const TABS = [
  { value: 'single' as const, label: 'Single symbol' },
  { value: 'compare' as const, label: 'Portfolio compare' },
]
const tab = ref<'single' | 'compare'>(
  new URLSearchParams(window.location.search).get('tab') === 'compare' ? 'compare' : 'single'
)
const compareSelection = ref<string[]>([])

const symbol = ref('')
const symbolQuery = ref('')
const exchange = ref('NSE')
const running = ref(false)
const runningAll = ref(false)
const runError = ref('')

const result = ref<BacktestResult | null>(null)
const summary = ref<BacktestReportSummary | null>(null)
const reports = ref<string[]>([])
const reportsError = ref(false)
const reportsLoaded = ref(false)
let reportRequestId = 0
const watchlist = ref<WatchlistEntry[]>([])
const watchlistLoading = ref(false)
const watchlistError = ref(false)
const symbolMenuOpen = ref(false)
const focusedSymbolIndex = ref(-1)
const symbolSelect = ref<HTMLElement | null>(null)
// True right after focusing an already-selected field: browse the full list
// without filtering by the displayed text, since that text names the current
// selection rather than a search the user typed. Cleared on the next actual
// keystroke, at which point normal filtering resumes.
const browsingFullList = ref(false)

const activeWatchlist = computed(() => watchlist.value.filter((entry) => entry.isActive))
const filteredWatchlist = computed(() => {
  const query = symbolQuery.value.trim().toLowerCase()
  if (!query || browsingFullList.value) return activeWatchlist.value
  return activeWatchlist.value.filter(
    (entry) =>
      entry.symbol.toLowerCase().includes(query) || entry.name.toLowerCase().includes(query)
  )
})
const selectedWatchlistSymbol = computed(() =>
  activeWatchlist.value.find((entry) => entry.symbol === symbol.value)
)

const selectSymbol = (entry: WatchlistEntry) => {
  symbol.value = entry.symbol
  symbolQuery.value = entry.symbol
  exchange.value = entry.exchange || 'NSE'
  symbolMenuOpen.value = false
  focusedSymbolIndex.value = -1
}

const openSymbolMenu = () => {
  // Keep whatever text is already displayed (a selected symbol) instead of
  // clearing it - clearing wiped the visible text while leaving `symbol` (the
  // actual selection) unchanged, so refocusing the field looked like the
  // selection had been lost even though it hadn't. Browse the full list
  // rather than filtering by that leftover text until the user actually types.
  browsingFullList.value = true
  symbolMenuOpen.value = true
  focusedSymbolIndex.value = filteredWatchlist.value.length > 0 ? 0 : -1
}

const handleSymbolInput = () => {
  browsingFullList.value = false
  const selected = activeWatchlist.value.find(
    (entry) => entry.symbol === symbolQuery.value.trim().toUpperCase()
  )
  symbol.value = selected?.symbol ?? ''
  symbolMenuOpen.value = true
  focusedSymbolIndex.value = filteredWatchlist.value.length > 0 ? 0 : -1
}

const moveSymbolFocus = (direction: number) => {
  symbolMenuOpen.value = true
  if (filteredWatchlist.value.length === 0) return
  const next = focusedSymbolIndex.value + direction
  focusedSymbolIndex.value =
    (next + filteredWatchlist.value.length) % filteredWatchlist.value.length
}

const selectFocusedSymbol = () => {
  const entry = filteredWatchlist.value[focusedSymbolIndex.value]
  if (entry) selectSymbol(entry)
}

const handleDocumentClick = (event: MouseEvent) => {
  if (symbolSelect.value && !symbolSelect.value.contains(event.target as Node)) {
    symbolMenuOpen.value = false
  }
}

function confirmed<T>(value: T | { success: boolean; data?: T; error?: string }): T | undefined {
  if (typeof value === 'object' && value !== null && 'success' in value) {
    return value.success ? value.data : undefined
  }
  return value as T
}

const handleRunSingle = async () => {
  if (!selectedWatchlistSymbol.value) {
    runError.value = 'Select a symbol from the active watchlist.'
    return
  }
  running.value = true
  runError.value = ''
  result.value = null
  try {
    const res = await runBacktest(symbol.value, exchange.value)
    const data = confirmed(res)
    if (data) result.value = data
    else {
      runError.value = 'Backtest failed — check the symbol has enough candle history.'
    }
  } finally {
    running.value = false
  }
}

const handleRunAll = async () => {
  runningAll.value = true
  runError.value = ''
  summary.value = null
  try {
    const res = await runBacktestAll(exchange.value)
    const data = confirmed(res)
    if (data) {
      summary.value = data
      await loadReports()
    } else {
      runError.value = 'Watchlist backtest failed.'
    }
  } finally {
    runningAll.value = false
  }
}

const loadWatchlist = async () => {
  watchlistLoading.value = true
  watchlistError.value = false
  try {
    watchlist.value = await getWatchlist()
    if (!selectedWatchlistSymbol.value) {
      const first = activeWatchlist.value[0]
      if (first) selectSymbol(first)
    }
  } catch {
    watchlistError.value = true
  } finally {
    watchlistLoading.value = false
  }
}

const loadReports = async () => {
  const requestId = ++reportRequestId
  reportsError.value = false
  try {
    const res = await listBacktestReports()
    if (requestId !== reportRequestId) return
    const data = confirmed(res)
    if (data) reports.value = data
    else reportsError.value = true
  } catch {
    if (requestId === reportRequestId) reportsError.value = true
  } finally {
    if (requestId === reportRequestId) reportsLoaded.value = true
  }
}

const viewReport = async (filename: string) => {
  const requestId = ++reportRequestId
  try {
    const res = await getBacktestReport(filename)
    if (requestId !== reportRequestId) return
    const data = confirmed(res)
    if (data) {
      summary.value = data
      result.value = null
    }
  } catch {
    // Keep the last confirmed report visible when a newer report cannot load.
  }
}

onMounted(() => {
  document.addEventListener('click', handleDocumentClick)
  loadWatchlist()
  loadReports()
})

onUnmounted(() => {
  document.removeEventListener('click', handleDocumentClick)
})
</script>
