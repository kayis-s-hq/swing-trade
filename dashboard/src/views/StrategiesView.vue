<template>
  <div class="view-shell strategies-view p-4 sm:p-6 animate-fade-in">
    <div class="mb-6 flex flex-col gap-2">
      <p class="text-[11px] font-semibold uppercase tracking-[0.18em] text-brand">
        Multi-strategy framework
      </p>
      <h1 class="font-display text-2xl font-semibold tracking-tight text-text-primary">
        Strategies
      </h1>
      <p class="max-w-2xl text-sm text-text-muted">
        Configure, validate, and compare strategy variants. Promotion between champion and
        challenger always requires manual confirmation — nothing here auto-promotes.
      </p>
    </div>

    <ErrorBoundary :error="Boolean(store.error)">
      <template #error>
        <div v-if="store.error" class="flex flex-col items-center justify-center py-20">
          <p class="text-sm text-danger">{{ store.error.message }}</p>
          <button class="mt-2 rounded-md bg-brand px-3 py-1.5 text-xs font-medium text-white" @click="store.loadAll()">
            Retry
          </button>
        </div>
      </template>

      <div v-if="store.loading" class="flex items-center justify-center py-20">
        <LoadingSpinner message="Loading strategy configuration..." />
      </div>

      <template v-else>
        <!-- Variant list -->
        <section class="card-panel mb-6 p-5">
          <div class="mb-4 flex items-center justify-between">
            <h2 class="text-lg font-semibold text-text-primary">Variants</h2>
            <span class="text-xs font-medium text-text-muted">
              Active {{ store.activeCount() }}/{{ MAX_ACTIVE_VARIANTS }}
            </span>
          </div>

          <div
            v-if="store.variants.length === 0"
            class="flex flex-col items-center justify-center rounded-lg border border-dashed border-border-default py-12"
          >
            <p class="text-sm font-medium text-text-primary">No strategy variants yet</p>
            <p class="mt-1 text-xs text-text-muted">Create one to get started.</p>
          </div>

          <div v-else class="overflow-x-auto">
            <table class="w-full text-sm">
              <thead>
                <tr class="border-b border-border-subtle text-left text-xs uppercase tracking-wide text-text-muted">
                  <th class="py-2 pr-3">Variant</th>
                  <th class="py-2 pr-3">Type</th>
                  <th class="py-2 pr-3">Version</th>
                  <th class="py-2 pr-3">Mode</th>
                  <th class="py-2 pr-3">OOS Sharpe</th>
                  <th class="py-2 pr-3">Trades</th>
                  <th class="py-2 pr-3">Sample</th>
                  <th class="py-2 pr-3">Actions</th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="v in store.variants"
                  :key="v.variantId"
                  class="border-b border-border-subtle/60"
                >
                  <td class="py-2 pr-3 font-medium text-text-primary">{{ v.variantId }}</td>
                  <td class="py-2 pr-3 text-text-muted">{{ v.strategyType }}</td>
                  <td class="py-2 pr-3 text-text-muted">v{{ v.version }}</td>
                  <td class="py-2 pr-3">
                    <select
                      class="settings-input-sm rounded-md border border-border-subtle bg-bg-primary px-2 py-1 text-xs text-text-primary"
                      :value="v.mode"
                      :aria-label="`Mode for ${v.variantId}`"
                      @change="onModeChange(v, $event)"
                    >
                      <option value="OFF">OFF</option>
                      <option value="BACKTEST_ONLY">BACKTEST_ONLY</option>
                      <option value="SHADOW">SHADOW</option>
                      <option value="CHAMPION">CHAMPION</option>
                    </select>
                  </td>
                  <td class="py-2 pr-3 text-text-muted">
                    {{ sampleBadge(v) === 'insufficient' ? 'insufficient sample' : 'not yet available' }}
                  </td>
                  <td class="py-2 pr-3 text-text-muted">not yet available</td>
                  <td class="py-2 pr-3">
                    <span class="inline-flex rounded-full border border-border-subtle px-2 py-0.5 text-[11px] text-text-muted">
                      insufficient sample
                    </span>
                  </td>
                  <td class="py-2 pr-3">
                    <div class="flex gap-2">
                      <button
                        class="rounded-md border border-border-subtle px-2 py-1 text-xs text-text-muted hover:text-text-primary"
                        @click="openEditor(v)"
                      >
                        Edit
                      </button>
                      <button
                        class="rounded-md border border-border-subtle px-2 py-1 text-xs text-text-muted hover:text-text-primary"
                        @click="openClone(v)"
                      >
                        Clone
                      </button>
                      <label class="flex items-center gap-1 text-xs text-text-muted">
                        <input v-model="selectedForCompare" type="checkbox" :value="v.variantId" />
                        Compare
                      </label>
                    </div>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>

          <div class="mt-4">
            <button
              class="rounded-md bg-brand px-3 py-2 text-sm font-medium text-white hover:bg-brand/90"
              @click="openCreate"
            >
              New variant
            </button>
          </div>
        </section>

        <!-- Editor / create panel -->
        <section v-if="editorOpen" class="card-panel mb-6 p-5">
          <h2 class="mb-4 text-lg font-semibold text-text-primary">
            {{ editingVariant ? `Edit ${editingVariant.variantId}` : 'New variant' }}
          </h2>

          <div v-if="!editingVariant" class="mb-4 grid gap-3 sm:grid-cols-2">
            <label class="flex flex-col gap-1 text-xs text-text-muted">
              Variant ID
              <input
                v-model="newVariantId"
                class="rounded-md border border-border-subtle bg-bg-primary px-2 py-1.5 text-sm text-text-primary"
              />
            </label>
            <label class="flex flex-col gap-1 text-xs text-text-muted">
              Strategy type
              <select
                v-model="selectedType"
                class="rounded-md border border-border-subtle bg-bg-primary px-2 py-1.5 text-sm text-text-primary"
              >
                <option v-for="t in store.types" :key="t.type" :value="t.type">{{ t.type }}</option>
              </select>
            </label>
          </div>

          <div v-if="currentTypeSchema" class="space-y-4">
            <div v-for="(group, groupName) in groupedParams" :key="groupName">
              <h3 class="mb-2 text-xs font-semibold uppercase tracking-wide text-text-muted">
                {{ groupName }}
              </h3>
              <div class="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
                <label
                  v-for="def in group"
                  :key="def.name"
                  class="flex flex-col gap-1 rounded-lg border border-border-subtle p-3 text-xs text-text-muted"
                  :title="def.description ?? ''"
                >
                  <span class="font-medium text-text-primary">{{ def.name }}</span>
                  <span v-if="def.description" class="text-[11px]">{{ def.description }}</span>
                  <input
                    v-if="def.type === 'BOOL'"
                    type="checkbox"
                    :checked="Boolean(formParams[def.name])"
                    @change="onBoolChange(def.name, $event)"
                  />
                  <input
                    v-else
                    type="number"
                    :min="def.min ?? undefined"
                    :max="def.max ?? undefined"
                    :value="numberFieldValue(def.name)"
                    class="rounded-md border border-border-subtle bg-bg-primary px-2 py-1 text-sm text-text-primary"
                    @input="onNumberChange(def.name, $event)"
                  />
                  <span v-if="def.min !== null && def.max !== null" class="text-[11px]">
                    range {{ def.min }}–{{ def.max }}
                  </span>
                </label>
              </div>
            </div>

            <div>
              <h3 class="mb-2 text-xs font-semibold uppercase tracking-wide text-text-muted">
                Overlays
              </h3>
              <div class="flex gap-4">
                <label class="flex items-center gap-2 text-xs text-text-muted">
                  <input v-model="overlays.regimeGate" type="checkbox" />
                  Regime gate
                </label>
                <label class="flex items-center gap-2 text-xs text-text-muted">
                  <input v-model="overlays.sentimentGate" type="checkbox" />
                  Sentiment gate
                </label>
              </div>
            </div>

            <div v-if="editingVariant">
              <h3 class="mb-2 text-xs font-semibold uppercase tracking-wide text-text-muted">
                Diff vs current version (v{{ editingVariant.version }})
              </h3>
              <ul class="space-y-1 text-xs">
                <li
                  v-for="d in paramDiff"
                  :key="d.name"
                  class="flex items-center gap-2 text-text-muted"
                >
                  <span class="font-mono">{{ d.name }}</span>
                  <span>{{ d.before }} → {{ d.after }}</span>
                </li>
                <li v-if="paramDiff.length === 0" class="text-text-muted">No changes.</li>
              </ul>

              <label class="mt-3 flex flex-col gap-1 text-xs text-text-muted">
                On new version, existing paper positions:
                <select
                  v-model="portfolioAction"
                  class="w-56 rounded-md border border-border-subtle bg-bg-primary px-2 py-1.5 text-sm text-text-primary"
                >
                  <option value="RESET">RESET (recommended for entry changes)</option>
                  <option value="CONTINUE">CONTINUE</option>
                </select>
              </label>
            </div>
          </div>

          <div v-if="validationResult" class="mt-4 rounded-lg border p-3 text-xs" :class="validationResult.valid ? 'border-border-subtle text-text-muted' : 'border-danger/40 bg-danger-bg text-danger'">
            <p v-if="validationResult.valid">Params valid.</p>
            <ul v-else class="list-inside list-disc">
              <li v-for="e in validationResult.errors" :key="e">{{ e }}</li>
            </ul>
          </div>

          <div v-if="store.mutationError" class="mt-3 text-xs text-danger">
            {{ store.mutationError.message }}
          </div>

          <div class="mt-4 flex gap-2">
            <button
              class="rounded-md border border-border-subtle px-3 py-2 text-sm text-text-muted hover:text-text-primary"
              :disabled="store.mutating"
              @click="handleValidate"
            >
              Validate
            </button>
            <button
              class="rounded-md bg-brand px-3 py-2 text-sm font-medium text-white hover:bg-brand/90 disabled:opacity-50"
              :disabled="store.mutating || (validationResult ? !validationResult.valid : false)"
              @click="handleSave"
            >
              {{ editingVariant ? `Save as v${editingVariant.version + 1}` : 'Create variant' }}
            </button>
            <button
              class="rounded-md border border-border-subtle px-3 py-2 text-sm text-text-muted"
              @click="closeEditor"
            >
              Cancel
            </button>
          </div>
        </section>

        <!-- Clone panel -->
        <section v-if="cloneTarget" class="card-panel mb-6 p-5">
          <h2 class="mb-3 text-lg font-semibold text-text-primary">Clone {{ cloneTarget.variantId }}</h2>
          <label class="flex flex-col gap-1 text-xs text-text-muted">
            New variant ID
            <input
              v-model="cloneNewId"
              class="w-64 rounded-md border border-border-subtle bg-bg-primary px-2 py-1.5 text-sm text-text-primary"
            />
          </label>
          <div class="mt-3 flex gap-2">
            <button class="rounded-md bg-brand px-3 py-2 text-sm font-medium text-white" @click="handleClone">
              Clone
            </button>
            <button class="rounded-md border border-border-subtle px-3 py-2 text-sm text-text-muted" @click="cloneTarget = null">
              Cancel
            </button>
          </div>
        </section>

        <!-- Compare (shared with the Backtest page) -->
        <div class="mb-6">
          <div class="mb-2 flex justify-end">
            <router-link to="/backtest?tab=compare" class="text-xs font-medium text-brand hover:underline"
              >Open in Backtest →</router-link
            >
          </div>
          <BacktestComparePanel v-model:selected="selectedForCompare" />
        </div>

        <!-- Live shadow leaderboard -->
        <section class="card-panel p-5">
          <h2 class="mb-3 text-lg font-semibold text-text-primary">Live shadow leaderboard</h2>
          <p class="mb-4 text-xs text-text-muted">
            Per-portfolio equity/open positions and gate-blocked signal aggregates are not yet
            exposed by any backend endpoint. Promotion eligibility below reflects
            PromotionEligibilityChecker output; nothing here auto-promotes.
          </p>

          <div
            v-if="activeVariants.length === 0"
            class="rounded-lg border border-dashed border-border-default p-6 text-center"
          >
            <p class="text-sm font-medium text-text-primary">No active variants</p>
            <p class="mt-1 text-xs text-text-muted">
              Set a variant to SHADOW or CHAMPION mode to see it here.
            </p>
          </div>

          <div v-else class="flex flex-col gap-4">
            <div
              v-for="variant in activeVariants"
              :key="variant.variantId"
              class="rounded-lg border border-border-subtle p-4"
            >
              <div class="mb-2 flex items-center justify-between">
                <span class="text-sm font-semibold text-text-primary">{{ variant.variantId }}</span>
                <span
                  v-if="variant.mode === 'CHAMPION'"
                  class="rounded-full bg-bg-secondary px-2 py-0.5 text-[11px] font-medium uppercase tracking-wide text-text-muted"
                >
                  Current champion
                </span>
              </div>

              <template v-if="variant.mode === 'CHAMPION'">
                <p class="text-xs text-text-muted">
                  This is the current champion. There is no promotion checklist to compare it
                  against.
                </p>
              </template>

              <template v-else>
                <div v-if="store.promotionEligibilityLoading[variant.variantId]" class="py-4">
                  <LoadingSpinner message="Checking promotion eligibility..." />
                </div>

                <div
                  v-else-if="store.promotionEligibilityError[variant.variantId]"
                  class="flex flex-col items-start gap-2"
                >
                  <p class="text-xs text-danger">
                    {{ store.promotionEligibilityError[variant.variantId]!.message }}
                  </p>
                  <button
                    class="rounded-md border border-border-subtle px-3 py-1.5 text-xs text-text-muted"
                    @click="store.loadPromotionEligibility(variant.variantId)"
                  >
                    Retry
                  </button>
                </div>

                <div v-else-if="eligibilityFor(variant.variantId)" class="flex flex-col gap-3">
                  <div class="flex items-center gap-2">
                    <span
                      class="rounded-full bg-bg-secondary px-2 py-0.5 text-[11px] font-medium uppercase tracking-wide text-text-muted"
                    >
                      {{ statusLabel(eligibilityFor(variant.variantId)!.status) }}
                    </span>
                    <span class="text-xs text-text-muted">
                      vs champion {{ eligibilityFor(variant.variantId)!.championVariantId }}
                      · tenure {{ eligibilityFor(variant.variantId)!.tenureCalendarDays }}d
                    </span>
                  </div>

                  <p
                    v-if="eligibilityFor(variant.variantId)!.status === 'INSUFFICIENT_SAMPLE'"
                    class="text-xs text-text-muted"
                  >
                    Not enough data yet to judge promotion eligibility. This is not a rejection
                    — check back once more live trades have accumulated.
                  </p>
                  <p v-else class="text-xs text-text-muted">
                    Promotion always requires a manual confirmation step; this checklist is
                    informational only.
                  </p>

                  <ul class="flex flex-col gap-2">
                    <li
                      v-for="cond in conditionRows(eligibilityFor(variant.variantId)!)"
                      :key="cond.label"
                      class="flex flex-col gap-0.5 rounded-md bg-bg-secondary px-3 py-2"
                    >
                      <div class="flex items-center justify-between">
                        <span class="text-xs font-medium text-text-primary">{{ cond.label }}</span>
                        <span class="text-[11px] font-medium text-text-muted">
                          {{ cond.met ? 'Met' : 'Not met' }}
                        </span>
                      </div>
                      <span class="text-[11px] text-text-muted">
                        {{ cond.actualValue }} (threshold: {{ cond.threshold }})
                      </span>
                    </li>
                  </ul>
                </div>
              </template>
            </div>
          </div>
        </section>
      </template>
    </ErrorBoundary>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { useStrategiesStore, MAX_ACTIVE_VARIANTS } from '../stores/strategies'
