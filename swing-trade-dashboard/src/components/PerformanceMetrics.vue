<script setup lang="ts">
export interface PerformanceMetricsProps {
  portfolioSummary?: {
    totalValue: number
    totalPnl: number
    totalPnlPercent: number
    winRate: number
    totalTrades: number
    averageWin: number
    averageLoss: number
    profitFactor: number
    maxDrawdown: number
    sharpeRatio: number
  }
}

const props = defineProps<PerformanceMetricsProps>()
</script>

<template>
  <div class="space-y-6">
    <!-- Portfolio Summary Cards -->
    <div class="grid grid-cols-2 gap-4 sm:grid-cols-4">
      <!-- Total Value -->
      <div class="rounded-2xl border border-gray-200 bg-white p-4 dark:border-gray-800 dark:bg-white/[0.03]">
        <span class="text-xs font-medium text-gray-500 dark:text-gray-400">Total Value</span>
        <p class="mt-1 text-xl font-bold text-gray-800 dark:text-white/90">
          ${{ portfolioSummary?.totalValue.toLocaleString() ?? 0 }}
        </p>
      </div>

      <!-- Total P&L -->
      <div class="rounded-2xl border border-gray-200 bg-white p-4 dark:border-gray-800 dark:bg-white/[0.03]">
        <span class="text-xs font-medium text-gray-500 dark:text-gray-400">Total P&L</span>
        <p
          :class="
            portfolioSummary?.totalPnlPercent && portfolioSummary.totalPnlPercent >= 0
              ? 'text-success-600'
              : 'text-error-600'
          "
          class="mt-1 text-xl font-bold"
        >
          ${{ portfolioSummary?.totalPnl.toLocaleString() ?? 0 }}
          <span class="text-sm font-normal opacity-70"
            >({{ portfolioSummary?.totalPnlPercent.toLocaleString(undefined, { maximumFractionDigits: 2 }) ?? 0 }}%)</span
          >
        </p>
      </div>

      <!-- Win Rate -->
      <div class="rounded-2xl border border-gray-200 bg-white p-4 dark:border-gray-800 dark:bg-white/[0.03]">
        <span class="text-xs font-medium text-gray-500 dark:text-gray-400">Win Rate</span>
        <p class="mt-1 text-xl font-bold text-gray-800 dark:text-white/90">
          {{ portfolioSummary?.winRate.toLocaleString() ?? 0 }}%
        </p>
      </div>

      <!-- Total Trades -->
      <div class="rounded-2xl border border-gray-200 bg-white p-4 dark:border-gray-800 dark:bg-white/[0.03]">
        <span class="text-xs font-medium text-gray-500 dark:text-gray-400">Total Trades</span>
        <p class="mt-1 text-xl font-bold text-gray-800 dark:text-white/90">
          {{ portfolioSummary?.totalTrades.toLocaleString() ?? 0 }}
        </p>
      </div>
    </div>

    <!-- Secondary Metrics -->
    <div class="grid grid-cols-2 gap-4 sm:grid-cols-3">
      <!-- Profit Factor -->
      <div class="rounded-2xl border border-gray-200 bg-white p-4 dark:border-gray-800 dark:bg-white/[0.03]">
        <span class="text-xs font-medium text-gray-500 dark:text-gray-400">Profit Factor</span>
        <p class="mt-1 text-lg font-bold text-gray-800 dark:text-white/90">
          {{ portfolioSummary?.profitFactor.toLocaleString(undefined, { maximumFractionDigits: 2 }) ?? 0 }}
        </p>
      </div>

      <!-- Average Win -->
      <div class="rounded-2xl border border-gray-200 bg-white p-4 dark:border-gray-800 dark:bg-white/[0.03]">
        <span class="text-xs font-medium text-gray-500 dark:text-gray-400">Avg Win</span>
        <p class="mt-1 text-lg font-bold text-success-600">
          ${{ portfolioSummary?.averageWin.toLocaleString() ?? 0 }}
        </p>
      </div>

      <!-- Average Loss -->
      <div class="rounded-2xl border border-gray-200 bg-white p-4 dark:border-gray-800 dark:bg-white/[0.03]">
        <span class="text-xs font-medium text-gray-500 dark:text-gray-400">Avg Loss</span>
        <p class="mt-1 text-lg font-bold text-error-600">
          ${{ portfolioSummary?.averageLoss.toLocaleString() ?? 0 }}
        </p>
      </div>
    </div>

    <!-- Equity Curve Placeholder -->
    <div class="rounded-2xl border border-gray-200 bg-white p-6 dark:border-gray-800 dark:bg-white/[0.03]">
      <div class="mb-4 flex items-center justify-between">
        <h4 class="text-sm font-medium text-gray-700 dark:text-gray-300">Equity Curve</h4>
        <div class="flex gap-1">
          <button
            v-for="range in ['1W', '1M', '3M', '6M', '1Y', 'ALL']"
            :key="range"
            class="rounded px-2 py-1 text-xs font-medium text-gray-600 hover:bg-gray-100 dark:text-gray-400 dark:hover:bg-gray-800"
          >
            {{ range }}
          </button>
        </div>
      </div>
      <div class="flex h-64 w-full items-center justify-center rounded-xl border-2 border-dashed border-gray-300 dark:border-gray-700">
        <div class="text-center">
          <svg class="mx-auto h-12 w-12 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path
              stroke-linecap="round"
              stroke-linejoin="round"
              stroke-width="2"
              d="M7 12l3-3 3 3 4-4M8 21l4-4 4 4M3 4h18M4 4h16v12a1 1 0 01-1 1H5a1 1 0 01-1-1V4z"
            />
          </svg>
          <p class="mt-2 text-sm text-gray-500 dark:text-gray-400">Equity Curve Chart</p>
          <p class="mt-1 text-xs text-gray-400 dark:text-gray-500">Data will be displayed when available</p>
        </div>
      </div>
    </div>
  </div>
</template>
