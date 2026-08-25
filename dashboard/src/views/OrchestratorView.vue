<template>
  <div class="p-6 animate-fade-in">
    <!-- Page Header -->
    <div class="mb-6 flex items-center justify-between">
      <div>
        <h1 class="font-display text-2xl font-semibold text-text-primary">Job Orchestrator</h1>
        <p class="mt-1 text-sm text-text-muted">6-stage pipeline for all watchlist symbols</p>
      </div>
      <div class="flex gap-2">
        <button
          :disabled="isRunning"
          class="flex items-center gap-2 rounded-md bg-brand px-3 py-2 text-sm font-medium text-white transition-colors hover:bg-brand/90 disabled:opacity-50"
          @click="startRun"
        >
          <svg v-if="isRunning" class="h-4 w-4 animate-spin" viewBox="0 0 24 24" fill="none">
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
          {{ isRunning ? 'Running...' : 'Run' }}
        </button>
        <button
          v-if="currentRunId"
          :disabled="!isRunning"
          class="flex items-center gap-2 rounded-md border border-danger/50 bg-bg-surface px-3 py-2 text-sm font-medium text-danger transition-colors hover:border-danger hover:bg-danger/10 disabled:opacity-50"
          @click="cancelRun"
        >
          Cancel
        </button>
        <button
          class="flex items-center gap-2 rounded-md border border-border-subtle bg-bg-surface px-3 py-2 text-sm font-medium text-text-muted transition-colors hover:border-border-default hover:text-text-primary"
          @click="loadHistory"
        >
          History
        </button>
      </div>
    </div>

    <ErrorBoundary :error="error">
      <template #error>
        <div class="flex flex-col items-center justify-center py-20">
          <p class="text-sm text-danger">
            {{ errorMessage }}
          </p>
          <button
            class="mt-2 rounded-md bg-brand px-3 py-1.5 text-xs font-medium text-white"
            @click="refresh"
          >
            Retry
          </button>
        </div>
      </template>
      <div v-if="loading" class="flex items-center justify-center py-20">
        <LoadingSpinner message="Loading orchestrator data..." />
      </div>

      <template v-else>
        <!-- Current Run -->
        <div v-if="currentRun" class="mb-6 card-panel p-5">
          <div class="mb-3 flex items-center justify-between">
            <div class="flex items-center gap-3">
              <StatusBadge :status="currentRun.status" :label="currentRun.status" />
              <span class="text-xs text-text-muted">
                Started: {{ formatTime(currentRun.startedAt) }}
              </span>
              <span v-if="currentRun.triggerType" class="text-xs text-text-muted">
                ({{ currentRun.triggerType }})
              </span>
            </div>
            <span v-if="currentRun.completedAt" class="text-xs text-text-muted">
              Duration: {{ formatDuration(currentRun.startedAt, currentRun.completedAt) }}
            </span>
          </div>

          <!-- Progress Bar -->
          <div v-if="currentRun.status === 'RUNNING'" class="mb-3">
            <div class="mb-1 flex items-center justify-between">
              <span class="text-xs font-medium text-text-muted">Progress</span>
              <span class="text-xs text-text-muted">
                {{ currentRun.completedCount }}/{{ currentRun.symbolsCount }} symbols complete
                <span v-if="currentRun.failedCount > 0" class="text-danger"
                  >({{ currentRun.failedCount }} failed)</span
                >
              </span>
            </div>
            <div class="h-2 rounded-full bg-bg-primary/50">
              <div
                class="h-full rounded-full bg-brand transition-all duration-300"
                :style="{ width: progressPercent + '%' }"
              />
            </div>
          </div>

          <!-- Stage Table -->
          <div class="mt-4 overflow-x-auto">
            <table class="w-full text-sm border-collapse">
              <thead>
                <tr class="border-b border-border-subtle">
                  <th class="pb-2 pr-4 text-left text-xs font-medium text-text-muted">Symbol</th>
                  <th
                    v-for="stage in stages"
                    :key="stage"
                    class="pb-2 px-3 text-center text-xs font-medium text-text-muted"
                  >
                    {{ stage }}
                  </th>
                  <th class="pb-2 px-3 text-center text-xs font-medium text-text-muted" />
                </tr>
              </thead>
              <tbody>
                <template v-for="symbol in symbols" :key="symbol">
                  <tr
                    class="border-b border-border-subtle/50 hover:bg-bg-hover/50 cursor-pointer"
                    @click="toggleSymbol(symbol)"
                  >
                    <td class="py-2 pr-4 font-medium text-text-primary">
                      {{ symbol }}
                    </td>
                    <td v-for="stage in stages" :key="stage" class="py-2 px-3 text-center">
                      <StageIcon :status="getStageStatus(symbol, stage)" />
                    </td>
                    <td class="py-2 px-3 text-center">
                      <svg
                        v-if="expandedSymbol === symbol"
                        class="h-4 w-4 mx-auto text-text-muted"
                        viewBox="0 0 24 24"
                        fill="none"
                        stroke="currentColor"
                        stroke-width="2"
                      >
                        <path d="M19 9l-7 7-7-7" />
                      </svg>
                      <svg
                        v-else
                        class="h-4 w-4 mx-auto text-text-muted"
                        viewBox="0 0 24 24"
                        fill="none"
                        stroke="currentColor"
                        stroke-width="2"
                      >
                        <path d="M9 5l7 7-7 7" />
                      </svg>
                    </td>
                  </tr>
                  <tr v-if="expandedSymbol === symbol" class="bg-bg-hover/30">
                    <td :colspan="8" class="p-4">
                      <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3">
                        <div
                          v-for="stage in stages"
                          :key="stage"
                          class="rounded-md border border-border-subtle bg-bg-surface p-3"
                        >
                          <div class="mb-1 flex items-center justify-between">
                            <span class="text-xs font-medium text-text-muted">{{ stage }}</span>
                            <StageIcon :status="getStageStatus(symbol, stage)" />
                          </div>
                          <div class="text-xs text-text-primary">
                            <div v-if="getStageResult(symbol, stage)" class="text-text-primary">
                              {{ getStageResult(symbol, stage) }}
                            </div>
                            <div v-if="getStageError(symbol, stage)" class="text-danger mt-1">
                              {{ getStageError(symbol, stage) }}
                            </div>
                            <div
                              v-if="getStageDuration(symbol, stage) != null"
                              class="text-text-muted mt-1"
                            >
                              {{ getStageDuration(symbol, stage) }}ms
                            </div>
                          </div>
                        </div>
                      </div>
                    </td>
                  </tr>
                </template>
              </tbody>
            </table>
          </div>

          <!-- Error Summary -->
          <div
            v-if="currentRun.errorMessage"
            class="mt-3 rounded-md border border-danger/30 bg-danger/5 p-3"
          >
            <p class="text-xs text-danger">
              {{ currentRun.errorMessage }}
            </p>
          </div>
        </div>

        <!-- No active run -->
        <div v-else-if="!currentRunId" class="py-12 text-center">
          <p class="text-sm text-text-muted">No active run. Click "Run" to start the pipeline.</p>
        </div>

        <!-- Live Log -->
        <div v-if="logEntries.length > 0" class="mt-4 card-panel p-4">
          <h3 class="mb-2 text-sm font-semibold text-text-primary">Live Log</h3>
          <div class="max-h-48 overflow-y-auto font-mono text-xs">
            <div
              v-for="(entry, idx) in logEntries"
              :key="idx"
              :class="[
                'py-0.5',
                entry.type === 'error'
                  ? 'text-danger'
                  : entry.type === 'warn'
                    ? 'text-warning'
                    : 'text-text-muted',
              ]"
            >
              [{{ entry.time }}] {{ entry.message }}
            </div>
          </div>
        </div>

        <!-- Past Runs -->
        <div class="mt-6 card-panel p-5">
          <h3 class="mb-3 text-sm font-semibold text-text-primary">Past Runs</h3>
          <div v-if="pastRuns.length === 0" class="text-sm text-text-muted">No past runs.</div>
          <table v-else class="w-full text-sm">
            <thead>
              <tr class="border-b border-border-subtle">
                <th class="pb-2 pr-4 text-left text-xs font-medium text-text-muted">Time</th>
                <th class="pb-2 pr-4 text-left text-xs font-medium text-text-muted">Type</th>
                <th class="pb-2 pr-4 text-left text-xs font-medium text-text-muted">Status</th>
                <th class="pb-2 pr-4 text-left text-xs font-medium text-text-muted">Symbols</th>
                <th class="pb-2 pr-4 text-left text-xs font-medium text-text-muted">Duration</th>
                <th class="pb-2 text-left text-xs font-medium text-text-muted">Actions</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="run in pastRuns"
                :key="run.runId"
                class="border-b border-border-subtle/50 hover:bg-bg-hover/50 cursor-pointer"
                @click="viewRun(run.runId)"
              >
                <td class="py-2 pr-4 text-text-primary">
                  {{ formatTime(run.startedAt) }}
                </td>
                <td class="py-2 pr-4 text-text-muted">
                  {{ run.triggerType }}
                </td>
                <td class="py-2 pr-4">
                  <StatusBadge :status="run.status" :label="run.status" />
                </td>
                <td class="py-2 pr-4 text-text-muted">
                  {{ run.completedCount }}/{{ run.symbolsCount }}
                  <span v-if="run.failedCount > 0" class="text-danger"
                    >({{ run.failedCount }} failed)</span
                  >
                </td>
                <td v-if="run.completedAt" class="py-2 pr-4 text-text-muted">
                  {{ formatDuration(run.startedAt, run.completedAt) }}
                </td>
                <td v-else class="py-2 pr-4 text-text-muted">-</td>
                <td class="py-2 text-text-muted">
                  <button
                    class="text-xs text-brand hover:underline"
                    @click.stop="viewRun(run.runId)"
                  >
                    View
                  </button>
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
import { startJobRun, getJobRunProgress, listJobRuns, cancelJobRun } from '../api/client'
import type { JobRunResponse, JobRunStageResponse } from '../api/types'
import LoadingSpinner from '../components/LoadingSpinner.vue'
import StatusBadge from '../components/StatusBadge.vue'
import StageIcon from '../components/StageIcon.vue'
import ErrorBoundary from '../components/ErrorBoundary.vue'
import { useAsyncData } from '../composables/useAsyncData'

