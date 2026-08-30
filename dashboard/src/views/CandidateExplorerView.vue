<template>
  <div class="view-shell candidate-explorer p-4 sm:p-6 animate-fade-in">
    <header class="mb-6 flex flex-col gap-4 rounded-2xl border border-border-subtle p-5 sm:flex-row sm:items-end sm:justify-between sm:p-7">
      <div>
        <p class="explorer-kicker">Research desk / candidate discovery</p>
        <h1 class="mt-2 font-display text-3xl font-semibold tracking-tight text-text-primary">
          Candidate Explorer
        </h1>
        <p class="mt-2 max-w-2xl text-sm leading-6 text-text-muted">
          Scan the NSE universe for technical BUY signals with a profitable backtest before adding a symbol to the pilot watchlist.
        </p>
      </div>
      <div class="flex items-center gap-2">
        <button
          v-if="run?.status === 'RUNNING'"
          class="rounded-xl border border-warning/40 px-4 py-2.5 text-sm font-semibold text-warning transition-transform duration-150 ease-out active:scale-[0.97]"
          @click="cancel"
        >
          Cancel scan
        </button>
        <button
          v-else
          class="rounded-xl bg-brand px-4 py-2.5 text-sm font-semibold text-brand-text transition-transform duration-150 ease-out hover:bg-brand-hover active:scale-[0.97]"
          :disabled="loading"
          @click="start"
        >
          {{ loading ? 'Starting...' : 'Scan NSE universe' }}
        </button>
      </div>
    </header>

    <div class="mb-6 grid gap-3 sm:grid-cols-2 xl:grid-cols-5">
      <div v-for="metric in metrics" :key="metric.label" class="rounded-xl border border-border-subtle bg-bg-surface/45 p-4">
        <span class="text-xs font-medium uppercase tracking-[0.12em] text-text-muted">{{ metric.label }}</span>
        <strong class="mt-2 block text-xl font-semibold" :class="metric.tone">{{ metric.value }}</strong>
        <small class="mt-1 block text-xs text-text-muted">{{ metric.detail }}</small>
      </div>
    </div>

    <section v-if="run?.status === 'RUNNING'" class="card-panel mb-4 overflow-hidden">
      <div class="flex items-center justify-between border-b border-border-subtle px-5 py-4">
        <div>
          <p class="explorer-kicker">Live telemetry</p>
          <h2 class="mt-1 text-base font-semibold text-text-primary">Scan activity</h2>
        </div>
        <span class="inline-flex items-center gap-2 text-xs text-text-muted">
          <span class="h-1.5 w-1.5 rounded-full" :class="streamConnected ? 'bg-success pulse-dot' : 'bg-text-muted'" />
          {{ streamConnected ? 'Streaming' : 'Waiting for scan' }}
        </span>
      </div>
      <div v-if="logs.length === 0" class="px-5 py-8 text-sm text-text-muted">
        Scan events will appear here as symbols move through data, signal, and backtest stages.
      </div>
      <ol v-else class="max-h-72 overflow-y-auto px-5 py-3 font-mono text-xs" aria-live="polite">
        <li v-for="(entry, index) in logs" :key="`${entry.timestamp}-${index}`" class="flex gap-3 border-b border-border-subtle/40 py-2 last:border-0">
          <time class="shrink-0 text-text-muted">{{ formatLogTime(entry.timestamp) }}</time>
          <span class="shrink-0 font-semibold" :class="logLevelClass(entry.level)">{{ entry.level }}</span>
          <span class="text-text-secondary">{{ entry.symbol ? `${entry.symbol}: ` : '' }}{{ entry.message }}</span>
        </li>
      </ol>
    </section>

    <section class="card-panel overflow-hidden">
      <div class="flex flex-col gap-3 border-b border-border-subtle px-5 py-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <p class="explorer-kicker">Three-stage screen</p>
          <h2 class="mt-1 text-base font-semibold text-text-primary">Signal candidates</h2>
        </div>
        <div class="flex items-center gap-2 text-xs text-text-muted">
          <span class="rounded-full bg-brand/10 px-2.5 py-1 font-semibold text-brand">BUY + ≥45% win rate + positive return</span>
          <span v-if="run">Updated {{ updatedAt }}</span>
        </div>
      </div>

      <div v-if="error" class="px-5 py-8 text-sm text-error">{{ error }}</div>
      <div v-else-if="!run" class="px-5 py-16 text-center">
        <div class="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-2xl bg-brand/10 text-xl text-brand">⌕</div>
        <h3 class="font-semibold text-text-primary">Find the next pilot symbol</h3>
        <p class="mx-auto mt-2 max-w-md text-sm leading-6 text-text-muted">
          Start a scan to fetch missing history, calculate the current signal, and run the default strategy backtest.
        </p>
      </div>
      <div v-else-if="results.length === 0 && run.status === 'RUNNING'" class="px-5 py-16 text-center text-sm text-text-muted">
        Waiting for the first symbol to finish…
      </div>
      <div v-else class="overflow-x-auto">
        <table class="w-full min-w-[850px] text-left text-sm">
          <thead class="border-b border-border-subtle bg-bg-surface/60 text-xs uppercase tracking-[0.12em] text-text-muted">
            <tr>
              <th class="px-5 py-3 font-semibold">Symbol</th>
              <th class="px-3 py-3 font-semibold">Data</th>
              <th class="px-3 py-3 font-semibold">Signal</th>
              <th class="px-3 py-3 font-semibold">Trades</th>
              <th class="px-3 py-3 font-semibold">Win rate</th>
              <th class="px-3 py-3 font-semibold">Return</th>
              <th class="px-3 py-3 font-semibold">Decision</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-border-subtle/70">
            <tr v-for="item in filteredResults" :key="item.symbol" class="transition-colors duration-150 hover:bg-bg-hover/60">
              <td class="px-5 py-3.5 font-semibold text-text-primary">{{ item.symbol }}</td>
              <td class="px-3 py-3.5 text-text-muted">{{ item.candleCount.toLocaleString() }} candles</td>
              <td class="px-3 py-3.5"><span :class="signalClass(item.signalType)">{{ item.signalType ?? '—' }}</span></td>
              <td class="px-3 py-3.5 text-text-muted">{{ item.totalTrades ?? '—' }}</td>
              <td class="px-3 py-3.5 text-text-primary">{{ percent(item.winRate) }}</td>
              <td class="px-3 py-3.5" :class="(item.totalReturn ?? 0) > 0 ? 'text-success' : 'text-text-muted'">{{ percent(item.totalReturn) }}</td>
              <td class="px-3 py-3.5">
                <span v-if="item.activated" class="rounded-full bg-success/10 px-2.5 py-1 text-xs font-semibold text-success">Activated</span>
                <span v-else class="text-xs text-text-muted">{{ item.reason ?? item.dataStatus }}</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-if="run && results.length > 0" class="flex items-center justify-between border-t border-border-subtle px-5 py-3 text-xs text-text-muted">
        <span>Showing {{ filteredResults.length }} of {{ results.length }} returned results</span>
        <span v-if="run.status === 'RUNNING'" class="inline-flex items-center gap-2 text-brand"><span class="h-1.5 w-1.5 rounded-full bg-brand pulse-dot" />Scanning in the background</span>
      </div>
    </section>

    <p class="mt-4 text-xs leading-5 text-text-muted">
      Candidate scans never invoke paper trading. Symbols are activated only after the current BUY signal and backtest gate both pass.
    </p>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { asAppError } from '../errors/appError'
