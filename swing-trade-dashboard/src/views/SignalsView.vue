<template>
  <div class="p-4">
    <h1 class="text-2xl font-bold text-gray-800 dark:text-white mb-6">Signals</h1>

    <!-- Header with Generate Button -->
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
      <button
        @click="generateSignals"
        class="rounded-lg bg-indigo-600 px-6 py-2.5 text-sm font-medium text-white hover:bg-indigo-700"
      >
        Generate Signals
      </button>
    </div>

    <!-- Signals Grid -->
    <div class="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-3">
      <div
        v-for="signal in filteredSignals"
        :key="signal.id"
        class="rounded-2xl border border-gray-200 bg-white p-5 dark:border-gray-800 dark:bg-white/[0.03]"
      >
        <div class="flex items-start justify-between">
          <div class="flex items-center gap-3">
            <span
              class="inline-flex items-center rounded-full px-3 py-1 text-xs font-medium text-white"
              :class="{
                'bg-success-500': signal.signalType === 'BUY',
                'bg-error-500': signal.signalType === 'SELL',
                'bg-gray-500': signal.signalType === 'HOLD',
              }"
            >
              {{ signal.signalType }}
            </span>
            <h3 class="text-lg font-bold text-gray-800 dark:text-white">{{ signal.symbol }}</h3>
          </div>
          <div class="text-right">
            <div class="text-sm text-gray-500 dark:text-gray-400">Confidence</div>
            <div class="text-lg font-bold text-indigo-600 dark:text-indigo-400">{{ signal.confidence }}%</div>
          </div>
        </div>

        <div class="mt-4 space-y-2">
          <div class="flex justify-between text-sm">
            <span class="text-gray-500 dark:text-gray-400">Entry:</span>
            <span class="font-medium text-gray-700 dark:text-gray-300">${{ signal.entryPrice }}</span>
          </div>
          <div class="flex justify-between text-sm">
            <span class="text-gray-500 dark:text-gray-400">Stop Loss:</span>
            <span class="font-medium text-gray-700 dark:text-gray-300">${{ signal.stopLoss }}</span>
          </div>
          <div class="flex justify-between text-sm">
            <span class="text-gray-500 dark:text-gray-400">Target:</span>
            <span class="font-medium text-gray-700 dark:text-gray-300">${{ signal.target }}</span>
          </div>
        </div>

        <div class="mt-4">
          <div class="text-xs font-medium text-gray-500 dark:text-gray-400">Reasoning</div>
          <p class="mt-1 text-sm text-gray-700 dark:text-gray-300">{{ signal.reasoning }}</p>
        </div>

        <div class="mt-4 flex flex-wrap gap-2">
          <span
            v-for="indicator in signal.indicators"
            :key="indicator"
            class="rounded-full bg-gray-100 px-2.5 py-0.5 text-xs font-medium text-gray-600 dark:bg-gray-700 dark:text-gray-300"
          >
            {{ indicator }}
          </span>
        </div>
      </div>
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
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { getSignalList, generateSignals as generateSignalsApi } from '../api/client'
import type { Signal } from '../api/types'

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

onMounted(async () => {
  try {
    const response = await getSignalList()
    if (response.success && response.data) {
      signals.value = response.data
    }
  } catch (error) {
    console.error('Failed to load signals:', error)
  }
})

const generateSignals = async () => {
  try {
    await generateSignalsApi()
    // Refresh signals after generation
    const response = await getSignalList()
    if (response.success && response.data) {
      signals.value = response.data
    }
  } catch (error) {
    console.error('Failed to generate signals:', error)
  }
}
</script>