const STAGES = ['DATA_FETCH', 'NEWS', 'SENTIMENT', 'SIGNAL', 'BACKTEST', 'PAPER_TRADE'] as const

const { loading, error, errorMessage, execute } = useAsyncData<void>()
const currentRunId = ref<string | null>(null)
const currentRun = ref<JobRunResponse | null>(null)
const pastRuns = ref<JobRunResponse[]>([])
const stageRows = ref<JobRunStageResponse[]>([])
const logEntries = ref<Array<{ time: string; message: string; type: 'info' | 'warn' | 'error' }>>(
  []
)
const expandedSymbol = ref<string | null>(null)
const isRunning = ref(false)

let pollTimer: ReturnType<typeof setInterval> | null = null

const stages = STAGES

const symbols = computed(() => {
  const seen = new Set<string>()
  stageRows.value.forEach((s) => seen.add(s.symbol))
  return Array.from(seen).sort()
})

const progressPercent = computed(() => {
  if (!currentRun.value || !currentRun.value.symbolsCount) return 0
  return Math.round((currentRun.value.completedCount / currentRun.value.symbolsCount) * 100)
})

// Plain object lookup — updated imperatively, no computed reactivity issues
const stageLookup: Record<
  string,
  {
    status: string
    resultSummary: string | null
    errorMessage: string | null
    durationMs: number | null
  }
