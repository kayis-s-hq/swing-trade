<template>
  <div class="p-6 animate-fade-in">
    <!-- Page Header -->
    <div class="mb-6 flex items-center justify-between">
      <div>
        <h1 class="font-display text-2xl font-semibold text-text-primary">Data Ingestion</h1>
        <p class="mt-1 text-sm text-text-muted">
          Monitor data quality and pull historical market data
        </p>
      </div>
      <div class="flex items-center gap-3">
        <button
          :disabled="loading || pulling"
          class="flex items-center gap-2 rounded-md border border-border-subtle bg-bg-surface px-3 py-2 text-sm font-medium text-text-muted transition-colors hover:border-border-default hover:text-text-primary disabled:opacity-50"
          @click="refreshStatus"
        >
          <svg
            class="h-4 w-4 transition-transform duration-300"
            :class="loading ? 'animate-spin' : ''"
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
          v-if="!pulling"
          :disabled="pullDisabled"
          class="flex items-center gap-2 rounded-md bg-brand px-3 py-2 text-sm font-medium text-white transition-colors hover:bg-brand/90 disabled:opacity-50"
          @click="startPull"
        >
          <svg class="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path
              stroke-linecap="round"
              stroke-linejoin="round"
              stroke-width="2"
              d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4"
            />
          </svg>
          Pull {{ dataSourceLabel }} Historical Data
        </button>
        <button
          v-else
          class="flex items-center gap-2 rounded-md bg-danger/10 px-3 py-2 text-sm font-medium text-danger transition-colors hover:bg-danger/20"
          @click="cancelPull"
        >
          <svg class="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path
              stroke-linecap="round"
              stroke-linejoin="round"
              stroke-width="2"
              d="M6 18L18 6M6 6l12 12"
            />
          </svg>
          Cancel Pull
        </button>
      </div>
    </div>

    <!-- Broker Connection Status -->
    <div class="mb-6 card-panel p-5">
      <div class="flex items-center justify-between">
        <div class="flex items-center gap-3">
          <span class="inline-flex h-3 w-3">
            <span
              class="pulse-dot inline-block h-3 w-3 rounded-full"
              :class="connectionReady ? 'bg-success' : 'bg-danger'"
            />
          </span>
          <div>
            <p class="text-sm font-medium text-text-primary">
              {{ connectionLabel }}
            </p>
            <p class="text-xs text-text-muted">
              {{ connectionMsg }}
            </p>
          </div>
        </div>
        <template v-if="needsAuth && !fyersConnected">
          <button
            class="rounded-md bg-brand px-3 py-2 text-sm font-medium text-white transition-colors hover:bg-brand/90"
            @click="openBrokerAuth"
          >
            Connect {{ connectionLabel }}
          </button>
        </template>
        <template v-else>
          <span class="text-xs font-medium text-success">Active</span>
        </template>
      </div>
    </div>

    <!-- Pull Progress -->
    <div v-if="pulling" class="mb-6 card-panel p-5">
      <div class="flex items-center justify-between mb-2">
        <div>
          <p class="text-sm font-medium text-text-primary">Pulling Historical Data</p>
          <p class="text-xs text-text-muted">
            Processing: {{ pullProgress?.currentSymbol || '...' }}
          </p>
        </div>
        <p class="text-sm font-bold text-brand">{{ pullProgress?.percentComplete || 0 }}%</p>
      </div>
      <div class="mb-2 h-2 overflow-hidden rounded-full bg-bg-primary">
        <div
          class="h-full rounded-full bg-brand transition-all duration-300"
          :style="{ width: `${pullProgress?.percentComplete || 0}%` }"
        />
      </div>
      <p class="text-xs text-text-muted">
        {{ pullProgress?.completed || 0 }} / {{ pullProgress?.total || 0 }} stocks complete
      </p>
    </div>

    <!-- Pull Complete -->
    <div v-if="pullComplete" class="mb-6 rounded-md bg-success-bg p-4">
      <p class="text-sm font-medium text-success">Data pull completed!</p>
      <p class="text-xs text-text-muted mt-1">
        {{ pullCompleted?.completed || 0 }} succeeded, {{ pullCompleted?.failed || 0 }} failed
      </p>
    </div>

    <ErrorBoundary>
      <template #error>
        <div class="flex flex-col items-center justify-center py-20">
          <p class="text-sm text-danger">{{ errorMessage }}</p>
          <button
            class="mt-2 rounded-md bg-brand px-3 py-1.5 text-xs font-medium text-white"
            @click="loadStatus"
          >
            Retry
          </button>
        </div>
      </template>

      <!-- Loading -->
      <div v-if="loading && !status.length" class="flex items-center justify-center py-20">
        <LoadingSpinner message="Loading ingestion status..." />
      </div>

      <!-- Summary Cards -->
    <template v-else>
      <div class="mb-6 grid grid-cols-2 gap-4 sm:grid-cols-4">
        <div class="card-panel p-4">
          <p class="text-xs font-medium text-text-muted">Total Stocks</p>
          <p class="mt-1 text-xl font-bold text-text-primary">
            {{ status.length }}
          </p>
        </div>
        <div class="card-panel p-4">
          <p class="text-xs font-medium text-text-muted">With Data</p>
          <p class="mt-1 text-xl font-bold text-success">
            {{ status.filter((s) => s.hasData).length }}
          </p>
        </div>
        <div class="card-panel p-4">
          <p class="text-xs font-medium text-text-muted">Missing Data</p>
          <p class="mt-1 text-xl font-bold text-danger">
            {{ status.filter((s) => !s.hasData).length }}
          </p>
        </div>
        <div class="card-panel p-4">
          <p class="text-xs font-medium text-text-muted">Total Candles</p>
          <p class="mt-1 text-xl font-bold text-text-primary">
            {{ formatNumber(status.reduce((sum, s) => sum + s.candleCount, 0)) }}
          </p>
        </div>
      </div>

      <!-- Status Table -->
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
                class="px-5 py-3 text-right text-xs font-semibold uppercase tracking-wider text-text-muted"
              >
                Candles
              </th>
              <th
                class="px-5 py-3 text-left text-xs font-semibold uppercase tracking-wider text-text-muted"
              >
                Date Range
              </th>
              <th
                class="px-5 py-3 text-left text-xs font-semibold uppercase tracking-wider text-text-muted"
              >
                Last Sync
              </th>
              <th
                class="px-5 py-3 text-center text-xs font-semibold uppercase tracking-wider text-text-muted"
              >
                Quality
              </th>
            </tr>
          </thead>
          <tbody class="divide-y divide-border-subtle/50">
            <tr
              v-for="entry in status"
              :key="entry.symbol"
              class="transition-colors hover:bg-bg-hover"
            >
              <td class="px-5 py-4 text-sm font-semibold text-text-primary">
                {{ entry.symbol }}
              </td>
              <td class="px-5 py-4 text-sm text-text-secondary">
                {{ entry.name || '—' }}
              </td>
              <td class="px-5 py-4 text-right text-sm text-text-secondary">
                {{ entry.candleCount }}
              </td>
              <td class="px-5 py-4 text-sm text-text-muted">
                <template v-if="entry.hasData">
                  {{ entry.earliestCandleDate }} → {{ entry.lastCandleDate }}
                </template>
                <template v-else> — </template>
              </td>
              <td class="px-5 py-4 text-sm text-text-muted">
                <template v-if="entry.lastSyncedAt">
                  {{ formatDate(entry.lastSyncedAt) }}
                </template>
                <template v-else> — </template>
              </td>
              <td class="px-5 py-4 text-center">
                <span
                  class="inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium"
                  :class="qualityBadge(entry.dataQuality)"
                >
                  {{ entry.dataQuality }}
                </span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>
      </ErrorBoundary>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import {
  getIngestionStatus,
  triggerDataPull,
  getPullProgress,
  cancelDataPull,
  getFyersStatus,
  getFyersLoginUrl,
  setBroker as apiSetBroker,
} from '../api/client'
import type { IngestionStatus, PullProgress } from '../api/types'
import LoadingSpinner from '../components/LoadingSpinner.vue'
import ErrorBoundary from '../components/ErrorBoundary.vue'
import { useAsyncData } from '../composables/useAsyncData'
import { getSettings } from '../stores/settings'

