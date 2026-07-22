<template>
  <div class="card-panel transition-all hover:border-border-default">
    <!-- Header -->
    <div class="flex items-center justify-between border-b border-border-subtle/50 px-4 py-3">
      <span class="text-sm font-semibold text-text-primary">Composite Analysis</span>
      <span class="inline-flex items-center rounded-full px-2.5 py-1 text-xs font-bold uppercase tracking-wider" :class="signalBadgeClass">
        {{ signal }}
      </span>
    </div>

    <!-- Body -->
    <div class="flex flex-col items-center px-4 py-6">
      <!-- Score -->
      <div class="mb-4 flex items-center justify-center">
        <span class="font-mono text-[4rem] font-bold leading-none" :class="scoreColor">
          {{ scoreDisplay }}
        </span>
      </div>

      <!-- Confidence Meter -->
      <div class="mb-5 w-full max-w-xs">
        <div class="mb-1.5 flex items-center justify-between">
          <span class="text-xs font-medium text-text-muted">Confidence</span>
          <span class="font-mono text-xs font-semibold text-text-primary">{{ (confidence * 100).toFixed(0) }}%</span>
        </div>
        <div class="h-2 w-full overflow-hidden rounded-full bg-bg-primary/50">
          <div
            class="h-full rounded-full transition-all"
            :class="confidenceBarClass"
            :style="{ width: (confidence * 100) + '%' }"
          ></div>
        </div>
      </div>

      <!-- Weight Breakdown -->
      <div class="flex gap-3">
        <span class="inline-flex items-center gap-1.5 rounded-full bg-bg-elevated px-3 py-1.5 text-xs font-medium text-text-secondary">
          <span class="text-[10px] uppercase tracking-wider text-text-muted">News</span>
          <span class="font-mono text-[11px] font-semibold text-text-primary">30%</span>
        </span>
        <span class="inline-flex items-center gap-1.5 rounded-full bg-bg-elevated px-3 py-1.5 text-xs font-medium text-text-secondary">
          <span class="text-[10px] uppercase tracking-wider text-text-muted">Technical</span>
          <span class="font-mono text-[11px] font-semibold text-text-primary">40%</span>
        </span>
        <span class="inline-flex items-center gap-1.5 rounded-full bg-bg-elevated px-3 py-1.5 text-xs font-medium text-text-secondary">
          <span class="text-[10px] uppercase tracking-wider text-text-muted">Fundamentals</span>
          <span class="font-mono text-[11px] font-semibold text-text-primary">30%</span>
        </span>
      </div>
    </div>

    <!-- Reasoning -->
    <div v-if="reasoning" class="border-t border-border-subtle/50 px-4 py-3">
      <p class="text-xs leading-relaxed text-text-muted">{{ reasoning }}</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  score: number
  signal: 'BUY' | 'SELL' | 'HOLD'
  confidence: number
  reasoning: string
}>()

const scoreDisplay = computed(() => {
  return props.score >= 0 ? `+${props.score}` : `${props.score}`
})

const scoreColor = computed(() => {
  if (props.score > 20) return 'text-success'
  if (props.score < -20) return 'text-danger'
  return 'text-warning'
})

const signalBadgeClass = computed(() => {
  switch (props.signal) {
    case 'BUY': return 'bg-success-bg text-success'
    case 'SELL': return 'bg-danger-bg text-danger'
    case 'HOLD': return 'bg-warning-bg text-warning'
  }
})

const confidenceBarClass = computed(() => {
  if (props.confidence >= 0.7) return 'bg-success'
  if (props.confidence >= 0.4) return 'bg-warning'
  return 'bg-danger'
})
</script>