<template>
  <div class="p-6 animate-fade-in">
    <!-- Page Header -->
    <div class="mb-6 flex items-center justify-between">
      <div>
        <h1 class="font-display text-2xl font-semibold text-text-primary">Dashboard</h1>
        <p class="mt-1 text-sm text-text-muted">Market overview and active positions</p>
      </div>
      <button
        class="flex items-center gap-2 rounded-md border border-border-subtle bg-bg-surface px-3 py-2 text-sm font-medium text-text-muted transition-colors hover:border-border-default hover:text-text-primary"
        @click="refreshDashboard"
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

    <ErrorBoundary :error="error">
      <template #error>
        <div class="flex flex-col items-center justify-center py-20">
          <p class="text-sm text-danger">{{ errorMessage }}</p>
          <button
            class="mt-2 rounded-md bg-brand px-3 py-1.5 text-xs font-medium text-white"
            @click="refreshDashboard"
          >
            Retry
          </button>
        </div>
      </template>
      <div v-if="loading" class="flex items-center justify-center py-20">
        <LoadingSpinner message="Loading market data..." />
      </div>

      <template v-else>
        <!-- P&L Card -->
        <div class="mb-6 card-panel p-5">
          <div class="flex items-center justify-between">
            <div>
              <p class="text-sm font-medium text-text-muted">Today's P&L</p>
              <div class="mt-2 flex items-baseline gap-3">
                <span
                  class="text-3xl font-bold tracking-tight"
                  :class="(marketOverview?.todayPnl ?? 0) >= 0 ? 'text-success' : 'text-danger'"
                >
                  {{ (marketOverview?.todayPnl ?? 0) >= 0 ? '+' : '' }}Rs.{{
                    (marketOverview?.todayPnl ?? 0).toLocaleString()
                  }}
                </span>
                <span
                  class="text-lg font-medium"
                  :class="
                    (portfolioSummary?.totalPnlPercent ?? 0) >= 0 ? 'text-success' : 'text-danger'
                  "
                >
                  ({{ (portfolioSummary?.totalPnlPercent ?? 0).toFixed(2) }}%)
                </span>
              </div>
            </div>
            <div class="flex items-center gap-4">
              <HealthStatus v-if="healthData" :health="healthData" />
              <div class="flex h-12 w-12 items-center justify-center rounded-xl bg-brand/10">
                <svg
                  class="h-6 w-6 text-brand"
                  fill="none"
                  stroke="currentColor"
                  viewBox="0 0 24 24"
                >
                  <path
                    stroke-linecap="round"
                    stroke-linejoin="round"
                    stroke-width="2"
                    d="M13 7h8m0 0v8m0-8l-8 8-4-4-6 6"
                  />
                </svg>
              </div>
            </div>
          </div>
        </div>

        <!-- Metrics Grid -->
        <div class="mb-6 grid grid-cols-2 gap-4 sm:grid-cols-5">
          <div v-for="metric in metrics" :key="metric.title" class="card-panel p-4">
            <p class="text-xs font-medium text-text-muted">
              {{ metric.title }}
            </p>
            <p class="mt-1 text-xl font-bold text-text-primary">
              {{ metric.value }}
            </p>
            <div v-if="metric.trend" class="mt-1 flex items-center gap-1">
              <span
                class="text-xs font-medium"
                :class="metric.trend.isPositive ? 'text-success' : 'text-danger'"
              >
                {{ metric.trend.isPositive ? '↑' : '↓' }} {{ metric.trend.value }}
              </span>
            </div>
          </div>
        </div>

        <!-- Positions Table -->
        <div class="card-panel">
          <div class="flex items-center justify-between border-b border-border-subtle px-5 py-3">
            <div>
              <h2 class="text-sm font-semibold text-text-primary">Active Positions</h2>
              <p class="text-xs text-text-muted">{{ positions.length }} positions</p>
            </div>
            <router-link to="/positions" class="text-sm font-medium text-brand hover:underline">
              View All →
            </router-link>
          </div>
          <div class="w-full overflow-x-auto">
            <table class="min-w-full">
              <thead>
                <tr class="border-b border-border-subtle bg-bg-primary/50">
                  <th
                    class="px-5 py-3 text-left text-xs font-semibold uppercase tracking-wider text-text-muted"
                  >
                    Symbol
                  </th>
                  <th
                    class="px-5 py-3 text-left text-xs font-semibold uppercase tracking-wider text-text-muted"
                  >
                    Entry
                  </th>
                  <th
                    class="px-5 py-3 text-right text-xs font-semibold uppercase tracking-wider text-text-muted"
                  >
                    Qty
                  </th>
                  <th
                    class="px-5 py-3 text-right text-xs font-semibold uppercase tracking-wider text-text-muted"
                  >
                    Current
                  </th>
                  <th
                    class="px-5 py-3 text-left text-xs font-semibold uppercase tracking-wider text-text-muted"
                  >
                    Status
                  </th>
                  <th
                    class="px-5 py-3 text-right text-xs font-semibold uppercase tracking-wider text-text-muted"
                  >
                    P&L
                  </th>
                </tr>
              </thead>
              <tbody class="divide-y divide-border-subtle/50">
                <tr
                  v-for="pos in positions.slice(0, 5)"
                  :key="pos.id"
                  class="transition-colors hover:bg-bg-hover"
                >
                  <td class="px-5 py-4 text-sm font-semibold text-text-primary">
                    {{ pos.symbol }}
                  </td>
                  <td class="px-5 py-4 text-sm text-text-secondary">Rs.{{ pos.entryPrice }}</td>
                  <td class="px-5 py-4 text-right text-sm text-text-secondary">
                    {{ pos.quantity }}
                  </td>
                  <td class="px-5 py-4 text-right text-sm text-text-secondary">
                    Rs.{{ pos.currentPrice }}
                  </td>
                  <td class="px-5 py-4">
                    <span
                      class="inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium"
                      :class="
                        pos.status === 'OPEN'
                          ? 'bg-success-bg text-success'
                          : 'bg-danger-bg text-danger'
                      "
                      >{{ pos.status }}</span
                    >
                  </td>
                  <td
                    class="px-5 py-4 text-right text-sm font-semibold"
                    :class="pos.pnl >= 0 ? 'text-success' : 'text-danger'"
                  >
                    {{ pos.pnl >= 0 ? '+' : '' }}Rs.{{ pos.pnl }}
                    <span class="ml-1 text-xs font-normal opacity-70"
                      >({{ pos.pnlPercent >= 0 ? '+' : '' }}{{ pos.pnlPercent.toFixed(2) }}%)</span
                    >
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
import { ref, computed, onMounted } from 'vue'
import {
  getMarketOverview,
  getPositions,
  getPortfolioSummary,
  getHealthStatus,
} from '../api/client'
import type {
  MarketOverview,
  Position,
  PortfolioSummary,
  HealthStatus as HealthStatusType,
} from '../api/types'
import LoadingSpinner from '../components/LoadingSpinner.vue'
import HealthStatus from '../components/HealthStatus.vue'
import ErrorBoundary from '../components/ErrorBoundary.vue'
import { useAsyncData } from '../composables/useAsyncData'