const settings = getSettings()
const backendBroker = ref(settings.selectedBroker)
const { loading, errorMessage, execute } = useAsyncData()
const status = ref<IngestionStatus[]>([])
const fyersConnected = ref(false)

const pulling = ref(false)
const pullProgress = ref<PullProgress | null>(null)
const pullComplete = ref(false)
const pullCompleted = ref<PullProgress | null>(null)
let pollTimer: ReturnType<typeof setInterval> | null = null

// Yahoo Finance needs no auth; Fyers/Upstox require connection
const needsAuth = computed(
  () => backendBroker.value === 'fyers' || backendBroker.value === 'upstox'
)
const pullDisabled = computed(
  () => loading.value || pulling.value || (needsAuth.value && !fyersConnected.value)
)

const connectionReady = computed(() => !needsAuth.value || fyersConnected.value)
const connectionLabel = computed(() => {
  if (settings.selectedBroker === 'fyers') return 'Fyers Connection'
  if (settings.selectedBroker === 'upstox') return 'Upstox Connection'
  return 'Yahoo Finance'
})
const dataSourceLabel = computed(() => {
  if (settings.selectedBroker === 'yahoo') return 'Yahoo Finance'
  if (settings.selectedBroker === 'upstox') return 'Upstox'
  return 'Fyers'
})

