<script setup lang="ts">
import { computed } from 'vue'

export interface PositionCardProps {
  position: {
    id: string
    symbol: string
    entryPrice: number
    entryDate: string
    quantity: number
    currentPrice: number
    status: 'OPEN' | 'CLOSED' | 'STOPPED' | 'TARGET_HIT'
    pnl: number
    pnlPercent: number
    stopLoss: number
    target: number
    reason: string
  }
}

const props = defineProps<PositionCardProps>()

const statusColor = computed(() => {
  switch (props.position.status) {
    case 'OPEN':
      return 'bg-success-50 text-success-600 dark:bg-success-500/15 dark:text-success-500'
    case 'STOPPED':
      return 'bg-warning-50 text-warning-600 dark:bg-warning-500/15 dark:text-orange-400'
    case 'TARGET_HIT':
    case 'CLOSED':
      return 'bg-error-50 text-error-600 dark:bg-error-500/15 dark:text-error-500'
    default:
      return 'bg-gray-50 text-gray-600 dark:bg-gray-500/15 dark:text-gray-500'
  }
})

const statusLabel = computed(() => {
  switch (props.position.status) {
    case 'OPEN':
      return 'Open'
    case 'STOPPED':
      return 'Stopped'
    case 'TARGET_HIT':
      return 'Target Hit'
    case 'CLOSED':
      return 'Closed'
    default:
      return props.position.status
  }
})

const pnlColor = computed(() => {
  return props.position.pnl >= 0 ? 'text-success-600' : 'text-error-600'
})

const emit = defineEmits<{
  (e: 'close-position', position: PositionCardProps['position']): void
}>()
</script>

<template>
  <div
    class="rounded-2xl border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-white/[0.03]"
  >
    <!-- Symbol and Status -->
    <div class="flex items-center justify-between">
      <h4 class="text-lg font-bold text-gray-800 dark:text-white/90">{{ position.symbol }}</h4>
      <span :class="statusColor" class="rounded-full px-2.5 py-0.5 text-xs font-medium">
        {{ statusLabel }}
      </span>
    </div>

    <!-- Price Info -->
    <div class="mt-4 grid grid-cols-2 gap-4">
      <div>
        <span class="text-xs text-gray-500 dark:text-gray-400">Entry</span>
        <p class="text-sm font-medium text-gray-700 dark:text-gray-300">${{ position.entryPrice }}</p>
      </div>
      <div>
        <span class="text-xs text-gray-500 dark:text-gray-400">Current</span>
        <p class="text-sm font-medium text-gray-700 dark:text-gray-300">${{ position.currentPrice }}</p>
      </div>
      <div>
        <span class="text-xs text-gray-500 dark:text-gray-400">Quantity</span>
        <p class="text-sm font-medium text-gray-700 dark:text-gray-300">{{ position.quantity }}</p>
      </div>
      <div>
        <span class="text-xs text-gray-500 dark:text-gray-400">P&L</span>
        <p :class="pnlColor" class="text-sm font-medium">
          ${{ position.pnl }} ({{ position.pnlPercent }}%)
        </p>
      </div>
    </div>

    <!-- Action Button -->
    <button
      v-if="position.status === 'OPEN'"
      @click="$emit('close-position', position)"
      class="mt-4 w-full rounded-lg bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-700 dark:bg-indigo-500 dark:hover:bg-indigo-600"
    >
      Close Position
    </button>
  </div>
</template>
