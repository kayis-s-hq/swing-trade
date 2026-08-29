<template>
  <div class="space-y-4">
    <div
      v-for="item in items"
      :key="item.id"
      class="relative flex gap-4"
    >
      <!-- Timeline line -->
      <div class="absolute left-3 top-0 bottom-0 w-px bg-border-subtle" />
      <!-- Timeline dot -->
      <div
        class="relative z-10 flex h-6 w-6 flex-shrink-0 items-center justify-center rounded-full text-xs font-bold"
        :class="dotClass(item.score)"
      >
        {{ item.score[0] }}
      </div>

      <div class="flex-1 min-w-0">
        <div class="flex items-center gap-2 mb-1">
          <span class="text-sm font-semibold text-text-primary">{{ item.symbol }}</span>
          <span class="text-xs text-text-muted">{{ item.date }}</span>
        </div>
        <SentimentBadge
          :score="item.score"
          :confidence="item.confidence"
        />
        <p class="mt-2 text-sm text-text-secondary line-clamp-2">
          {{ item.summary }}
        </p>
        <div
          v-if="item.redFlags.length"
          class="mt-2"
        >
          <span class="text-xs font-medium text-danger">Red flags:</span>
          <ul class="mt-1 list-disc pl-4 text-xs text-text-muted">
            <li
              v-for="rf in item.redFlags"
              :key="rf"
            >
              {{ rf }}
            </li>
          </ul>
        </div>
        <div
          v-if="item.catalysts.length"
          class="mt-2"
        >
          <span class="text-xs font-medium text-success">Catalysts:</span>
          <ul class="mt-1 list-disc pl-4 text-xs text-text-muted">
            <li
              v-for="c in item.catalysts"
              :key="c"
            >
              {{ c }}
            </li>
          </ul>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import SentimentBadge from './SentimentBadge.vue'
import type { SentimentResult } from '../api/types'

defineProps<{
  items: SentimentResult[]
}>()

const dotClass = (score: string) =>
  ({
    POSITIVE: 'bg-green-500 text-white',
    NEUTRAL: 'bg-amber-500 text-white',
    NEGATIVE: 'bg-red-500 text-white',
  })[score] as string
</script>
