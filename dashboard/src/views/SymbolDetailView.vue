<template>
  <div class="view-shell p-4 sm:p-6 animate-fade-in">
    <div class="mb-6">
      <router-link to="/watchlist" class="text-xs text-brand hover:underline"
        >← Watchlist</router-link
      >
      <h1 class="mt-1 font-display text-2xl font-semibold text-text-primary">{{ symbol }}</h1>
      <p class="mt-1 text-sm text-text-muted">Every strategy's view of this symbol</p>
    </div>

    <LoadingSpinner v-if="loading" message="Loading strategy matrix..." />
    <ErrorMessage v-else-if="error" title="Couldn’t load this symbol" :message="error" />

    <template v-else-if="matrix">
      <div class="card-panel overflow-x-auto p-5">
        <h2 class="mb-3 text-sm font-semibold text-text-primary">Strategy matrix</h2>
        <table class="w-full min-w-[720px] text-sm">
          <thead>
            <tr class="border-b border-border-subtle text-left text-xs text-text-muted">
              <th class="pb-2 pr-3 font-medium">Variant</th>
              <th class="pb-2 pr-3 font-medium">Mode</th>
              <th class="pb-2 pr-3 font-medium">Latest signal</th>
              <th class="pb-2 pr-3 font-medium">Open shadow position</th>
              <th class="pb-2 pr-3 font-medium">Closed trades</th>
              <th class="pb-2 pr-3 font-medium">Win rate</th>
              <th class="pb-2 font-medium">Realized P&amp;L</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="row in matrix.strategies"
              :key="row.variantId"
              class="border-b border-border-subtle/50"
            >
              <td class="py-2 pr-3 font-medium text-text-primary">{{ row.variantId }}</td>
              <td class="py-2 pr-3 text-text-muted">{{ row.mode }}</td>
              <td class="py-2 pr-3">
                <span v-if="row.latestSignal" :class="signalClass(row.latestSignal)">
                  {{ row.latestSignal }} {{ confidence(row.latestConfidence) }}
                  <span class="text-text-muted">({{ row.latestSignalDate }})</span>
                </span>
                <span v-else class="text-text-muted">-</span>
              </td>
              <td class="py-2 pr-3 text-text-muted">
                <template v-if="row.openPosition">
                  {{ row.openPosition.quantity }} @
                  {{ Number(row.openPosition.entryPrice).toFixed(2) }} ({{
                    row.openPosition.entryDate
                  }})
                </template>
                <template v-else>-</template>
              </td>
              <td class="py-2 pr-3 text-text-muted">{{ row.closedTrades }}</td>
              <td class="py-2 pr-3 text-text-muted">
                {{ row.winRate == null ? '-' : Math.round(row.winRate * 100) + '%' }}
              </td>
              <td
                class="py-2"
                :class="
                  Number(row.realizedPnl) > 0
                    ? 'text-success'
                    : Number(row.realizedPnl) < 0
                      ? 'text-danger'
                      : 'text-text-muted'
                "
              >
                {{ formatSignedCurrency(Number(row.realizedPnl)) }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="mt-6 card-panel p-5">
        <h2 class="mb-3 text-sm font-semibold text-text-primary">Tournament history</h2>
        <p v-if="matrix.tournaments.length === 0" class="text-sm text-text-muted">
          No variant has won a tournament on this symbol yet.
        </p>
        <ul v-else class="space-y-1 text-sm text-text-muted">
          <li v-for="t in matrix.tournaments" :key="t.selectionDate">
            {{ t.selectionDate }} ·
            <span class="font-medium text-success">{{ t.winnerVariantId }}</span> ({{
              confidence(t.winnerConfidence)
            }}) · {{ t.status }}
          </li>
        </ul>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { getStrategyMatrix, type StrategyMatrix } from '../api/portfolios'
import LoadingSpinner from '../components/LoadingSpinner.vue'
import ErrorMessage from '../components/ErrorMessage.vue'
import { formatSignedCurrency } from '../utils/format'

const route = useRoute()
const symbol = computed(() => String(route.params.symbol ?? '').toUpperCase())
const matrix = ref<StrategyMatrix | null>(null)
const loading = ref(false)
const error = ref('')

function confidence(value: number | string | null): string {
  const n = Number(value)
  return value == null || !Number.isFinite(n) ? '' : `${Math.round(n * 100)}%`
}

function signalClass(signal: string): string {
  if (signal === 'BUY') return 'font-medium text-success'
  if (signal === 'SELL') return 'font-medium text-danger'
  return 'text-text-muted'
}

watch(
  symbol,
  async (value) => {
    if (!value) return
    loading.value = true
    error.value = ''
    try {
      matrix.value = await getStrategyMatrix(value)
    } catch {
      matrix.value = null
      error.value = 'The strategy matrix could not be loaded.'
    } finally {
      loading.value = false
    }
  },
  { immediate: true }
)
</script>
