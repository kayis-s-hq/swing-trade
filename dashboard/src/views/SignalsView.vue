<template>
  <div class="view-shell signals-view p-4 sm:p-6 animate-fade-in">
    <!-- Page Header -->
    <div class="mb-6 flex flex-col gap-4 xl:flex-row xl:items-end xl:justify-between">
      <div>
        <div
          class="mb-2 flex items-center gap-2 text-[11px] font-semibold uppercase tracking-[0.18em] text-brand"
        >
          <span class="h-1.5 w-1.5 rounded-full bg-brand pulse-dot" aria-hidden="true" />
          Market intelligence
        </div>
        <h1 class="font-display text-2xl font-semibold tracking-tight text-text-primary">
          Signals
        </h1>
        <p class="mt-1 max-w-xl text-sm text-text-muted">
          Review fresh opportunities, compare conviction, and execute only when the setup is clear.
        </p>
      </div>
      <div class="flex flex-wrap gap-2">
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
          v-if="signals.length > 0"
          class="flex items-center gap-2 rounded-md border border-border-subtle bg-bg-surface px-3 py-2 text-sm font-medium text-text-muted transition-colors hover:border-danger hover:text-danger"
          @click="clearSignals"
        >
          <svg class="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path
              stroke-linecap="round"
              stroke-linejoin="round"
              stroke-width="2"
              d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"
            />
          </svg>
          {{ selectedCount > 0 ? `Clear selected (${selectedCount})` : 'Clear all' }}
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

    <div class="mb-6 grid grid-cols-2 gap-2 sm:grid-cols-4">
      <div class="signal-stat">
        <span class="signal-stat-label">Visible</span>
        <span class="signal-stat-value">{{ filteredSignals.length }}</span>
        <span class="signal-stat-meta">of {{ signals.length }} signals</span>
      </div>
      <div class="signal-stat">
        <span class="signal-stat-label">Active</span>
        <span class="signal-stat-value">{{ activeSignalCount }}</span>
        <span class="signal-stat-meta">ready to review</span>
      </div>
      <div class="signal-stat">
        <span class="signal-stat-label text-success">Buy bias</span>
        <span class="signal-stat-value">{{ buySignalCount }}</span>
        <span class="signal-stat-meta">bullish setups</span>
      </div>
      <div class="signal-stat">
        <span class="signal-stat-label text-danger">Sell bias</span>
        <span class="signal-stat-value">{{ sellSignalCount }}</span>
        <span class="signal-stat-meta">bearish setups</span>
      </div>
    </div>

    <ErrorBoundary :error="Boolean(error)">
      <template #error>
        <div v-if="error" class="flex flex-col items-center justify-center py-20">
          <p class="text-sm text-danger">
            {{ errorMessage }}
          </p>
          <button
            class="mt-2 rounded-md bg-brand px-3 py-1.5 text-xs font-medium text-white"
            @click="refreshSignals"
          >
            Retry
          </button>
        </div>
      </template>
      <div v-if="loading" class="flex items-center justify-center py-20">
        <LoadingSpinner message="Scanning for signals..." />
      </div>

      <template v-else>
        <!-- Generation progress -->
        <div v-if="generating" class="mb-4 card-panel signal-progress-panel p-4">
          <div class="mb-2 flex items-center justify-between">
            <span class="text-sm font-medium text-text-primary">Signal Generation</span>
            <span class="text-xs text-text-muted">{{ progressCurrent }}/{{ progressTotal }}</span>
          </div>
          <div
            class="h-2 overflow-hidden rounded-full bg-bg-primary/50"
            role="progressbar"
            :aria-valuenow="progressCurrent"
            :aria-valuemin="0"
            :aria-valuemax="progressTotal || 1"
            aria-label="Signal generation progress"
          >
            <div
              class="h-full rounded-full bg-brand transition-[width] duration-300 ease-out"
              :style="{
                width: progressTotal > 0 ? (progressCurrent / progressTotal) * 100 + '%' : '0%',
              }"
            />
          </div>
          <p class="mt-2 text-xs text-text-muted">
            {{ progressMessage || 'Starting...' }}
          </p>
        </div>

        <!-- Generation summary -->
        <div v-if="generationSummary" class="mb-4 card-panel signal-summary-panel p-4">
          <div class="mb-2 flex items-center justify-between">
            <span class="text-sm font-medium text-text-primary">Generation Complete</span>
            <span class="text-xs text-text-muted">
              {{ generationSummary.generated }} generated, {{ generationSummary.skipped }} skipped
            </span>
          </div>
          <button
            v-if="generationSummary.reasons.length > 0"
            class="text-xs text-brand hover:underline"
            @click="showSkipReasons = !showSkipReasons"
          >
            {{ showSkipReasons ? 'Hide reasons' : 'Show reasons' }}
          </button>
          <div
            v-if="showSkipReasons && generationSummary.reasons.length > 0"
            class="mt-2 space-y-1"
          >
            <p
              v-for="(reason, i) in generationSummary.reasons"
              :key="i"
              class="text-xs text-text-muted"
            >
              {{ reason }}
            </p>
          </div>
        </div>

        <!-- Filters -->
        <div
          class="mb-4 flex flex-col gap-3 rounded-lg border border-border-subtle bg-bg-surface/60 p-3 sm:flex-row sm:items-center sm:justify-between"
        >
          <div class="flex flex-wrap items-center gap-3">
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
            <div
              class="signal-filter-group flex rounded-md border border-border-subtle bg-bg-primary/40 p-0.5"
              aria-label="Direction filter"
            >
              <button
                v-for="dir in ['ALL', 'BUY', 'SELL']"
                :key="dir"
                class="rounded px-3 py-1.5 text-xs font-medium transition-colors"
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
            <div
              class="signal-filter-group flex rounded-md border border-border-subtle bg-bg-primary/40 p-0.5"
              aria-label="Status filter"
            >
              <button
                v-for="st in ['ALL', 'ACTIVE', 'PENDING']"
                :key="st"
                class="rounded px-3 py-1.5 text-xs font-medium transition-colors"
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
            <div
              class="signal-filter-group flex rounded-md border border-border-subtle bg-bg-primary/40 p-0.5"
              aria-label="View mode"
            >
              <button
                v-for="mode in VIEW_MODES"
                :key="mode.value"
                class="rounded px-3 py-1.5 text-xs font-medium transition-colors"
                :class="
                  effectiveViewMode === mode.value
                    ? 'bg-brand-subtle text-brand'
                    : 'text-text-muted hover:bg-bg-hover'
                "
                @click="viewPreference = mode.value"
              >
                {{ mode.label }}
              </button>
            </div>
            <label class="flex items-center gap-2 text-xs text-text-muted">
              Strategy
              <select
                v-model="strategyFilter"
                aria-label="Strategy filter"
                class="rounded-md border border-border-subtle bg-bg-primary px-2 py-1.5 text-xs text-text-primary"
              >
                <option value="ALL">All strategies</option>
                <option v-for="s in strategyOptions" :key="s" :value="s">{{ s }}</option>
              </select>
            </label>
          </div>
          <span v-if="selectedCount > 0" class="text-xs font-medium text-brand"
            >{{ selectedCount }} selected</span
          >
        </div>

        <!-- Grouped by symbol: one row per symbol, a chip per variant -->
        <div v-if="effectiveViewMode === 'GROUPED'" class="space-y-2" aria-label="Signals by symbol">
          <div
            v-for="group in groupedSignals"
            :key="group.symbol"
            class="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-border-subtle bg-bg-surface p-3"
          >
            <div class="flex items-center gap-3">
              <span class="w-28 font-semibold text-text-primary">{{ group.symbol }}</span>
              <span
                v-if="group.consensusLabel"
                class="rounded-full bg-brand/10 px-2 py-0.5 text-[11px] font-semibold text-brand"
                data-testid="consensus-badge"
                >{{ group.consensusLabel }}</span
              >
            </div>
            <VariantSignalChips :chips="group.chips" />
          </div>
          <p v-if="groupedSignals.length === 0" class="py-8 text-center text-sm text-text-muted">
            No signals match this view.
          </p>
        </div>

        <!-- Signal Grid -->
        <TransitionGroup
          v-if="effectiveViewMode === 'FLAT'"
          name="signal-card"
          tag="div"
          class="grid grid-cols-1 gap-4 lg:grid-cols-2 xl:grid-cols-3"
        >
          <div
            v-for="signal in filteredSignals"
            :key="signal.id"
            class="signal-card-wrapper relative"
            :class="isSelected(signal.id) ? 'ring-2 ring-brand/50 rounded-lg' : ''"
          >
            <SignalCard
              :signal="signal"
              :selected="isSelected(signal.id)"
              @toggle-selection="toggleSignal(signal.id)"
            />
          </div>
        </TransitionGroup>

        <div
          v-if="effectiveViewMode === 'FLAT' && filteredSignals.length === 0"
          class="signal-empty-state flex flex-col items-center justify-center rounded-lg border border-dashed border-border-default py-16"
        >
          <div
            class="mb-3 flex h-10 w-10 items-center justify-center rounded-full bg-brand-subtle text-brand"
            aria-hidden="true"
          >
            <svg class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path
                stroke-linecap="round"
                stroke-linejoin="round"
                stroke-width="1.7"
                d="M4 19l5-5 3 3 8-9M16 8h4v4"
              />
            </svg>
          </div>
          <p class="text-sm font-medium text-text-primary">No signals match this view</p>
          <p class="mt-1 text-xs text-text-muted">Try another filter or generate a fresh scan.</p>
        </div>
      </template>
    </ErrorBoundary>

    <!-- Execution results toast -->
    <div v-if="execResult" class="fixed bottom-4 right-4 z-50 max-w-md">
      <div class="rounded-lg border border-border-subtle bg-bg-surface p-4 shadow-lg">
        <div class="flex items-start justify-between gap-3">
          <div>
            <p class="text-sm font-semibold text-text-primary">
              {{ execResult.success > 0 ? 'Executed' : 'Failed' }}
            </p>
            <p class="mt-1 text-xs text-text-muted">
              {{ execResult.success }} succeeded, {{ execResult.failed }} failed<span
                v-if="execResult.unconfirmed > 0"
                >, {{ execResult.unconfirmed }} unconfirmed</span
              >
            </p>
            <p
              v-for="reason in execResult.errors"
              :key="reason"
              class="mt-1 text-xs text-text-muted"
            >
              {{ reason }}
            </p>
            <button
              v-if="execResult.offerPositionRefresh"
              class="mt-2 text-xs font-medium text-brand hover:underline"
              @click="refreshPositionsStatus"
            >
              Refresh positions
            </button>
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
import { ref, computed, onMounted, onUnmounted } from 'vue'
import {
  getSignals,
  generateAllSignalsStream,
  clearAllSignals,
  clearSignalsForSymbol,
} from '../api/signals'
import { executeTrade, getPositions } from '../api/positions'
import type { Signal } from '../api/types'
import SignalCard from '../components/SignalCard.vue'
import VariantSignalChips from '../components/VariantSignalChips.vue'
import { listSignalSelections, latestTournament, type SignalSelection } from '../api/selections'
import { groupSignalsBySymbol } from '../utils/signalGrouping'
import { getSettings } from '../stores/settings'
import LoadingSpinner from '../components/LoadingSpinner.vue'
import ErrorBoundary from '../components/ErrorBoundary.vue'
import { useAsyncData } from '../composables/useAsyncData'
import { asAppError, safeHumanMessage } from '../errors/appError'

