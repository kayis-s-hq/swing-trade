<template>
  <div class="view-shell p-4 sm:p-6 animate-fade-in">
    <div class="mb-6 flex flex-wrap items-end justify-between gap-3">
      <div>
        <h1 class="font-display text-2xl font-semibold text-text-primary">Strategy report</h1>
        <p class="mt-1 text-sm text-text-muted">
          Which variant wins the daily signal tournament, and how its picks perform
        </p>
      </div>
      <label class="text-xs text-text-muted">
        Window
        <select
          v-model.number="days"
          class="ml-2 rounded-md border border-border-subtle bg-bg-primary px-2 py-1 text-sm text-text-primary"
          aria-label="Report window"
          @change="load"
        >
          <option :value="7">Last 7 days</option>
          <option :value="30">Last 30 days</option>
          <option :value="90">Last 90 days</option>
        </select>
      </label>
    </div>

    <div v-if="loading && !report" class="flex items-center justify-center py-20">
      <LoadingSpinner message="Loading strategy report..." />
    </div>
    <p v-else-if="errorText" class="card-panel p-5 text-sm text-danger" role="alert">
      {{ errorText }}
    </p>

    <template v-else-if="report">
      <div class="mb-6 grid grid-cols-2 gap-3 lg:grid-cols-4">
        <div class="card-panel p-4">
          <div class="text-xs text-text-muted">Tournaments</div>
          <div class="text-xl font-semibold text-text-primary">{{ report.totals.tournaments }}</div>
        </div>
        <div class="card-panel p-4">
          <div class="text-xs text-text-muted">Executed / blocked / pending</div>
          <div class="text-xl font-semibold text-text-primary">
            {{ report.totals.executed }} / {{ report.totals.blocked }} / {{ report.totals.pending }}
          </div>
        </div>
        <div class="card-panel p-4">
          <div class="text-xs text-text-muted">Selected-book P&amp;L</div>
          <div class="text-xl font-semibold" :class="pnlClass(report.totals.selectedPnl)">
            {{ money(report.totals.selectedPnl) }}
          </div>
        </div>
        <div class="card-panel p-4">
          <div class="text-xs text-text-muted">Avg selection regret</div>
          <div class="text-xl font-semibold text-text-primary">
            {{ pct(report.totals.avgRegretPct) }}
          </div>
        </div>
      </div>

      <div class="card-panel mb-6 p-5" aria-label="Wins by variant">
        <h2 class="mb-3 text-sm font-semibold text-text-primary">Wins by variant</h2>
        <p v-if="report.totals.tournaments === 0" class="text-sm text-text-muted">
          No tournament winners in this window.
        </p>
        <div v-else class="space-y-2">
          <div
            v-for="v in report.variants"
            :key="v.variantId"
            class="flex items-center gap-3 text-sm"
          >
            <span class="w-32 shrink-0 truncate text-text-primary">{{ v.variantId }}</span>
            <div class="h-3 flex-1 rounded-full bg-bg-primary/50">
              <div
                class="h-full rounded-full bg-brand"
                :style="{ width: (v.selectionRatePct ?? 0) + '%' }"
              />
            </div>
            <span class="w-20 shrink-0 text-right text-text-muted">
              {{ v.timesSelected }} ({{ pct(v.selectionRatePct) }})
            </span>
          </div>
        </div>
      </div>

      <div class="card-panel p-5" aria-label="Strategy leaderboard">
        <h2 class="mb-3 text-sm font-semibold text-text-primary">Leaderboard</h2>
        <p v-if="report.variants.length === 0" class="text-sm text-text-muted">
          No active variants.
        </p>
        <div v-else class="overflow-x-auto">
          <table class="w-full min-w-[960px] text-sm">
            <thead>
              <tr
                class="border-b border-border-subtle text-left text-xs font-medium text-text-muted"
              >
                <th class="pb-2 pr-3">Variant</th>
                <th class="pb-2 pr-3">Signals / BUY</th>
                <th class="pb-2 pr-3">Won</th>
                <th class="pb-2 pr-3">Selected trades</th>
                <th class="pb-2 pr-3">Selected P&amp;L</th>
                <th class="pb-2 pr-3">Shadow trades</th>
                <th class="pb-2 pr-3">Shadow win</th>
                <th class="pb-2 pr-3">Shadow P&amp;L</th>
                <th class="pb-2 pr-3">Agreement</th>
                <th class="pb-2 pr-3">Regret</th>
                <th class="pb-2">Vetoes (sent/LLM)</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="v in report.variants"
                :key="v.variantId"
                class="border-b border-border-subtle/50"
              >
                <td class="py-2 pr-3">
                  <div class="font-medium text-text-primary">{{ v.variantId }}</div>
                  <div class="text-xs text-text-muted">{{ v.strategyType }} · {{ v.mode }}</div>
                </td>
                <td class="py-2 pr-3 text-text-muted">
                  {{ v.signalsGenerated }} / {{ v.buySignals }}
                </td>
                <td class="py-2 pr-3 text-text-primary">{{ v.timesSelected }}</td>
                <td class="py-2 pr-3 text-text-muted">
                  {{ v.selectedTrades }} ({{ pct(v.selectedWinRatePct) }} win)
                </td>
                <td class="py-2 pr-3" :class="pnlClass(v.selectedPnl)">
                  {{ money(v.selectedPnl) }}
                </td>
                <td class="py-2 pr-3 text-text-muted">{{ v.shadowTrades }}</td>
                <td class="py-2 pr-3 text-text-muted">{{ pct(v.shadowWinRatePct) }}</td>
                <td class="py-2 pr-3" :class="pnlClass(v.shadowPnl)">{{ money(v.shadowPnl) }}</td>
                <td class="py-2 pr-3 text-text-muted">{{ pct(v.agreementRatePct) }}</td>
                <td class="py-2 pr-3 text-text-muted">{{ pct(v.avgRegretPct) }}</td>
                <td class="py-2 text-text-muted">{{ v.sentimentVetoes }} / {{ v.llmVetoes }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <p class="mt-3 text-xs text-text-muted">
          Regret = gap between the best rejected BUY's shadow return and the winner's, only when
          both have closed trades. Agreement = share of other active variants also buying on the
          same day.
        </p>
      </div>

      <div class="card-panel mt-6 p-5" aria-label="Arbitration rules">
        <h3 class="text-sm font-semibold text-text-primary">Arbitration rules</h3>
        <p class="mt-0.5 mb-3 text-xs text-text-muted">
          Recorded tournaments replayed under each rule, scored by the picked variant's own shadow
          trade on that symbol. Evidence-ranked uses only trades closed before each decision.
        </p>
        <p v-if="!comparison || comparison.tournaments === 0" class="text-sm text-text-muted">
          Not enough recorded tournaments yet.
        </p>
        <div v-else class="overflow-x-auto">
          <table class="w-full min-w-[520px] text-sm">
            <thead>
              <tr class="border-b border-border-subtle text-left text-xs text-text-muted">
                <th class="pb-2 pr-3 font-medium">Rule</th>
                <th class="pb-2 pr-3 font-medium">Decisions</th>
                <th class="pb-2 pr-3 font-medium">With outcome</th>
                <th class="pb-2 pr-3 font-medium">Avg return</th>
                <th class="pb-2 pr-3 font-medium">Win rate</th>
                <th class="pb-2 font-medium">Differs from confidence</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="r in comparison.rules"
                :key="r.rule"
                class="border-b border-border-subtle/50"
              >
                <td class="py-2 pr-3 font-medium text-text-primary">{{ r.rule }}</td>
                <td class="py-2 pr-3 text-text-muted">{{ r.decisions }}</td>
                <td class="py-2 pr-3 text-text-muted">{{ r.decisionsWithOutcome }}</td>
                <td class="py-2 pr-3" :class="pnlClass(r.avgReturnPct ?? 0)">
                  {{ pct(r.avgReturnPct) }}
                </td>
                <td class="py-2 pr-3 text-text-muted">{{ pct(r.winRatePct) }}</td>
                <td class="py-2 text-text-muted">{{ r.differsFromHighestConfidence }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import LoadingSpinner from '../components/LoadingSpinner.vue'
import {
  getArbitrationComparison,
  getStrategyReport,
  type ArbitrationComparison,
  type StrategyReport,
} from '../api/strategyReport'

const days = ref(30)
const report = ref<StrategyReport | null>(null)
const comparison = ref<ArbitrationComparison | null>(null)
const loading = ref(false)
const errorText = ref('')

function iso(d: Date): string {
  return d.toISOString().slice(0, 10)
}

async function load() {
  loading.value = true
  errorText.value = ''
  try {
    const to = new Date()
    const from = new Date(to.getTime() - days.value * 24 * 60 * 60 * 1000)
    report.value = await getStrategyReport(iso(from), iso(to))
    // Supplementary: the report must render even if the replay fails.
    try {
      comparison.value = await getArbitrationComparison(iso(from), iso(to))
    } catch {
      comparison.value = null
    }
  } catch {
    errorText.value = 'The strategy report could not be loaded.'
  } finally {
    loading.value = false
  }
}

function pct(value: number | null): string {
  return value == null ? '-' : `${value.toFixed(1)}%`
}

function money(value: number | string): string {
  const n = Number(value)
  return Number.isFinite(n) ? `₹${n.toLocaleString('en-IN', { maximumFractionDigits: 2 })}` : '-'
}

function pnlClass(value: number | string): string {
  const n = Number(value)
  if (n > 0) return 'text-success'
  if (n < 0) return 'text-danger'
  return 'text-text-muted'
}

onMounted(load)
</script>
