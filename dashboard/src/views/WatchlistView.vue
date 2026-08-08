<template>
  <div class="p-6 animate-fade-in">
    <!-- Page Header -->
    <div class="mb-6 flex items-center justify-between">
      <div>
        <h1 class="font-display text-2xl font-semibold text-text-primary">Watchlist</h1>
        <p class="mt-1 text-sm text-text-muted">{{ watchlist.length }} stocks being monitored</p>
      </div>
      <button
        class="flex items-center gap-2 rounded-md bg-brand px-3 py-2 text-sm font-medium text-white transition-colors hover:bg-brand/90"
        @click="showAddForm = !showAddForm"
      >
        <svg class="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path
            stroke-linecap="round"
            stroke-linejoin="round"
            stroke-width="2"
            d="M12 4v16m8-8H4"
          />
        </svg>
        Add Stock
      </button>
    </div>

    <!-- Add Stock Form -->
    <div v-if="showAddForm" class="mb-6 card-panel p-5">
      <h3 class="mb-3 text-sm font-semibold text-text-primary">Add Stock to Watchlist</h3>
      <form class="flex flex-col sm:flex-row gap-3" @submit.prevent="handleSubmit">
        <div class="flex-1">
          <label class="mb-1 block text-xs font-medium text-text-muted">Symbol</label>
          <input
            v-model="newSymbol"
            type="text"
            placeholder="e.g. RELIANCE"
            required
            class="w-full rounded-md border border-border-subtle bg-bg-primary px-3 py-2 text-sm text-text-primary placeholder:text-text-muted/50 focus:outline-none focus:ring-2 focus:ring-brand/30"
          />
        </div>
        <div class="flex-1">
          <label class="mb-1 block text-xs font-medium text-text-muted"
            >Company Name (optional)</label
          >
          <input
            v-model="newName"
            type="text"
            placeholder="e.g. Reliance Industries"
            class="w-full rounded-md border border-border-subtle bg-bg-primary px-3 py-2 text-sm text-text-primary placeholder:text-text-muted/50 focus:outline-none focus:ring-2 focus:ring-brand/30"
          />
        </div>
        <div class="flex items-end gap-2">
          <button
            type="submit"
            :disabled="adding"
            class="rounded-md bg-brand px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-brand/90 disabled:opacity-50"
          >
            {{ adding ? 'Adding...' : 'Add' }}
          </button>
          <button
            type="button"
            class="rounded-md border border-border-subtle px-3 py-2 text-sm text-text-muted transition-colors hover:bg-bg-hover"
            @click="showAddForm = false"
          >
            Cancel
          </button>
        </div>
      </form>
    </div>

    <ErrorBoundary>
      <template #error>
        <div class="flex flex-col items-center justify-center py-20">
          <p class="text-sm text-danger">{{ errorMessage }}</p>
          <button
            class="mt-2 rounded-md bg-brand px-3 py-1.5 text-xs font-medium text-white"
            @click="loadWatchlist"
          >
            Retry
          </button>
        </div>
      </template>
      <div v-if="loading" class="flex items-center justify-center py-20">
        <LoadingSpinner message="Loading watchlist..." />
      </div>

      <template v-else>
      <div class="card-panel overflow-x-auto">
        <table class="min-w-full">
          <thead>
            <tr class="border-b border-border-subtle bg-bg-primary/50">
              <th
                class="px-5 py-3 text-left text-xs font-semibold uppercase tracking-wider text-text-muted"
              >
                Symbol
              </th>
              <th
                class="px-5 py-3 text-left text-xs font-semibold uppercase tracking-wider text-text-muted"
              >
                Name
              </th>
              <th
                class="px-5 py-3 text-left text-xs font-semibold uppercase tracking-wider text-text-muted"
              >
                Exchange
              </th>
              <th
                class="px-5 py-3 text-center text-xs font-semibold uppercase tracking-wider text-text-muted"
              >
                Status
              </th>
              <th
                class="px-5 py-3 text-right text-xs font-semibold uppercase tracking-wider text-text-muted"
              >
                Candles
              </th>
              <th
                class="px-5 py-3 text-right text-xs font-semibold uppercase tracking-wider text-text-muted"
              >
                Actions
              </th>
            </tr>
          </thead>
          <tbody class="divide-y divide-border-subtle/50">
            <tr
              v-for="entry in watchlist"
              :key="entry.symbol"
              class="transition-colors hover:bg-bg-hover"
            >
              <td class="px-5 py-4 text-sm font-semibold text-text-primary">
                {{ entry.symbol }}
              </td>
              <td class="px-5 py-4 text-sm text-text-secondary">
                {{ entry.name || '—' }}
              </td>
              <td class="px-5 py-4 text-sm text-text-muted">
                {{ entry.exchange }}
              </td>
              <td class="px-5 py-4 text-center">
                <button
                  class="inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium transition-colors"
                  :class="
                    entry.isActive
                      ? 'bg-success-bg text-success hover:bg-success-bg/80'
                      : 'bg-bg-hover text-text-muted hover:bg-border-subtle'
                  "
                  @click="toggleEntry(entry.symbol)"
                >
                  {{ entry.isActive ? 'Active' : 'Inactive' }}
                </button>
              </td>
              <td class="px-5 py-4 text-right text-sm text-text-secondary">
                {{ entry.candleCount ?? 0 }}
              </td>
              <td class="px-5 py-4 text-right">
                <button
                  class="rounded-md p-1 text-text-muted transition-colors hover:bg-danger-bg hover:text-danger"
                  :title="'Remove ' + entry.symbol"
                  @click="removeEntry(entry.symbol)"
                >
                  <svg class="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path
                      stroke-linecap="round"
                      stroke-linejoin="round"
                      stroke-width="2"
                      d="M19 7l-.867 12.14A2 2 0 0116.237 21H7.763a2 2 0 01-1.896-1.86L5 7m1 0h4m-4 0V3h4v4m-4 0H5m14-4H15m4 0v4m-4-4h1m-5 0h.01"
                    />
                  </svg>
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div v-if="watchlist.length === 0" class="mt-6 text-center text-sm text-text-muted">
        No stocks in watchlist. Click "Add Stock" to get started.
      </div>
    </template>
      </ErrorBoundary>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import {
  getWatchlist,
  addToWatchlist,
  removeFromWatchlist,
  toggleWatchlistActive,
} from '../api/client'
import type { WatchlistEntry } from '../api/types'
import LoadingSpinner from '../components/LoadingSpinner.vue'
import ErrorBoundary from '../components/ErrorBoundary.vue'
import { useAsyncData } from '../composables/useAsyncData'

