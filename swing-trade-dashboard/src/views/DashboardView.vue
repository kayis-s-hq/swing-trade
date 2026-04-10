<template>
  <div class="p-4">
    <h1 class="text-2xl font-bold text-gray-800 dark:text-white mb-6">Dashboard</h1>

    <!-- Metrics Grid -->
    <div class="grid grid-cols-1 gap-6 md:grid-cols-2 xl:grid-cols-3">
      <!-- Total Positions -->
      <div class="rounded-2xl border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-white/[0.03]">
        <div class="flex h-12 w-12 items-center justify-center rounded-xl bg-gray-100 dark:bg-gray-800">
          <svg class="h-6 w-6 text-gray-800 dark:text-white/90" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2m-3 7h3m-3 4h3m-6-4h.01M9 16h.01" />
          </svg>
        </div>
        <div class="mt-5 flex items-end justify-between">
          <div>
            <span class="text-sm text-gray-500 dark:text-gray-400">Total Positions</span>
            <h4 class="mt-2 text-title-sm font-bold text-gray-800 dark:text-white/90">{{ marketOverview?.totalPositions ?? 0 }}</h4>
          </div>
        </div>
      </div>

      <!-- Total Value -->
      <div class="rounded-2xl border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-white/[0.03]">
        <div class="flex h-12 w-12 items-center justify-center rounded-xl bg-gray-100 dark:bg-gray-800">
          <svg class="h-6 w-6 text-gray-800 dark:text-white/90" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8c-1.657 0-3 .895-3 2s1.343 2 3 2 3 .895 3 2-1.343 2-3 2m0-8c1.11 0 2.08.402 2.599 1M12 8V7m0 1v8m0 0v1m0-1c-1.11 0-2.08-.402-2.599-1M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
        </div>
        <div class="mt-5 flex items-end justify-between">
          <div>
            <span class="text-sm text-gray-500 dark:text-gray-400">Total Value</span>
            <h4 class="mt-2 text-title-sm font-bold text-gray-800 dark:text-white/90">${{ marketOverview?.totalValue ?? 0 }}</h4>
          </div>
        </div>
      </div>

      <!-- Today's P&L -->
      <div class="rounded-2xl border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-white/[0.03]">
        <div class="flex h-12 w-12 items-center justify-center rounded-xl bg-gray-100 dark:bg-gray-800">
          <svg class="h-6 w-6 text-gray-800 dark:text-white/90" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 7h8m0 0v8m0-8l-8 8-4-4-6 6" />
          </svg>
        </div>
        <div class="mt-5 flex items-end justify-between">
          <div>
            <span class="text-sm text-gray-500 dark:text-gray-400">Today's P&L</span>
            <h4 class="mt-2 text-title-sm font-bold text-gray-800 dark:text-white/90">${{ marketOverview?.todayPnl ?? 0 }}</h4>
          </div>
        </div>
      </div>

      <!-- Win Rate -->
      <div class="rounded-2xl border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-white/[0.03]">
        <div class="flex h-12 w-12 items-center justify-center rounded-xl bg-gray-100 dark:bg-gray-800">
          <svg class="h-6 w-6 text-gray-800 dark:text-white/90" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
        </div>
        <div class="mt-5 flex items-end justify-between">
          <div>
            <span class="text-sm text-gray-500 dark:text-gray-400">Win Rate</span>
            <h4 class="mt-2 text-title-sm font-bold text-gray-800 dark:text-white/90">{{ portfolioSummary?.winRate ?? 0 }}%</h4>
          </div>
        </div>
      </div>

      <!-- Total Trades -->
      <div class="rounded-2xl border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-white/[0.03]">
        <div class="flex h-12 w-12 items-center justify-center rounded-xl bg-gray-100 dark:bg-gray-800">
          <svg class="h-6 w-6 text-gray-800 dark:text-white/90" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
        </div>
        <div class="mt-5 flex items-end justify-between">
          <div>
            <span class="text-sm text-gray-500 dark:text-gray-400">Total Trades</span>
            <h4 class="mt-2 text-title-sm font-bold text-gray-800 dark:text-white/90">{{ portfolioSummary?.totalTrades ?? 0 }}</h4>
          </div>
        </div>
      </div>

      <!-- Total P&L -->
      <div class="rounded-2xl border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-white/[0.03]">
        <div class="flex h-12 w-12 items-center justify-center rounded-xl bg-gray-100 dark:bg-gray-800">
          <svg class="h-6 w-6 text-gray-800 dark:text-white/90" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 7h12m0 0l-4-4m4 4l-4 4m0 6H4m0 0l4 4m-4-4l4-4" />
          </svg>
        </div>
        <div class="mt-5 flex items-end justify-between">
          <div>
            <span class="text-sm text-gray-500 dark:text-gray-400">Total P&L</span>
            <h4 class="mt-2 text-title-sm font-bold text-gray-800 dark:text-white/90">${{ portfolioSummary?.totalPnl ?? 0 }}</h4>
          </div>
        </div>
      </div>
    </div>

    <!-- Recent Positions Table -->
    <div class="mt-6 overflow-hidden rounded-2xl border border-gray-200 bg-white dark:border-gray-800 dark:bg-white/[0.03]">
      <div class="flex flex-col gap-2 mb-4 p-4 sm:flex-row sm:items-center sm:justify-between">
        <h3 class="text-lg font-semibold text-gray-800 dark:text-white/90">Recent Positions</h3>
        <router-link to="/positions" class="text-sm text-indigo-600 hover:text-indigo-800 dark:text-indigo-400 dark:hover:text-indigo-300">View All</router-link>
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
            <tr v-for="position in recentPositions" :key="position.id" class="hover:bg-gray-50 dark:hover:bg-white/[0.02]">
              <td class="px-4 py-3 text-sm text-gray-700 dark:text-gray-300">{{ position.symbol }}</td>
              <td class="px-4 py-3 text-sm text-gray-700 dark:text-gray-300">${{ position.entryPrice }}</td>
              <td class="px-4 py-3 text-sm text-gray-700 dark:text-gray-300">{{ position.quantity }}</td>
              <td class="px-4 py-3">
                <span class="inline-flex items-center rounded-full bg-success-50 px-2.5 py-0.5 text-xs font-medium text-success-600 dark:bg-success-500/15 dark:text-success-500" v-if="position.status === 'OPEN'">Open</span>
                <span class="inline-flex items-center rounded-full bg-warning-50 px-2.5 py-0.5 text-xs font-medium text-warning-600 dark:bg-warning-500/15 dark:text-orange-400" v-else-if="position.status === 'STOPPED'">Stopped</span>
                <span class="inline-flex items-center rounded-full bg-error-50 px-2.5 py-0.5 text-xs font-medium text-error-600 dark:bg-error-500/15 dark:text-error-500" v-else>Closed</span>
              </td>
              <td class="px-4 py-3 text-sm font-medium" :class="position.pnl >= 0 ? 'text-success-600' : 'text-error-600'">
                ${{ position.pnl }} ({{ position.pnlPercent }}%)
              </td>
            </tr>
            <tr v-if="recentPositions.length === 0">
              <td colspan="5" class="px-4 py-8 text-center text-gray-500 dark:text-gray-400">No recent positions</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getMarketOverview, getPortfolioSummary, getPositionList } from '../api/client'
import type { MarketOverview, PortfolioSummary, Position } from '../api/types'

const marketOverview = ref<MarketOverview | null>(null)
const portfolioSummary = ref<PortfolioSummary | null>(null)
const recentPositions = ref<Position[]>([])

onMounted(async () => {
  try {
    const [marketRes, portfolioRes, positionsRes] = await Promise.all([
      getMarketOverview(),
      getPortfolioSummary(),
      getPositionList(),
    ])

    if (marketRes.success && marketRes.data) {
      marketOverview.value = marketRes.data
    }

    if (portfolioRes.success && portfolioRes.data) {
      portfolioSummary.value = portfolioRes.data
    }

    if (positionsRes.success && positionsRes.data) {
      recentPositions.value = positionsRes.data.slice(0, 5)
    }
  } catch (error) {
    console.error('Failed to load dashboard data:', error)
  }
})
</script>
