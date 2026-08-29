<template>
  <div class="view-shell p-6 animate-fade-in">
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

    <div class="mb-6 card-panel p-5">
      <div class="mb-3 flex items-center justify-between">
        <div>
          <p class="text-sm font-medium text-text-primary">Data pull options</p>
          <p class="text-xs text-text-muted">
            Use a full range to repair historical gaps or pull one date.
          </p>
        </div>
        <select
          v-model="pullMode"
          class="rounded-md border border-border-subtle bg-bg-surface px-3 py-2 text-sm text-text-primary"
        >
          <option value="history">Default history</option>
          <option value="range">Custom date range</option>
          <option value="date">Single date</option>
        </select>
      </div>
      <div v-if="pullMode === 'range'" class="grid gap-3 sm:grid-cols-2">
        <label class="text-xs text-text-muted"
          >From
          <input
            v-model="rangeFrom"
            type="date"
            class="mt-1 block w-full rounded-md border border-border-subtle bg-bg-surface px-3 py-2 text-sm text-text-primary"
        /></label>
        <label class="text-xs text-text-muted"
          >To
          <input
            v-model="rangeTo"
            type="date"
            class="mt-1 block w-full rounded-md border border-border-subtle bg-bg-surface px-3 py-2 text-sm text-text-primary"
        /></label>
      </div>
      <div v-if="pullMode === 'date'" class="grid gap-3 sm:grid-cols-2">
        <label class="text-xs text-text-muted"
          >Symbol
          <input
            v-model="selectedSymbol"
            placeholder="e.g. RELIANCE"
            class="mt-1 block w-full rounded-md border border-border-subtle bg-bg-surface px-3 py-2 text-sm uppercase text-text-primary"
        /></label>
        <label class="text-xs text-text-muted"
          >Date
          <input
            v-model="selectedDate"
            type="date"
            class="mt-1 block w-full rounded-md border border-border-subtle bg-bg-surface px-3 py-2 text-sm text-text-primary"
        /></label>
      </div>
      <p v-if="pullValidation" class="mt-2 text-xs text-danger">{{ pullValidation }}</p>
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

    <div
      v-if="operationNotice"
      role="alert"
      class="mb-4 rounded-md border border-warning/30 bg-warning/5 px-3 py-2 text-sm text-warning"
    >
      <p>{{ operationNotice }}</p>
      <button
        v-if="offerPullStatusRefresh"
        class="mt-2 text-xs font-medium underline"
        @click="refreshPullStatus"
      >
        Refresh pull status
      </button>
    </div>

    <div
      v-if="pollingWarning"
      role="status"
      class="mb-4 rounded-md border border-warning/30 bg-warning/5 px-3 py-2 text-sm text-warning"
    >
      <p>Progress refresh failed. The last progress may be stale.</p>
      <button class="mt-2 text-xs font-medium underline" @click="retryPollNow">Retry now</button>
    </div>

    <ErrorBoundary :error="Boolean(error)">
      <template #error>
        <div class="flex flex-col items-center justify-center py-20">
          <p class="text-sm text-danger">
            {{ errorMessage }}
          </p>
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
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import {
  getIngestionStatus,
  triggerDataPull,
  getPullProgress,
  cancelDataPull,
  triggerDataPullRange,
  ingestSelectedDate,
} from '../api/ingestion'
import { getFyersStatus, getFyersLoginUrl, setBroker as apiSetBroker } from '../api/client'
import type { IngestionStatus, PullProgress } from '../api/types'
import LoadingSpinner from '../components/LoadingSpinner.vue'
import ErrorBoundary from '../components/ErrorBoundary.vue'
import { useAsyncData } from '../composables/useAsyncData'
import { safeHumanMessage } from '../errors/appError'
import { getSettings, loadSettings } from '../stores/settings'

const settings = getSettings()
const backendBroker = ref(settings.selectedBroker)
const { loading, error, errorMessage, execute } = useAsyncData<void>()
const status = ref<IngestionStatus[]>([])
const fyersConnected = ref(false)
const operationNotice = ref('')
const offerPullStatusRefresh = ref(false)
const pollingWarning = ref(false)
const pullMode = ref<'history' | 'range' | 'date'>('history')
const rangeFrom = ref('')
const rangeTo = ref('')
const selectedSymbol = ref('')
const selectedDate = ref('')

const pullValidation = computed(() => {
  if (pullMode.value === 'range' && (!rangeFrom.value || !rangeTo.value))
    return 'Select both dates.'
  if (pullMode.value === 'range' && rangeFrom.value > rangeTo.value)
    return 'The start date must be before the end date.'
  if (pullMode.value === 'date' && (!selectedSymbol.value.trim() || !selectedDate.value))
    return 'Select a symbol and date.'
  return ''
})

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

