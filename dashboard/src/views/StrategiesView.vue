<template>
  <div class="view-shell p-4 sm:p-6 animate-fade-in">
    <div class="mb-6 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
      <div>
        <p class="text-xs font-medium uppercase tracking-[0.16em] text-text-muted">
          Strategy desk / configuration
        </p>
        <h1
          class="mt-2 font-display text-2xl font-semibold tracking-tight text-text-primary sm:text-3xl"
        >
          Strategies
        </h1>
        <p class="mt-1 max-w-xl text-sm leading-6 text-text-muted">
          Review the current version and operating mode for every configured strategy variant.
        </p>
      </div>
      <button
        type="button"
        class="rounded-lg border border-border-subtle bg-bg-surface px-3 py-2 text-sm font-medium text-text-muted transition-colors hover:border-border-default hover:text-text-primary disabled:opacity-50"
        :disabled="loading"
        @click="store.load"
      >
        {{ loading ? 'Refreshing...' : 'Refresh' }}
      </button>
    </div>

    <div v-if="loading && !strategies" class="flex items-center justify-center py-20" role="status">
      <span class="text-sm text-text-muted">Loading strategies...</span>
    </div>

    <section v-else-if="error && !strategies" class="card-panel p-6 text-center" role="alert">
      <h2 class="text-base font-semibold text-text-primary">Couldn’t load strategies</h2>
      <p class="mt-1 text-sm text-text-muted">
        {{ errorMessage || 'Strategy configuration is unavailable.' }}
      </p>
      <button
        type="button"
        class="mt-4 rounded-lg bg-brand px-4 py-2 text-sm font-semibold text-brand-text hover:bg-brand-hover"
        @click="store.retry"
      >
        Retry
      </button>
    </section>

    <section v-else-if="strategies?.length === 0" class="card-panel p-10 text-center">
      <h2 class="text-base font-semibold text-text-primary">No strategies configured</h2>
      <p class="mt-1 text-sm text-text-muted">
        Add a strategy variant through the configuration API to see it here.
      </p>
    </section>

    <template v-else>
      <p
        v-if="error"
        class="mb-4 rounded-lg border border-warning/30 bg-warning-bg px-4 py-3 text-sm text-warning"
        role="status"
      >
        Showing the last successful configuration. Refresh failed: {{ errorMessage }}
      </p>
      <div class="grid gap-4 lg:grid-cols-2">
        <article
          v-for="strategy in strategies"
          :key="`${strategy.variantId}-${strategy.version}`"
          class="card-panel p-5 transition-colors hover:border-border-default"
        >
          <div class="flex items-start justify-between gap-4">
            <div class="min-w-0">
              <p class="truncate text-xs font-medium uppercase tracking-[0.14em] text-text-muted">
                {{ strategy.strategyType }} · v{{ strategy.version }}
              </p>
              <h2 class="mt-2 truncate text-lg font-semibold text-text-primary">
                {{ strategy.variantId }}
              </h2>
            </div>
            <span
              class="shrink-0 rounded-full px-2.5 py-1 text-xs font-semibold"
              :class="modeClass(strategy.mode)"
              :aria-label="`Mode: ${strategy.mode}`"
            >
              {{ modeLabel(strategy.mode) }}
            </span>
          </div>
          <dl class="mt-5 grid grid-cols-2 gap-4 border-t border-border-subtle pt-4">
            <div>
              <dt class="text-xs text-text-muted">Paper capital</dt>
              <dd class="mt-1 text-sm font-semibold text-text-primary">
                {{ formatCapital(strategy.paperCapital) }}
              </dd>
            </div>
            <div>
              <dt class="text-xs text-text-muted">Status</dt>
              <dd class="mt-1 text-sm font-semibold text-text-primary">
                {{ strategy.current ? 'Current' : 'Historical' }}
              </dd>
            </div>
          </dl>
          <p v-if="strategy.notes" class="mt-4 text-sm leading-6 text-text-muted">
            {{ strategy.notes }}
          </p>
        </article>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useStrategiesStore } from '../stores/strategies'
import type { StrategyMode } from '../api/types'

const store = useStrategiesStore()
const strategies = computed(() => store.strategies)
const loading = computed(() => store.loading)
const error = computed(() => store.error)
const errorMessage = computed(() => store.errorMessage)

const modeLabel = (mode: StrategyMode): string => mode.replace('_', ' ')

const modeClass = (mode: StrategyMode): string =>
  ({
    CHAMPION: 'bg-success-bg text-success',
    SHADOW: 'bg-brand-subtle text-brand',
    BACKTEST_ONLY: 'bg-warning-bg text-warning',
    OFF: 'bg-bg-hover text-text-muted',
  })[mode]

const formatCapital = (value: number): string =>
  new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency: 'INR',
    maximumFractionDigits: 0,
  }).format(value)

onMounted(() => {
  if (!store.strategies) void store.load()
})
</script>
