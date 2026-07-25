<template>
  <div class="flex flex-col gap-3">
    <template v-if="details.type === 'news'">
      <!-- News fetch details -->
      <div class="grid grid-cols-2 gap-3">
        <div class="rounded-md bg-bg-elevated px-3 py-2">
          <p class="text-xs text-text-muted">Articles</p>
          <p class="text-lg font-semibold text-text-primary">{{ details.payload.articleCount }}</p>
        </div>
        <div class="rounded-md bg-bg-elevated px-3 py-2">
          <p class="text-xs text-text-muted">Sources</p>
          <p class="text-lg font-semibold text-text-primary">{{ details.payload.sourceCount }}</p>
        </div>
      </div>
      <div v-if="sources.length" class="flex flex-wrap gap-1.5">
        <span
          v-for="source in sources"
          :key="source"
          class="rounded bg-brand/10 px-2 py-0.5 text-xs font-medium text-brand"
        >
          {{ source }}
        </span>
      </div>
    </template>

    <template v-else-if="details.type === 'sentiment'">
      <!-- Sentiment analysis details -->
      <div class="grid grid-cols-3 gap-3">
        <div class="rounded-md bg-bg-elevated px-3 py-2">
          <p class="text-xs text-text-muted">Score</p>
          <p class="text-lg font-semibold" :class="scoreColor">{{ scoreLabel }}</p>
        </div>
        <div class="rounded-md bg-bg-elevated px-3 py-2">
          <p class="text-xs text-text-muted">Confidence</p>
          <p class="text-lg font-semibold text-text-primary">{{ (confidence * 100).toFixed(0) }}%</p>
        </div>
        <div class="rounded-md bg-bg-elevated px-3 py-2">
          <p class="text-xs text-text-muted">Articles</p>
          <p class="text-lg font-semibold text-text-primary">{{ articleCount }}</p>
        </div>
      </div>

      <div v-if="summary">
        <p class="text-xs text-text-secondary leading-relaxed">{{ summary }}</p>
      </div>

      <div v-if="catalysts.length" class="flex flex-col gap-1">
        <p class="text-xs font-semibold text-success">Catalysts</p>
        <ul class="list-disc pl-4 text-xs text-text-secondary">
          <li v-for="c in catalysts" :key="c">{{ c }}</li>
        </ul>
      </div>

      <div v-if="redFlags.length" class="flex flex-col gap-1">
        <p class="text-xs font-semibold text-danger">Red Flags</p>
        <ul class="list-disc pl-4 text-xs text-text-secondary">
          <li v-for="rf in redFlags" :key="rf">{{ rf }}</li>
        </ul>
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

const scoreLabel = computed(() => {
  const score = props.details.payload.score as string
  return score || 'N/A'
})

const scoreColor = computed(() => {
  const score = props.details.payload.score as string
  if (score === 'POSITIVE') return 'text-success'
  if (score === 'NEGATIVE') return 'text-danger'
  return 'text-warning'
})

const confidence = computed(() => (props.details.payload.confidence as number) ?? 0)
const articleCount = computed(() => (props.details.payload.articleCount as number) ?? 0)
const summary = computed(() => (props.details.payload.summary as string) ?? '')
const catalysts = computed(() => (props.details.payload.catalysts as string[]) ?? [])
const redFlags = computed(() => (props.details.payload.redFlags as string[]) ?? [])
const sources = computed(() => (props.details.payload.sources as string[]) ?? [])
</script>