const loadStatus = async () => {
  await execute(async () => {
    await syncBroker()
    const [statusResult, progressResult] = await Promise.all([
      getIngestionStatus(),
      getPullProgress(),
    ])
    status.value = statusResult
    if (needsAuth.value) {
      const fyersResult = await getFyersStatus()
      const fyersData =
        typeof fyersResult === 'object' && fyersResult !== null && 'data' in fyersResult
          ? fyersResult.data
          : fyersResult
      if (typeof fyersData === 'object' && fyersData !== null && 'connected' in fyersData) {
        fyersConnected.value = fyersData.connected === true
      }
    } else {
      fyersConnected.value = false
    }
    if (progressResult.status === 'running') {
      pulling.value = true
      pullProgress.value = progressResult
      startPolling(progressResult.pullId)
    }
  })
}

const refreshStatus = () => {
  loadStatus()
}

const startPull = async () => {
  if (pullValidation.value) return
  pulling.value = true
  pullComplete.value = false
  pullProgress.value = null
  operationNotice.value = ''
  try {
    if (pullMode.value === 'date') {
      await ingestSelectedDate(selectedSymbol.value.trim(), selectedDate.value)
      pulling.value = false
      await loadStatus()
    } else {
      const result =
        pullMode.value === 'range'
          ? await triggerDataPullRange(rangeFrom.value, rangeTo.value)
          : await triggerDataPull(3)
      startPolling(result.pullId)
    }
  } catch (err: unknown) {
    pulling.value = false
    operationNotice.value = safeHumanMessage(
      err instanceof Error ? err.message : undefined,
      'The data pull could not be started.'
    )
  }
}

const cancelPull = async () => {
  operationNotice.value = ''
  offerPullStatusRefresh.value = false
  try {
    await cancelDataPull()
    pulling.value = false
    if (pollTimer) {
      clearInterval(pollTimer)
      pollTimer = null
    }
  } catch (err: unknown) {
    const outcomeUnknown =
      typeof err === 'object' &&
      err !== null &&
      'outcomeUnknown' in err &&
      err.outcomeUnknown === true
    operationNotice.value = outcomeUnknown
      ? 'Cancellation could not be confirmed. Refresh pull status before taking another action.'
      : safeHumanMessage(
          err instanceof Error ? err.message : undefined,
          'The data pull could not be cancelled.'
        )
    offerPullStatusRefresh.value = outcomeUnknown
  }
}

async function pollProgress(pullId: string) {
  try {
    const result = await getPullProgress(pullId)
    pullProgress.value = result
    pollingWarning.value = false
    if (result.status === 'completed' || result.status === 'cancelled') {
      pulling.value = false
      pullComplete.value = result.status === 'completed'
      pullCompleted.value = result
      if (pollTimer) {
        clearInterval(pollTimer)
        pollTimer = null
      }
      if (result.status === 'completed') await loadStatus()
    }
  } catch {
    pollingWarning.value = true
  }
}

const startPolling = (pullId: string) => {
  if (pollTimer) clearInterval(pollTimer)
  pollTimer = setInterval(() => void pollProgress(pullId), 1000)
}

async function retryPollNow() {
  if (pullProgress.value?.pullId) await pollProgress(pullProgress.value.pullId)
}

async function refreshPullStatus() {
  if (pullProgress.value?.pullId) await pollProgress(pullProgress.value.pullId)
  if (!pollingWarning.value) {
    operationNotice.value = ''
    offerPullStatusRefresh.value = false
  }
}

const openFyersAuth = async () => {
  try {
    const result = await getFyersLoginUrl()
    if (result.url) {
      const authWindow = window.open(result.url, 'fyers-auth', 'width=500,height=600')

      // Poll for auth completion — callback redirects to settings, we detect via status
      let attempts = 0
      const pollInterval = setInterval(async () => {
        attempts++
        if (authWindow?.closed || attempts > 60) {
          clearInterval(pollInterval)
          await loadStatus()
        } else {
          const status = await getFyersStatus()
          if (status.connected) {
            clearInterval(pollInterval)
            fyersConnected.value = true
            await loadStatus()
          }
        }
      }, 2000)
    }
  } catch (err: unknown) {
    operationNotice.value = safeHumanMessage(
      err instanceof Error ? err.message : undefined,
      'The broker login URL could not be loaded.'
    )
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

onMounted(async () => {
  await loadSettings()
  await loadStatus()
})
onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer)
})
</script>
