<template>
  <div class="p-4">
    <!-- Header -->
    <div class="mb-6 flex items-center justify-between">
      <h1 class="text-2xl font-bold text-gray-800 dark:text-white">Portfolio</h1>
      <button
        @click="refreshPortfolio"
        class="rounded-lg bg-gray-100 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-200 dark:bg-gray-800 dark:text-gray-300 dark:hover:bg-gray-700"
      >
        Refresh
      </button>
    </div>

    <!-- Loading State -->
    <div v-if="loading" class="flex items-center justify-center py-12">
      <div class="text-center">
        <svg class="mx-auto h-12 w-12 animate-spin text-indigo-600" fill="none" viewBox="0 0 24 24">
          <circle
            class="opacity-25"
            cx="12"
            cy="12"
            r="10"
            stroke="currentColor"
            stroke-width="4"
          />
          <path
            class="opacity-75"
            fill="currentColor"
            d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"
          />
        </svg>
        <p class="mt-2 text-sm text-gray-500 dark:text-gray-400">Loading portfolio data...</p>
      </div>
    </div>

    <!-- Error State -->
    <div v-else-if="error" class="rounded-2xl border border-error-200 bg-error-50 p-6 dark:border-error-900 dark:bg-error-900/20">
      <div class="flex items-center gap-3">
        <svg class="h-8 w-8 text-error-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
        </svg>
        <div>
          <h3 class="text-lg font-medium text-error-800 dark:text-error-200">Error Loading Portfolio</h3>
          <p class="text-sm text-error-600 dark:text-error-300">{{ errorMessage }}</p>
          <button
            @click="refreshPortfolio"
            class="mt-2 rounded bg-error-600 px-3 py-1 text-sm font-medium text-white hover:bg-error-700"
          >
            Retry
          </button>
        </div>
      </div>
    </div>

    <!-- Portfolio Content -->
    <div v-else>
      <!-- Portfolio Summary Cards -->
      <div class="grid grid-cols-2 gap-4 md:grid-cols-4 mb-6">
        <div class="rounded-2xl border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-white/[0.03]">
          <div class="text-sm text-gray-500 dark:text-gray-400">Total P&L</div>
          <div
            class="mt-2 text-2xl font-bold"
            :class="portfolioSummary?.totalPnl && portfolioSummary.totalPnl >= 0 ? 'text-success-600' : 'text-error-600'"
          >
            ${{ portfolioSummary?.totalPnl ?? 0 }}
          </div>
        </div>
        <div class="rounded-2xl border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-white/[0.03]">
          <div class="text-sm text-gray-500 dark:text-gray-400">Win Rate</div>
          <div class="mt-2 text-2xl font-bold text-gray-800 dark:text-white">{{ portfolioSummary?.winRate ?? 0 }}%</div>
        </div>
        <div class="rounded-2xl border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-white/[0.03]">
          <div class="text-sm text-gray-500 dark:text-gray-400">Total Trades</div>
          <div class="mt-2 text-2xl font-bold text-gray-800 dark:text-white">{{ portfolioSummary?.totalTrades ?? 0 }}</div>
        </div>
        <div class="rounded-2xl border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-white/[0.03]">
          <div class="text-sm text-gray-500 dark:text-gray-400">Profit Factor</div>
          <div class="mt-2 text-2xl font-bold text-gray-800 dark:text-white">{{ portfolioSummary?.profitFactor ?? 0 }}</div>
        </div>
      </div>

      <!-- Equity Curve Chart Placeholder -->
      <div class="rounded-2xl border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-white/[0.03] mb-6">
        <h3 class="text-lg font-semibold text-gray-800 dark:text-white mb-4">Equity Curve</h3>
        <div class="h-64 flex items-center justify-center border border-dashed border-gray-300 rounded-lg dark:border-gray-700">
          <div class="text-center">
            <svg class="mx-auto h-12 w-12 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path
                stroke-linecap="round"
                stroke-linejoin="round"
                stroke-width="2"
                d="M7 12l3-3 3 3 4-4M8 21l4-4 4 4M3 4h18M4 4h16v12a1 1 0 01-1 1H5a1 1 0 01-1-1V4z"
              />
            </svg>
            <p class="mt-2 text-gray-500 dark:text-gray-400">Equity Curve Chart</p>
          </div>
        </div>
      </div>

      <!-- Trade History Table -->
      <div class="overflow-hidden rounded-2xl border border-gray-200 bg-white dark:border-gray-800 dark:bg-white/[0.03]">
        <div class="flex flex-col gap-2 p-4 sm:flex-row sm:items-center sm:justify-between">
          <h3 class="text-lg font-semibold text-gray-800 dark:text-white/90">Trade History</h3>
          <div class="flex gap-2">
            <select
              v-model="timeRange"
              class="rounded-lg border border-gray-200 bg-white px-3 py-1.5 text-sm focus:border-indigo-500 focus:outline-none dark:border-gray-700 dark:bg-gray-700 dark:text-white"
            >
              <option value="1W">1 Week</option>
              <option value="1M">1 Month</option>
              <option value="3M">3 Months</option>
              <option value="6M">6 Months</option>
              <option value="1Y">1 Year</option>
              <option value="ALL">All Time</option>
            </select>
          </div>
        </div>
        <div class="w-full overflow-x-auto">
          <table class="min-w-full">
            <thead class="border-gray-100 border-y dark:border-gray-800">
              <tr>
                <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Date</th>
                <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Symbol</th>
                <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Type</th>
                <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Entry</th>
                <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Exit</th>
                <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">P&L</th>
                <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Duration</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-gray-100 dark:divide-gray-800">
              <tr
                v-for="trade in recentTrades"
                :key="trade.id"
                class="hover:bg-gray-50 dark:hover:bg-white/[0.02]"
              >
                <td class="px-4 py-3 text-sm text-gray-700 dark:text-gray-300">{{ trade.entryDate }}</td>
                <td class="px-4 py-3 text-sm font-medium text-gray-700 dark:text-gray-300">{{ trade.symbol }}</td>
                <td class="px-4 py-3">
                  <span
                    class="inline-flex items-center rounded-full bg-success-50 px-2.5 py-0.5 text-xs font-medium text-success-600 dark:bg-success-500/15 dark:text-success-500"
                    v-if="trade.status === 'OPEN'"
                    >Open</span
                  >
                  <span
                    class="inline-flex items-center rounded-full bg-error-50 px-2.5 py-0.5 text-xs font-medium text-error-600 dark:bg-error-500/15 dark:text-error-500"
                    v-else
                    >Closed</span
                  >
                </td>
                <td class="px-4 py-3 text-sm text-gray-700 dark:text-gray-300">${{ trade.entryPrice }}</td>
                <td class="px-4 py-3 text-sm text-gray-700 dark:text-gray-300">{{ trade.currentPrice }}</td>
                <td
                  class="px-4 py-3 text-sm font-medium"
                  :class="trade.pnl >= 0 ? 'text-success-600' : 'text-error-600'"
                >
                  ${{ trade.pnl }}
                </td>
                <td class="px-4 py-3 text-sm text-gray-700 dark:text-gray-300">{{ trade.quantity }} days</td>
              </tr>
              <tr v-if="recentTrades.length === 0">
                <td colspan="7" class="px-4 py-8 text-center text-gray-500 dark:text-gray-400">
                  No trade history yet
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getPortfolioSummary, getTradeHistory } from '../api/client'
import type { PortfolioSummary, Position } from '../api/types'

const loading = ref(true)
const error = ref(false)
const errorMessage = ref('')

const portfolioSummary = ref<PortfolioSummary | null>(null)
const recentTrades = ref<Position[]>([])
const timeRange = ref('1M')

const refreshPortfolio = async () => {
  loading.value = true
  error.value = false
  errorMessage.value = ''

  try {
    const [summaryRes, tradesRes] = await Promise.all([
      getPortfolioSummary(),
      getTradeHistory(10),
    ])

    if (summaryRes.success && summaryRes.data) {
      portfolioSummary.value = summaryRes.data
    } else if (summaryRes.error) {
      throw new Error(summaryRes.error)
    }

    if (tradesRes.success && tradesRes.data) {
      recentTrades.value = tradesRes.data
    } else if (tradesRes.error) {
      throw new Error(tradesRes.error)
    }
  } catch (err: unknown) {
    const errorMsg = err instanceof Error ? err.message : 'Failed to load portfolio data'
    errorMessage.value = errorMsg
    error.value = true
    console.error('PortfolioView error:', err)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  refreshPortfolio()
})
</script>
