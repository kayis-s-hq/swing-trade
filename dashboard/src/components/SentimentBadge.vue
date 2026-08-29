<template>
  <div class="inline-flex flex-col items-center gap-1">
    <span
      class="inline-flex items-center gap-1 rounded-full px-3 py-1 text-xs font-semibold"
      :class="badgeClass"
    >
      {{ emoji }} {{ score }}
    </span>
    <span v-if="confidence != null" class="text-[10px] text-text-muted">
      {{ Math.round(confidence * 100) }}% confidence
    </span>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  score: 'POSITIVE' | 'NEUTRAL' | 'NEGATIVE'
  confidence: number
}>()

const emoji = computed(
  () =>
    ({
      POSITIVE: '\u{1F7E2}',
      NEUTRAL: '\u{1F7E1}',
      NEGATIVE: '\u{1F534}',
    })[props.score]
)

const badgeClass = computed(
  () =>
    ({
      POSITIVE: 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-400',
      NEUTRAL: 'bg-amber-100 text-amber-800 dark:bg-amber-900/30 dark:text-amber-400',
      NEGATIVE: 'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-400',
    })[props.score]
)
</script>
