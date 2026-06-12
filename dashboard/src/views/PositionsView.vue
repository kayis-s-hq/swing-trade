<template>
  <div class="p-6 animate-fade-in">
    <!-- Page Header -->
    <div class="mb-6 flex items-center justify-between">
      <div>
        <h1 class="font-display text-2xl font-semibold text-text-primary">Positions</h1>
        <p class="mt-1 text-sm text-text-muted">Active and closed positions</p>
      </div>
      <button @click="refreshPositions" class="flex items-center gap-2 rounded-md border border-border-subtle bg-bg-surface px-3 py-2 text-sm font-medium text-text-muted transition-colors hover:border-border-default hover:text-text-primary">
        <svg class="h-4 w-4 transition-transform duration-300 hover:rotate-180" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
        </svg>
        Refresh
      </button>
    </div>

    <div v-if="loading" class="flex items-center justify-center py-20">
      <LoadingSpinner message="Loading positions..." />
    </div>

    <ErrorMessage v-else-if="error" :message="errorMessage" :showRetry="true" retryText="Retry" @retry="refreshPositions" />

    <template v-else>
      <!-- Filters -->
      <div class="mb-4 flex items-center gap-3">
        <input v-model="searchQuery" placeholder="Search symbol..." class="w-56 rounded-md border border-border-subtle bg-bg-surface px-3 py-2 text-sm text-text-primary placeholder:text-text-muted/60 transition-colors focus:border-brand/50 focus:outline-none" />
        <div class="flex rounded-md border border-border-subtle">
          <button v-for="filter in ['ALL', 'OPEN', 'CLOSED']" :key="filter" @click="statusFilter = filter" class="px-3 py-1.5 text-xs font-medium transition-colors first:rounded-l-md last:rounded-r-md" :class="statusFilter === filter ? 'bg-brand-subtle text-brand' : 'text-text-muted hover:bg-bg-hover'">{{ filter }}</button>
        </div>
      </div>

      <!-- Table -->
      <div class="card-panel">
        <div class="w-full overflow-x-auto">
          <table class="min-w-full">
            <thead>
              <tr class="border-b border-border-subtle bg-bg-primary/50">
                <th class="px-5 py-3 text-left text-xs font-semibold uppercase tracking-wider text-text-muted">Symbol</th>
                <th class="px-5 py-3 text-left text-xs font-semibold uppercase tracking-wider text-text-muted">Entry</th>
                <th class="px-5 py-3 text-right text-xs font-semibold uppercase tracking-wider text-text-muted">Qty</th>
                <th class="px-5 py-3 text-right text-xs font-semibold uppercase tracking-wider text-text-muted">Current</th>
                <th class="px-5 py-3 text-right text-xs font-semibold uppercase tracking-wider text-text-muted">Stop Loss</th>
                <th class="px-5 py-3 text-right text-xs font-semibold uppercase tracking-wider text-text-muted">Target</th>
                <th class="px-5 py-3 text-left text-xs font-semibold uppercase tracking-wider text-text-muted">Status</th>
                <th class="px-5 py-3 text-right text-xs font-semibold uppercase tracking-wider text-text-muted">P&L</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-border-subtle/50">
              <tr v-for="pos in filteredPositions" :key="pos.id" class="transition-colors hover:bg-bg-hover">
                <td class="px-5 py-4 text-sm font-semibold text-text-primary">{{ pos.symbol }}</td>
                <td class="px-5 py-4 text-sm text-text-secondary">${{ pos.entryPrice }}</td>
                <td class="px-5 py-4 text-right text-sm text-text-secondary">{{ pos.quantity }}</td>
                <td class="px-5 py-4 text-right text-sm text-text-secondary">${{ pos.currentPrice }}</td>
                <td class="px-5 py-4 text-right text-sm text-danger">${{ pos.stopLoss ?? '—' }}</td>
                <td class="px-5 py-4 text-right text-sm text-success">${{ pos.target ?? '—' }}</td>
                <td class="px-5 py-4">
                  <span class="inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium" :class="pos.status === 'OPEN' ? 'bg-success-bg text-success' : 'bg-danger-bg text-danger'">{{ pos.status }}</span>
                </td>
                <td class="px-5 py-4 text-right text-sm font-semibold" :class="pos.pnl >= 0 ? 'text-success' : 'text-danger'">
                  {{ pos.pnl >= 0 ? '+' : '' }}${{ pos.pnl }}
                  <span class="ml-1 text-xs font-normal opacity-70">({{ pos.pnlPercent >= 0 ? '+' : '' }}{{ pos.pnlPercent.toFixed(2) }}%)</span>
                </td>
              </tr>
              <tr v-if="filteredPositions.length === 0">
                <td colspan="8" class="px-5 py-12 text-center text-sm text-text-muted">No positions found</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { getPositions } from '../api/client'
import type { Position } from '../api/types'
import ErrorMessage from '../components/ErrorMessage.vue'
import LoadingSpinner from '../components/LoadingSpinner.vue'

const loading = ref(true)
const error = ref(false)
const errorMessage = ref('')
const positions = ref<Position[]>([])
const searchQuery = ref('')
const statusFilter = ref('ALL')

const filteredPositions = computed(() => {
  return positions.value.filter(p => {
    const matchesSearch = p.symbol.toLowerCase().includes(searchQuery.value.toLowerCase())
    const matchesStatus = statusFilter.value === 'ALL' || p.status === statusFilter.value
    return matchesSearch && matchesStatus
  })
})

const refreshPositions = async () => {
  loading.value = true
  error.value = false
  errorMessage.value = ''
  try {
    const res = await getPositions()
    if (res.success && res.data) positions.value = res.data
    if (res.error) throw new Error(res.error)
  } catch (err: unknown) {
    errorMessage.value = err instanceof Error ? err.message : 'Failed to load positions'
    error.value = true
  } finally {
    loading.value = false
  }
}

onMounted(() => { refreshPositions() })
</script>
