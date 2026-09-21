import { reactive, ref } from 'vue'
import { defineStore } from 'pinia'
import { useAsyncData } from '../composables/useAsyncData'
import {
  changeStrategyMode,
  fetchPromotionEligibility,
  getStrategies,
  getStrategyTypes,
  saveStrategyConfig,
} from '../api/strategies'
import { asAppError } from '../errors/appError'
import type {
  PromotionEligibilityResponse,
  StrategyConfig,
  StrategyConfigRequest,
  StrategyMode,
  StrategyTypeInfo,
} from '../api/types'

export type MutationResult = { ok: true } | { ok: false; message: string; outcomeUnknown: boolean }

export interface PromotionEligibilityState {
  loading: boolean
  error: string | null
  result: PromotionEligibilityResponse | null
}

export const useStrategiesStore = defineStore('strategies', () => {
  const request = useAsyncData<StrategyConfig[]>()
  const promotionEligibility = reactive<Record<string, PromotionEligibilityState>>({})

  const strategyTypes = ref<StrategyTypeInfo[] | null>(null)
  const strategyTypesError = ref('')
  let typesRequestId = 0

  async function loadStrategyTypes(): Promise<void> {
    const requestId = ++typesRequestId
    try {
      const types = await getStrategyTypes()
      if (requestId !== typesRequestId) return
      strategyTypes.value = types
      strategyTypesError.value = ''
    } catch (errorLike: unknown) {
      if (requestId !== typesRequestId) return
      strategyTypesError.value = asAppError(errorLike).message
    }
  }

  async function mutate(action: () => Promise<unknown>): Promise<MutationResult> {
    try {
      await action()
    } catch (errorLike: unknown) {
      const appError = asAppError(errorLike)
      return { ok: false, message: appError.message, outcomeUnknown: appError.outcomeUnknown }
    }
    await load()
    return { ok: true }
  }

  function changeMode(variantId: string, mode: StrategyMode): Promise<MutationResult> {
    return mutate(() => changeStrategyMode(variantId, mode))
  }

  /** Saves params as a new immutable version. */
  function saveConfig(request: StrategyConfigRequest): Promise<MutationResult> {
    return mutate(() => saveStrategyConfig(request))
  }

  function load(): Promise<void> {
    return request.execute((signal) => getStrategies(signal))
  }

  async function loadPromotionEligibility(variantId: string): Promise<void> {
    promotionEligibility[variantId] = {
      loading: true,
      error: null,
      result: promotionEligibility[variantId]?.result ?? null,
    }

    try {
      const result = await fetchPromotionEligibility(variantId)
      promotionEligibility[variantId] = { loading: false, error: null, result }
    } catch (errorLike: unknown) {
      const appError = asAppError(errorLike)
      promotionEligibility[variantId] = {
        loading: false,
        error: appError.message,
        result: null,
      }
    }
  }

  return {
    strategies: request.data,
    loading: request.loading,
    error: request.error,
    errorMessage: request.errorMessage,
    isInitialLoading: request.isInitialLoading,
    isRefreshing: request.isRefreshing,
    load,
    retry: request.retry,
    promotionEligibility,
    loadPromotionEligibility,
    strategyTypes,
    strategyTypesError,
    loadStrategyTypes,
    changeMode,
    saveConfig,
  }
})