> = {}

function rebuildLookup() {
  for (const key of Object.keys(stageLookup)) delete stageLookup[key]
  for (const row of stageRows.value) {
    const k = `${row.symbol}::${row.stageName}`
    stageLookup[k] = {
      status: row.status || 'PENDING',
      resultSummary: row.resultSummary || null,
      errorMessage: row.errorMessage || null,
      durationMs: row.durationMs ?? null,
    }
  }
  console.log(
    '[Orchestrator] rebuildLookup: rows=',
    stageRows.value.length,
    'lookup keys=',
    Object.keys(stageLookup).length,
    'sample=',
    stageLookup['AXISBANK::DATA_FETCH']
  )
}

function getStageStatus(symbol: string, stageName: string): string {
  const cell = stageLookup[`${symbol}::${stageName}`]
  return cell?.status || 'PENDING'
}

function getStageResult(symbol: string, stageName: string): string | null {
  const cell = stageLookup[`${symbol}::${stageName}`]
  return cell?.resultSummary || null
}

function getStageError(symbol: string, stageName: string): string | null {
  const cell = stageLookup[`${symbol}::${stageName}`]
  return cell?.errorMessage || null
}

function getStageDuration(symbol: string, stageName: string): number | null {
  const cell = stageLookup[`${symbol}::${stageName}`]
  return cell?.durationMs ?? null
}

function toggleSymbol(symbol: string) {
  expandedSymbol.value = expandedSymbol.value === symbol ? null : symbol
}

function addLogEntry(message: string, type: 'info' | 'warn' | 'error' = 'info') {
  const now = new Date()
  const time = now.toTimeString().slice(0, 8)
  logEntries.value.push({ time, message, type })
  // Keep last 50 entries
  if (logEntries.value.length > 50) {
    logEntries.value = logEntries.value.slice(-50)
  }
}

