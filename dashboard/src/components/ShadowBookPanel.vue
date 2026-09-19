<template>
  <div class="card-panel p-5" aria-label="Shadow book">
    <div class="mb-3 flex items-center justify-between">
      <div>
        <h3 class="text-sm font-semibold text-text-primary">{{ portfolioId }}</h3>
        <p class="mt-0.5 text-xs text-text-muted">
          Simulated paper book. Never touches real orders or the Real positions above.
        </p>
      </div>
      <span
        class="rounded-full bg-warning-subtle px-2.5 py-1 text-[11px] font-semibold text-warning"
        >SHADOW</span
      >
    </div>

    <LoadingSpinner v-if="loading" message="Loading shadow positions..." />
    <p v-else-if="failed" class="text-sm text-danger">Could not load this book's positions.</p>
    <p v-else-if="positions.length === 0" class="text-sm text-text-muted">No positions yet.</p>

    <div v-else class="overflow-x-auto">
      <p class="mb-2 text-xs text-text-muted">
        {{ openCount }} open · {{ closedCount }} closed · realized
        {{ formatSignedCurrency(realized) }}
      </p>
      <table class="w-full min-w-[640px] text-sm">
        <thead>
          <tr class="border-b border-border-subtle text-left text-xs text-text-muted">
            <th class="pb-2 pr-3 font-medium">Symbol</th>
            <th class="pb-2 pr-3 font-medium">Status</th>
            <th class="pb-2 pr-3 font-medium">Entry</th>
            <th class="pb-2 pr-3 font-medium">Qty</th>
            <th class="pb-2 pr-3 font-medium">Stop / Target</th>
            <th class="pb-2 pr-3 font-medium">Exit</th>
            <th class="pb-2 font-medium">P&amp;L</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="(p, i) in positions"
            :key="`${p.symbol}-${p.entryDate}-${i}`"
            class="border-b border-border-subtle/50"
          >
            <td class="py-2 pr-3 font-medium text-text-primary">
              <router-link :to="`/symbols/${p.symbol}`" class="hover:underline">{{
                p.symbol
              }}</router-link>
            </td>
            <td class="py-2 pr-3 text-text-muted">{{ p.status }}</td>
            <td class="py-2 pr-3 text-text-muted">{{ p.entryDate }} @ {{ num(p.entryPrice) }}</td>
            <td class="py-2 pr-3 text-text-muted">{{ p.quantity }}</td>
            <td class="py-2 pr-3 text-text-muted">{{ num(p.stopLoss) }} / {{ num(p.target) }}</td>
            <td class="py-2 pr-3 text-text-muted">
              <template v-if="p.exitDate"
                >{{ p.exitDate }} @ {{ num(p.exitPrice) }} ({{ p.exitReason }})</template
              >
              <template v-else>-</template>
            </td>
            <td
              class="py-2"
              :class="
                Number(p.pnl) > 0
                  ? 'text-success'
                  : Number(p.pnl) < 0
                    ? 'text-danger'
                    : 'text-text-muted'
              "
            >
              {{ p.pnl == null ? '-' : formatSignedCurrency(Number(p.pnl)) }}
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { listPortfolioPositions, type ShadowPositionView } from '../api/portfolios'
import LoadingSpinner from './LoadingSpinner.vue'
import { formatSignedCurrency } from '../utils/format'

const props = defineProps<{ portfolioId: string }>()
const positions = ref<ShadowPositionView[]>([])
const loading = ref(false)
const failed = ref(false)

const openCount = computed(() => positions.value.filter((p) => p.status === 'OPEN').length)
const closedCount = computed(() => positions.value.filter((p) => p.status === 'CLOSED').length)
const realized = computed(() =>
  positions.value.reduce((sum, p) => sum + (p.status === 'CLOSED' ? Number(p.pnl ?? 0) : 0), 0)
)

function num(value: number | string | null): string {
  const n = Number(value)
  return value == null || !Number.isFinite(n) ? '-' : n.toFixed(2)
}

watch(
  () => props.portfolioId,
  async (id) => {
    loading.value = true
    failed.value = false
    try {
      positions.value = await listPortfolioPositions(id)
    } catch {
      positions.value = []
      failed.value = true
    } finally {
      loading.value = false
    }
  },
  { immediate: true }
)
</script>