import BacktestComparePanel from '../components/BacktestComparePanel.vue'
import type {
  StrategyVariant,
  ParamDef,
  ValidateParamsResult,
  PromotionEligibilityResult,
} from '../api/strategies'
import LoadingSpinner from '../components/LoadingSpinner.vue'
import ErrorBoundary from '../components/ErrorBoundary.vue'

const store = useStrategiesStore()

const editorOpen = ref(false)
const editingVariant = ref<StrategyVariant | null>(null)
const newVariantId = ref('')
const selectedType = ref('')
const formParams = reactive<Record<string, unknown>>({})
const overlays = reactive<{ regimeGate: boolean; sentimentGate: boolean }>({
  regimeGate: false,
  sentimentGate: false,
})
const portfolioAction = ref<'CONTINUE' | 'RESET'>('RESET')
const validationResult = ref<ValidateParamsResult | null>(null)

const cloneTarget = ref<StrategyVariant | null>(null)
const cloneNewId = ref('')

const selectedForCompare = ref<string[]>([])

const currentTypeSchema = computed(() =>
  store.types.find((t) => t.type === (editingVariant.value?.strategyType ?? selectedType.value))
)

const groupedParams = computed<Record<string, ParamDef[]>>(() => {
  const schema = currentTypeSchema.value
  if (!schema) return {}
  const groups: Record<string, ParamDef[]> = {}
  for (const def of schema.params) {
    const key = def.group ?? 'General'
    if (!groups[key]) groups[key] = []
    groups[key].push(def)
  }
  return groups
})

