<template>
  <div class="p-6 animate-fade-in">
    <!-- Page Header -->
    <div class="mb-6 flex items-center justify-between">
      <div>
        <h1 class="font-display text-2xl font-semibold text-text-primary">Signals</h1>
        <p class="mt-1 text-sm text-text-muted">Active scanning and signal generation</p>
      </div>
      <div class="flex gap-2">
        <button
          :disabled="generating"
          class="flex items-center gap-2 rounded-md bg-brand px-3 py-2 text-sm font-medium text-white transition-colors hover:bg-brand/90 disabled:opacity-50"
          @click="generateAll"
        >
          <svg v-if="generating" class="h-4 w-4 animate-spin" viewBox="0 0 24 24" fill="none">
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
              d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"
            />
          </svg>
          {{ generating ? 'Generating...' : 'Generate All' }}
        </button>
        <button
          class="flex items-center gap-2 rounded-md border border-border-subtle bg-bg-surface px-3 py-2 text-sm font-medium text-text-muted transition-colors hover:border-border-default hover:text-text-primary"
          @click="refreshSignals"
        >
          <svg
            class="h-4 w-4 transition-transform duration-300 hover:rotate-180"
            fill="none"
            stroke="currentColor"
            viewBox="0 0 24 24"
          >
            <path
              stroke-linecap="round"
              stroke-linejoin="round"
              stroke-width="2"
              d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15"
            />
          </svg>
          Refresh
        </button>
        <button
          v-if="selectedCount > 0"
          class="flex items-center gap-2 rounded-md border border-danger/50 bg-bg-surface px-3 py-2 text-sm font-medium text-danger transition-colors hover:border-danger hover:bg-danger/10"
          @click="clearSelected"
        >
          <svg class="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path
              stroke-linecap="round"
              stroke-linejoin="round"
              stroke-width="2"
              d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"
            />
          </svg>
          Clear {{ selectedCount }}
        </button>
        <button
          v-if="signals.length > 0"
          class="flex items-center gap-2 rounded-md border border-border-subtle bg-bg-surface px-3 py-2 text-sm font-medium text-text-muted transition-colors hover:border-danger hover:text-danger"
          @click="clearAll"
        >
          <svg class="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path
              stroke-linecap="round"
              stroke-linejoin="round"
              stroke-width="2"
              d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"
            />
          </svg>
          Clear All
        </button>
        <button
          v-if="selectedCount > 0"
          :disabled="executing"
          class="flex items-center gap-2 rounded-md bg-success px-3 py-2 text-sm font-medium text-white transition-colors hover:bg-success/90 disabled:opacity-50"
          @click="executeSelected"
        >
          <svg class="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path
              stroke-linecap="round"
              stroke-linejoin="round"
              stroke-width="2"
              d="M13 10V3L4 14h7v7l9-11h-7z"
            />
          </svg>
          {{ executing ? 'Executing...' : `Execute ${selectedCount}` }}
        </button>
      </div>
    </div>

    <div v-if="loading" class="flex items-center justify-center py-20">
      <LoadingSpinner message="Scanning for signals..." />
    </div>

    <ErrorMessage
      v-else-if="error"
      :message="errorMessage"
      :show-retry="true"
      retry-text="Retry"
      @retry="refreshSignals"
    />

    <template v-else>
      <!-- Generation progress -->
      <div v-if="generating" class="mb-4 card-panel p-4">
        <div class="mb-2 flex items-center justify-between">
          <span class="text-sm font-medium text-text-primary">Signal Generation</span>
          <span class="text-xs text-text-muted">{{ progressCurrent }}/{{ progressTotal }}</span>
        </div>
        <div class="h-2 rounded-full bg-bg-primary/50">
          <div
            class="h-full rounded-full bg-brand transition-all duration-300"
            :style="{ width: progressTotal > 0 ? (progressCurrent / progressTotal) * 100 + '%' : '0%' }"
          />
        </div>
        <p class="mt-2 text-xs text-text-muted">{{ progressMessage || 'Starting...' }}</p>
      </div>

      <!-- Filters -->
      <div class="mb-4 flex items-center justify-between">
        <div class="flex items-center gap-3">
          <label class="flex items-center gap-2 cursor-pointer select-none">
            <input
              type="checkbox"
              :checked="isSelectAll"
              class="h-4 w-4 rounded border-border-subtle text-brand focus:ring-brand bg-bg-surface"
              @change="toggleSelectAll"
            />
            <span class="text-xs font-medium text-text-muted"
              >Select all ({{ filteredSignals.length }})</span
            >
          </label>
          <div class="flex rounded-md border border-border-subtle">
            <button
              v-for="dir in ['ALL', 'BUY', 'SELL']"
              :key="dir"
              class="px-3 py-1.5 text-xs font-medium transition-colors first:rounded-l-md last:rounded-r-md"
              :class="
                directionFilter === dir
                  ? 'bg-brand-subtle text-brand'
                  : 'text-text-muted hover:bg-bg-hover'
              "
              @click="directionFilter = dir"
            >
              {{ dir }}
            </button>
          </div>
          <div class="flex rounded-md border border-border-subtle">
            <button
              v-for="st in ['ALL', 'ACTIVE', 'PENDING']"
              :key="st"
              class="px-3 py-1.5 text-xs font-medium transition-colors first:rounded-l-md last:rounded-r-md"
              :class="
                statusFilter === st
                  ? 'bg-brand-subtle text-brand'
                  : 'text-text-muted hover:bg-bg-hover'
              "
              @click="statusFilter = st"
            >
              {{ st }}
            </button>
          </div>
        </div>
        <span v-if="selectedCount > 0" class="text-xs font-medium text-brand"
          >{{ selectedCount }} selected</span
        >
      </div>

      <!-- Signal Grid -->
      <div class="grid grid-cols-1 gap-4 lg:grid-cols-2 xl:grid-cols-3">
        <div
          v-for="signal in filteredSignals"
          :key="signal.id"
          class="relative transition-all"
          :class="isSelected(signal.id) ? 'ring-2 ring-brand/50 rounded-lg' : ''"
        >
          <div class="absolute top-2 left-2 z-10">
            <label class="flex items-center gap-1 cursor-pointer">
              <input
                type="checkbox"
                :checked="isSelected(signal.id)"
                class="h-4 w-4 rounded border-border-subtle text-brand focus:ring-brand bg-bg-surface"
                @change="toggleSignal(signal.id)"
              />
            </label>
          </div>
          <div class="ml-7">
            <SignalCard :signal="signal" />
          </div>
        </div>
      </div>

      <div
        v-if="filteredSignals.length === 0"
        class="flex flex-col items-center justify-center py-16"
      >
        <p class="text-sm text-text-muted">No signals matching filter</p>
      </div>
    </template>

    <!-- Execution results toast -->
    <div v-if="execResult" class="fixed bottom-4 right-4 z-50 max-w-md">
      <div class="rounded-lg border border-border-subtle bg-bg-surface p-4 shadow-lg">
        <div class="flex items-start justify-between gap-3">
          <div>
            <p class="text-sm font-semibold text-text-primary">
              {{ execResult.success > 0 ? 'Executed' : 'Failed' }}
            </p>
            <p class="mt-1 text-xs text-text-muted">
              {{ execResult.success }} succeeded, {{ execResult.failed }} failed
              <span v-if="execResult.errors.length">{{
                execResult.errors.slice(0, 3).join('; ')
              }}</span>
            </p>
          </div>
          <button class="text-text-muted hover:text-text-primary" @click="execResult = null">
            <svg class="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path
                stroke-linecap="round"
                stroke-linejoin="round"
                stroke-width="2"
                d="M6 18L18 6M6 6l12 12"
              />
            </svg>
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import {
  getSignals,
  generateAllSignalsStream,
  executeTrade,
  clearAllSignals,
  clearSignalsForSymbol,
} from '../api/client'
import type { Signal } from '../api/types'
import SignalCard from '../components/SignalCard.vue'
import ErrorMessage from '../components/ErrorMessage.vue'
import LoadingSpinner from '../components/LoadingSpinner.vue'

