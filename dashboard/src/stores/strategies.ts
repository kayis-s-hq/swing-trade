import { ref } from 'vue'
import { defineStore } from 'pinia'
import {
  listStrategyTypes,
  listCurrentStrategies,
  listStrategyVersions,
  createStrategyVariant,
  createStrategyVersion,
  cloneStrategyVariant,
  changeStrategyMode,
  validateStrategyParams,
  compareBacktests,
  type StrategyTypeInfo,
  type StrategyVariant,
  type ValidateParamsResult,
  type BacktestCompareRequest,
  type BacktestCompareResult,
} from '../api/strategies'
import { asAppError, type AppError } from '../errors/appError'

/** Maximum concurrently-active (SHADOW|CHAMPION) variants, per plan §4.4 (ux_strategy_config_current). */
export const MAX_ACTIVE_VARIANTS = 12

export const useStrategiesStore = defineStore('strategies', () => {
  const types = ref<StrategyTypeInfo[]>([])
  const variants = ref<StrategyVariant[]>([])
  const versionsByVariant = ref<Record<string, StrategyVariant[]>>({})
  const compareResult = ref<BacktestCompareResult | null>(null)

  const loading = ref(false)
  const error = ref<AppError | null>(null)
  const mutating = ref(false)
  const mutationError = ref<AppError | null>(null)
  const comparing = ref(false)
  const compareError = ref<AppError | null>(null)

  const activeCount = () =>
    variants.value.filter((v) => v.mode === 'SHADOW' || v.mode === 'CHAMPION').length

  async function loadAll(): Promise<void> {
    loading.value = true
    error.value = null
    try {
      const [typesRes, variantsRes] = await Promise.all([
        listStrategyTypes(),
        listCurrentStrategies(),
      ])
      types.value = typesRes
      variants.value = variantsRes
    } catch (err) {
      error.value = asAppError(err)
    } finally {
      loading.value = false
    }
  }

  async function loadVersions(variantId: string): Promise<StrategyVariant[]> {
    const versions = await listStrategyVersions(variantId)
    versionsByVariant.value = { ...versionsByVariant.value, [variantId]: versions }
    return versions
  }

  async function createVariant(request: {
    variantId: string
    strategyType: string
    params?: Record<string, unknown>
    overlays?: Record<string, unknown>
    paperCapital?: number
    notes?: string
  }): Promise<StrategyVariant | null> {
    mutating.value = true
    mutationError.value = null
    try {
      const created = await createStrategyVariant(request)
      variants.value = [...variants.value.filter((v) => v.variantId !== created.variantId), created]
      return created
    } catch (err) {
      mutationError.value = asAppError(err)
      return null
    } finally {
      mutating.value = false
    }
  }

  async function createVersion(
    variantId: string,
    request: {
      params?: Record<string, unknown>
      overlays?: Record<string, unknown>
      paperCapital?: number
      portfolioAction?: 'CONTINUE' | 'RESET'
      notes?: string
    }
  ): Promise<StrategyVariant | null> {
    mutating.value = true
    mutationError.value = null
    try {
      const created = await createStrategyVersion(variantId, request)
      variants.value = [...variants.value.filter((v) => v.variantId !== variantId), created]
      delete versionsByVariant.value[variantId]
      return created
    } catch (err) {
      mutationError.value = asAppError(err)
      return null
    } finally {
      mutating.value = false
    }
  }

  async function cloneVariant(
    variantId: string,
    newVariantId: string,
    notes?: string
  ): Promise<StrategyVariant | null> {
    mutating.value = true
    mutationError.value = null
    try {
      const cloned = await cloneStrategyVariant(variantId, newVariantId, notes)
      variants.value = [...variants.value.filter((v) => v.variantId !== cloned.variantId), cloned]
      return cloned
    } catch (err) {
      mutationError.value = asAppError(err)
      return null
    } finally {
      mutating.value = false
    }
  }

  async function setMode(
    variantId: string,
    mode: StrategyVariant['mode'],
    confirm = false
  ): Promise<StrategyVariant | null> {
    mutating.value = true
    mutationError.value = null
    try {
      const updated = await changeStrategyMode(variantId, mode, confirm)
      variants.value = variants.value.map((v) => (v.variantId === variantId ? updated : v))
      return updated
    } catch (err) {
      mutationError.value = asAppError(err)
      return null
    } finally {
      mutating.value = false
    }
  }

  async function validateParams(
    strategyType: string,
    params: Record<string, unknown>
  ): Promise<ValidateParamsResult | null> {
    try {
      return await validateStrategyParams(strategyType, params)
    } catch (err) {
      mutationError.value = asAppError(err)
      return null
    }
  }

  async function runCompare(request: BacktestCompareRequest): Promise<void> {
    comparing.value = true
    compareError.value = null
    try {
      compareResult.value = await compareBacktests(request)
    } catch (err) {
      compareError.value = asAppError(err)
      compareResult.value = null
    } finally {
      comparing.value = false
    }
  }

  return {
    types,
    variants,
    versionsByVariant,
    compareResult,
    loading,
    error,
    mutating,
    mutationError,
    comparing,
    compareError,
    activeCount,
    loadAll,
    loadVersions,
    createVariant,
    createVersion,
    cloneVariant,
    setMode,
    validateParams,
    runCompare,
  }
})