// Never rank variants below the minimum sample-size threshold (plan §7.4 / UI rule);
// no live-vs-backtest trade-count endpoint exists yet, so this always reports insufficient
// until that data is wired up.
function sampleBadge(_v: StrategyVariant): 'insufficient' | 'ok' {
  return 'insufficient'
}

function onModeChange(v: StrategyVariant, event: Event) {
  const mode = (event.target as HTMLSelectElement).value as StrategyVariant['mode']
  const confirm = mode === 'CHAMPION' ? window.confirm(`Promote ${v.variantId} to CHAMPION?`) : false
  if (mode === 'CHAMPION' && !confirm) return
  store.setMode(v.variantId, mode, confirm)
}

function resetForm() {
  Object.keys(formParams).forEach((k) => delete formParams[k])
  overlays.regimeGate = false
  overlays.sentimentGate = false
  validationResult.value = null
  portfolioAction.value = 'RESET'
}

function loadSchemaDefaults(typeName: string) {
  const schema = store.types.find((t) => t.type === typeName)
  if (!schema) return
  for (const def of schema.params) {
    formParams[def.name] = def.defaultValue
  }
}

function openCreate() {
  editingVariant.value = null
  newVariantId.value = ''
  selectedType.value = store.types[0]?.type ?? ''
  resetForm()
  if (selectedType.value) loadSchemaDefaults(selectedType.value)
  editorOpen.value = true
}

