<script setup lang="ts">
import { computed } from 'vue'

interface Props {
  totalTrades: number
  winRate: number
  profitFactor: number
  maxDrawdown: number
  totalReturn: number
  expectancy: number
  hasEnoughData: boolean
}

const props = withDefaults(defineProps<Props>(), {
  totalTrades: 0,
  winRate: 0,
  profitFactor: 0,
  maxDrawdown: 0,
  totalReturn: 0,
  expectancy: 0,
  hasEnoughData: false,
})

const winRateColor = computed(() => {
  if (props.winRate > 50) return 'text-success'
  if (props.winRate < 40) return 'text-danger'
  return 'text-text-primary'
})

const profitFactorColor = computed(() => {
  if (props.profitFactor > 1.5) return 'text-success'
  if (props.profitFactor < 0.8) return 'text-danger'
  return 'text-text-primary'
})

const totalReturnColor = computed(() => {
  if (props.totalReturn > 0) return 'text-success'
  if (props.totalReturn < 0) return 'text-danger'
  return 'text-text-primary'
})

const expectancyColor = computed(() => {
  if (props.expectancy > 0) return 'text-success'
  if (props.expectancy < 0) return 'text-danger'
  return 'text-text-primary'
})

const formatPercent = (value: number) => {
  const sign = value >= 0 ? '+' : ''
  return `${sign}${value.toFixed(1)}%`
}

const noTradesMessage = computed(() => {
  if (props.hasEnoughData && props.totalTrades === 0) {
    return 'No trades matched strategy rules in backtest period'
  }
  return ''
})
</script>

<template>
  <div class="card-panel p-5">
    <!-- Header -->
    <h2 class="mb-4 text-base font-semibold text-text-primary">Backtest Performance</h2>

    <!-- Stats Grid -->
    <div class="grid grid-cols-2 gap-4">
      <!-- Row 1 -->
      <div class="rounded-md bg-bg-elevated/50 p-3.5">
        <p class="text-[10px] uppercase tracking-[0.15em] text-text-muted/50">Total Trades</p>
        <p class="mt-1 text-xl font-bold text-text-primary">{{ totalTrades }}</p>
      </div>
      <div class="rounded-md bg-bg-elevated/50 p-3.5">
        <p class="text-[10px] uppercase tracking-[0.15em] text-text-muted/50">Win Rate</p>
        <p class="mt-1 text-xl font-bold" :class="winRateColor">{{ winRate.toFixed(1) }}%</p>
      </div>

      <!-- Row 2 -->
      <div class="rounded-md bg-bg-elevated/50 p-3.5">
        <p class="text-[10px] uppercase tracking-[0.15em] text-text-muted/50">Profit Factor</p>
        <p class="mt-1 text-xl font-bold" :class="profitFactorColor">{{ profitFactor.toFixed(2) }}</p>
      </div>
      <div class="rounded-md bg-bg-elevated/50 p-3.5">
        <p class="text-[10px] uppercase tracking-[0.15em] text-text-muted/50">Max Drawdown</p>
        <p class="mt-1 text-xl font-bold text-danger">{{ (-maxDrawdown).toFixed(1) }}%</p>
      </div>

      <!-- Row 3 -->
      <div class="rounded-md bg-bg-elevated/50 p-3.5">
        <p class="text-[10px] uppercase tracking-[0.15em] text-text-muted/50">Total Return</p>
        <p class="mt-1 text-xl font-bold" :class="totalReturnColor">{{ formatPercent(totalReturn) }}</p>
      </div>
      <div class="rounded-md bg-bg-elevated/50 p-3.5">
        <p class="text-[10px] uppercase tracking-[0.15em] text-text-muted/50">Expectancy</p>
        <p class="mt-1 text-xl font-bold" :class="expectancyColor">{{ formatPercent(expectancy) }}</p>
      </div>
    </div>

    <!-- No trades notice -->
    <p v-if="noTradesMessage" class="mt-3 text-xs text-text-muted/60">{{ noTradesMessage }}</p>
  </div>
</template>