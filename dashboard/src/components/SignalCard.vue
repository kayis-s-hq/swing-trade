<template>
  <div
    class="signal-card card-panel cursor-pointer transition-all hover:border-border-default hover:shadow-lg"
    role="button"
    tabindex="0"
    :aria-label="`View full analysis for ${signal.symbol}`"
    @click="navigateToSentiment"
    @keydown.enter="navigateToSentiment"
    @keydown.space.prevent="navigateToSentiment"
  >
    <!-- Header -->
    <div class="flex items-center justify-between border-b border-border-subtle/50 px-4 py-3">
      <div class="flex min-w-0 items-center gap-2">
        <span class="truncate text-sm font-bold tracking-wide text-text-primary">{{
          signal.symbol
        }}</span>
        <span
          class="inline-flex items-center rounded-full px-2 py-0.5 text-xs font-semibold"
          :class="
            signal.direction === 'BUY'
              ? 'bg-success-bg text-success'
              : signal.direction === 'SELL'
                ? 'bg-danger-bg text-danger'
                : 'bg-info-bg text-info'
          "
          >{{ signal.direction }}</span
        >
        <span
          v-if="sentimentBadge"
          class="inline-flex items-center rounded-full px-2 py-0.5 text-xs font-semibold"
          :class="sentimentBadge.class"
          >{{ sentimentBadge.text }}</span
        >
        <span
          v-if="strategyLabel"
          class="inline-flex items-center rounded-full bg-brand-subtle px-2 py-0.5 text-xs font-medium text-brand"
          >{{ strategyLabel }}</span
        >
      </div>
      <div class="flex shrink-0 items-center gap-3">
        <span
          class="flex items-center gap-1.5 text-[11px] font-semibold uppercase tracking-wide"
          :class="statusColor"
        >
          <span class="h-1.5 w-1.5 rounded-full bg-current" aria-hidden="true" />
          {{ signal.status }}
        </span>
        <label
          class="signal-select-control"
          :class="{ 'signal-select-control-selected': selected }"
          :aria-label="selected ? `Deselect ${signal.symbol}` : `Select ${signal.symbol}`"
          @click.stop
        >
          <input
            type="checkbox"
            :checked="selected"
            class="sr-only"
            @change="emit('toggle-selection')"
          />
          <span class="signal-select-check" aria-hidden="true">
            <svg
              v-if="selected"
              class="h-3 w-3"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path
                stroke-linecap="round"
                stroke-linejoin="round"
                stroke-width="3"
                d="m5 12 4 4L19 6"
              />
            </svg>
          </span>
        </label>
      </div>
    </div>

    <!-- Body -->
    <div class="px-4 py-4">
      <!-- Confidence -->
      <div class="mb-3 flex items-center gap-2">
        <span class="text-xs font-medium text-text-muted">Confidence</span>
        <div class="h-1.5 flex-1 overflow-hidden rounded-full bg-bg-primary/50">
          <div
            class="h-full rounded-full bg-brand transition-[width] duration-300 ease-out"
            :style="{ width: signal.confidence + '%' }"
          />
        </div>
        <span class="text-xs font-semibold text-text-primary">{{ signal.confidence }}%</span>
      </div>

      <!-- Price Data -->
      <div class="grid grid-cols-3 gap-3 text-xs">
        <div>
          <p class="text-text-muted">Entry</p>
          <p class="mt-0.5 font-semibold text-text-primary">
            {{ signal.entryPrice ? '₹' + signal.entryPrice : '—' }}
          </p>
        </div>
        <div>
          <p class="text-text-muted">Stop Loss</p>
          <p class="mt-0.5 font-semibold text-danger">
            {{ signal.stopLoss ? '₹' + signal.stopLoss : '—' }}
          </p>
        </div>
        <div>
          <p class="text-text-muted">Target</p>
          <p class="mt-0.5 font-semibold text-success">
            {{ signal.target ? '₹' + signal.target : '—' }}
          </p>
        </div>
      </div>

      <!-- R:R -->
      <div class="mt-3 flex items-center justify-between border-t border-border-subtle/50 pt-3">
        <span class="text-xs font-medium text-text-muted">Risk:Reward</span>
        <span
          class="text-sm font-bold"
          :class="signal.riskReward >= 2 ? 'text-brand' : 'text-text-primary'"
          >{{ signal.riskReward ? '1:' + signal.riskReward.toFixed(2) : '—' }}</span
        >
      </div>
    </div>

    <!-- Indicators -->
    <div
      v-if="signal.indicators?.length"
      class="flex flex-wrap gap-1.5 border-t border-border-subtle/50 px-4 py-2.5"
    >
      <span
        v-for="indicator in signal.indicators"
        :key="indicator"
        class="rounded bg-bg-primary/50 px-1.5 py-0.5 text-[11px] font-medium text-text-muted"
        >{{ indicator }}</span
      >
    </div>

    <!-- Technical Reason -->
    <div class="border-t border-border-subtle/50 px-4 py-3">
      <p class="text-xs leading-relaxed text-text-secondary">
        {{ signal.reason }}
      </p>
    </div>

    <!-- Sentiment Reasoning -->
    <div v-if="signal.sentimentReasoning" class="border-t border-border-subtle/50 px-4 py-3">
      <p class="mb-1 text-xs font-semibold uppercase tracking-wider text-text-muted">Sentiment</p>
      <p class="text-xs leading-relaxed text-text-secondary">
        {{ signal.sentimentReasoning }}
      </p>
    </div>

    <!-- Click hint -->
    <div class="border-t border-border-subtle/50 px-4 py-2">
      <p class="text-center text-[10px] text-text-muted/60">
        Click for full sentiment analysis <span aria-hidden="true">→</span>
      </p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()

const props = defineProps<{
  selected?: boolean
  signal: {
    symbol: string
    direction: 'BUY' | 'SELL' | 'HOLD'
    confidence: number
    reason: string
    entryPrice: number
    stopLoss: number
    target: number
    riskReward: number
    status: string
    strategy?: string
    indicators?: string[]
    sentimentScore?: string
    sentimentReasoning?: string
  }
}>()

const emit = defineEmits<{
  'toggle-selection': []
}>()

const navigateToSentiment = () => {
  router.push({ name: 'Sentiment', query: { symbol: props.signal.symbol } })
}

const statusColor = computed(() => {
  const colors: Record<string, string> = {
    ACTIVE: 'text-success',
    PENDING: 'text-warning',
    EXECUTED: 'text-info',
    EXPIRED: 'text-text-muted',
  }
  return colors[props.signal.status] ?? 'text-text-muted'
})

const strategyLabel = computed(() => {
  const labels: Record<string, string> = {
    PRICE_ACTION: 'Price Action',
    DEFAULT: 'Technical',
  }
  return props.signal.strategy ? (labels[props.signal.strategy] ?? props.signal.strategy) : ''
})

const sentimentBadge = computed(() => {
  const s = props.signal.sentimentScore
  if (!s) return null
  const map: Record<string, { text: string; class: string }> = {
    POSITIVE: { text: 'POS', class: 'bg-success-bg text-success' },
    NEUTRAL: { text: 'NEUTRAL', class: 'bg-info-bg text-info' },
    NEGATIVE: { text: 'NEG', class: 'bg-danger-bg text-danger' },
    UNKNOWN: { text: 'UNK', class: 'bg-warning-bg text-warning' },
  }
  return map[s] ?? null
})
</script>
