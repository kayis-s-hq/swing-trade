<script setup lang="ts">
import { computed } from 'vue'

export interface MetricCardProps {
  title: string
  value: string | number
  icon?: string
  trend?: { value: string; isPositive: boolean }
  loading?: boolean
}

const props = withDefaults(defineProps<MetricCardProps>(), {
  icon: undefined,
  trend: undefined,
  loading: false,
})

const trendColor = computed(() => {
  if (!props.trend) return 'text-gray-400'
  return props.trend.isPositive ? 'text-success-600' : 'text-error-600'
})

const trendSign = computed(() => {
  if (!props.trend) return ''
  return props.trend.isPositive ? '+' : ''
})
</script>

<template>
  <div
    class="rounded-2xl border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-white/[0.03]"
  >
    <!-- Icon and Title -->
    <div class="flex items-start justify-between">
      <div>
        <h3 class="text-sm text-gray-500 dark:text-gray-400">{{ props.title }}</h3>
        <div v-if="props.loading" class="mt-3 h-8 w-24 animate-pulse rounded bg-gray-200 dark:bg-gray-700" />
        <h4 v-else class="mt-2 text-2xl font-bold text-gray-800 dark:text-white/90">
          {{ props.value }}
        </h4>
      </div>
      <div v-if="props.icon" class="flex h-12 w-12 items-center justify-center rounded-xl bg-gray-100 dark:bg-gray-800">
        <svg
          class="h-6 w-6 text-gray-800 dark:text-white/90"
          fill="none"
          stroke="currentColor"
          viewBox="0 0 24 24"
        >
          <path
            stroke-linecap="round"
            stroke-linejoin="round"
            stroke-width="2"
            :d="props.icon"
          />
        </svg>
      </div>
    </div>

    <!-- Trend Indicator -->
    <div v-if="props.trend && !props.loading" class="mt-3">
      <span class="text-xs font-medium" :class="trendColor">
        {{ trendSign }}{{ props.trend.value }}
      </span>
    </div>
  </div>
</template>
