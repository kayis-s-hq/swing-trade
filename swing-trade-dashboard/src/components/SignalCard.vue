<script setup lang="ts">
import { computed } from 'vue'

export interface SignalCardProps {
  signal: {
    id: string
    symbol: string
    signalType: 'BUY' | 'SELL' | 'HOLD'
    confidence: number
    reasoning: string
    entryPrice: number
    stopLoss: number
    target: number
    createdDate: string
    indicators: string[]
  }
}

const props = defineProps<SignalCardProps>()

const typeColor = computed(() => {
  switch (props.signal.signalType) {
    case 'BUY':
      return 'bg-success-50 text-success-600 dark:bg-success-500/15 dark:text-success-500'
    case 'SELL':
      return 'bg-error-50 text-error-600 dark:bg-error-500/15 dark:text-error-500'
    case 'HOLD':
      return 'bg-gray-50 text-gray-600 dark:bg-gray-500/15 dark:text-gray-500'
    default:
      return 'bg-gray-50 text-gray-600 dark:bg-gray-500/15 dark:text-gray-500'
  }
})

const typeLabel = computed(() => {
  return props.signal.signalType
})

const confidenceColor = computed(() => {
  if (props.signal.confidence >= 70) return 'text-success-600'
  if (props.signal.confidence >= 50) return 'text-warning-600'
  return 'text-error-600'
})
</script>

<template>
  <div
    class="rounded-2xl border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-white/[0.03]"
  >
    <!-- Signal Type Badge -->
    <div class="flex items-center justify-between">
      <span :class="typeColor" class="rounded-full px-2.5 py-0.5 text-xs font-bold uppercase tracking-wide">
        {{ typeLabel }}
      </span>
      <span :class="confidenceColor" class="text-xs font-medium">
        {{ signal.confidence }}% Confidence
      </span>
    </div>

    <!-- Symbol -->
    <h4 class="mt-3 text-lg font-bold text-gray-800 dark:text-white/90">{{ signal.symbol }}</h4>

    <!-- Price Levels -->
    <div class="mt-3 grid grid-cols-3 gap-2 text-xs">
      <div>
        <span class="text-gray-500 dark:text-gray-400">Entry</span>
        <p class="font-medium text-gray-700 dark:text-gray-300">${{ signal.entryPrice }}</p>
      </div>
      <div>
        <span class="text-gray-500 dark:text-gray-400">Stop</span>
        <p class="font-medium text-gray-700 dark:text-gray-300">${{ signal.stopLoss }}</p>
      </div>
      <div>
        <span class="text-gray-500 dark:text-gray-400">Target</span>
        <p class="font-medium text-gray-700 dark:text-gray-300">${{ signal.target }}</p>
      </div>
    </div>

    <!-- Reasoning -->
    <div class="mt-3">
      <span class="text-xs font-medium text-gray-500 dark:text-gray-400">Reasoning</span>
      <p class="mt-1 text-sm text-gray-700 dark:text-gray-300">
        {{ signal.reasoning || 'No reasoning provided' }}
      </p>
    </div>

    <!-- Indicators -->
    <div class="mt-3 flex flex-wrap gap-1">
      <span
        v-for="indicator in signal.indicators"
        :key="indicator"
        class="rounded-md bg-gray-100 px-2 py-0.5 text-[10px] font-medium text-gray-600 dark:bg-gray-800 dark:text-gray-400"
      >
        {{ indicator }}
      </span>
    </div>
  </div>
</template>
