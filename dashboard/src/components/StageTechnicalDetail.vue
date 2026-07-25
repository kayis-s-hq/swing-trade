<template>
  <div class="flex flex-col gap-3">
    <div class="grid grid-cols-3 gap-3">
      <div class="rounded-md bg-bg-elevated px-3 py-2">
        <p class="text-xs text-text-muted">Signal</p>
        <p class="text-lg font-semibold" :class="signalColor">{{ signal }}</p>
      </div>
      <div class="rounded-md bg-bg-elevated px-3 py-2">
        <p class="text-xs text-text-muted">Score</p>
        <p class="text-lg font-semibold text-text-primary">{{ score }}</p>
      </div>
      <div class="rounded-md bg-bg-elevated px-3 py-2">
        <p class="text-xs text-text-muted">Confidence</p>
        <p class="text-lg font-semibold text-text-primary">{{ (confidence * 100).toFixed(0) }}%</p>
      </div>
    </div>

    <div v-if="indicators.length">
      <p class="mb-1.5 text-xs font-semibold text-text-muted">Indicators</p>
      <div class="flex flex-col gap-1.5">
        <div
          v-for="indicator in indicators"
          :key="indicator.name"
          class="flex items-center justify-between rounded-md bg-bg-elevated px-3 py-1.5"
        >
          <span class="text-xs text-text-secondary">{{ indicator.name }}</span>
          <span class="text-xs font-mono font-semibold" :class="indicator.value >= 0 ? 'text-success' : 'text-danger'">
            {{ indicator.value >= 0 ? '+' : '' }}{{ indicator.value }}
          </span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { StageDetails } from '../api/types'

const props = defineProps<{
  details: StageDetails
}>()

const score = computed(() => (props.details.payload.score as number) ?? 0)
const signal = computed(() => (props.details.payload.signal as string) ?? 'HOLD')
const confidence = computed(() => (props.details.payload.confidence as number) ?? 0)

const signalColor = computed(() => {
  if (signal.value === 'BUY') return 'text-success'
  if (signal.value === 'SELL') return 'text-danger'
  return 'text-warning'
})

const indicators = computed(() => {
  const raw = props.details.payload.indicators
  if (!raw || !Array.isArray(raw) || raw.length === 0) return []
  if (typeof raw[0] === 'string') {
    return (raw as string[]).map(name => ({ name, value: 0 }))
  }
  return raw as Array<{ name: string; value: number }>
})
</script>