<template>
  <section class="mb-6 card-panel p-5" data-testid="start-panel" aria-label="Run options">
    <div class="mb-3 flex flex-wrap items-center justify-between gap-2">
      <h2 class="text-sm font-semibold text-text-primary">Run options</h2>
      <p v-if="disabled" class="text-xs text-text-muted">{{ disabledReason }}</p>
    </div>

    <div class="grid gap-4 md:grid-cols-2">
      <fieldset :disabled="disabled" class="min-w-0">
        <legend class="mb-1 text-xs font-medium text-text-muted">
          Strategies (none selected = all)
        </legend>
        <p v-if="strategiesUnavailable" class="text-xs text-warning" role="status">
          Strategies unavailable — the run will use every configured strategy.
        </p>
        <p v-else-if="selectable.length === 0" class="text-xs text-text-muted">
          No active strategies configured.
        </p>
        <ul v-else class="flex flex-wrap gap-x-4 gap-y-1">
          <li v-for="s in selectable" :key="s.variantId">
            <label class="flex items-center gap-1.5 text-sm text-text-primary">
              <input
                type="checkbox"
                :value="s.variantId"
                :checked="model.variantIds.includes(s.variantId)"
                @change="toggleVariant(s.variantId)"
              />
              {{ s.variantId }}
              <span class="text-[10px] text-text-muted">{{ s.mode }}</span>
            </label>
          </li>
        </ul>
      </fieldset>

      <div class="flex flex-col gap-3">
        <label class="block text-xs font-medium text-text-muted">
          Symbol filter (comma separated, empty = whole watchlist)
          <input
            data-testid="symbol-filter"
            type="text"
            :value="model.symbolsText"
            :disabled="disabled"
            placeholder="TCS, INFY"
            class="mt-1 block w-full rounded-md border border-border-subtle bg-bg-primary px-2 py-1.5 text-sm text-text-primary disabled:opacity-50"
            @input="update({ symbolsText: ($event.target as HTMLInputElement).value })"
          />
        </label>
        <div class="flex flex-wrap gap-x-6 gap-y-1">
          <label class="flex items-center gap-1.5 text-sm text-text-primary">
            <input
              data-testid="quick-run"
              type="checkbox"
              :checked="model.skipLlm"
              :disabled="disabled"
              @change="update({ skipLlm: ($event.target as HTMLInputElement).checked })"
            />
            Quick run (no LLM)
          </label>
          <label class="flex items-center gap-1.5 text-sm text-text-primary">
            <input
              data-testid="dry-run"
              type="checkbox"
              :checked="model.dryRun"
              :disabled="disabled"
              @change="update({ dryRun: ($event.target as HTMLInputElement).checked })"
            />
            Dry run (no orders or persistence)
          </label>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { StrategyConfig } from '../api/types'
import type { RunOptions } from '../utils/runOptions'

const props = defineProps<{
  strategies: StrategyConfig[] | null
  disabled: boolean
  disabledReason: string
  modelValue: RunOptions
}>()
const emit = defineEmits<{ 'update:modelValue': [value: RunOptions] }>()

const model = computed(() => props.modelValue)
const strategiesUnavailable = computed(() => props.strategies === null)
const selectable = computed(() =>
  (props.strategies ?? []).filter((s) => s.current && s.mode !== 'OFF')
)

function update(patch: Partial<RunOptions>) {
  emit('update:modelValue', { ...model.value, ...patch })
}

function toggleVariant(variantId: string) {
  const has = model.value.variantIds.includes(variantId)
  update({
    variantIds: has
      ? model.value.variantIds.filter((v) => v !== variantId)
      : [...model.value.variantIds, variantId],
  })
}
</script>
