<template>
  <div class="p-6 animate-fade-in">
    <!-- Page Header -->
    <div class="mb-6 flex items-center justify-between">
      <div>
        <h1 class="font-display text-2xl font-semibold text-text-primary">Portfolio</h1>
        <p class="mt-1 text-sm text-text-muted">Performance analytics and trade history</p>
      </div>
      <button @click="refreshPortfolio" class="flex items-center gap-2 rounded-md border border-border-subtle bg-bg-surface px-3 py-2 text-sm font-medium text-text-muted transition-colors hover:border-border-default hover:text-text-primary">
        <svg class="h-4 w-4 transition-transform duration-300 hover:rotate-180" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
        </svg>
        Refresh
      </button>
    </div>

    <div v-if="loading" class="flex items-center justify-center py-20">
      <LoadingSpinner message="Loading portfolio data..." />
    </div>

    <ErrorMessage v-else-if="error" :message="errorMessage" :showRetry="true" retryText="Retry" @retry="refreshPortfolio" />

    <template v-else>
      <PerformanceMetrics :portfolioSummary="portfolioSummary ?? undefined" :equityPoints="equityPoints" @rangeChange="handleRangeChange" />

      <!-- Trade History -->
      <div class="mt-6 card-panel">
        <div class="flex items-center justify-between border-b border-border-subtle px-5 py-3">
          <div>
            <h2 class="text-sm font-semibold text-text-primary">Trade History</h2>
            <p class="text-xs text-text-muted">{{ recentTrades.length }} records</p>
          </div>
          <select v-model="timeRange" class="rounded-md border border-border-subtle bg-bg-surface px-3 py-1.5 text-xs font-medium text-text-primary transition-colors focus:border-brand/50 focus:outline-none">
            <option value="1W">1 Week</option>
            <option value="1M">1 Month</option>
            <option value="3M">3 Months</option>
            <option value="6M">6 Months</option>
            <option value="1Y">1 Year</option>
            <option value="ALL">All Time</option>
          </select>
        </div>
        <div class="w-full overflow-x-auto">
          <table class="min-w-full">
            <thead>
              <tr class="border-b border-border-subtle bg-bg-primary/50">
                <th class="px-5 py-3 text-left text-xs font-semibold uppercase tracking-wider text-text-muted">Date</th>
                <th class="px-5 py-3 text-left text-xs font-semibold uppercase tracking-wider text-text-muted">Symbol</th>
                <th class="px-5 py-3 text-left text-xs font-semibold uppercase tracking-wider text-text-muted">Type</th>
                <th class="px-5 py-3 text-left text-xs font-semibold uppercase tracking-wider text-text-muted">Entry</th>
                <th class="px-5 py-3 text-left text-xs font-semibold uppercase tracking-wider text-text-muted">Exit</th>
                <th class="px-5 py-3 text-right text-xs font-semibold uppercase tracking-wider text-text-muted">P&L</th>
                <th class="px-5 py-3 text-left text-xs font-semibold uppercase tracking-wider text-text-muted">Duration</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-border-subtle/50">
              <tr v-for="trade in recentTrades" :key="trade.id" class="transition-colors hover:bg-bg-hover">
                <td class="px-5 py-4 text-sm text-text-secondary">{{ trade.entryDate }}</td>
                <td class="px-5 py-4 text-sm font-semibold text-text-primary">{{ trade.symbol }}</td>
                <td class="px-5 py-4">
                  <span class="inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium" :class="trade.status === 'OPEN' ? 'bg-success-bg text-success' : 'bg-danger-bg text-danger'">{{ trade.status }}</span>
                </td>
                <td class="px-5 py-4 text-sm text-text-secondary">₹{{ trade.entryPrice }}</td>
                <td class="px-5 py-4 text-sm text-text-secondary">{{ trade.currentPrice ? '$' + trade.currentPrice : '—' }}</td>
                <td class="px-5 py-4 text-right text-sm font-semibold" :class="trade.pnl >= 0 ? 'text-success' : 'text-danger'">{{ trade.pnl >= 0 ? '+' : '' }}₹{{ trade.pnl }}</td>
                <td class="px-5 py-4 text-sm text-text-muted">{{ tradeDuration(trade.entryDate) }}</td>
              </tr>
              <tr v-if="recentTrades.length === 0">
                <td colspan="7" class="px-5 py-12 text-center text-sm text-text-muted">No trade history</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getPortfolioSummary, getTradeHistory, getEquityCurve } from '../api/client'
import type { PortfolioSummary, Position, EquityPoint } from '../api/types'
import PerformanceMetrics from '../components/PerformanceMetrics.vue'
import ErrorMessage from '../components/ErrorMessage.vue'
import LoadingSpinner from '../components/LoadingSpinner.vue'

const loading = ref(true)
const error = ref(false)
const errorMessage = ref('')
const portfolioSummary = ref<PortfolioSummary | null>(null)
const recentTrades = ref<Position[]>([])
const equityPoints = ref<EquityPoint[]>([])
const timeRange = ref('1M')

const tradeDuration = (entryDate: string): string => {
  const days = Math.round((Date.now() - new Date(entryDate).getTime()) / 86400000)
  return `${days}d`
}

const handleRangeChange = async (range: string) => {
  timeRange.value = range
  const res = await getEquityCurve(range)
  if (res.success && res.data) equityPoints.value = res.data.data
}

const refreshPortfolio = async () => {
  loading.value = true
  error.value = false
  errorMessage.value = ''
  try {
    const [summaryRes, tradesRes, equityRes] = await Promise.all([getPortfolioSummary(), getTradeHistory(10), getEquityCurve(timeRange.value)])
    if (summaryRes.success && summaryRes.data) portfolioSummary.value = summaryRes.data
    if (tradesRes.success && tradesRes.data) recentTrades.value = tradesRes.data
    if (equityRes.success && equityRes.data) equityPoints.value = equityRes.data.data
    if (summaryRes.error || tradesRes.error || equityRes.error) throw new Error(summaryRes.error ?? tradesRes.error ?? equityRes.error)
  } catch (err: unknown) {
    errorMessage.value = err instanceof Error ? err.message : 'Failed to load portfolio data'
    error.value = true
  } finally {
    loading.value = false
  }
}

onMounted(() => { refreshPortfolio() })
</script>
