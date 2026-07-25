<template>
  <div class="flex flex-col gap-3">
    <!-- Composite summary -->
    <div class="grid grid-cols-3 gap-3">
      <div class="rounded-md bg-bg-elevated px-3 py-2">
        <p class="text-xs text-text-muted">Score</p>
        <p class="text-lg font-semibold" :class="scoreColor">{{ compositeScore }}</p>
      </div>
      <div class="rounded-md bg-bg-elevated px-3 py-2">
        <p class="text-xs text-text-muted">Signal</p>
        <p class="text-lg font-semibold" :class="signalColor">{{ compositeSignal }}</p>
      </div>
      <div class="rounded-md bg-bg-elevated px-3 py-2">
        <p class="text-xs text-text-muted">Confidence</p>
        <p class="text-lg font-semibold text-text-primary">{{ (compositeConfidence * 100).toFixed(0) }}%</p>
      </div>
    </div>

    <div v-if="reasoning">
      <p class="text-xs text-text-secondary leading-relaxed">{{ reasoning }}</p>
    </div>

    <!-- Synthesis -->
    <div v-if="synthesis" class="border-t border-border-subtle/50 pt-3">
      <div class="mb-2 flex items-center gap-2">
        <svg class="h-4 w-4 text-brand" viewBox="0 0 20 20" fill="currentColor">
          <path d="M11 3a1 1 0 10-2 0v1a1 1 0 102 0V3zM15.657 5.757a1 1 0 00-1.414-1.414l-.707.707a1 1 0 001.414 1.414l.707-.707zM18 10a1 1 0 01-1 1h-1a1 1 0 110-2h1a1 1 0 011 1zM5.05 8.464a1 1 0 10-1.414 1.414l.707.707a1 1 0 001.414-1.414l-.707-.707zM4 10a1 1 0 011-1h1a1 1 0 110 2H5a1 1 0 01-1-1zM7.657 14.243a1 1 0 00-1.414-1.414l-.707.707a1 1 0 001.414 1.414l.707-.707zM15 17a1 1 0 001-1v-1a1 1 0 10-2 0v1a1 1 0 001 1zM12.243 15.657a1 1 0 001.414 0l.707-.707a1 1 0 00-1.414-1.414l-.707.707a1 1 0 000 1.414z" />
        </svg>
        <span class="text-sm font-semibold text-text-primary">LLM Synthesis</span>
      </div>

      <div class="mb-3 rounded-md bg-bg-elevated px-3 py-2">
        <div class="mb-1 flex items-center justify-between">
          <span class="text-xs font-semibold uppercase tracking-wider" :class="synthesisRecommendationColor">{{ synthesis.recommendation }}</span>
          <span class="text-xs text-text-muted">{{ (synthesis.confidence * 100).toFixed(0) }}% confidence</span>
        </div>
        <p class="text-xs leading-relaxed text-text-secondary">{{ synthesis.narrative }}</p>
      </div>

      <div v-if="synthesis.keyDrivers.length" class="flex flex-col gap-1">
        <p class="text-xs font-semibold text-text-muted">Key Drivers</p>
        <ul class="list-disc pl-4 text-xs text-text-secondary">
          <li v-for="d in synthesis.keyDrivers" :key="d">{{ d }}</li>
        </ul>
      </div>

      <div class="mt-2 grid grid-cols-2 gap-3">
        <div v-if="synthesis.bullishFactors.length" class="flex flex-col gap-1">
          <p class="text-xs font-semibold text-success">Bullish</p>
          <ul class="list-disc pl-4 text-xs text-text-secondary">
            <li v-for="f in synthesis.bullishFactors" :key="f">{{ f }}</li>
          </ul>
        </div>
        <div v-if="synthesis.bearishFactors.length" class="flex flex-col gap-1">
          <p class="text-xs font-semibold text-danger">Bearish</p>
          <ul class="list-disc pl-4 text-xs text-text-secondary">
            <li v-for="f in synthesis.bearishFactors" :key="f">{{ f }}</li>
          </ul>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { StageDetails, SynthesisResult } from '../api/types'

const props = defineProps<{
  details: StageDetails
  synthesis?: SynthesisResult | null
}>()

const compositeScore = computed(() => (props.details.payload.compositeScore as number) ?? 0)
const compositeSignal = computed(() => (props.details.payload.compositeSignal as string) ?? 'HOLD')
const compositeConfidence = computed(() => (props.details.payload.compositeConfidence as number) ?? 0)
const reasoning = computed(() => (props.details.payload.reasoning as string) ?? '')

const scoreColor = computed(() => {
  if (compositeScore.value > 20) return 'text-success'
  if (compositeScore.value < -20) return 'text-danger'
  return 'text-warning'
})

const signalColor = computed(() => {
  if (compositeSignal.value === 'BUY') return 'text-success'
  if (compositeSignal.value === 'SELL') return 'text-danger'
  return 'text-warning'
})

const synthesisRecommendationColor = computed(() => {
  if (!props.synthesis) return ''
  if (props.synthesis.recommendation === 'BUY') return 'text-success'
  if (props.synthesis.recommendation === 'SELL') return 'text-danger'
  return 'text-warning'
})
</script>