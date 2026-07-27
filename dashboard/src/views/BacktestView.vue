<template>
  <div class="p-6 animate-fade-in">
    <!-- Page Header -->
    <div class="mb-6 flex items-center justify-between">
      <div>
        <h1 class="font-display text-2xl font-semibold text-text-primary">Backtest</h1>
        <p class="mt-1 text-sm text-text-muted">
          Replay the price-action strategy against historical candles
        </p>
      </div>
    </div>

    <!-- Run Form -->
    <div class="mb-6 card-panel p-5">
      <h3 class="mb-3 text-sm font-semibold text-text-primary">Run Backtest</h3>
      <form class="flex flex-col sm:flex-row gap-3" @submit.prevent="handleRunSingle">
        <div class="flex-1">
          <label class="mb-1 block text-xs font-medium text-text-muted">Symbol</label>
          <input
            v-model="symbol"
            type="text"
            placeholder="e.g. RELIANCE"
            required
            class="w-full rounded-md border border-border-subtle bg-bg-primary px-3 py-2 text-sm text-text-primary placeholder:text-text-muted/50 focus:outline-none focus:ring-2 focus:ring-brand/30"
          />
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
      <div v-if="reports.length === 0" class="py-4 text-center text-sm text-text-muted">
        No saved reports yet.
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { runBacktest, runBacktestAll, listBacktestReports, getBacktestReport } from '../api/client'
import type { BacktestResult, BacktestReportSummary } from '../api/types'
import MetricCard from '../components/MetricCard.vue'

const symbol = ref('')
const exchange = ref('NSE')
const running = ref(false)
const runningAll = ref(false)
const runError = ref('')

const result = ref<BacktestResult | null>(null)
const summary = ref<BacktestReportSummary | null>(null)
const reports = ref<string[]>([])

const handleRunSingle = async () => {
  if (!symbol.value.trim()) return
  running.value = true
  runError.value = ''
  result.value = null
  try {
    const res = await runBacktest(symbol.value, exchange.value)
    if (res.success && res.data) {
      result.value = res.data
    } else {
      runError.value = res.error || 'Backtest failed — check the symbol has enough candle history.'
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
    if (res.success && res.data) {
      summary.value = res.data
      await loadReports()
    } else {
      runError.value = res.error || 'Watchlist backtest failed.'
    }
  } finally {
    runningAll.value = false
  }
}

const loadReports = async () => {
  const res = await listBacktestReports()
  if (res.success && res.data) reports.value = res.data
}

const viewReport = async (filename: string) => {
  const res = await getBacktestReport(filename)
  if (res.success && res.data) {
    summary.value = res.data
    result.value = null
  }
}

onMounted(() => {
  loadReports()
})
</script>
