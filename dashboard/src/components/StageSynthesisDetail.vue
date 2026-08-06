<template>
  <div class="flex flex-col gap-3">
    <div class="rounded-md bg-bg-elevated px-3 py-3">
      <div class="mb-1 flex items-center justify-between">
        <span class="text-sm font-semibold uppercase tracking-wider" :class="recommendationColor">{{
          recommendation
        }}</span>
        <span class="text-xs text-text-muted">{{ (confidence * 100).toFixed(0) }}% confidence</span>
      </div>
      <p class="text-sm leading-relaxed text-text-secondary">
        {{ narrative }}
      </p>
    </div>

    <div v-if="keyDrivers.length" class="flex flex-col gap-1">
      <p class="text-xs font-semibold text-text-muted">Key Drivers</p>
      <ul class="list-disc pl-4 text-xs text-text-secondary">
        <li v-for="d in keyDrivers" :key="d">
          {{ d }}
        </li>
      </ul>
    </div>

    <div class="grid grid-cols-2 gap-3">
      <div v-if="bullishFactors.length" class="flex flex-col gap-1">
        <p class="text-xs font-semibold text-success">Bullish Factors</p>
        <ul class="list-disc pl-4 text-xs text-text-secondary">
          <li v-for="f in bullishFactors" :key="f">
            {{ f }}
          </li>
        </ul>
      </div>
      <div v-if="bearishFactors.length" class="flex flex-col gap-1">
        <p class="text-xs font-semibold text-danger">Bearish Factors</p>
        <ul class="list-disc pl-4 text-xs text-text-secondary">
          <li v-for="f in bearishFactors" :key="f">
            {{ f }}
          </li>
        </ul>
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

const narrative = computed(() => (props.details.payload.narrative as string) ?? '')
const recommendation = computed(() => (props.details.payload.recommendation as string) ?? 'HOLD')
const confidence = computed(() => (props.details.payload.confidence as number) ?? 0)
const keyDrivers = computed(() => (props.details.payload.keyDrivers as string[]) ?? [])
const bullishFactors = computed(() => (props.details.payload.bullishFactors as string[]) ?? [])
const bearishFactors = computed(() => (props.details.payload.bearishFactors as string[]) ?? [])

const recommendationColor = computed(() => {
  if (recommendation.value === 'BUY') return 'text-success'
  if (recommendation.value === 'SELL') return 'text-danger'
  return 'text-warning'
})
</script>
