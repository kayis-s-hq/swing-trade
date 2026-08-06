<template>
  <div class="card-panel p-5">
    <h3 class="mb-4 text-sm font-semibold text-text-primary">Analysis Context</h3>

    <!-- Technical Signal Agreement -->
    <div class="mb-4">
      <h4 class="mb-2 text-xs font-semibold uppercase tracking-wider text-text-muted">
        Technical Signal
      </h4>
      <div v-if="signal" class="flex items-center gap-3">
        <span
          class="inline-flex items-center gap-1 rounded-full px-3 py-1 text-xs font-semibold"
          :class="signalBadgeClass"
        >
          {{ signal.direction }}
        </span>
        <span class="text-xs font-medium" :class="agreementClass">
          {{ agreementText }}
        </span>
      </div>
      <p v-else class="text-xs text-text-muted">No technical signal available</p>
    </div>

    <!-- Confidence Breakdown -->
    <div class="mb-4">
      <h4 class="mb-2 text-xs font-semibold uppercase tracking-wider text-text-muted">
        Confidence Breakdown
      </h4>
      <div class="grid grid-cols-3 gap-3 text-center">
        <div class="rounded-lg bg-bg-primary p-2">
          <div class="text-lg font-semibold text-text-primary">{{ confidencePct }}%</div>
          <div class="text-[10px] text-text-muted">Confidence</div>
        </div>
        <div class="rounded-lg bg-bg-primary p-2">
          <div class="text-lg font-semibold text-text-primary">
            {{ articleCount }}
          </div>
          <div class="text-[10px] text-text-muted">Articles</div>
        </div>
        <div class="rounded-lg bg-bg-primary p-2">
          <div class="text-lg font-semibold text-text-primary">
            {{ (props.sentiment.summary ?? '').length }} chars
          </div>
          <div class="text-[10px] text-text-muted">Summary</div>
        </div>
      </div>
    </div>

    <!-- Historical Trend -->
    <div>
      <h4 class="mb-2 text-xs font-semibold uppercase tracking-wider text-text-muted">
        Recent Trend
      </h4>
      <div v-if="trend.length" class="flex items-center gap-1">
        <div
          v-for="(item, i) in trend"
          :key="i"
          class="h-6 w-4 rounded-t-sm transition-colors"
          :class="trendColor(item.score)"
          :title="`${item.date}: ${item.score}`"
        />
      </div>
      <p v-else class="text-xs text-text-muted">No historical data</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { Signal } from '../api/types'
import type { SentimentResult } from '../api/types'

const props = defineProps<{
  signal: Signal | null
  sentiment: SentimentResult
  articleCount: number
  trend: SentimentResult[]
}>()

const confidencePct = computed(() => Math.round((props.sentiment.confidence ?? 0) * 100))

const agreementText = computed(() => {
  if (!props.signal) return 'No signal to compare'
  const sentimentMatchesBuy = props.sentiment.score === 'POSITIVE'
  const sentimentMatchesSell = props.sentiment.score === 'NEGATIVE'

  if (props.signal.direction === 'BUY' && sentimentMatchesBuy) return 'Agrees — bullish sentiment'
  if (props.signal.direction === 'SELL' && sentimentMatchesSell) return 'Agrees — bearish sentiment'
  if (props.signal.direction === 'BUY' && sentimentMatchesSell)
    return 'Conflicts — bearish sentiment vs buy signal'
  if (props.signal.direction === 'SELL' && sentimentMatchesBuy)
    return 'Conflicts — bullish sentiment vs sell signal'
  return 'Neutral — no strong sentiment'
})

const agreementClass = computed(() => {
  if (!props.signal) return 'text-text-muted'
  const sentimentMatchesBuy = props.sentiment.score === 'POSITIVE'
  const sentimentMatchesSell = props.sentiment.score === 'NEGATIVE'

  if (props.signal.direction === 'BUY' && sentimentMatchesBuy) return 'text-success'
  if (props.signal.direction === 'SELL' && sentimentMatchesSell) return 'text-success'
  if (props.signal.direction === 'BUY' && sentimentMatchesSell) return 'text-danger'
  if (props.signal.direction === 'SELL' && sentimentMatchesBuy) return 'text-danger'
  return 'text-text-muted'
})

const signalBadgeClass = computed(
  () =>
    ({
      BUY: 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-400',
      SELL: 'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-400',
      HOLD: 'bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-400',
    })[props.signal?.direction ?? 'BUY']
)

const trendColor = (score: string) =>
  ({
    POSITIVE: 'bg-green-500',
    NEUTRAL: 'bg-amber-500',
    NEGATIVE: 'bg-red-500',
  })[score] as string
</script>
