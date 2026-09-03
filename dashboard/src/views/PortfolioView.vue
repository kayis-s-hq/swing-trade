<template>
  <div class="view-shell p-6 animate-fade-in">
    <!-- Page Header -->
    <div class="mb-6 flex items-center justify-between">
      <div>
        <h1 class="font-display text-2xl font-semibold text-text-primary">Portfolio</h1>
        <p class="mt-1 text-sm text-text-muted">Performance analytics and trade history</p>
      </div>
      <button
        class="flex items-center gap-2 rounded-md border border-border-subtle bg-bg-surface px-3 py-2 text-sm font-medium text-text-muted transition-colors hover:border-border-default hover:text-text-primary"
        @click="refreshPortfolio"
      >
        <svg
          class="h-4 w-4 transition-transform duration-300 hover:rotate-180"
          fill="none"
          stroke="currentColor"
          viewBox="0 0 24 24"
        >
          <path
            stroke-linecap="round"
            stroke-linejoin="round"
            stroke-width="2"
            d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15"
          />
        </svg>
        Refresh
      </button>
    </div>

    <ErrorBoundary :error="false">
      <div v-if="loading" class="flex items-center justify-center py-20">
        <LoadingSpinner message="Loading portfolio data..." />
      </div>

      <template v-else>
        <PerformanceMetrics
          :portfolio-summary="portfolioSummary ?? undefined"
          :equity-points="equityPoints"
          @range-change="handleRangeChange"
        />
        <ErrorMessage
          v-if="performanceError"
          title="Portfolio refresh failed"
          message="Showing the last successful performance snapshot."
          action-label="Retry"
          @action="refreshPerformance"
        />
        <p v-if="lastPerformanceUpdate" class="mt-2 text-xs text-text-muted">
          Last updated
          <time :datetime="lastPerformanceUpdate.toISOString()">
            {{ lastPerformanceUpdate.toLocaleTimeString() }}
          </time>
        </p>

        <!-- Trade History -->
        <div class="mt-6 card-panel">
          <div class="flex items-center justify-between border-b border-border-subtle px-5 py-3">
            <div>
              <h2 class="text-sm font-semibold text-text-primary">Trade History</h2>
              <p v-if="!tradeHistoryError" class="text-xs text-text-muted">
                {{ recentTrades.length }} records
              </p>
            </div>
            <select
              v-model="timeRange"
              class="rounded-md border border-border-subtle bg-bg-surface px-3 py-1.5 text-xs font-medium text-text-primary transition-colors focus:border-brand/50 focus:outline-none"
            >
              <option value="1W">1 Week</option>
              <option value="1M">1 Month</option>
              <option value="3M">3 Months</option>
              <option value="6M">6 Months</option>
              <option value="1Y">1 Year</option>
              <option value="ALL">All Time</option>
            </select>
          </div>
          <ErrorMessage
            v-if="tradeHistoryError"
            title="Couldn’t load trade history"
            message="Trade history is temporarily unavailable."
            action-label="Retry"
            @action="loadTradeHistory"
          />
          <div v-else class="w-full overflow-x-auto">
            <table class="min-w-full">
              <thead>
                <tr class="border-b border-border-subtle bg-bg-primary/50">
                  <th
                    class="px-5 py-3 text-left text-xs font-semibold uppercase tracking-wider text-text-muted"
                  >
                    Date
                  </th>
                  <th
                    class="px-5 py-3 text-left text-xs font-semibold uppercase tracking-wider text-text-muted"
                  >
                    Symbol
                  </th>
                  <th
                    class="px-5 py-3 text-left text-xs font-semibold uppercase tracking-wider text-text-muted"
                  >
                    Type
                  </th>
                  <th
                    class="px-5 py-3 text-left text-xs font-semibold uppercase tracking-wider text-text-muted"
                  >
                    Entry
                  </th>
                  <th
                    class="px-5 py-3 text-left text-xs font-semibold uppercase tracking-wider text-text-muted"
                  >
                    Exit
                  </th>
                  <th
                    class="px-5 py-3 text-right text-xs font-semibold uppercase tracking-wider text-text-muted"
                  >
                    P&L
                  </th>
                  <th
                    class="px-5 py-3 text-left text-xs font-semibold uppercase tracking-wider text-text-muted"
                  >
                    Duration
                  </th>
                </tr>
              </thead>
              <tbody class="divide-y divide-border-subtle/50">
                <tr
                  v-for="trade in recentTrades"
                  :key="trade.id"
                  class="transition-colors hover:bg-bg-hover"
                >
                  <td class="px-5 py-4 text-sm text-text-secondary">
                    {{ trade.entryDate }}
                  </td>
                  <td class="px-5 py-4 text-sm font-semibold text-text-primary">
                    {{ trade.symbol }}
                  </td>
                  <td class="px-5 py-4">
                    <span
                      class="inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium"
                      :class="
                        trade.status === 'OPEN'
                          ? 'bg-success-bg text-success'
                          : 'bg-danger-bg text-danger'
                      "
                      >{{ trade.status }}</span
                    >
                  </td>
                  <td class="px-5 py-4 text-sm text-text-secondary">₹{{ trade.entryPrice }}</td>
                  <td class="px-5 py-4 text-sm text-text-secondary">
                    {{ trade.currentPrice ? 'Rs.' + trade.currentPrice : '—' }}
                  </td>
                  <td
                    class="px-5 py-4 text-right text-sm font-semibold"
                    :class="trade.pnl >= 0 ? 'text-success' : 'text-danger'"
                  >
                    {{ trade.pnl >= 0 ? '+' : '' }}₹{{ trade.pnl }}
                  </td>
                  <td class="px-5 py-4 text-sm text-text-muted">
                    {{ tradeDuration(trade.entryDate) }}
                  </td>
                </tr>
                <tr v-if="recentTrades.length === 0">
                  <td colspan="7" class="px-5 py-12 text-center text-sm text-text-muted">
                    No trade history
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </template>
    </ErrorBoundary>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { getPortfolioSummary, getTradeHistory, getEquityCurve } from '../api/client'
