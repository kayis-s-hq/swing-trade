<template>
  <div class="p-4">
    <!-- Header -->
    <div class="mb-6 flex items-center justify-between">
      <h1 class="text-2xl font-bold text-gray-800 dark:text-white">Signals</h1>
      <div class="flex gap-2">
        <button
          @click="refreshSignals"
          class="rounded-lg bg-gray-100 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-200 dark:bg-gray-800 dark:text-gray-300 dark:hover:bg-gray-700"
        >
          Refresh
        </button>
        <button
          @click="generateSignals"
          class="rounded-lg bg-indigo-600 px-6 py-2.5 text-sm font-medium text-white hover:bg-indigo-700"
        >
          Generate Signals
        </button>
      </div>
    </div>

    <!-- Loading State -->
    <div v-if="loading" class="flex items-center justify-center py-12">
      <div class="text-center">
        <svg class="mx-auto h-12 w-12 animate-spin text-indigo-600" fill="none" viewBox="0 0 24 24">
          <circle
            class="opacity-25"
            cx="12"
            cy="12"
            r="10"
            stroke="currentColor"
            stroke-width="4"
          />
          <path
            class="opacity-75"
            fill="currentColor"
            d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"
          />
        </svg>
        <p class="mt-2 text-sm text-gray-500 dark:text-gray-400">Loading signals...</p>
      </div>
    </div>

    <!-- Error State -->
    <div v-else-if="error" class="rounded-2xl border border-error-200 bg-error-50 p-6 dark:border-error-900 dark:bg-error-900/20">
      <div class="flex items-center gap-3">
        <svg class="h-8 w-8 text-error-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
        </svg>
        <div>
          <h3 class="text-lg font-medium text-error-800 dark:text-error-200">Error Loading Signals</h3>
          <p class="text-sm text-error-600 dark:text-error-300">{{ errorMessage }}</p>
          <button
            @click="refreshSignals"
            class="mt-2 rounded bg-error-600 px-3 py-1 text-sm font-medium text-white hover:bg-error-700"
          >
            Retry
          </button>
        </div>
      </div>
    </div>

    <!-- Signals Content -->
    <div v-else>
      <!-- Filters -->
      <div class="mb-6 flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div class="flex gap-2">
          <select
            v-model="signalTypeFilter"
            class="rounded-lg border border-gray-200 bg-white px-4 py-2 text-sm focus:border-indigo-500 focus:outline-none dark:border-gray-700 dark:bg-gray-700 dark:text-white"
          >
            <option value="">All Types</option>
            <option value="BUY">Buy</option>
            <option value="SELL">Sell</option>
            <option value="HOLD">Hold</option>
          </select>
          <input
            type="number"
            v-model.number="confidenceFilter"
            placeholder="Min Confidence (%)"
            class="w-32 rounded-lg border border-gray-200 bg-white px-4 py-2 text-sm focus:border-indigo-500 focus:outline-none dark:border-gray-700 dark:bg-gray-700 dark:text-white"
          />
        </div>
      </div>

      <!-- Signals Grid -->
      <div class="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-3">
        <SignalCard
          v-for="signal in filteredSignals"
          :key="signal.id"
          :signal="signal"
        />
        <div v-if="filteredSignals.length === 0" class="col-span-full py-12 text-center text-gray-500 dark:text-gray-400">
          <p>No signals found</p>
          <button
            @click="generateSignals"
            class="mt-2 text-indigo-600 hover:text-indigo-800 dark:text-indigo-400"
          >
            Generate signals now
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { getSignalList, generateSignals as generateSignalsApi } from '../api/client'
import type { Signal } from '../api/types'
import SignalCard from '../components/SignalCard.vue'

const loading = ref(true)
const error = ref(false)
const errorMessage = ref('')

const signals = ref<Signal[]>([])
const signalTypeFilter = ref('')
const confidenceFilter = ref<number | null>(null)

const filteredSignals = computed(() => {
  return signals.value.filter((signal) => {
    const matchesType = !signalTypeFilter.value || signal.signalType === signalTypeFilter.value
    const matchesConfidence = !confidenceFilter.value || signal.confidence >= confidenceFilter.value
    return matchesType && matchesConfidence
  })
})

const refreshSignals = async () => {
  loading.value = true
  error.value = false
  errorMessage.value = ''

  try {
    const response = await getSignalList()
    if (response.success && response.data) {
      signals.value = response.data
    } else if (response.error) {
      throw new Error(response.error)
    }
  } catch (err: unknown) {
    const errorMsg = err instanceof Error ? err.message : 'Failed to load signals'
    errorMessage.value = errorMsg
    error.value = true
    console.error('SignalsView error:', err)
  } finally {
    loading.value = false
  }
}

const generateSignals = async () => {
  try {
    await generateSignalsApi()
    // Refresh signals after generation
    await refreshSignals()
  } catch (err: unknown) {
    const errorMsg = err instanceof Error ? err.message : 'Failed to generate signals'
    errorMessage.value = errorMsg
    error.value = true
    console.error('Failed to generate signals:', err)
  }
}

onMounted(() => {
  refreshSignals()
})
</script>
