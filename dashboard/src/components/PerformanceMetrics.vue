<script setup lang="ts">
import { computed, ref } from 'vue'

export interface EquityPoint {
  date: string
  value: number
}

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
  equityPoints?: EquityPoint[]
}

const props = withDefaults(defineProps<PerformanceMetricsProps>(), {
  portfolioSummary: undefined,
  equityPoints: () => [],
})

const emit = defineEmits<{ rangeChange: [range: string] }>()

const selectedRange = ref('1M')
const ranges = ['1W', '1M', '3M', '6M', '1Y', 'ALL']

const selectRange = (range: string) => {
  selectedRange.value = range
  emit('rangeChange', range)
}

const equityCoords = computed(() => {
  const pts = props.equityPoints ?? []
  if (pts.length < 2) return []
  const W = 600,
    plotH = 140,
    top = 10
  const vals = pts.map((p) => p.value)
  const min = Math.min(...vals)
  const max = Math.max(...vals)
  const range = max - min || 1
  return pts.map((p, i) => ({
    x: (i / (pts.length - 1)) * W,
    y: top + (1 - (p.value - min) / range) * plotH,
  }))
})

const linePath = computed(() => {
  const coords = equityCoords.value
  if (!coords.length) return ''
  let d = `M ${coords[0].x.toFixed(1)} ${coords[0].y.toFixed(1)}`
  for (let i = 1; i < coords.length; i++) {
    const prev = coords[i - 1]
    const curr = coords[i]
    const cpx = ((prev.x + curr.x) / 2).toFixed(1)
    d += ` C ${cpx} ${prev.y.toFixed(1)}, ${cpx} ${curr.y.toFixed(1)}, ${curr.x.toFixed(1)} ${curr.y.toFixed(1)}`
  }
  return d
})

const areaPath = computed(() => {
  const coords = equityCoords.value
  if (coords.length < 2) return ''
  const last = coords[coords.length - 1]
  return `${linePath.value} L ${last.x.toFixed(1)} 160 L 0 160 Z`
})

const hasChart = computed(() => equityCoords.value.length >= 2)
const pnlPositive = computed(() => (props.portfolioSummary?.totalPnlPercent ?? 0) >= 0)
</script>

<template>
  <div>
    <!-- Metrics Grid -->
    <div class="grid grid-cols-2 gap-4 sm:grid-cols-4">
      <div class="card-panel p-4">
        <p class="text-xs font-medium text-text-muted">Total Value</p>
        <p class="mt-1 text-xl font-bold text-text-primary">
          ₹{{ portfolioSummary?.totalValue.toLocaleString() ?? 0 }}
        </p>
      </div>
      <div class="card-panel p-4">
        <p class="text-xs font-medium text-text-muted">Total P&L</p>
        <p class="mt-1 text-xl font-bold" :class="pnlPositive ? 'text-success' : 'text-danger'">
          ₹{{ portfolioSummary?.totalPnl.toLocaleString() ?? 0 }}
          <span class="ml-1 text-sm font-normal opacity-70"
            >({{ (portfolioSummary?.totalPnlPercent ?? 0).toFixed(2) }}%)</span
          >
        </p>
      </div>
      <div class="card-panel p-4">
        <p class="text-xs font-medium text-text-muted">Win Rate</p>
        <p class="mt-1 text-xl font-bold text-text-primary">
          {{ portfolioSummary?.winRate.toLocaleString() ?? 0 }}%
        </p>
      </div>
      <div class="card-panel p-4">
        <p class="text-xs font-medium text-text-muted">Total Trades</p>
        <p class="mt-1 text-xl font-bold text-text-primary">
          {{ portfolioSummary?.totalTrades.toLocaleString() ?? 0 }}
        </p>
      </div>
    </div>

    <!-- Secondary Metrics -->
    <div class="mt-4 grid grid-cols-3 gap-4">
      <div class="card-panel p-4">
        <p class="text-xs font-medium text-text-muted">Profit Factor</p>
        <p class="mt-1 text-lg font-bold text-text-primary">
          {{ (portfolioSummary?.profitFactor ?? 0).toFixed(2) }}
        </p>
      </div>
      <div class="card-panel p-4">
        <p class="text-xs font-medium text-text-muted">Avg Win</p>
        <p class="mt-1 text-lg font-bold text-success">
          ₹{{ portfolioSummary?.averageWin.toLocaleString() ?? 0 }}
        </p>
      </div>
      <div class="card-panel p-4">
        <p class="text-xs font-medium text-text-muted">Avg Loss</p>
        <p class="mt-1 text-lg font-bold text-danger">
          ₹{{ portfolioSummary?.averageLoss.toLocaleString() ?? 0 }}
        </p>
      </div>
    </div>

    <!-- Equity Curve -->
    <div class="mt-4 card-panel">
      <div class="flex items-center justify-between border-b border-border-subtle px-5 py-3">
        <h3 class="text-sm font-semibold text-text-primary">Equity Curve</h3>
        <div class="flex rounded-md border border-border-subtle">
          <button
            v-for="range in ranges"
            :key="range"
            class="px-2.5 py-1 text-xs font-medium transition-colors first:rounded-l-md last:rounded-r-md"
            :class="
              selectedRange === range
                ? 'bg-brand-subtle text-brand'
                : 'text-text-muted hover:bg-bg-hover'
            "
            @click="selectRange(range)"
          >
            {{ range }}
          </button>
        </div>
      </div>
      <div class="relative h-48 w-full overflow-hidden bg-bg-primary/50">
        <svg v-if="hasChart" class="h-full w-full" viewBox="0 0 600 160" preserveAspectRatio="none">
          <defs>
            <linearGradient id="equityFill" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stop-color="#00d4a0" stop-opacity="0.2" />
              <stop offset="100%" stop-color="#00d4a0" stop-opacity="0" />
            </linearGradient>
          </defs>
          <path :d="areaPath" fill="url(#equityFill)" />
          <path
            :d="linePath"
            fill="none"
            stroke="#00d4a0"
            stroke-width="2"
            stroke-linecap="round"
            stroke-linejoin="round"
          />
        </svg>
        <div v-else class="flex h-full flex-col items-center justify-center">
          <p class="text-sm text-text-muted">No data available</p>
        </div>
      </div>
    </div>
  </div>
</template>