const { loading, error, errorMessage, execute } = useAsyncData<void>()
const generating = ref(false)
const executing = ref(false)
const signals = ref<Signal[]>([])
const directionFilter = ref('ALL')
const statusFilter = ref('ALL')
const strategyFilter = ref('ALL')
const VIEW_MODES = [
  { value: 'GROUPED' as const, label: 'By symbol' },
  { value: 'FLAT' as const, label: 'List' },
]
const viewPreference = ref<'GROUPED' | 'FLAT' | null>(null)
const selections = ref<SignalSelection[]>([])
const selectedSignalIds = ref(new Set<string>())
const execResult = ref<{
  success: number
  failed: number
  unconfirmed: number
  errors: string[]
  offerPositionRefresh: boolean
} | null>(null)
const progressMessage = ref('')
const progressCurrent = ref(0)
const progressTotal = ref(0)
const generationSummary = ref<{ generated: number; skipped: number; reasons: string[] } | null>(
  null
)
const showSkipReasons = ref(false)

const strategyOptions = computed(() => {
  const values = new Set<string>()
  signals.value.forEach((s) => {
    if (s.strategy) values.add(s.strategy)
  })
  return Array.from(values).sort()
})

const filteredSignals = computed(() => {
  return signals.value.filter((s) => {
    const matchesDir = directionFilter.value === 'ALL' || s.direction === directionFilter.value
    const matchesStatus = statusFilter.value === 'ALL' || s.status === statusFilter.value
    const matchesStrategy = strategyFilter.value === 'ALL' || s.strategy === strategyFilter.value
    return matchesDir && matchesStatus && matchesStrategy
  })
})

