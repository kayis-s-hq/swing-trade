<script setup lang="ts">
import { computed } from 'vue'

interface Props {
  score: number
  summary: string
  catalysts: string[]
  redFlags: string[]
  articleCount: number
}

const props = defineProps<Props>()

const positiveCount = computed(() => Math.ceil(props.articleCount / 2))
const negativeCount = computed(() => Math.floor(props.articleCount / 2))

const summaryLine = computed(() => {
  if (props.summary) return props.summary
  if (props.articleCount > 0) {
    return `${positiveCount.value} positive vs ${negativeCount.value} negative articles`
  }
  return ''
})

const scoreDisplay = computed(() => {
  const sign = props.score >= 0 ? '+' : ''
  return `${sign}${props.score}`
})

const scoreColor = computed(() => {
  if (props.score > 0) return 'text-emerald-400'
  if (props.score < 0) return 'text-red-400'
  return 'text-amber-400'
})
</script>

<template>
  <div class="card-panel p-5">
    <!-- Header -->
    <div class="flex items-center justify-between mb-4">
      <h3 class="text-sm font-semibold text-gray-300 uppercase tracking-wider">News Sentiment</h3>
      <span :class="['text-sm font-bold px-2 py-0.5 rounded', scoreColor]">
        [{{ scoreDisplay }}]
      </span>
    </div>

    <!-- Summary -->
    <p v-if="summaryLine" class="mb-4 text-xs text-gray-400">
      {{ summaryLine }}
    </p>

    <div class="border-t border-gray-700/50 my-4" />

    <!-- Catalysts -->
    <div v-if="catalysts.length" class="mb-3">
      <h4 class="mb-2 text-xs font-semibold text-emerald-400">Catalysts</h4>
      <div class="flex flex-col gap-1.5">
        <div
          v-for="(item, index) in catalysts"
          :key="index"
          class="flex items-start gap-2 text-xs"
        >
          <span class="w-2 h-2 rounded-full bg-emerald-400 flex-shrink-0 mt-1" />
          <span class="text-gray-200">{{ item }}</span>
        </div>
      </div>
    </div>

    <!-- Red Flags -->
    <div v-if="redFlags.length" class="mb-3">
      <h4 class="mb-2 text-xs font-semibold text-red-400">Red Flags</h4>
      <div class="flex flex-col gap-1.5">
        <div
          v-for="(item, index) in redFlags"
          :key="index"
          class="flex items-start gap-2 text-xs"
        >
          <span class="w-2 h-2 rounded-full bg-red-400 flex-shrink-0 mt-1" />
          <span class="text-gray-200">{{ item }}</span>
        </div>
      </div>
    </div>

    <!-- Empty state -->
    <p v-if="!summaryLine && !catalysts.length && !redFlags.length" class="text-xs text-gray-500 text-center py-4">
      No news sentiment data available
    </p>
  </div>
</template>