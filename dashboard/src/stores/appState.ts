import { reactive } from 'vue'
import { checkHealth } from '../api/health'
import { AppError, asAppError } from '../errors/appError'

export type BackendHealthStatus = 'checking' | 'healthy' | 'degraded' | 'unavailable'

interface AppState {
  healthStatus: BackendHealthStatus
  healthError: AppError | null
  checking: boolean
  lastHealthAttempt: number | null
  lastHealthSuccess: number | null
  bannerDismissed: boolean
}

const state = reactive<AppState>({
  healthStatus: 'checking',
  healthError: null,
  checking: false,
  lastHealthAttempt: null,
  lastHealthSuccess: null,
  bannerDismissed: false,
})

let healthInterval: ReturnType<typeof setInterval> | null = null
let healthPromise: Promise<void> | null = null

function failureStatus(error: AppError): BackendHealthStatus {
  return error.kind === 'network' || error.kind === 'timeout' ? 'unavailable' : 'degraded'
}

export function checkHealthNow(): Promise<void> {
  if (healthPromise) return healthPromise

  state.checking = true
  state.lastHealthAttempt = Date.now()
  healthPromise = (async () => {
    try {
      const response = await checkHealth()
      state.healthStatus = response.status === 'UP' ? 'healthy' : 'degraded'
      state.healthError = null
      state.lastHealthSuccess = Date.now()
      if (state.healthStatus === 'healthy') state.bannerDismissed = false
    } catch (error: unknown) {
      const healthError = asAppError(error, {
        message: 'The backend health check failed.',
        retryable: true,
      })
      state.healthStatus = failureStatus(healthError)
      state.healthError = healthError
    } finally {
      state.checking = false
    }
  })().finally(() => {
    healthPromise = null
  })

  return healthPromise
}

export async function startHealthPolling(intervalMs = 15_000): Promise<void> {
  if (!healthInterval) {
    healthInterval = setInterval(() => {
      void checkHealthNow()
    }, intervalMs)
  }
  await checkHealthNow()
}

export function stopHealthPolling(): void {
  if (healthInterval) {
    clearInterval(healthInterval)
    healthInterval = null
  }
}

export function dismissBackendBanner(): void {
  state.bannerDismissed = true
}

export function getAppState(): AppState {
  return state
}
