<template>
  <div class="view-shell gate-effectiveness-view p-4 sm:p-6 animate-fade-in">
    <div class="mx-auto max-w-6xl space-y-6">
      <header class="flex flex-col justify-between gap-4 sm:flex-row sm:items-end">
        <div>
          <p class="text-xs font-semibold uppercase tracking-[0.18em] text-brand">Analytics</p>
          <h1 class="mt-1 text-2xl font-semibold text-text-primary">Gate effectiveness</h1>
          <p class="mt-1 max-w-2xl text-sm text-text-secondary">
            Compare gate decisions with forward returns and realized paper P&amp;L. Rows without
            trustworthy provenance are excluded from realized P&amp;L.
          </p>
        </div>
        <div class="flex flex-wrap items-center gap-2">
          <select v-model="selectedGate" class="field-select" aria-label="Gate">
            <option value="SENTIMENT">Sentiment</option>
            <option value="LLM_ANALYSIS">LLM analysis</option>
            <option value="LIVE_ELIGIBILITY">Live eligibility</option>
          </select>
          <button class="button-secondary" :disabled="loading" @click="load">
            {{ loading ? 'Loading…' : 'Refresh' }}
          </button>
        </div>
      </header>

      <div
        v-if="error"
        class="rounded-xl border border-error/30 bg-error/10 p-4 text-sm text-error"
      >
        {{ error }}
      </div>
      <LoadingSpinner v-else-if="loading && !report" message="Loading gate outcomes…" />
      <div v-else-if="report" class="space-y-5">
        <section class="grid gap-3 sm:grid-cols-3">
          <div class="metric-card">
            <span>Audits</span><strong>{{ report.auditCount }}</strong>
          </div>
          <div class="metric-card">
            <span>Strategies</span><strong>{{ Object.keys(report.byStrategy).length }}</strong>
          </div>
          <div class="metric-card">
            <span>Realized P&amp;L</span><strong>{{ formatCurrency(realizedPnl) }}</strong>
          </div>
        </section>

        <section class="panel-card overflow-hidden">
          <div class="border-b border-border-subtle px-4 py-4">
            <h2 class="text-sm font-semibold text-text-primary">By decision</h2>
            <p class="mt-1 text-xs text-text-muted">
              Mean forward return uses the available 1/5/20-session observations.
            </p>
          </div>
          <div v-if="rows.length" class="overflow-x-auto">
            <table class="w-full min-w-[680px] text-left text-sm">
              <thead class="bg-bg-surface text-xs uppercase tracking-wide text-text-muted">
                <tr>
                  <th class="px-4 py-3">Decision</th>
                  <th class="px-4 py-3">Count</th>
                  <th class="px-4 py-3">1 session</th>
                  <th class="px-4 py-3">5 sessions</th>
                  <th class="px-4 py-3">20 sessions</th>
                  <th class="px-4 py-3">Realized P&amp;L</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-border-subtle/60">
                <tr v-for="row in rows" :key="row.name" class="text-text-secondary">
                  <td class="px-4 py-3 font-medium text-text-primary">{{ row.name }}</td>
                  <td class="px-4 py-3">{{ row.summary.count }}</td>
                  <td v-for="horizon in ['1', '5', '20']" :key="horizon" class="px-4 py-3">
                    {{ formatPercent(row.summary.meanForwardReturnPct[horizon]) }}
                  </td>
                  <td
                    class="px-4 py-3 font-medium"
                    :class="row.summary.realizedPnl >= 0 ? 'text-success' : 'text-error'"
                  >
                    {{ formatCurrency(row.summary.realizedPnl) }}
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <p v-else class="px-4 py-8 text-center text-sm text-text-muted">
            No audited decisions in this window.
          </p>
        </section>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import LoadingSpinner from '../components/LoadingSpinner.vue'
import { getGateEffectiveness, type GateEffectivenessReport } from '../api/signals'

const selectedGate = ref('SENTIMENT')
const loading = ref(false)
const error = ref<string | null>(null)
const report = ref<GateEffectivenessReport | null>(null)

const dates = () => {
  const to = new Date()
  const from = new Date(to)
  from.setDate(from.getDate() - 90)
  const iso = (date: Date) => date.toISOString().slice(0, 10)
  return { from: iso(from), to: iso(to) }
}

const rows = computed(() =>
  Object.entries(report.value?.verdicts ?? {}).map(([name, summary]) => ({ name, summary }))
)
const realizedPnl = computed(() =>
  rows.value.reduce((sum, row) => sum + row.summary.realizedPnl, 0)
)

const formatPercent = (value: number | undefined) => (value == null ? '—' : `${value.toFixed(2)}%`)
const formatCurrency = (value: number) => `Rs. ${value.toFixed(2)}`

async function load() {
  loading.value = true
  error.value = null
  try {
    report.value = await getGateEffectiveness({ ...dates(), gate: selectedGate.value })
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : 'Could not load gate outcomes'
  } finally {
    loading.value = false
  }
}

onMounted(() => void load())
</script>

<style scoped>
.field-select,
.button-secondary {
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-lg);
  background: var(--color-bg-surface);
  padding: 0.5rem 0.75rem;
  color: var(--color-text-primary);
  font-size: 0.875rem;
  line-height: 1.25rem;
}
.button-secondary {
  font-weight: 500;
  transition: border-color 150ms ease;
}
.button-secondary:hover {
  border-color: var(--color-brand);
}
.button-secondary:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}
.panel-card {
  overflow: hidden;
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-xl);
  background: var(--color-bg-surface);
  box-shadow: 0 1px 2px rgb(0 0 0 / 0.16);
}
.metric-card {
  display: flex;
  min-height: 6rem;
  flex-direction: column;
  justify-content: space-between;
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-xl);
  background: var(--color-bg-surface);
  padding: 1rem;
}
.metric-card span {
  color: var(--color-text-muted);
  font-size: 0.75rem;
  text-transform: uppercase;
  letter-spacing: 0.025em;
}
.metric-card strong {
  margin-top: 0.5rem;
  color: var(--color-text-primary);
  font-size: 1.25rem;
  font-weight: 600;
}
</style>