const { loading, error, errorMessage, execute } = useAsyncData<void>()
const marketOverview = ref<MarketOverview | null>(null)
const positions = ref<Position[]>([])
const portfolioSummary = ref<PortfolioSummary | null>(null)
const healthData = ref<HealthStatusType | null>(null)

const metrics = computed(() => [
  {
    title: 'Positions',
    value: marketOverview.value?.totalPositions ?? 0,
    trend: { value: `${marketOverview.value?.openPositions ?? 0} open`, isPositive: true },
  },
  { title: 'Value', value: `Rs.${(portfolioSummary.value?.totalValue ?? 0).toLocaleString()}` },
  { title: 'Win Rate', value: `${portfolioSummary.value?.winRate ?? 0}%` },
  { title: 'Trades', value: portfolioSummary.value?.totalTrades ?? 0 },
  {
    title: 'P&L',
    value: `Rs.${(portfolioSummary.value?.totalPnl ?? 0).toLocaleString()}`,
    trend: {
      value: `${(portfolioSummary.value?.totalPnlPercent ?? 0).toFixed(2)}%`,
      isPositive: Boolean(
        portfolioSummary.value?.totalPnlPercent && portfolioSummary.value.totalPnlPercent >= 0
      ),
    },
  },
])

const refreshDashboard = () => {
  execute(async () => {
    const [overviewRes, posRes, summaryRes, healthRes] = await Promise.all([
      getMarketOverview(),
      getPositions(),
      getPortfolioSummary(),
      getHealthStatus(),
    ])
    if (overviewRes.success && overviewRes.data) marketOverview.value = overviewRes.data
    if (posRes.success && posRes.data) positions.value = posRes.data
    if (summaryRes.success && summaryRes.data) portfolioSummary.value = summaryRes.data
    if (healthRes.success && healthRes.data) healthData.value = healthRes.data
    if (overviewRes.error || posRes.error || summaryRes.error)
      throw new Error(overviewRes.error ?? posRes.error ?? summaryRes.error)
  })
}

onMounted(() => {
  refreshDashboard()
})
</script>
