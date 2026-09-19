<template>
  <section class="card-panel overflow-hidden" aria-label="Strategy board">
    <div class="flex items-center justify-between border-b border-border-subtle px-5 py-4">
      <div>
        <h2 class="text-sm font-semibold text-text-primary">Strategy board</h2>
        <p class="mt-0.5 text-xs text-text-muted">
          Variants and BUY signals on {{ latestDate || 'the latest signal date' }}
        </p>
      </div>
      <router-link to="/strategies" class="text-xs font-medium text-brand hover:underline"
        >Open strategies →</router-link
      >
    </div>
    <div v-if="rows.length" class="divide-y divide-border-subtle/50">
      <div
        v-for="row in rows"
        :key="row.variantId"
        class="flex items-center justify-between gap-3 px-5 py-3"
        data-testid="strategy-board-row"
      >
        <div class="min-w-0">
          <p class="truncate text-sm font-medium text-text-primary">{{ row.variantId }}</p>
          <p class="text-xs text-text-muted">{{ row.strategyType }}</p>
        </div>
        <div class="flex items-center gap-3">
          <span class="text-xs text-text-muted">{{ row.buyCount }} BUY</span>
          <StatusBadge :status="badgeStatus(row.mode)" :label="row.mode" />
        </div>
      </div>
    </div>
    <p v-else class="px-5 py-6 text-sm text-text-muted">No strategy variants configured.</p>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { listCurrentStrategies, type StrategyVariant } from '../api/strategies'
import type { Signal } from '../api/types'
import StatusBadge from './StatusBadge.vue'

const props = defineProps<{ signals: Signal[] }>()

const variants = ref<StrategyVariant[]>([])

onMounted(async () => {
  // Supplementary card: a failed load shows the empty state, never breaks the dashboard.
  try {
    variants.value = await listCurrentStrategies()
  } catch {
    variants.value = []
  }
})

const latestDate = computed(() =>
  props.signals.reduce((max, s) => (s.timestamp > max ? s.timestamp : max), '').slice(0, 10)
)

const rows = computed(() =>
  variants.value
    .filter((v) => v.mode !== 'OFF')
    .map((v) => ({
      variantId: v.variantId,
      strategyType: v.strategyType,
      mode: v.mode,
      buyCount: props.signals.filter(
        (s) =>
          s.strategy === v.variantId &&
          s.direction === 'BUY' &&
          s.timestamp.slice(0, 10) === latestDate.value
      ).length,
    }))
)

function badgeStatus(mode: StrategyVariant['mode']): string {
  return mode === 'CHAMPION' || mode === 'SHADOW' ? 'COMPLETED' : 'PENDING'
}
</script>
