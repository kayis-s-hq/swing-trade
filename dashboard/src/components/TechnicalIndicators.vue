<script setup lang="ts">
import { computed } from 'vue'

interface Props {
  score: number
  signal: 'BUY' | 'SELL' | 'HOLD'
  confidence: number
  indicators: string[]
}

const props = defineProps<Props>()

const confidencePercent = computed(() => Math.round(props.confidence * 100))

const signalColor = computed(() => {
  switch (props.signal) {
    case 'BUY': return 'text-emerald-400 bg-emerald-400/10 border-emerald-400/30'
    case 'SELL': return 'text-red-400 bg-red-400/10 border-red-400/30'
    case 'HOLD': return 'text-amber-400 bg-amber-400/10 border-amber-400/30'
  }
})

const confidenceColor = computed(() => {
  if (props.confidence >= 0.7) return 'bg-emerald-400'
  if (props.confidence >= 0.4) return 'bg-amber-400'
  return 'bg-red-400'
})

const isBullish = (indicator: string) => {
  const parts = indicator.split(': ')
  if (parts.length < 2) return false
  const value = parts[1].toLowerCase()
  return value.startsWith('bullish') || value.startsWith('above') || value.startsWith('proximate') || value.startsWith('positive')
}

const isBearish = (indicator: string) => {
  const parts = indicator.split(': ')
  if (parts.length < 2) return false
  const value = parts[1].toLowerCase()
  return value.startsWith('bearish') || value.startsWith('below') || value.startsWith('distant') || value.startsWith('negative')
}
</script>

<template>
  <div class="card-panel p-5">
    <!-- Header -->
    <div class="flex items-center justify-between mb-4">
      <h3 class="text-sm font-semibold text-gray-300 uppercase tracking-wider">Technical Signal</h3>
      <span :class="['text-xs font-bold px-2 py-0.5 rounded border', signalColor]">
        {{ signal }}
      </span>
    </div>

    <!-- Score + Confidence -->
    <div class="flex items-center gap-3 mb-4">
      <span :class="['text-lg font-bold', score > 0 ? 'text-emerald-400' : score < 0 ? 'text-red-400' : 'text-amber-400']">
        {{ score > 0 ? '+' : '' }}{{ score }}
      </span>
      <div class="flex-1">
        <div class="flex justify-between text-xs text-gray-500 mb-1">
          <span>Confidence</span>
          <span>{{ confidencePercent }}%</span>
        </div>
        <div class="h-1.5 bg-gray-700 rounded-full overflow-hidden">
          <div
            :class="['h-full rounded-full transition-all', confidenceColor]"
            :style="{ width: `${confidencePercent}%` }"
          />
        </div>
      </div>
    </div>

    <div class="border-t border-gray-700/50 my-4" />

    <!-- Indicator Grid -->
    <div v-if="indicators.length" class="space-y-2">
      <div
        v-for="indicator in indicators"
        :key="indicator"
        class="flex items-center gap-2 text-xs"
      >
        <span
          :class="['w-2 h-2 rounded-full flex-shrink-0', isBullish(indicator) ? 'bg-emerald-400' : isBearish(indicator) ? 'bg-red-400' : 'bg-gray-500']"
        />
        <span class="flex-1 flex">
          <span v-if="indicator.includes(': ')" class="text-gray-400 flex-shrink-0 w-24 truncate">
            {{ indicator.split(': ')[0] }}
          </span>
          <span v-else class="text-gray-400 flex-shrink-0 w-24 truncate">{{ indicator }}</span>
          <span v-if="indicator.includes(': ')" class="text-gray-200 truncate">
            {{ indicator.split(': ')[1] }}
          </span>
          <span v-else class="text-gray-200 truncate" />
        </span>
      </div>
    </div>

    <div v-else class="text-xs text-gray-500 text-center py-4">
      No indicators available
    </div>
  </div>
</template>