// Grouping only carries information once variants emit signals; before that (legacy
// strategy-less signals) the selectable card list stays the default.
const hasVariantSignals = computed(() => signals.value.some((s) => s.strategy))
const effectiveViewMode = computed(
  () => viewPreference.value ?? (hasVariantSignals.value ? 'GROUPED' : 'FLAT')
)
const groupedSignals = computed(() => groupSignalsBySymbol(filteredSignals.value, selections.value))

// The tournament winner star is supplementary; a failure must never break the signal list.
async function loadSelections() {
  try {
    const to = new Date()
    const from = new Date(to.getTime() - 7 * 24 * 60 * 60 * 1000)
    const iso = (d: Date) => d.toISOString().slice(0, 10)
    selections.value = latestTournament(await listSignalSelections(iso(from), iso(to)))
  } catch {
    selections.value = []
  }
}

const selectedCount = computed(() => selectedSignalIds.value.size)

const activeSignalCount = computed(
  () => signals.value.filter((signal) => signal.status === 'ACTIVE').length
)
const buySignalCount = computed(
  () => signals.value.filter((signal) => signal.direction === 'BUY').length
)
const sellSignalCount = computed(
  () => signals.value.filter((signal) => signal.direction === 'SELL').length
)

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
  let failedCount = 0
  let unconfirmedCount = 0
  const errors: string[] = []
  const confirmedIds = new Set<string>()

  for (const signal of selected) {
    const allocation = getSettings().tradingConfig.allocationPerPosition
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
      confirmedIds.add(signal.id)
    } catch (errorLike: unknown) {
      const outcomeUnknown =
        typeof errorLike === 'object' &&
        errorLike !== null &&
        'outcomeUnknown' in errorLike &&
        errorLike.outcomeUnknown === true
      if (outcomeUnknown) {
        unconfirmedCount++
        errors.push(`${signal.symbol}: Order could not be confirmed. Refresh positions.`)
      } else {
        failedCount++
        errors.push(
          `${signal.symbol}: ${safeHumanMessage(
            errorLike instanceof Error ? errorLike.message : undefined,
            'Order execution failed.'
          )}`
        )
      }
    }
  }

  confirmedIds.forEach((id) => selectedSignalIds.value.delete(id))
  selectedSignalIds.value = new Set(selectedSignalIds.value)
  execResult.value = {
    success: successCount,
    failed: failedCount,
    unconfirmed: unconfirmedCount,
    errors,
    offerPositionRefresh: unconfirmedCount > 0,
  }
  executing.value = false
}