const loading = ref(true)
const generating = ref(false)
const executing = ref(false)
const error = ref(false)
const errorMessage = ref('')
const signals = ref<Signal[]>([])
const directionFilter = ref('ALL')
const statusFilter = ref('ALL')
const selectedSignalIds = ref(new Set<string>())
const execResult = ref<{ success: number; failed: number; errors: string[] } | null>(null)
const progressMessage = ref('')
const progressCurrent = ref(0)
const progressTotal = ref(0)

const filteredSignals = computed(() => {
  return signals.value.filter((s) => {
    const matchesDir = directionFilter.value === 'ALL' || s.direction === directionFilter.value
    const matchesStatus = statusFilter.value === 'ALL' || s.status === statusFilter.value
    return matchesDir && matchesStatus
  })
})

const selectedCount = computed(() => selectedSignalIds.value.size)

const isSelectAll = computed(() => {
  if (filteredSignals.value.length === 0) return false
  return filteredSignals.value.every((s) => selectedSignalIds.value.has(s.id))
})

const isSelected = (id: string) => selectedSignalIds.value.has(id)

const toggleSignal = (id: string) => {
  if (selectedSignalIds.value.has(id)) {
    selectedSignalIds.value.delete(id)
  } else {
    selectedSignalIds.value.add(id)
  }
  // Trigger reactivity — Set is mutable
  selectedSignalIds.value = new Set(selectedSignalIds.value)
}

