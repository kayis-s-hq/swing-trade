<template>
  <div class="flex flex-col gap-3">
    <div class="grid grid-cols-2 gap-3">
      <div class="rounded-md bg-bg-elevated px-3 py-2">
        <p class="text-xs text-text-muted">
          Score
        </p>
        <p
          class="text-lg font-semibold"
          :class="scoreColor"
        >
          {{ score }}
        </p>
      </div>
      <div class="rounded-md bg-bg-elevated px-3 py-2">
        <p class="text-xs text-text-muted">
          Signal
        </p>
        <p
          class="text-lg font-semibold"
          :class="signalColor"
        >
          {{ signal }}
        </p>
      </div>
    </div>

    <div v-if="factors.length">
      <p class="mb-1.5 text-xs font-semibold text-text-muted">
        Factors
      </p>
      <ul class="flex flex-col gap-1">
        <li
          v-for="factor in factors"
          :key="factor"
          class="text-xs leading-relaxed text-text-secondary"
        >
          {{ factor }}
        </li>
      </ul>
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
const factors = computed(() => (props.details.payload.factors as string[]) ?? [])

const signal = computed(() => {
  if (score.value > 0) return 'BULLISH'
  if (score.value < 0) return 'BEARISH'
  return 'NEUTRAL'
})

const scoreColor = computed(() => {
  if (score.value > 0) return 'text-success'
  if (score.value < 0) return 'text-danger'
  return 'text-warning'
})

const signalColor = computed(() => {
  if (signal.value === 'BULLISH') return 'text-success'
  if (signal.value === 'BEARISH') return 'text-danger'
  return 'text-warning'
})
</script>
