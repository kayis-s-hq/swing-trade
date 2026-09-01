import { ref } from 'vue'
import { defineStore } from 'pinia'
import { checkHealth } from '../api/health'
import { AppError, asAppError } from '../errors/appError'

export type BackendHealthStatus = 'checking' | 'healthy' | 'degraded' | 'unavailable'

function failureStatus(error: AppError): BackendHealthStatus {
  return error.kind === 'network' || error.kind === 'timeout' ? 'unavailable' : 'degraded'
}

export const useAppStateStore = defineStore('appState', () => {
  const healthStatus = ref<BackendHealthStatus>('checking')
  const healthError = ref<AppError | null>(null)
  const checking = ref(false)
  const lastHealthAttempt = ref<number | null>(null)
  const lastHealthSuccess = ref<number | null>(null)
  const bannerDismissed = ref(false)

  let healthInterval: ReturnType<typeof setInterval> | null = null
  let healthPromise: Promise<void> | null = null

  function checkHealthNow(): Promise<void> {
    if (healthPromise) return healthPromise

    checking.value = true
    lastHealthAttempt.value = Date.now()
    healthPromise = (async () => {
      try {
        const response = await checkHealth()
        healthStatus.value = response.status === 'UP' ? 'healthy' : 'degraded'
        healthError.value = null
        lastHealthSuccess.value = Date.now()
        if (healthStatus.value === 'healthy') bannerDismissed.value = false
      } catch (error: unknown) {
        const err = asAppError(error, {
          message: 'The backend health check failed.',
          retryable: true,
        })
        healthStatus.value = failureStatus(err)
        healthError.value = err
      } finally {
        checking.value = false
      }
    })().finally(() => {
      healthPromise = null
    })

    return healthPromise
  }

  async function startHealthPolling(intervalMs = 15_000): Promise<void> {
    if (!healthInterval) {
      healthInterval = setInterval(() => {
        void checkHealthNow()
      }, intervalMs)
    }
    await checkHealthNow()
  }

  function stopHealthPolling(): void {
    if (healthInterval) {
      clearInterval(healthInterval)
      healthInterval = null
    }
  }

  function dismissBackendBanner(): void {
    bannerDismissed.value = true
  }

  return {
    healthStatus,
    healthError,
    checking,
    lastHealthAttempt,
    lastHealthSuccess,
    bannerDismissed,
    checkHealthNow,
    startHealthPolling,
    stopHealthPolling,
    dismissBackendBanner,
  }
})