function openEditor(v: StrategyVariant) {
  editingVariant.value = v
  selectedType.value = v.strategyType
  resetForm()
  Object.assign(formParams, v.params)
  overlays.regimeGate = Boolean(v.overlays?.regimeGate)
  overlays.sentimentGate = Boolean(v.overlays?.sentimentGate)
  editorOpen.value = true
}

function closeEditor() {
  editorOpen.value = false
  editingVariant.value = null
}

function openClone(v: StrategyVariant) {
  cloneTarget.value = v
  cloneNewId.value = `${v.variantId}_COPY`
}

function onBoolChange(name: string, event: Event) {
  formParams[name] = (event.target as HTMLInputElement).checked
}

function numberFieldValue(name: string): number | string {
  const value = formParams[name]
  return typeof value === 'number' || typeof value === 'string' ? value : ''
}

function onNumberChange(name: string, event: Event) {
  const raw = (event.target as HTMLInputElement).value
  formParams[name] = raw === '' ? '' : Number(raw)
}

const paramDiff = computed(() => {
  if (!editingVariant.value) return []
  const before = editingVariant.value.params
  const diffs: Array<{ name: string; before: unknown; after: unknown }> = []
  for (const key of Object.keys(formParams)) {
    if (JSON.stringify(before[key]) !== JSON.stringify(formParams[key])) {
      diffs.push({ name: key, before: before[key], after: formParams[key] })
    }
  }
  return diffs
})