const connectionMsg = computed(() => {
  if (settings.selectedBroker === 'yahoo') return 'Free data — no authentication required'
  if (fyersConnected.value) return 'Connected & ready'
  return 'Not connected — authenticate to pull data'
})

const openBrokerAuth = async () => {
  if (settings.selectedBroker === 'fyers') {
    openFyersAuth()
  }
}

const syncBroker = async () => {
  if (backendBroker.value === settings.selectedBroker) return
  try {
    await apiSetBroker(settings.selectedBroker)
    backendBroker.value = settings.selectedBroker
  } catch (err: unknown) {
    console.error('[Ingestion] Broker sync failed:', err)
  }
}

const loadStatus = () => {
  execute(async () => {
    await syncBroker()
    const [statusResult, fyersResult, progressResult] = await Promise.all([
      getIngestionStatus(),
      getFyersStatus(),
      getPullProgress(),
    ])
    if (statusResult.success && statusResult.data && Array.isArray(statusResult.data)) {
      status.value = statusResult.data
    } else {
      status.value = []
      throw new Error(statusResult.error || 'Failed to load ingestion status')
    }
    if (fyersResult.success && fyersResult.data) {
      fyersConnected.value = fyersResult.data.connected
    }
    // Check for active pull in progress
    if (progressResult.success && progressResult.data && progressResult.data.status === 'running') {
      pulling.value = true
      pullProgress.value = progressResult.data
      startPolling(progressResult.data.pullId)
    }
  })
}

const refreshStatus = () => {
  loadStatus()
}

const startPull = async () => {
  pulling.value = true
  pullComplete.value = false
  pullProgress.value = null
  try {
    const result = await triggerDataPull(1)
    if (result.success && result.data) {
      startPolling(result.data.pullId)
    } else {
      pulling.value = false
      console.error('[Ingestion] Pull failed:', result.error, result)
      alert(result.error || 'Failed to start data pull')
    }
  } catch (err: unknown) {
    pulling.value = false
    console.error('[Ingestion] Pull error:', err)
    alert(err instanceof Error ? err.message : 'Failed to start data pull')
  }
}

const cancelPull = async () => {
  try {
    await cancelDataPull()
    pulling.value = false
    if (pollTimer) {
      clearInterval(pollTimer)
      pollTimer = null
    }
  } catch (err: unknown) {
    alert(err instanceof Error ? err.message : 'Failed to cancel pull')
  }
}

const startPolling = (pullId: string) => {
  if (pollTimer) clearInterval(pollTimer)
  pollTimer = setInterval(async () => {
    try {
      const result = await getPullProgress(pullId)
      if (result.success && result.data) {
        pullProgress.value = result.data
        if (result.data.status === 'completed' || result.data.status === 'cancelled') {
          pulling.value = false
          pullComplete.value = result.data.status === 'completed'
          pullCompleted.value = result.data
          if (pollTimer) {
            clearInterval(pollTimer)
            pollTimer = null
          }
          // Refresh status after pull completes
          if (result.data.status === 'completed') {
            await loadStatus()
          }
        }
      }
    } catch {
      // Ignore polling errors
    }
  }, 1000)
}

const openFyersAuth = async () => {
  try {
    const result = await getFyersLoginUrl()
    if (result.success && result.data?.url) {
      const authWindow = window.open(result.data.url, 'fyers-auth', 'width=500,height=600')

      // Poll for auth completion — callback redirects to settings, we detect via status
      let attempts = 0
      const pollInterval = setInterval(async () => {
        attempts++
        if (authWindow?.closed || attempts > 60) {
          clearInterval(pollInterval)
          await loadStatus()
        } else {
          const status = await getFyersStatus()
          if (status.success && status.data?.connected) {
            clearInterval(pollInterval)
            fyersConnected.value = true
            await loadStatus()
          }
        }
      }, 2000)
    }
  } catch (err: unknown) {
    alert(err instanceof Error ? err.message : 'Failed to get Fyers login URL')
  }
}

const qualityBadge = (quality: string) => {
  const map: Record<string, string> = {
    excellent: 'bg-success-bg text-success',
    good: 'bg-success-bg text-success',
    partial: 'bg-warning-bg text-warning',
    poor: 'bg-danger-bg text-danger',
    no_data: 'bg-bg-hover text-text-muted',
  }
  return map[quality] || map.no_data
}

const formatNumber = (n: number): string => {
  if (n >= 1000) return `${(n / 1000).toFixed(1)}k`
  return String(n)
}

const formatDate = (dateStr: string): string => {
  try {
    const d = new Date(dateStr)
    return d.toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' })
  } catch {
    return dateStr
  }
}

onMounted(() => {
  loadStatus()
})
onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer)
})
</script>
