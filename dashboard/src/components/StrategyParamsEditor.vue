<template>
  <form
    class="mt-4 border-t border-border-subtle pt-4"
    data-testid="params-form"
    novalidate
    @submit.prevent="submit"
  >
    <p class="mb-3 text-xs text-text-muted">
      Saving creates a new immutable version (v{{ strategy.version + 1 }}) with the current mode.
    </p>

    <p v-if="specs.length === 0" class="text-sm text-text-muted">
      No parameter schema is available for {{ strategy.strategyType }}, so its parameters cannot be
      edited here.
    </p>

    <template v-else>
      <fieldset v-for="group in groups" :key="group.name" class="mb-3">
        <legend class="mb-1 text-xs font-medium uppercase tracking-[0.1em] text-text-muted">
          {{ group.name }}
        </legend>
        <div class="grid gap-3 sm:grid-cols-2">
          <div v-for="spec in group.specs" :key="spec.name">
            <label class="block text-xs font-medium text-text-primary" :for="fieldId(spec.name)">
              {{ spec.name }}
            </label>
            <input
              v-if="isBool(spec)"
              :id="fieldId(spec.name)"
              v-model="values[spec.name]"
              type="checkbox"
              :data-testid="`param-${spec.name}`"
              class="mt-1"
            />
            <input
              v-else
              :id="fieldId(spec.name)"
              v-model="values[spec.name]"
              :type="isNumeric(spec) ? 'number' : 'text'"
              :min="spec.min ?? undefined"
              :max="spec.max ?? undefined"
              :step="spec.type === 'INT' ? 1 : 'any'"
              :data-testid="`param-${spec.name}`"
              :aria-invalid="fieldError(spec.name) ? 'true' : undefined"
              :aria-describedby="fieldError(spec.name) ? `${fieldId(spec.name)}-error` : undefined"
              class="mt-1 block w-full rounded-md border bg-bg-primary px-2 py-1.5 text-sm text-text-primary"
              :class="fieldError(spec.name) ? 'border-danger' : 'border-border-subtle'"
            />
            <p v-if="spec.description" class="mt-0.5 text-[11px] text-text-muted">
              {{ spec.description }}
              <span v-if="spec.min != null || spec.max != null">
                ({{ spec.min ?? '−∞' }}–{{ spec.max ?? '∞' }})
              </span>
            </p>
            <p
              v-if="fieldError(spec.name)"
              :id="`${fieldId(spec.name)}-error`"
              class="mt-0.5 text-xs text-danger"
              :data-testid="`param-error-${spec.name}`"
            >
              {{ fieldError(spec.name) }}
            </p>
          </div>
        </div>
      </fieldset>
    </template>

    <p v-if="error" class="mb-3 text-sm text-danger" role="alert" data-testid="params-error">
      {{ error }}
    </p>

    <div class="flex gap-2">
      <button
        type="submit"
        :disabled="saving || specs.length === 0"
        class="rounded-lg bg-brand px-3 py-1.5 text-sm font-semibold text-brand-text hover:bg-brand-hover disabled:opacity-50"
      >
        {{ saving ? 'Saving...' : 'Save new version' }}
      </button>
      <button
        type="button"
        :disabled="saving"
        class="rounded-lg border border-border-subtle px-3 py-1.5 text-sm text-text-muted hover:text-text-primary"
        @click="emit('cancel')"
      >
        Cancel
      </button>
    </div>
  </form>
</template>

<script setup lang="ts">
import { computed, reactive } from 'vue'
import type { StrategyConfig, StrategyParamSpec, StrategyTypeInfo } from '../api/types'

const props = defineProps<{
  strategy: StrategyConfig
  typeInfo: StrategyTypeInfo | null
  saving: boolean
  error: string
}>()
const emit = defineEmits<{ save: [params: Record<string, unknown>]; cancel: [] }>()

const specs = computed<StrategyParamSpec[]>(() => props.typeInfo?.paramSchema.params ?? [])

const values = reactive<Record<string, unknown>>({})
for (const spec of specs.value) {
  values[spec.name] = props.strategy.params[spec.name] ?? spec.defaultValue ?? ''
}

const groups = computed(() => {
  const byGroup = new Map<string, StrategyParamSpec[]>()
  for (const spec of specs.value) {
    const name = spec.group || 'general'
    byGroup.set(name, [...(byGroup.get(name) ?? []), spec])
  }
  return [...byGroup.entries()].map(([name, list]) => ({ name, specs: list }))
})

const isBool = (spec: StrategyParamSpec) => spec.type === 'BOOL'
const isNumeric = (spec: StrategyParamSpec) => spec.type === 'INT' || spec.type === 'DECIMAL'
const fieldId = (name: string) => `param-${props.strategy.variantId}-${name}`

/** Server errors are free text; attach the message to fields it names. */
function fieldError(name: string): string {
  if (!props.error) return ''
  return new RegExp(`\\b${name.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}\\b`).test(props.error)
    ? props.error
    : ''
}

function submit() {
  const params: Record<string, unknown> = { ...props.strategy.params }
  for (const spec of specs.value) {
    const raw = values[spec.name]
    if (raw === '' || raw === null || raw === undefined) {
      delete params[spec.name]
    } else if (isNumeric(spec)) {
      params[spec.name] = Number(raw)
    } else {
      params[spec.name] = raw
    }
  }
  emit('save', params)
}
</script>
