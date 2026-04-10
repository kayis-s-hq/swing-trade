<template>
  <div class="p-4">
    <!-- Header -->
    <div class="mb-6 flex items-center justify-between">
      <h1 class="text-2xl font-bold text-gray-800 dark:text-white">Dashboard</h1>
      <button
        @click="refreshData"
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
        <p class="mt-2 text-sm text-gray-500 dark:text-gray-400">Loading data...</p>
      </div>
    </div>

    <!-- Error State -->
    <div v-else-if="error" class="rounded-2xl border border-error-200 bg-error-50 p-6 dark:border-error-900 dark:bg-error-900/20">
      <div class="flex items-center gap-3">
        <svg class="h-8 w-8 text-error-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
        </svg>
        <div>
          <h3 class="text-lg font-medium text-error-800 dark:text-error-200">Error Loading Data</h3>
          <p class="text-sm text-error-600 dark:text-error-300">{{ errorMessage }}</p>
          <button
            @click="refreshData"
            class="mt-2 rounded bg-error-600 px-3 py-1 text-sm font-medium text-white hover:bg-error-700"
          >
            Retry
          </button>
        </div>
      </div>
    </div>

    <!-- Dashboard Content -->
    <div v-else>
      <!-- Metrics Grid -->
      <div class="grid grid-cols-1 gap-6 md:grid-cols-2 xl:grid-cols-3">
        <MetricCard
          title="Total Positions"
          :value="marketOverview?.totalPositions ?? 0"
          icon="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2m-3 7h3m-3 4h3m-6-4h.01M9 16h.01"
        />

        <MetricCard
          title="Total Value"
          :value="`$${(marketOverview?.totalValue ?? 0).toLocaleString()}`"
          icon="M12 8c-1.657 0-3 .895-3 2s1.343 2 3 2 3 .895 3 2-1.343 2-3 2m0-8c1.11 0 2.08.402 2.599 1M12 8V7m0 1v8m0 0v1m0-1c-1.11 0-2.08-.402-2.599-1M21 12a9 9 0 11-18 0 9 9 0 0118 0z"
        />

        <MetricCard
          title="Today's P&L"
          :value="`$${(marketOverview?.todayPnl ?? 0).toLocaleString()}`"
          :trend="{
            value: `${(portfolioSummary?.totalPnlPercent ?? 0).toFixed(2)}%`,
            isPositive: portfolioSummary?.totalPnlPercent && portfolioSummary.totalPnlPercent >= 0,
          }"
          icon="M13 7h8m0 0v8m0-8l-8 8-4-4-6 6"
        />

        <MetricCard
          title="Win Rate"
          :value="`${portfolioSummary?.winRate ?? 0}%`"
          icon="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"
        />

        <MetricCard
          title="Total Trades"
          :value="portfolioSummary?.totalTrades ?? 0"
          icon="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z"
        />

        <MetricCard
          title="Total P&L"
          :value="`$${(portfolioSummary?.totalPnl ?? 0).toLocaleString()}`"
          :trend="{
            value: `${(portfolioSummary?.totalPnlPercent ?? 0).toFixed(2)}%`,
            isPositive: portfolioSummary?.totalPnlPercent && portfolioSummary.totalPnlPercent >= 0,
          }"
          icon="M8 7h12m0 0l-4-4m4 4l-4 4m0 6H4m0 0l4 4m-4-4l4-4"
        />
      </div>

      <!-- Recent Positions Table -->
      <div class="mt-6 overflow-hidden rounded-2xl border border-gray-200 bg-white dark:border-gray-800 dark:bg-white/[0.03]">
        <div class="flex flex-col gap-2 p-4 sm:flex-row sm:items-center sm:justify-between">
          <h3 class="text-lg font-semibold text-gray-800 dark:text-white/90">Recent Positions</h3>
          <router-link
            to="/positions"
            class="text-sm text-indigo-600 hover:text-indigo-800 dark:text-indigo-400 dark:hover:text-indigo-300"
          >
            View All
          </router-link>
        </div>
        <div class="w-full overflow-x-auto">
          <table class="min-w-full">
            <thead class="border-gray-100 border-y dark:border-gray-800">
              <tr>
                <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Symbol</th>
                <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Entry Price</th>
                <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Quantity</th>
                <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Status</th>
                <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">P&L</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-gray-100 dark:divide-gray-800">
              <tr
                v-for="position in recentPositions"
                :key="position.id"
                class="hover:bg-gray-50 dark:hover:bg-white/[0.02]"
              >
                <td class="px-4 py-3 text-sm text-gray-700 dark:text-gray-300">{{ position.symbol }}</td>
                <td class="px-4 py-3 text-sm text-gray-700 dark:text-gray-300">${{ position.entryPrice }}</td>
                <td class="px-4 py-3 text-sm text-gray-700 dark:text-gray-300">{{ position.quantity }}</td>
                <td class="px-4 py-3">
                  <span
                    class="inline-flex items-center rounded-full bg-success-50 px-2.5 py-0.5 text-xs font-medium text-success-600 dark:bg-success-500/15 dark:text-success-500"
                    v-if="position.status === 'OPEN'"
                    >Open</span
                  >
                  <span
                    class="inline-flex items-center rounded-full bg-warning-50 px-2.5 py-0.5 text-xs font-medium text-warning-600 dark:bg-warning-500/15 dark:text-orange-400"
                    v-else-if="position.status === 'STOPPED'"
                    >Stopped</span
                  >
                  <span
                    class="inline-flex items-center rounded-full bg-error-50 px-2.5 py-0.5 text-xs font-medium text-error-600 dark:bg-error-500/15 dark:text-error-500"
                    v-else
                    >Closed</span
                  >
                </td>
                <td
                  class="px-4 py-3 text-sm font-medium"
                  :class="position.pnl >= 0 ? 'text-success-600' : 'text-error-600'"
                >
                  ${{ position.pnl }} ({{ position.pnlPercent }}%)
                </td>
              </tr>
              <tr v-if="recentPositions.length === 0">
                <td colspan="5" class="px-4 py-8 text-center text-gray-500 dark:text-gray-400">
                  No recent positions
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
import { getMarketOverview, getPortfolioSummary, getPositionList } from '../api/client'
import type { MarketOverview, PortfolioSummary, Position } from '../api/types'
import MetricCard from '../components/MetricCard.vue'

