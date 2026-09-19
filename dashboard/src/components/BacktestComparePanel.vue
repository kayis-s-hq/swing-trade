<template>
  <section class="card-panel p-5" aria-label="Portfolio compare">
    <h2 class="mb-1 text-lg font-semibold text-text-primary">Compare</h2>
    <p class="mb-3 text-xs text-text-muted">
      Shared-capital portfolio backtest across variants (T+1 fills, costs, deflated Sharpe).
    </p>

    <div v-if="showPicker" class="mb-3 flex flex-wrap gap-x-4 gap-y-2" aria-label="Variants to compare">
      <label
        v-for="variant in pickableVariants"
        :key="variant.variantId"
        class="flex items-center gap-2 text-xs text-text-muted"
      >
        <input v-model="selected" type="checkbox" :value="variant.variantId" />
        {{ variant.variantId }}
        <span class="text-[11px] uppercase text-text-muted/70">{{ variant.mode }}</span>
      </label>
      <p v-if="pickableVariants.length === 0" class="text-xs text-text-muted">
        No strategy variants configured.
      </p>
    </div>
    <p v-else class="mb-3 text-xs text-text-muted">
      Selected: {{ selected.length === 0 ? 'none' : selected.join(', ') }}
    </p>

    <div class="mb-3 flex flex-wrap gap-3">
      <label class="flex flex-col gap-1 text-xs text-text-muted">
        Start
        <input
          v-model="compareStart"
          type="date"
          class="rounded-md border border-border-subtle bg-bg-primary px-2 py-1 text-sm text-text-primary"
        />
      </label>
      <label class="flex flex-col gap-1 text-xs text-text-muted">
        End
        <input
          v-model="compareEnd"
          type="date"
          class="rounded-md border border-border-subtle bg-bg-primary px-2 py-1 text-sm text-text-primary"
        />
      </label>
      <label class="flex items-center gap-2 self-end text-xs text-text-muted">
        <input v-model="costsOn" type="checkbox" /> Costs on
      </label>
      <label class="flex items-center gap-2 self-end text-xs text-text-muted">
        <input v-model="walkForwardOn" type="checkbox" /> Walk-forward
      </label>
    </div>
    <button
      class="rounded-md bg-brand px-3 py-2 text-sm font-medium text-white disabled:opacity-50"
      :disabled="selected.length === 0 || store.comparing"
      @click="handleCompare"
    >
      {{ store.comparing ? 'Running...' : 'Run comparison' }}
    </button>

    <div v-if="store.compareError" class="mt-3 text-xs text-danger">
      {{ store.compareError.message }}
    </div>

    <div v-if="store.compareResult" class="mt-5 overflow-x-auto">
      <table class="w-full text-sm">
        <thead>
          <tr
            class="border-b border-border-subtle text-left text-xs uppercase tracking-wide text-text-muted"
          >
            <th class="py-2 pr-3">Variant</th>
            <th class="py-2 pr-3">Fold</th>
            <th class="py-2 pr-3">Window</th>
            <th class="py-2 pr-3">Sharpe</th>
            <th class="py-2 pr-3">DSR</th>
            <th class="py-2 pr-3">Trades</th>
            <th class="py-2 pr-3">Win rate (95% CI)</th>
          </tr>
        </thead>
        <tbody>
          <template v-for="vr in store.compareResult.variants" :key="vr.variantId">
            <tr
              v-for="fold in vr.folds"
              :key="`${vr.variantId}-${fold.fold}`"
              class="border-b border-border-subtle/60 text-text-muted"
            >
              <td class="py-2 pr-3 font-medium text-text-primary">
                {{ vr.variantId }} (v{{ vr.version }})
              </td>
              <td class="py-2 pr-3">{{ fold.fold === 0 ? 'plain' : fold.fold }}</td>
              <td class="py-2 pr-3">{{ fold.windowStart }} – {{ fold.windowEnd }}</td>
              <td class="py-2 pr-3">{{ metricValue(fold.metrics, 'sharpe', 'sharpeRatio') }}</td>
              <td class="py-2 pr-3">
                {{ vr.deflatedSharpeRatio.toFixed(3) }}
                <span v-if="vr.deflatedSharpeRatio > 0.1" class="ml-1 text-[11px]"
                  >(likely noise)</span
                >
              </td>
              <td class="py-2 pr-3">{{ metricValue(fold.metrics, 'tradeCount', 'totalTrades') }}</td>
              <td class="py-2 pr-3">{{ winRateWithCi(fold.metrics) }}</td>
            </tr>
          </template>
        </tbody>
      </table>
      <p class="mt-3 text-xs text-text-muted">
        Overlaid equity curves and drawdown chart are not yet implemented for this view (deferred —
        see report). Signal overlap matrix is deferred pending a SignalOverlapAnalyzer endpoint,
        which does not currently exist.
      </p>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useStrategiesStore } from '../stores/strategies'

const props = withDefaults(defineProps<{ showPicker?: boolean }>(), { showPicker: false })
const selected = defineModel<string[]>('selected', { default: () => [] })

const store = useStrategiesStore()

const isoDate = (d: Date) => d.toISOString().slice(0, 10)
const today = new Date()
const compareStart = ref(isoDate(new Date(today.getFullYear() - 3, today.getMonth(), today.getDate())))
const compareEnd = ref(isoDate(today))
const costsOn = ref(true)
const walkForwardOn = ref(false)

const pickableVariants = computed(() => store.variants.filter((v) => v.mode !== 'OFF'))

onMounted(() => {
  if (props.showPicker && store.variants.length === 0) void store.loadAll()
})

async function handleCompare() {
  await store.runCompare({
    variants: selected.value.map((id) => ({ id })),
    start: compareStart.value,
    end: compareEnd.value,
    costsOn: costsOn.value,
    walkForward: walkForwardOn.value ? { trainM: 24, testM: 6, stepM: 6 } : null,
  })
}

// The backend emits e.g. sharpeRatio / totalTrades / winRatePct; older shapes used sharpe /
// tradeCount / winRate, so each metric accepts either spelling.
function pick(metrics: Record<string, unknown>, ...keys: string[]): unknown {
  for (const key of keys) {
    if (metrics[key] !== undefined && metrics[key] !== null) return metrics[key]
  }
  return undefined
}

function metricValue(metrics: Record<string, unknown>, ...keys: string[]): string {
  const value = pick(metrics, ...keys)
  if (value === undefined) return 'not yet available'
  return typeof value === 'number' ? value.toFixed(3) : String(value)
}

function winRateWithCi(metrics: Record<string, unknown>): string {
  const winRate = pick(metrics, 'winRate', 'winRatePct')
  const ciLow = pick(metrics, 'winRateCiLow', 'winRateCiLowPct')
  const ciHigh = pick(metrics, 'winRateCiHigh', 'winRateCiHighPct')
  if (winRate === undefined) return 'not yet available'
  if (ciLow === undefined || ciHigh === undefined) return 'not yet available (no CI provided)'
  return `${Number(winRate).toFixed(3)} [${Number(ciLow).toFixed(3)}, ${Number(ciHigh).toFixed(3)}]`
}
</script>