async function handleValidate() {
  validationResult.value = await store.validateParams(selectedType.value, { ...formParams })
}

async function handleSave() {
  const overlaysPayload = { regimeGate: overlays.regimeGate, sentimentGate: overlays.sentimentGate }
  if (editingVariant.value) {
    await store.createVersion(editingVariant.value.variantId, {
      params: { ...formParams },
      overlays: overlaysPayload,
      portfolioAction: portfolioAction.value,
    })
  } else {
    if (!newVariantId.value.trim()) return
    await store.createVariant({
      variantId: newVariantId.value.trim(),
      strategyType: selectedType.value,
      params: { ...formParams },
      overlays: overlaysPayload,
    })
  }
  if (!store.mutationError) closeEditor()
}

async function handleClone() {
  if (!cloneTarget.value || !cloneNewId.value.trim()) return
  await store.cloneVariant(cloneTarget.value.variantId, cloneNewId.value.trim())
  if (!store.mutationError) cloneTarget.value = null
}

const activeVariants = computed(() =>
  store.variants.filter((v) => v.mode === 'SHADOW' || v.mode === 'CHAMPION')
)

function eligibilityFor(variantId: string): PromotionEligibilityResult | undefined {
  return store.promotionEligibility[variantId]
}

function statusLabel(status: PromotionEligibilityResult['status']): string {
  switch (status) {
    case 'ELIGIBLE':
      return 'Eligible'
    case 'NOT_ELIGIBLE':
      return 'Not eligible'
    case 'INSUFFICIENT_SAMPLE':
      return 'Insufficient sample'
  }
}

function conditionRows(
  result: PromotionEligibilityResult
): Array<{ label: string; met: boolean; actualValue: string; threshold: string }> {
  return [
    { label: 'Tenure and sample size', ...result.tenureAndSampleSize },
    { label: 'Expectancy vs champion', ...result.expectancyVsChampion },
    { label: 'Drawdown guard', ...result.drawdownGuard },
    { label: 'Walk-forward and overfitting', ...result.walkForwardAndOverfitting },
  ]
}

// Fetches promotion eligibility for every SHADOW variant whenever the active variant set
// changes (e.g. after loadAll() resolves, or a mode change adds/removes a SHADOW variant).
// The champion itself is skipped — there is nothing to compare it against.
watch(
  activeVariants,
  (next) => {
    for (const variant of next) {
      if (
        variant.mode === 'SHADOW' &&
        !store.promotionEligibility[variant.variantId] &&
        !store.promotionEligibilityLoading[variant.variantId]
      ) {
        store.loadPromotionEligibility(variant.variantId)
      }
    }
  },
  { immediate: true }
)

onMounted(() => {
  store.loadAll()
})
</script>
