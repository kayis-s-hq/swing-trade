<template>
  <div class="flex flex-col gap-3">
    <div v-if="!hasEnoughData" class="rounded-md bg-warning-bg px-3 py-2 text-xs text-warning">
      Insufficient data for backtest. Need more historical candles.
    </div>

    <template v-else>
      <div class="grid grid-cols-3 gap-3">
        <div class="rounded-md bg-bg-elevated px-3 py-2">
          <p class="text-xs text-text-muted">Trades</p>
          <p class="text-lg font-semibold text-text-primary">
            {{ totalTrades }}
          </p>
        </div>
        <div class="rounded-md bg-bg-elevated px-3 py-2">
          <p class="text-xs text-text-muted">Win Rate</p>
          <p class="text-lg font-semibold" :class="winRateColor">{{ winRate.toFixed(1) }}%</p>
        </div>
        <div class="rounded-md bg-bg-elevated px-3 py-2">
          <p class="text-xs text-text-muted">Profit Factor</p>
          <p class="text-lg font-semibold" :class="pfColor">
            {{ profitFactor.toFixed(2) }}
          </p>
        </div>
      </div>

      <div class="grid grid-cols-3 gap-3">
        <div class="rounded-md bg-bg-elevated px-3 py-2">
          <p class="text-xs text-text-muted">Return</p>
          <p class="text-lg font-semibold" :class="returnColor">{{ totalReturn.toFixed(1) }}%</p>
        </div>
        <div class="rounded-md bg-bg-elevated px-3 py-2">
          <p class="text-xs text-text-muted">Drawdown</p>
          <p class="text-lg font-semibold text-danger">{{ maxDrawdown.toFixed(1) }}%</p>
        </div>
        <div class="rounded-md bg-bg-elevated px-3 py-2">
          <p class="text-xs text-text-muted">Expectancy</p>
          <p class="text-lg font-semibold" :class="expectancyColor">{{ expectancy.toFixed(1) }}%</p>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { StageDetails } from '../api/types'

const props = defineProps<{
  details: StageDetails
}>()

const totalTrades = computed(() => (props.details.payload.totalTrades as number) ?? 0)
const winRate = computed(() => (props.details.payload.winRate as number) ?? 0)
const profitFactor = computed(() => (props.details.payload.profitFactor as number) ?? 0)
const maxDrawdown = computed(() => (props.details.payload.maxDrawdown as number) ?? 0)
const totalReturn = computed(() => (props.details.payload.totalReturn as number) ?? 0)
const expectancy = computed(() => (props.details.payload.expectancy as number) ?? 0)
const hasEnoughData = computed(() => (props.details.payload.hasEnoughData as boolean) ?? false)

const winRateColor = computed(() =>
  winRate.value >= 55 ? 'text-success' : winRate.value >= 45 ? 'text-warning' : 'text-danger'
)
const pfColor = computed(() =>
  profitFactor.value >= 1.5
    ? 'text-success'
    : profitFactor.value >= 1.0
      ? 'text-warning'
      : 'text-danger'
)
const returnColor = computed(() => (totalReturn.value >= 0 ? 'text-success' : 'text-danger'))
const expectancyColor = computed(() => (expectancy.value >= 0 ? 'text-success' : 'text-danger'))
</script>
