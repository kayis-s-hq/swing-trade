<template>
  <div class="card-panel transition-all hover:border-border-default hover:shadow-lg">
    <!-- Header -->
    <div class="flex items-center justify-between border-b border-border-subtle/50 px-4 py-3">
      <div class="flex items-center gap-2">
        <span class="text-sm font-bold text-text-primary">{{ signal.symbol }}</span>
        <span class="inline-flex items-center rounded-full px-2 py-0.5 text-xs font-semibold" :class="signal.direction === 'BUY' ? 'bg-success-bg text-success' : 'bg-danger-bg text-danger'">{{ signal.direction }}</span>
      </div>
      <span class="text-xs font-medium" :class="statusColor">{{ signal.status }}</span>
    </div>

    <!-- Body -->
    <div class="px-4 py-4">
      <!-- Confidence -->
      <div class="mb-3 flex items-center gap-2">
        <span class="text-xs font-medium text-text-muted">Confidence</span>
        <div class="h-1.5 flex-1 rounded-full bg-bg-primary/50">
          <div class="h-full rounded-full bg-brand" :style="{ width: signal.confidence + '%' }"></div>
        </div>
        <span class="text-xs font-semibold text-text-primary">{{ signal.confidence }}%</span>
      </div>

      <!-- Price Data -->
      <div class="grid grid-cols-3 gap-3 text-xs">
        <div>
          <p class="text-text-muted">Entry</p>
          <p class="mt-0.5 font-semibold text-text-primary">${{ signal.entryPrice }}</p>
        </div>
        <div>
          <p class="text-text-muted">Stop Loss</p>
          <p class="mt-0.5 font-semibold text-danger">${{ signal.stopLoss }}</p>
        </div>
        <div>
          <p class="text-text-muted">Target</p>
          <p class="mt-0.5 font-semibold text-success">${{ signal.target }}</p>
        </div>
      </div>

      <!-- R:R -->
      <div class="mt-3 flex items-center justify-between border-t border-border-subtle/50 pt-3">
        <span class="text-xs font-medium text-text-muted">Risk:Reward</span>
        <span class="text-sm font-bold" :class="signal.riskReward >= 2 ? 'text-brand' : 'text-text-primary'">1:{{ signal.riskReward.toFixed(2) }}</span>
      </div>
    </div>

    <!-- Reason -->
    <div class="border-t border-border-subtle/50 px-4 py-3">
      <p class="text-xs leading-relaxed text-text-muted">{{ signal.reason }}</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  signal: {
    symbol: string
    direction: 'BUY' | 'SELL'
    confidence: number
    reason: string
    entryPrice: number
    stopLoss: number
    target: number
    riskReward: number
    status: string
  }
}>()

const statusColor = computed(() => {
  const colors: Record<string, string> = {
    ACTIVE: 'text-success',
    PENDING: 'text-warning',
    EXECUTED: 'text-info',
    EXPIRED: 'text-text-muted',
  }
  return colors[props.signal.status] ?? 'text-text-muted'
})
</script>