async function refreshPositionsStatus() {
  try {
    await getPositions()
    execResult.value = null
  } catch {
    // Keep the current outcome summary visible.
  }
}

const doRefresh = async () => {
  await execute(async () => {
    signals.value = await getSignals()
  })
  void loadSelections()
}

const generateAll = async () => {
  generating.value = true
  error.value = null
  errorMessage.value = ''
  progressMessage.value = ''
  progressCurrent.value = 0
  progressTotal.value = 0
  generationSummary.value = null
  showSkipReasons.value = false
  signals.value = []
  const allSignals: Signal[] = []
  const skipReasons: string[] = []

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
        skipReasons.push(`${progress.symbol}: ${progress.message}`)
      } else if (progress.eventType === 'COMPLETE') {
        progressMessage.value = progress.message
        generationSummary.value = {
          generated: allSignals.length,
          skipped: skipReasons.length,
          reasons: skipReasons,
        }
      }
    }
  } catch (err: unknown) {
    const generationError = asAppError(err, { message: 'Signal generation failed.' })
    errorMessage.value = generationError.message
    error.value = generationError
  } finally {
    generating.value = false
    doRefresh()
  }
}

const clearAll = async () => {
  if (!confirm('Clear all signals?')) return
  try {
    await clearAllSignals()
    signals.value = []
    selectedSignalIds.value = new Set()
    execResult.value = {
      success: 0,
      failed: 0,
      unconfirmed: 0,
      errors: ['All signals cleared.'],
      offerPositionRefresh: false,
    }
  } catch (errorLike: unknown) {
    execResult.value = {
      success: 0,
      failed: 1,
      unconfirmed: 0,
      errors: [
        safeHumanMessage(
          errorLike instanceof Error ? errorLike.message : undefined,
          'Signals could not be cleared.'
        ),
      ],
      offerPositionRefresh: false,
    }
  }
}

const clearSelected = async () => {
  const selected = signals.value.filter((s) => selectedSignalIds.value.has(s.id))
  const clearedIds = new Set<string>()
  const errors: string[] = []
  for (const signal of selected) {
    try {
      await clearSignalsForSymbol(signal.symbol)
      clearedIds.add(signal.id)
    } catch (errorLike: unknown) {
      errors.push(
        `${signal.symbol}: ${safeHumanMessage(
          errorLike instanceof Error ? errorLike.message : undefined,
          'Signal could not be cleared.'
        )}`
      )
    }
  }
  signals.value = signals.value.filter((signal) => !clearedIds.has(signal.id))
  clearedIds.forEach((id) => selectedSignalIds.value.delete(id))
  selectedSignalIds.value = new Set(selectedSignalIds.value)
  execResult.value = {
    success: clearedIds.size,
    failed: errors.length,
    unconfirmed: 0,
    errors: [`${clearedIds.size} cleared, ${errors.length} failed`, ...errors],
    offerPositionRefresh: false,
  }
}

const clearSignals = () => (selectedCount.value > 0 ? clearSelected() : clearAll())

const refreshSignals = doRefresh

let refreshTimer: ReturnType<typeof setInterval> | undefined

onMounted(() => {
  // Clear any stale error state from a previous failed load
  error.value = null
  errorMessage.value = ''
  refreshSignals()
  refreshTimer = setInterval(() => void refreshSignals(), 60_000)
})

onUnmounted(() => {
  if (refreshTimer) clearInterval(refreshTimer)
})
</script>
