<template>
  <div class="card-panel p-5">
    <!-- Header -->
    <div class="flex items-center justify-between mb-4">
      <h3 class="text-sm font-semibold text-text-primary">Fundamentals</h3>
      <span
        class="inline-flex items-center rounded-md px-2 py-1 text-xs font-bold"
        :class="scoreBadgeClass"
      >
        [{{ scoreDisplay }}]
      </span>
    </div>

    <!-- Factor List -->
    <div class="flex flex-col gap-2">
      <div
        v-for="(factor, index) in parsedFactors"
        :key="index"
        class="flex items-center justify-between rounded-md bg-bg-primary px-3 py-2"
      >
        <span class="text-xs text-text-primary">{{ factor.description }}</span>
        <span
          class="inline-flex items-center rounded-sm px-1.5 py-0.5 text-[10px] font-bold"
          :class="factor.scoreBadgeClass"
        >
          {{ factor.scoreDisplay }}
        </span>
      </div>
      <p v-if="!factors.length" class="text-xs text-text-muted">No fundamentals data available</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  score: number
  factors: string[]
}>()

const scoreDisplay = computed(() => {
  const sign = props.score >= 0 ? '+' : ''
  return `${sign}${props.score}`
})

const scoreBadgeClass = computed(() => {
  if (props.score > 0) {
    return 'bg-success/15 text-success'
  }
  if (props.score < 0) {
    return 'bg-danger/15 text-danger'
  }
  return 'bg-warning/15 text-warning'
})

interface ParsedFactor {
  description: string
  score: number
  scoreDisplay: string
  scoreBadgeClass: string
}

const parsedFactors = computed<ParsedFactor[]>(() => {
  return props.factors.map((factor) => {
    const match = factor.match(/^(.+?)\s*=\s*(\+?\d+)$/)
    let description = factor
    let score = 0

    if (match) {
      description = match[1].trim()
      score = parseInt(match[2], 10)
    }

    const scoreSign = score >= 0 ? '+' : ''
    const scoreDisplayStr = `${scoreSign}${score}`

    let scoreBadgeClass = 'bg-text-muted/10 text-text-muted'
    if (score > 0) {
      scoreBadgeClass = 'bg-success/15 text-success'
    } else if (score < 0) {
      scoreBadgeClass = 'bg-danger/15 text-danger'
    }

    return { description, score, scoreDisplay: scoreDisplayStr, scoreBadgeClass }
  })
})
</script>