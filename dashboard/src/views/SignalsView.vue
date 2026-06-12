<template>
  <div class="p-6 animate-fade-in">
    <!-- Page Header -->
    <div class="mb-6 flex items-center justify-between">
      <div>
        <h1 class="font-display text-2xl font-semibold text-text-primary">Signals</h1>
        <p class="mt-1 text-sm text-text-muted">Active scanning and signal generation</p>
      </div>
      <button @click="refreshSignals" class="flex items-center gap-2 rounded-md border border-border-subtle bg-bg-surface px-3 py-2 text-sm font-medium text-text-muted transition-colors hover:border-border-default hover:text-text-primary">
        <svg class="h-4 w-4 transition-transform duration-300 hover:rotate-180" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
        </svg>
        Refresh
      </button>
    </div>

    <div v-if="loading" class="flex items-center justify-center py-20">
      <LoadingSpinner message="Scanning for signals..." />
    </div>

    <ErrorMessage v-else-if="error" :message="errorMessage" :showRetry="true" retryText="Retry" @retry="refreshSignals" />

    <template v-else>
      <!-- Filters -->
      <div class="mb-4 flex items-center gap-3">
        <div class="flex rounded-md border border-border-subtle">
          <button v-for="dir in ['ALL', 'BUY', 'SELL']" :key="dir" @click="directionFilter = dir" class="px-3 py-1.5 text-xs font-medium transition-colors first:rounded-l-md last:rounded-r-md" :class="directionFilter === dir ? 'bg-brand-subtle text-brand' : 'text-text-muted hover:bg-bg-hover'">{{ dir }}</button>
        </div>
        <div class="flex rounded-md border border-border-subtle">
          <button v-for="st in ['ALL', 'ACTIVE', 'PENDING']" :key="st" @click="statusFilter = st" class="px-3 py-1.5 text-xs font-medium transition-colors first:rounded-l-md last:rounded-r-md" :class="statusFilter === st ? 'bg-brand-subtle text-brand' : 'text-text-muted hover:bg-bg-hover'">{{ st }}</button>
        </div>
      </div>

      <!-- Signal Grid -->
      <div class="grid grid-cols-1 gap-4 lg:grid-cols-2 xl:grid-cols-3">
        <SignalCard v-for="signal in filteredSignals" :key="signal.id" :signal="signal" />
      </div>

      <div v-if="filteredSignals.length === 0" class="flex flex-col items-center justify-center py-16">
        <p class="text-sm text-text-muted">No signals matching filter</p>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { getSignals } from '../api/client'
import type { Signal } from '../api/types'
import SignalCard from '../components/SignalCard.vue'
import ErrorMessage from '../components/ErrorMessage.vue'
import LoadingSpinner from '../components/LoadingSpinner.vue'

const loading = ref(true)
const error = ref(false)
const errorMessage = ref('')
const signals = ref<Signal[]>([])
const directionFilter = ref('ALL')
const statusFilter = ref('ALL')

const filteredSignals = computed(() => {
  return signals.value.filter(s => {
    const matchesDir = directionFilter.value === 'ALL' || s.direction === directionFilter.value
    const matchesStatus = statusFilter.value === 'ALL' || s.status === statusFilter.value
    return matchesDir && matchesStatus
  })
})

const refreshSignals = async () => {
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

onMounted(() => { refreshSignals() })
</script>
