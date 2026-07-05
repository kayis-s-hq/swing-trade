<template>
  <div class="p-6 animate-fade-in">
    <!-- Page Header -->
    <div class="mb-6 flex items-center justify-between">
      <div>
        <h1 class="font-display text-2xl font-semibold text-text-primary">LLM Accuracy Monitoring</h1>
        <p class="mt-1 text-sm text-text-muted">Track sentiment analysis accuracy against actual trade outcomes</p>
      </div>
    </div>

    <!-- Error State -->
    <ErrorMessage v-if="loadError" :message="loadError" :show-retry="true" @retry="loadAccuracy" />

    <!-- Accuracy Stats -->
    <div v-else class="space-y-6">
      <!-- Overall Accuracy -->
      <div class="card-panel p-5">
        <h3 class="mb-4 text-sm font-semibold text-text-primary">Overall Accuracy</h3>

        <div v-if="!stats" class="flex justify-center py-8">
          <LoadingSpinner message="Loading accuracy data..." />
        </div>

        <div v-else>
          <!-- Large percentage display -->
          <div class="mb-4 flex items-end gap-3">
            <span class="text-5xl font-bold" :class="stats.accuracy_pct >= 60 ? 'text-success' : stats.accuracy_pct >= 40 ? 'text-warning' : 'text-danger'">
              {{ stats.accuracy_pct.toFixed(1) }}%
            </span>
            <span class="mb-1.5 text-sm text-text-muted">
              {{ stats.correct }}/{{ stats.total }} correct
            </span>
          </div>

          <!-- Progress bar -->
          <div class="mb-4 h-3 w-full overflow-hidden rounded-full bg-bg-hover">
            <div
              class="h-full rounded-full transition-all duration-500"
              :class="stats.accuracy_pct >= 60 ? 'bg-success' : stats.accuracy_pct >= 40 ? 'bg-warning' : 'bg-danger'"
              :style="{ width: `${stats.accuracy_pct}%` }"
            />
          </div>

          <!-- Breakdown grid -->
          <div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <!-- By Sentiment -->
            <div>
              <h4 class="mb-2 text-xs font-semibold uppercase tracking-wider text-text-muted">By Sentiment</h4>
              <div class="space-y-2">
                <div v-for="(count, sentiment) in stats.by_sentiment" :key="sentiment" class="flex items-center gap-3">
                  <span class="w-20 text-sm font-medium text-text-secondary">{{ sentiment }}</span>
                  <div class="flex-1 h-2 overflow-hidden rounded-full bg-bg-hover">
                    <div
                      class="h-full rounded-full"
                      :class="sentiment === 'POSITIVE' ? 'bg-green-500' : sentiment === 'NEGATIVE' ? 'bg-red-500' : 'bg-amber-500'"
                      :style="{ width: `${stats.total > 0 ? (count / stats.total) * 100 : 0}%` }"
                    />
                  </div>
                  <span class="text-sm text-text-muted">{{ count }}</span>
                </div>
              </div>
            </div>

            <!-- By Symbol -->
            <div>
              <h4 class="mb-2 text-xs font-semibold uppercase tracking-wider text-text-muted">By Symbol</h4>
              <div class="space-y-2">
                <div v-for="(count, symbol) in stats.by_symbol" :key="symbol" class="flex items-center gap-3">
                  <span class="w-24 truncate text-sm font-medium text-text-secondary">{{ symbol }}</span>
                  <div class="flex-1 h-2 overflow-hidden rounded-full bg-bg-hover">
                    <div
                      class="h-full rounded-full bg-brand"
                      :style="{ width: `${stats.total > 0 ? (count / stats.total) * 100 : 0}%` }"
                    />
                  </div>
                  <span class="text-sm text-text-muted">{{ count }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Info -->
      <div class="card-panel p-4">
        <p class="text-xs text-text-muted">
          Accuracy is determined by matching sentiment direction against trade outcomes.
          POSITIVE sentiment is marked correct when the trade hits the target.
          NEGATIVE sentiment is marked correct when the trade hits the stop loss.
        </p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getAccuracyStats } from '../api/client'
import type { SentimentAccuracyStats } from '../api/types'
import LoadingSpinner from '../components/LoadingSpinner.vue'
import ErrorMessage from '../components/ErrorMessage.vue'

const stats = ref<SentimentAccuracyStats | null>(null)
const loadError = ref('')

const loadAccuracy = async () => {
  loadError.value = ''
  stats.value = null
  const res = await getAccuracyStats()
  if (res.success && res.data) {
    stats.value = res.data
  } else {
    loadError.value = res.error || 'Failed to load accuracy data'
  }
}

onMounted(() => { loadAccuracy() })
</script>