import {
  cancelCandidateScan,
  getCandidateScan,
  getCandidateScanHistory,
  getCandidateScanResults,
  openCandidateScanStream,
  startCandidateScan,
} from '../api/candidateScan'
import type { CandidateScanLogEvent, CandidateScanResult, CandidateScanRun } from '../api/types'

const run = ref<CandidateScanRun | null>(null)
const results = ref<CandidateScanResult[]>([])
const loading = ref(false)
const error = ref('')
const updatedAt = ref('—')
let timer: ReturnType<typeof setInterval> | undefined
let stream: EventSource | null = null
const logs = ref<CandidateScanLogEvent[]>([])
const streamConnected = ref(false)

const filteredResults = computed(() => [...results.value].sort((a, b) => Number(b.qualified) - Number(a.qualified) || a.symbol.localeCompare(b.symbol)))
const metrics = computed(() => [
  { label: 'Universe', value: run.value ? run.value.totalSymbols : '—', detail: 'NSE symbols', tone: 'text-text-primary' },
  { label: 'Completed', value: run.value ? `${run.value.completedSymbols}/${run.value.totalSymbols}` : '—', detail: 'processed', tone: 'text-text-primary' },
  { label: 'BUY candidates', value: run.value?.qualifiedSymbols ?? '—', detail: 'passed gate', tone: 'text-brand' },
  { label: 'Failed', value: run.value?.failedSymbols ?? '—', detail: 'provider or data errors', tone: 'text-error' },
  { label: 'Status', value: run.value?.status ?? 'READY', detail: 'no trades run', tone: 'text-text-primary' },
])