const { loading, errorMessage, execute } = useAsyncData()
const watchlist = ref<WatchlistEntry[]>([])
const showAddForm = ref(false)
const newSymbol = ref('')
const newName = ref('')
const adding = ref(false)

const loadWatchlist = () => {
  execute(async () => {
    console.log('[Watchlist] Loading watchlist...')
    const result = await getWatchlist()
    if (result.success && result.data) {
      console.log('[Watchlist] Loaded', result.data.length, 'entries')
      watchlist.value = result.data
    } else {
      console.error('[Watchlist] Load failed:', result.error, result)
      throw new Error(result.error || 'Failed to load watchlist')
    }
  })
}

const handleSubmit = async () => {
  if (!newSymbol.value.trim()) return
  adding.value = true
  try {
    const result = await addToWatchlist(newSymbol.value, newName.value || undefined)
    if (result.success) {
      newSymbol.value = ''
      newName.value = ''
      showAddForm.value = false
      await loadWatchlist()
    } else {
      console.error('[Watchlist] Add failed:', result.error, result)
      alert(result.error || 'Failed to add stock')
    }
  } catch (err: unknown) {
    console.error('[Watchlist] Add error:', err)
    alert(err instanceof Error ? err.message : 'Failed to add stock')
  } finally {
    adding.value = false
  }
}

const removeEntry = async (symbol: string) => {
  if (!confirm(`Remove ${symbol} from watchlist?`)) return
  try {
    const result = await removeFromWatchlist(symbol)
    if (result.success) {
      await loadWatchlist()
    } else {
      console.error('[Watchlist] Remove failed:', result.error, result)
      alert(result.error || 'Failed to remove stock')
    }
  } catch (err: unknown) {
    console.error('[Watchlist] Remove error:', err)
    alert(err instanceof Error ? err.message : 'Failed to remove stock')
  }
}

const toggleEntry = async (symbol: string) => {
  const entry = watchlist.value.find((e) => e.symbol === symbol)
  if (!entry) return
  try {
    const result = await toggleWatchlistActive(symbol, entry.isActive)
    if (result.success) {
      await loadWatchlist()
    } else {
      console.error('[Watchlist] Toggle failed:', result.error, result)
    }
  } catch (err: unknown) {
    console.error('[Watchlist] Toggle error:', err)
  }
}

onMounted(() => {
  loadWatchlist()
})
</script>