const loading = ref(true)
const error = ref(false)
const errorMessage = ref('')

const marketOverview = ref<MarketOverview | null>(null)
const portfolioSummary = ref<PortfolioSummary | null>(null)
const recentPositions = ref<Position[]>([])

const refreshData = async () => {
  loading.value = true
  error.value = false
  errorMessage.value = ''

  try {
    const [marketRes, portfolioRes, positionsRes] = await Promise.all([
      getMarketOverview(),
      getPortfolioSummary(),
      getPositionList(),
    ])

    if (marketRes.success && marketRes.data) {
      marketOverview.value = marketRes.data
    } else if (marketRes.error) {
      throw new Error(marketRes.error)
    }

    if (portfolioRes.success && portfolioRes.data) {
      portfolioSummary.value = portfolioRes.data
    } else if (portfolioRes.error) {
      throw new Error(portfolioRes.error)
    }

    if (positionsRes.success && positionsRes.data) {
      recentPositions.value = positionsRes.data.slice(0, 5)
    } else if (positionsRes.error) {
      throw new Error(positionsRes.error)
    }
  } catch (err: unknown) {
    const errorMsg = err instanceof Error ? err.message : 'Failed to load dashboard data'
    errorMessage.value = errorMsg
    error.value = true
    console.error('Dashboard error:', err)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  refreshData()
})
</script>
