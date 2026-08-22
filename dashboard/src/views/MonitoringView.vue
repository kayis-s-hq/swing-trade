<template>
  <div class="p-6 animate-fade-in">
    <!-- Page Header -->
    <div class="mb-6 flex items-center justify-between">
      <div>
        <h1 class="font-display text-2xl font-semibold text-text-primary">
          LLM Accuracy Monitoring
        </h1>
        <p class="mt-1 text-sm text-text-muted">
          Track sentiment analysis accuracy against actual market outcomes
        </p>
      </div>
    </div>

    <!-- Tabs -->
    <div class="mb-6 flex gap-1 rounded-lg bg-bg-primary p-1">
      <button
        v-for="tab in tabs"
        :key="tab.key"
        class="rounded-md px-4 py-1.5 text-sm font-medium transition-colors"
        :class="
          activeTab === tab.key
            ? 'bg-brand/10 text-brand'
            : 'text-text-muted hover:text-text-primary'
        "
        @click="activeTab = tab.key"
      >
        {{ tab.label }}
      </button>
    </div>

    <ErrorBoundary :error="error">
      <template #error>
        <div class="flex flex-col items-center justify-center py-20">
          <p class="text-sm text-danger">{{ errorMessage }}</p>
          <button
            class="mt-2 rounded-md bg-brand px-3 py-1.5 text-xs font-medium text-white"
            @click="loadAll"
          >
            Retry
          </button>
        </div>
      </template>

      <!-- Overview Tab -->
      <div v-if="activeTab === 'overview'">
        <!-- Metric Cards -->
        <div class="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-4">
          <AccuracyMetricCard
            title="Overall Accuracy"
            :value="formatPercent(stats?.accuracy_pct)"
            :color="getAccuracyColor(stats?.accuracy_pct)"
            :subtext="`${stats?.correct ?? 0}/${stats?.total ?? 0} correct`"
          />
          <AccuracyMetricCard
            title="Directional Accuracy"
            :value="formatPercent(summary?.directional_accuracy)"
            :color="getAccuracyColor(summary?.directional_accuracy)"
            subtext="30-day rolling"
          />
          <AccuracyMetricCard
            title="Avg Confidence"
            :value="formatPercent(summary?.avg_confidence)"
            color="text-brand"
            subtext="LLM self-reported"
          />
          <AccuracyMetricCard
            title="ECE"
            :value="formatPercent(ece?.ece)"
            :color="
              ece?.ece && ece.ece > 15
                ? 'text-warning'
                : ece?.ece && ece.ece > 25
                  ? 'text-danger'
                  : 'text-success'
            "
            subtext="Calibration error"
          />
        </div>

        <!-- Signal Volume -->
        <div class="mt-6 card-panel p-5">
          <h3 class="mb-4 text-sm font-semibold text-text-primary">Signal Volume</h3>
          <div class="grid grid-cols-1 gap-4 md:grid-cols-3">
            <div>
              <p class="text-xs text-text-muted">Today</p>
              <p class="text-2xl font-bold">
                {{ signalVolume?.today_count ?? 0 }}
              </p>
            </div>
            <div>
              <p class="text-xs text-text-muted">7-Day Avg</p>
              <p class="text-2xl font-bold">
                {{ signalVolume?.seven_day_avg ?? 0 }}
              </p>
            </div>
            <div>
              <p class="text-xs text-text-muted">30-Day Avg</p>
              <p class="text-2xl font-bold">
                {{ signalVolume?.thirty_day_avg ?? 0 }}
              </p>
            </div>
          </div>
        </div>

        <!-- By Sentiment -->
        <div class="mt-6 grid grid-cols-1 gap-6 md:grid-cols-2">
          <div class="card-panel p-5">
            <h3 class="mb-4 text-sm font-semibold text-text-primary">By Sentiment</h3>
            <div v-if="!stats?.by_sentiment" class="flex justify-center py-4">
              <LoadingSpinner />
            </div>
            <div v-else class="space-y-3">
              <div
                v-for="(count, sentiment) in stats?.by_sentiment"
                :key="sentiment"
                class="flex items-center gap-3"
              >
                <span class="w-14 text-sm font-medium text-text-secondary">{{ sentiment }}</span>
                <div class="flex-1 h-2 overflow-hidden rounded-full bg-bg-hover">
                  <div
                    class="h-full rounded-full"
                    :class="
                      sentiment === 'POSITIVE'
                        ? 'bg-green-500'
                        : sentiment === 'NEGATIVE'
                          ? 'bg-red-500'
                          : 'bg-amber-500'
                    "
                    :style="{ width: `${total > 0 ? (count / total) * 100 : 0}%` }"
                  />
                </div>
                <span class="text-sm text-text-muted">{{ count }}</span>
              </div>
            </div>
          </div>

          <div class="card-panel p-5">
            <h3 class="mb-4 text-sm font-semibold text-text-primary">By Symbol</h3>
            <div v-if="!stats?.by_symbol" class="flex justify-center py-4">
              <LoadingSpinner />
            </div>
            <div v-else class="space-y-3">
              <div
                v-for="(count, symbol) in stats?.by_symbol"
                :key="symbol"
                class="flex items-center gap-3"
              >
                <span class="w-24 truncate text-sm font-medium text-text-secondary">{{
                  symbol
                }}</span>
                <div class="flex-1 h-2 overflow-hidden rounded-full bg-bg-hover">
                  <div
                    class="h-full rounded-full bg-brand"
                    :style="{ width: `${total > 0 ? (count / total) * 100 : 0}%` }"
                  />
                </div>
                <span class="text-sm text-text-muted">{{ count }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Breakdown Tab -->
      <div v-if="activeTab === 'breakdown'">
        <!-- Accuracy by Window -->
        <div class="mb-6 card-panel p-5">
          <h3 class="mb-4 text-sm font-semibold text-text-primary">
            Accuracy by Evaluation Window
          </h3>
          <div v-if="!byWindow" class="flex justify-center py-4">
            <LoadingSpinner />
          </div>
          <div v-else-if="byWindow.length === 0" class="text-sm text-text-muted">
            No data available yet.
          </div>
          <div v-else>
            <div class="overflow-x-auto">
              <table class="w-full text-sm">
                <thead>
                  <tr class="text-text-muted">
                    <th class="pb-3 text-left font-medium">Window</th>
                    <th class="pb-3 text-left font-medium">Total</th>
                    <th class="pb-3 text-left font-medium">Accuracy</th>
                    <th class="pb-3 text-left font-medium">Avg Return</th>
                  </tr>
                </thead>
                <tbody class="text-text-secondary">
                  <tr v-for="w in byWindow" :key="w.window" class="border-t border-border-subtle">
                    <td class="py-3">
                      {{ w.window }}
                    </td>
                    <td class="py-3">
                      {{ w.total }}
                    </td>
                    <td class="py-3">
                      <span :class="getAccuracyColor(w.accuracy)">{{
                        formatPercent(w.accuracy)
                      }}</span>
                    </td>
                    <td class="py-3">
                      {{ formatPercent(w.avg_return) }}
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>

        <!-- Accuracy by Regime -->
        <div class="mb-6 card-panel p-5">
          <h3 class="mb-4 text-sm font-semibold text-text-primary">Accuracy by Market Regime</h3>
          <div v-if="!byRegime" class="flex justify-center py-4">
            <LoadingSpinner />
          </div>
          <div v-else-if="byRegime.length === 0" class="text-sm text-text-muted">
            No data available yet.
          </div>
          <div v-else>
            <div class="overflow-x-auto">
              <table class="w-full text-sm">
                <thead>
                  <tr class="text-text-muted">
                    <th class="pb-3 text-left font-medium">Regime</th>
                    <th class="pb-3 text-left font-medium">Total</th>
                    <th class="pb-3 text-left font-medium">Accuracy</th>
                    <th class="pb-3 text-left font-medium">Avg Confidence</th>
                  </tr>
                </thead>
                <tbody class="text-text-secondary">
                  <tr v-for="r in byRegime" :key="r.regime" class="border-t border-border-subtle">
                    <td class="py-3">
                      <span
                        :class="
                          r.regime === 'BULL'
                            ? 'text-green-500'
                            : r.regime === 'BEAR'
                              ? 'text-red-500'
                              : 'text-amber-500'
                        "
                      >
                        {{ r.regime }}
                      </span>
                    </td>
                    <td class="py-3">
                      {{ r.total }}
                    </td>
                    <td class="py-3">
                      <span :class="getAccuracyColor(r.accuracy)">{{
                        formatPercent(r.accuracy)
                      }}</span>
                    </td>
                    <td class="py-3">
                      {{ formatPercent(r.avg_confidence) }}
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>

        <!-- Accuracy by Symbol -->
        <div class="card-panel p-5">
          <h3 class="mb-4 text-sm font-semibold text-text-primary">Accuracy by Symbol</h3>
          <div v-if="!bySymbol" class="flex justify-center py-4">
            <LoadingSpinner />
          </div>
          <div v-else-if="bySymbol.length === 0" class="text-sm text-text-muted">
            No data available yet.
          </div>
          <div v-else>
            <div class="overflow-x-auto">
              <table class="w-full text-sm">
                <thead>
                  <tr class="text-text-muted">
                    <th class="pb-3 text-left font-medium">Symbol</th>
                    <th class="pb-3 text-left font-medium">Signals</th>
                    <th class="pb-3 text-left font-medium">Accuracy</th>
                    <th class="pb-3 text-left font-medium">Avg Confidence</th>
                  </tr>
                </thead>
                <tbody class="text-text-secondary">
                  <tr v-for="s in bySymbol" :key="s.symbol" class="border-t border-border-subtle">
                    <td class="py-3 font-medium">
                      {{ s.symbol }}
                    </td>
                    <td class="py-3">
                      {{ s.total_signals }}
                    </td>
                    <td class="py-3">
                      <span :class="getAccuracyColor(s.accuracy)">{{
                        formatPercent(s.accuracy)
                      }}</span>
                    </td>
                    <td class="py-3">
                      {{ formatPercent(s.avg_confidence) }}
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </div>

      <!-- Calibration Tab -->
      <div v-if="activeTab === 'calibration'">
        <!-- ECE Display -->
        <div class="mb-6 card-panel p-5">
          <h3 class="mb-4 text-sm font-semibold text-text-primary">Expected Calibration Error</h3>
          <div class="flex items-end gap-3">
            <span
              class="text-5xl font-bold"
              :class="ece?.ece && ece.ece > 15 ? 'text-warning' : 'text-success'"
            >
              {{ ece ? formatPercent(ece.ece) : '—' }}
            </span>
            <span class="mb-1.5 text-sm text-text-muted">{{ ece?.bins ?? 0 }} bins</span>
          </div>
          <p class="mt-2 text-xs text-text-muted">
            Perfectly calibrated model follows the diagonal (predicted = actual). Lower is better.
          </p>
          <div class="mt-2 text-xs text-text-muted">
            <span class="text-warning">Warning threshold: 15%</span> |
            <span class="text-danger">Critical threshold: 25%</span>
          </div>
        </div>

        <!-- Calibration Curve -->
        <div class="card-panel p-5">
          <h3 class="mb-4 text-sm font-semibold text-text-primary">Calibration Curve</h3>
          <div v-if="!calibration" class="flex justify-center py-4">
            <LoadingSpinner />
          </div>
          <div v-else-if="calibration.length === 0" class="text-sm text-text-muted">
            No data available yet.
          </div>
          <div v-else class="relative" style="height: 320px">
            <svg :viewBox="`0 0 400 300`" class="h-full w-full">
              <!-- Grid -->
              <line x1="40" y1="20" x2="40" y2="260" stroke="currentColor" stroke-opacity="0.1" />
              <line x1="40" y1="260" x2="380" y2="260" stroke="currentColor" stroke-opacity="0.1" />
              <line
                x1="40"
                y1="140"
                x2="380"
                y2="140"
                stroke="currentColor"
                stroke-opacity="0.05"
              />
              <!-- Diagonal (perfect calibration) -->
              <line
                x1="40"
                y1="260"
                x2="360"
                y2="40"
                stroke="currentColor"
                stroke-opacity="0.3"
                stroke-dasharray="4,4"
              />
              <!-- Data points -->
              <circle
                v-for="d in calibration"
                :key="d.confidence_bin"
                :cx="40 + d.confidence_bin * 34"
                :cy="260 - d.actual_accuracy * 220"
                r="6"
                :fill="d.error > 0.15 ? '#ef4444' : d.error > 0.1 ? '#f59e0b' : '#3b82f6'"
                :stroke="d.error > 0.15 ? '#ef4444' : d.error > 0.1 ? '#f59e0b' : '#3b82f6'"
                stroke-width="2"
                stroke-opacity="0.3"
              />
              <!-- Error bars -->
              <line
                v-for="d in calibration"
                :key="d.confidence_bin + '-err'"
                :x1="40 + d.confidence_bin * 34"
                :y1="260 - d.predicted_confidence * 220"
                :x2="40 + d.confidence_bin * 34"
                :y2="260 - d.actual_accuracy * 220"
                :stroke="d.error > 0.15 ? '#ef4444' : '#3b82f6'"
                stroke-width="2"
                stroke-opacity="0.5"
              />
              <!-- Axis labels -->
              <text
                x="200"
                y="295"
                text-anchor="middle"
                fill="currentColor"
                fill-opacity="0.5"
                font-size="11"
              >
                Predicted Confidence
              </text>
              <text
                x="15"
                y="150"
                text-anchor="middle"
                fill="currentColor"
                fill-opacity="0.5"
                font-size="11"
                transform="rotate(-90 15 150)"
              >
                Actual Accuracy
              </text>
            </svg>
          </div>

          <!-- Calibration table -->
          <div class="mt-4 overflow-x-auto">
            <table class="w-full text-sm">
              <thead>
                <tr class="text-text-muted">
                  <th class="pb-3 text-left font-medium">Bin</th>
                  <th class="pb-3 text-left font-medium">Predicted</th>
                  <th class="pb-3 text-left font-medium">Actual</th>
                  <th class="pb-3 text-left font-medium">Error</th>
                  <th class="pb-3 text-left font-medium">Count</th>
                </tr>
              </thead>
              <tbody class="text-text-secondary">
                <tr
                  v-for="d in calibration"
                  :key="d.confidence_bin"
                  class="border-t border-border-subtle"
                >
                  <td class="py-2">
                    {{ formatPercent(d.confidence_bin * 0.1) }}
                  </td>
                  <td class="py-2">
                    {{ formatPercent(d.predicted_confidence) }}
                  </td>
                  <td class="py-2">
                    {{ formatPercent(d.actual_accuracy) }}
                  </td>
                  <td
                    class="py-2"
                    :class="
                      d.error > 0.15
                        ? 'text-danger'
                        : d.error > 0.1
                          ? 'text-warning'
                          : 'text-success'
                    "
                  >
                    {{ formatPercent(d.error) }}
                  </td>
                  <td class="py-2">
                    {{ d.count }}
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>

      <!-- A/B Tab -->
      <div v-if="activeTab === 'ab-testing'">
        <div class="card-panel p-5">
          <h3 class="mb-4 text-sm font-semibold text-text-primary">
            A/B Testing & Model Comparison
          </h3>
          <p class="text-sm text-text-muted">
            Compare accuracy across different prompt versions and model configurations. Data is
            stored in prompt_hash and model_version fields.
          </p>
          <div v-if="!stats" class="flex justify-center py-8">
            <LoadingSpinner />
          </div>
          <div v-else-if="stats.total === 0" class="text-sm text-text-muted py-4">
            No accuracy data available yet. Run the evaluation job to populate data.
          </div>
          <div v-else>
            <div class="mt-4 grid grid-cols-1 gap-4 md:grid-cols-2">
              <div class="rounded-lg bg-bg-hover p-4">
                <h4 class="mb-2 text-xs font-semibold uppercase tracking-wider text-text-muted">
                  Total Evaluations
                </h4>
                <p class="text-3xl font-bold">
                  {{ stats.total }}
                </p>
              </div>
              <div class="rounded-lg bg-bg-hover p-4">
                <h4 class="mb-2 text-xs font-semibold uppercase tracking-wider text-text-muted">
                  Evaluated
                </h4>
                <p class="text-3xl font-bold text-success">
                  {{ stats.correct }}
                </p>
              </div>
            </div>
            <div class="mt-6 text-xs text-text-muted">
              <p>
                The evaluation job runs nightly at 2 AM to compute ground truth returns and
                accuracy. To compare prompts or models, ensure they are recorded in the prompt_hash
                and model_version fields during analysis.
              </p>
            </div>
          </div>
        </div>
      </div>

      <!-- Info -->
      <div class="mt-6 card-panel p-4">
        <p class="text-xs text-text-muted">
          Accuracy is computed by comparing LLM sentiment direction against actual stock returns
          over 1/5/21-day windows. Ground truth: UP if return ≥ +0.5%, DOWN if return ≤ -0.5%, FLAT
          otherwise. Evaluation runs nightly via scheduled job.
        </p>
      </div>
    </ErrorBoundary>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import {
  getAccuracyStats,
  getAccuracySummary,
  getAccuracyByWindow,
  getAccuracyByRegime,
  getAccuracyBySymbol,
  getCalibration,
  getSignalVolume,
  getECE,
} from '../api/client'
import type {
  SentimentAccuracyStats,
  AccuracySummary,
  AccuracyByWindow,
  AccuracyByRegime,
  AccuracyBySymbol,
  CalibrationData,
  SignalVolumeStats,
  ECEStats,
} from '../api/types'
import LoadingSpinner from '../components/LoadingSpinner.vue'
import AccuracyMetricCard from '../components/AccuracyMetricCard.vue'
import ErrorBoundary from '../components/ErrorBoundary.vue'
import { useAsyncData } from '../composables/useAsyncData'

const { error, errorMessage, execute } = useAsyncData<void>()

const tabs = [
  { key: 'overview', label: 'Overview' },
  { key: 'breakdown', label: 'Breakdown' },
  { key: 'calibration', label: 'Calibration' },
  { key: 'ab-testing', label: 'A/B Testing' },
]
const activeTab = ref('overview')
const stats = ref<SentimentAccuracyStats | null>(null)
const summary = ref<AccuracySummary | null>(null)
const byWindow = ref<AccuracyByWindow[] | null>(null)
const byRegime = ref<AccuracyByRegime[] | null>(null)
const bySymbol = ref<AccuracyBySymbol[] | null>(null)
const calibration = ref<CalibrationData[] | null>(null)
const signalVolume = ref<SignalVolumeStats | null>(null)
const ece = ref<ECEStats | null>(null)

const total = ref(0)

const loadAll = () => {
  execute(async () => {
    const [statsRes, summaryRes, windowRes, regimeRes, symbolRes, calRes, volRes, eceRes] =
      await Promise.all([
        getAccuracyStats(),
        getAccuracySummary(),
        getAccuracyByWindow(),
        getAccuracyByRegime(),
        getAccuracyBySymbol(),
        getCalibration(),
        getSignalVolume(),
        getECE(),
      ])

    if (statsRes.success && statsRes.data) {
      stats.value = statsRes.data
      total.value = statsRes.data.total
    } else {
      throw new Error(statsRes.error || 'Failed to load accuracy data')
    }

    if (summaryRes.success && summaryRes.data) summary.value = summaryRes.data
    if (windowRes.success && windowRes.data) byWindow.value = windowRes.data
    if (regimeRes.success && regimeRes.data) byRegime.value = regimeRes.data
    if (symbolRes.success && symbolRes.data) bySymbol.value = symbolRes.data
    if (calRes.success && calRes.data) calibration.value = calRes.data
    if (volRes.success && volRes.data) signalVolume.value = volRes.data
    if (eceRes.success && eceRes.data) ece.value = eceRes.data
  })
}

const formatPercent = (v: number | undefined | null): string => {
  if (v == null) return '—'
  return `${(v * 100).toFixed(1)}%`
}

const getAccuracyColor = (v: number | undefined | null): string => {
  if (v == null) return 'text-text-muted'
  if (v >= 55) return 'text-success'
  if (v >= 52) return 'text-warning'
  return 'text-danger'
}

onMounted(() => {
  loadAll()
})
</script>
