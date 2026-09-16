import { defineStore } from 'pinia'
import { useAsyncData } from '../composables/useAsyncData'
import { getStrategies } from '../api/strategies'
import type { StrategyConfig } from '../api/types'

export const useStrategiesStore = defineStore('strategies', () => {
  const request = useAsyncData<StrategyConfig[]>()

  function load(): Promise<void> {
    return request.execute((signal) => getStrategies(signal))
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
  }
})
