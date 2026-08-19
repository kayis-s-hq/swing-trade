import { reactive } from 'vue'
import { checkHealth } from '../api/health'

const state = reactive({
  backendUp: true,
  backendError: '',
  lastHealthCheck: 0,
  healthStatus: '' as '' | 'healthy' | 'degraded' | 'down',
  connectionFailed: false,
})

let healthInterval: ReturnType<typeof setInterval> | null = null
let healthPromise: Promise<void> | null = null

export async function startHealthPolling(intervalMs = 15000): Promise<void> {
  if (healthInterval) return
  healthInterval = setInterval(doHealthCheck, intervalMs)
  await doHealthCheck()
}

export function stopHealthPolling(): void {
  if (healthInterval) {
    clearInterval(healthInterval)
    healthInterval = null
  }
}

async function doHealthCheck(): Promise<void> {
  if (healthPromise) return
  healthPromise = (async () => {
    const res = await checkHealth()
    if (res.success && res.data) {
      state.backendUp = true
      state.backendError = ''
      state.healthStatus = res.data.status === 'UP' ? 'healthy' : 'degraded'
      state.connectionFailed = false
      state.lastHealthCheck = Date.now()
    } else {
      state.backendUp = false
      state.backendError = res.error || 'Backend server is unreachable'
      // "Unable to connect" = down; HTTP errors = degraded (server is running but broken)
      state.connectionFailed = state.backendError.includes('Unable to connect')
      state.healthStatus = state.connectionFailed ? 'down' : 'degraded'
    }
  })().finally(() => {
    healthPromise = null
  })
  await healthPromise
}

export function getAppState() {
  return state
}
