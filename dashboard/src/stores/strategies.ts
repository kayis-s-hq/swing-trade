import { reactive } from 'vue'
import { defineStore } from 'pinia'
import { useAsyncData } from '../composables/useAsyncData'
import { fetchPromotionEligibility, getStrategies } from '../api/strategies'
import { asAppError } from '../errors/appError'
import type { PromotionEligibilityResponse, StrategyConfig } from '../api/types'

export interface PromotionEligibilityState {
  loading: boolean
  error: string | null
  result: PromotionEligibilityResponse | null
}

export const useStrategiesStore = defineStore('strategies', () => {
  const request = useAsyncData<StrategyConfig[]>()
  const promotionEligibility = reactive<Record<string, PromotionEligibilityState>>({})

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
  }
})