const toggleSelectAll = () => {
  const allIds = new Set(filteredSignals.value.map((s) => s.id))
  const allSelected =
    allIds.size > 0 && filteredSignals.value.every((s) => selectedSignalIds.value.has(s.id))

  if (allSelected) {
    allIds.forEach((id) => selectedSignalIds.value.delete(id))
  } else {
    filteredSignals.value.forEach((s) => selectedSignalIds.value.add(s.id))
  }
  selectedSignalIds.value = new Set(selectedSignalIds.value)
}

const executeSelected = async () => {
  executing.value = true
  const selected = signals.value.filter((s) => selectedSignalIds.value.has(s.id))
  let successCount = 0
  const errors: string[] = []

  for (const signal of selected) {
    // Calculate quantity: allocate ~Rs.1,00,000 per position
    const allocation = 100000
    const quantity = Math.max(1, Math.floor(allocation / signal.entryPrice))

    try {
      await executeTrade({
        symbol: signal.symbol,
        quantity,
        direction: signal.direction === 'BUY' ? 'LONG' : 'SHORT',
        orderType: 'MARKET',
        price: signal.entryPrice,
        target: signal.target,
        entryReason: `Signal: ${signal.symbol} — ${signal.reason.slice(0, 100)}`,
      })
      successCount++
    } catch {
      errors.push(signal.symbol)
    }
  }

  selectedSignalIds.value.clear()
  selectedSignalIds.value = new Set()
  execResult.value = { success: successCount, failed: selected.length - successCount, errors }

  // Auto-dismiss after 5s
  setTimeout(() => {
    execResult.value = null
  }, 5000)
  executing.value = false
}

const doRefresh = async () => {
  loading.value = true
  error.value = false
  errorMessage.value = ''
  try {
    const res = await getSignals()
    if (res.success && res.data) signals.value = res.data
    if (res.error) throw new Error(res.error)
  } catch (err: unknown) {
    errorMessage.value = err instanceof Error ? err.message : 'Failed to load signals'
    error.value = true
  } finally {
    loading.value = false
  }
}

const generateAll = async () => {
  generating.value = true
  error.value = false
  errorMessage.value = ''
  progressMessage.value = ''
  progressCurrent.value = 0
  progressTotal.value = 0
  const allSignals: Signal[] = []

  try {
    for await (const progress of generateAllSignalsStream()) {
      progressCurrent.value = progress.current ?? progressCurrent.value
      progressTotal.value = progress.total ?? progressTotal.value

      if (progress.eventType === 'GENERATING') {
        progressMessage.value = `Analyzing ${progress.symbol} (${progressCurrent.value}/${progressTotal.value})`
      } else if (progress.eventType === 'SENTIMENT_ANALYZING') {
        progressMessage.value = `Analyzing sentiment for ${progress.symbol} (${progressCurrent.value}/${progressTotal.value})`
      } else if (progress.eventType === 'SIGNAL_DONE' && progress.signal) {
        allSignals.push(progress.signal)
        signals.value = allSignals
      } else if (progress.eventType === 'SKIPPED') {
        progressMessage.value = `${progress.symbol}: ${progress.message}`
      } else if (progress.eventType === 'COMPLETE') {
        progressMessage.value = progress.message
      }
    }
    console.log(`Signal generation complete: ${allSignals.length} signals`)
  } catch (err: unknown) {
    errorMessage.value = err instanceof Error ? err.message : 'Signal generation failed'
    error.value = true
  } finally {
    generating.value = false
    loading.value = false
  }
}

const clearAll = async () => {
  if (!confirm('Clear all signals?')) return
  try {
    const res = await clearAllSignals()
    if (res.success) {
      signals.value = []
      selectedSignalIds.value.clear()
    }
  } catch {
    errorMessage.value = 'Failed to clear signals'
    error.value = true
  }
}

const clearSelected = async () => {
  const selected = signals.value.filter((s) => selectedSignalIds.value.has(s.id))
  for (const signal of selected) {
    try {
      await clearSignalsForSymbol(signal.symbol)
    } catch {
      // skip
    }
  }
  selectedSignalIds.value.clear()
  selectedSignalIds.value = new Set()
  await doRefresh()
}

const refreshSignals = doRefresh

onMounted(() => {
  refreshSignals()
})
</script>