async function startRun() {
  try {
    const res = await startJobRun()
    if (res.success && res.data) {
      currentRunId.value = res.data.runId
      currentRun.value = res.data
      isRunning.value = true
      stageRows.value = []
      rebuildLookup()
      logEntries.value = []
      addLogEntry('Pipeline run started')
      startPolling()
    } else {
      errorMessage.value = res.error || 'Failed to start run'
      error.value = true
    }
  } catch (err: unknown) {
    errorMessage.value = err instanceof Error ? err.message : 'Failed to start run'
    error.value = true
  }
}

async function cancelRun() {
  if (!currentRunId.value) return
  try {
    await cancelJobRun(currentRunId.value)
    addLogEntry('Run cancelled', 'warn')
    isRunning.value = false
    stopPolling()
    await refresh()
  } catch {
    errorMessage.value = 'Failed to cancel run'
    error.value = true
  }
}

function refresh() {
  execute(async () => {
    // If we have a currentRunId, load its progress
    if (currentRunId.value) {
      const progressRes = await getJobRunProgress(currentRunId.value)
      if (progressRes.success && progressRes.data) {
        currentRunId.value = progressRes.data.runId
        currentRun.value = {
          runId: progressRes.data.runId,
          triggerType: 'MANUAL',
          status: progressRes.data.status as JobRunResponse['status'],
          startedAt: progressRes.data.startedAt ?? new Date().toISOString(),
          completedAt: progressRes.data.completedAt ?? null,
          symbolsCount: progressRes.data.totalSymbols,
          completedCount: progressRes.data.completedSymbols,
          failedCount: progressRes.data.failedSymbols,
          errorMessage: null,
        }
        stageRows.value = [...progressRes.data.stages]
        rebuildLookup()
        expandedSymbol.value = null
        isRunning.value = (progressRes.data.status as string) === 'RUNNING'

        // Log stage changes
        for (const stage of progressRes.data.stages) {
          if (stage.status === 'COMPLETED' && stage.resultSummary) {
            addLogEntry(`${stage.symbol} ${stage.stageName} -> COMPLETED (${stage.resultSummary})`)
          } else if (stage.status === 'ERROR') {
            addLogEntry(
              `${stage.symbol} ${stage.stageName} -> ERROR: ${stage.errorMessage}`,
              'error'
            )
          }
        }
      }
    } else {
      // No currentRunId — load the latest run's progress
      const runsRes = await listJobRuns()
      if (runsRes.success && runsRes.data && runsRes.data.length > 0) {
        const latest = runsRes.data[0]
        currentRunId.value = latest.runId
        currentRun.value = latest
        const progressRes = await getJobRunProgress(latest.runId)
        if (progressRes.success && progressRes.data) {
          currentRun.value = {
            ...latest,
            status: progressRes.data.status as JobRunResponse['status'],
            completedAt: progressRes.data.completedAt ?? null,
            symbolsCount: progressRes.data.totalSymbols,
            completedCount: progressRes.data.completedSymbols,
            failedCount: progressRes.data.failedSymbols,
          }
          stageRows.value = [...progressRes.data.stages]
          rebuildLookup()
          isRunning.value = (progressRes.data.status as string) === 'RUNNING'
        }
      }
    }

    // Load history
    await loadHistory()
  })
}

async function loadHistory() {
  try {
    const res = await listJobRuns()
    if (res.success && res.data) {
      pastRuns.value = res.data
    }
  } catch {
    // Ignore
  }
}

async function viewRun(runId: string) {
  currentRunId.value = runId
  currentRun.value = pastRuns.value.find((r) => r.runId === runId) || null
  isRunning.value = false
  stopPolling()
  expandedSymbol.value = null
  loading.value = true
  error.value = false
  try {
    const summaryRes = await getJobRunProgress(runId)
    if (summaryRes.success && summaryRes.data) {
      stageRows.value = [...summaryRes.data.stages]
      rebuildLookup()
    }
  } catch {
    // Ignore
  } finally {
    loading.value = false
  }
}

function startPolling() {
  stopPolling()
  pollTimer = setInterval(() => {
    if (currentRunId.value) {
      refresh()
    }
  }, 3000)
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

function formatTime(iso: string): string {
  try {
    return new Date(iso).toLocaleTimeString()
  } catch {
    return iso
  }
}

function formatDuration(start: string, end: string): string {
  const ms = new Date(end).getTime() - new Date(start).getTime()
  if (ms < 0) return '0s'
  const s = Math.floor(ms / 1000)
  const m = Math.floor(s / 60)
  const h = Math.floor(m / 60)
  if (h > 0) return `${h}h ${m % 60}m`
  if (m > 0) return `${m}m ${s % 60}s`
  return `${s}s`
}

onMounted(() => {
  refresh()
})

onUnmounted(() => {
  stopPolling()
})
</script>