import type { PortfolioSummary, Position, EquityPoint } from '../api/types'
import PerformanceMetrics from '../components/PerformanceMetrics.vue'
import LoadingSpinner from '../components/LoadingSpinner.vue'
import ErrorBoundary from '../components/ErrorBoundary.vue'
import ErrorMessage from '../components/ErrorMessage.vue'
import { asAppError, type AppError } from '../errors/appError'

const loading = ref(true)
const performanceError = ref<AppError | null>(null)
const tradeHistoryError = ref<AppError | null>(null)
const lastPerformanceUpdate = ref<Date | null>(null)
const portfolioSummary = ref<PortfolioSummary | null>(null)
const recentTrades = ref<Position[]>([])
const equityPoints = ref<EquityPoint[]>([])
const timeRange = ref('1M')

const tradeDuration = (entryDate: string): string => {
  const days = Math.round((Date.now() - new Date(entryDate).getTime()) / 86400000)
  return `${days}d`
}

const refreshPerformance = async () => {
  performanceError.value = null
  try {
    const [summaryValue, equityValue] = await Promise.all([
      getPortfolioSummary(),
      getEquityCurve(timeRange.value),
    ])
    const nextSummary = summaryValue
    const nextEquity = equityValue
    portfolioSummary.value = nextSummary
    equityPoints.value = nextEquity.data
    lastPerformanceUpdate.value = new Date()
  } catch (cause) {
    performanceError.value = asAppError(cause)
  }
}

const loadTradeHistory = async () => {
  tradeHistoryError.value = null
  try {
    recentTrades.value = await getTradeHistory(10)
  } catch (cause) {
    tradeHistoryError.value = asAppError(cause)
  }
}

const handleRangeChange = async (range: string) => {
  timeRange.value = range
  await refreshPerformance()
}

const refreshPortfolio = async () => {
  loading.value = portfolioSummary.value === null && recentTrades.value.length === 0
  await Promise.all([refreshPerformance(), loadTradeHistory()])
  loading.value = false
}

let refreshTimer: ReturnType<typeof setInterval> | undefined

onMounted(() => {
  refreshPortfolio()
  refreshTimer = setInterval(() => void refreshPortfolio(), 60_000)
})

onUnmounted(() => {
  if (refreshTimer) clearInterval(refreshTimer)
})
</script>
