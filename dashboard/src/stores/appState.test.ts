import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

const healthMocks = vi.hoisted(() => ({
  checkHealth: vi.fn(),
}))

vi.mock('../api/health', () => ({ checkHealth: healthMocks.checkHealth }))

interface Deferred<T> {
  promise: Promise<T>
  resolve: (value: T) => void
  reject: (reason?: unknown) => void
}

function deferred<T>(): Deferred<T> {
  let resolve!: (value: T) => void
  let reject!: (reason?: unknown) => void
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise
    reject = rejectPromise
  })
  return { promise, resolve, reject }
}

async function loadStore() {
  vi.resetModules()
  const [{ useAppStateStore }, { AppError }] = await Promise.all([
    import('./appState'),
    import('../errors/appError'),
  ])
  setActivePinia(createPinia())
  return { store: useAppStateStore(), AppError }
}

describe('appState store', () => {
  beforeEach(() => {
    vi.useRealTimers()
    healthMocks.checkHealth.mockReset()
  })

  it('starts in an explicit checking state rather than claiming the backend is healthy', async () => {
    const { store } = await loadStore()

    expect(store).toMatchObject({
      healthStatus: 'checking',
      checking: false,
      healthError: null,
      lastHealthAttempt: null,
      lastHealthSuccess: null,
      bannerDismissed: false,
    })
  })

  it('stores a typed health error and marks network failures unavailable', async () => {
    const { store, AppError } = await loadStore()
    const error = new AppError({
      kind: 'network',
      message: 'Failed to fetch http://private-host/health',
      retryable: true,
    })
    healthMocks.checkHealth.mockRejectedValue(error)

    await expect(store.checkHealthNow()).resolves.toBeUndefined()

    expect(store).toMatchObject({
      healthStatus: 'unavailable',
      checking: false,
      healthError: error,
    })
    expect(store.healthError).toBeInstanceOf(AppError)
  })

  it.each(['network', 'timeout'] as const)(
    'classifies a %s health failure as unavailable',
    async (kind) => {
      const { store, AppError } = await loadStore()
      healthMocks.checkHealth.mockRejectedValue(
        new AppError({ kind, message: `${kind} details`, retryable: kind === 'network' })
      )

      await store.checkHealthNow()

      expect(store.healthStatus).toBe('unavailable')
    }
  )

  it('marks a reachable unhealthy response as degraded rather than unavailable', async () => {
    const { store } = await loadStore()
    healthMocks.checkHealth.mockResolvedValue({ status: 'DOWN', components: {} })

    await store.checkHealthNow()

    expect(store).toMatchObject({
      healthStatus: 'degraded',
      checking: false,
      healthError: null,
    })
  })

  it('marks a typed server response failure as degraded because the backend was reachable', async () => {
    const { store, AppError } = await loadStore()
    const error = new AppError({
      kind: 'server',
      message: 'internal health response body',
      status: 503,
      retryable: true,
    })
    healthMocks.checkHealth.mockRejectedValue(error)

    await store.checkHealthNow()

    expect(store).toMatchObject({ healthStatus: 'degraded', healthError: error })
  })

  it('tracks every attempt while preserving the last successful health timestamp', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-08-26T09:00:00.000Z'))
    const { store, AppError } = await loadStore()
    healthMocks.checkHealth.mockResolvedValueOnce({ status: 'UP', components: {} })

    await store.checkHealthNow()

    const successfulAt = Date.parse('2026-08-26T09:00:00.000Z')
    expect(store).toMatchObject({
      healthStatus: 'healthy',
      lastHealthAttempt: successfulAt,
      lastHealthSuccess: successfulAt,
    })

    vi.setSystemTime(new Date('2026-08-26T09:05:00.000Z'))
    healthMocks.checkHealth.mockRejectedValueOnce(
      new AppError({ kind: 'timeout', message: 'timed out', retryable: false })
    )
    await store.checkHealthNow()

    expect(store).toMatchObject({
      healthStatus: 'unavailable',
      lastHealthAttempt: Date.parse('2026-08-26T09:05:00.000Z'),
      lastHealthSuccess: successfulAt,
    })
  })

  it('deduplicates concurrent immediate checks and exposes the checking flag', async () => {
    const pending = deferred<{ status: string; components: Record<string, unknown> }>()
    healthMocks.checkHealth.mockReturnValue(pending.promise)
    const { store } = await loadStore()

    const first = store.checkHealthNow()
    const second = store.checkHealthNow()

    expect(healthMocks.checkHealth).toHaveBeenCalledTimes(1)
    expect(store.checking).toBe(true)

    pending.resolve({ status: 'UP', components: {} })
    await Promise.all([first, second])

    expect(store).toMatchObject({ healthStatus: 'healthy', checking: false })
  })

  it('exports an immediate check that still runs while the polling interval is active', async () => {
    vi.useFakeTimers()
    healthMocks.checkHealth.mockResolvedValue({ status: 'UP', components: {} })
    const { store } = await loadStore()

    await store.startHealthPolling(60_000)
    expect(healthMocks.checkHealth).toHaveBeenCalledTimes(1)

    await store.checkHealthNow()
    expect(healthMocks.checkHealth).toHaveBeenCalledTimes(2)

    store.stopHealthPolling()
  })

  it('dismisses only the current outage banner without changing connectivity', async () => {
    const { store, AppError } = await loadStore()
    const outage = new AppError({
      kind: 'network',
      message: 'offline',
      retryable: true,
    })
    healthMocks.checkHealth.mockRejectedValue(outage)
    await store.checkHealthNow()

    store.dismissBackendBanner()

    expect(store).toMatchObject({
      healthStatus: 'unavailable',
      healthError: outage,
      bannerDismissed: true,
    })

    await store.checkHealthNow()
    expect(store.bannerDismissed).toBe(true)

    healthMocks.checkHealth.mockResolvedValueOnce({ status: 'UP', components: {} })
    await store.checkHealthNow()
    expect(store).toMatchObject({
      healthStatus: 'healthy',
      healthError: null,
      bannerDismissed: false,
    })

    healthMocks.checkHealth.mockRejectedValueOnce(outage)
    await store.checkHealthNow()
    expect(store).toMatchObject({
      healthStatus: 'unavailable',
      bannerDismissed: false,
    })
  })
})