const percent = (value?: number) => value == null ? '—' : `${value.toFixed(1)}%`
const signalClass = (signal?: string) => signal === 'BUY'
  ? 'rounded-full bg-success/10 px-2.5 py-1 text-xs font-semibold text-success'
  : signal === 'SELL' ? 'rounded-full bg-error/10 px-2.5 py-1 text-xs font-semibold text-error' : 'text-xs text-text-muted'

async function load(runId: string) {
  const [nextRun, nextResults] = await Promise.all([
    getCandidateScan(runId),
    getCandidateScanResults(runId),
  ])
  run.value = nextRun
  results.value = nextResults
  updatedAt.value = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
  if (nextRun.status !== 'RUNNING') stopPolling()
}

function startPolling(runId: string) {
  stopPolling()
  timer = setInterval(() => void load(runId).catch(handleError), 2000)
}
function stopPolling() { if (timer) clearInterval(timer); timer = undefined }
function closeStream() { stream?.close(); stream = null; streamConnected.value = false }
function startStream(runId: string) {
  closeStream()
  stream = openCandidateScanStream(runId, (event) => {
    streamConnected.value = true
    logs.value = [...logs.value, event].slice(-100)
    if (event.eventType === 'RUN_COMPLETED' || event.eventType === 'RUN_CANCELLED') closeStream()
  }, () => { streamConnected.value = false })
}
function handleError(cause: unknown) { error.value = asAppError(cause).message }
function formatLogTime(value: string) { return new Date(value).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' }) }
function logLevelClass(level: CandidateScanLogEvent['level']) {
  return level === 'ERROR' ? 'text-error' : level === 'WARN' ? 'text-warning' : level === 'SUCCESS' ? 'text-success' : 'text-brand'
}

async function start() {
  loading.value = true; error.value = ''
  try {
    const started = await startCandidateScan()
    run.value = started; results.value = []; logs.value = []; startStream(started.runId); startPolling(started.runId); await load(started.runId)
  } catch (cause) { handleError(cause) } finally { loading.value = false }
}

async function cancel() {
  if (!run.value) return
  try { run.value = await cancelCandidateScan(run.value.runId); stopPolling(); closeStream() }
  catch (cause) { handleError(cause) }
}

onMounted(async () => {
  try {
    const history = await getCandidateScanHistory()
    if (history[0]) {
      await load(history[0].runId)
      logs.value = []
      if (run.value?.status === 'RUNNING') {
        startStream(history[0].runId)
        startPolling(history[0].runId)
      }
    }
  } catch (cause) { handleError(cause) }
})
onBeforeUnmount(() => { stopPolling(); closeStream() })
</script>

<style scoped>
.candidate-explorer { --explorer-ease: cubic-bezier(0.23, 1, 0.32, 1); }
.explorer-kicker { color: var(--color-brand); font-size: 0.68rem; font-weight: 700; letter-spacing: 0.14em; text-transform: uppercase; }
</style>
