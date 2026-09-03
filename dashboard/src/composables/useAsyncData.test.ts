import { afterEach, describe, expect, expectTypeOf, it, vi } from 'vitest'
import { AppError } from '../errors/appError'
import { useAsyncData } from './useAsyncData'

function deferred<T>() {
  let resolve!: (value: T | PromiseLike<T>) => void
  let reject!: (reason?: unknown) => void
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise
    reject = rejectPromise
  })

  return { promise, resolve, reject }
}

function networkError(message = 'Backend unavailable') {
  return new AppError({
    kind: 'network',
    message,
    retryable: true,
  })
}

describe('useAsyncData', () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  describe('typed request state', () => {
    it('starts idle, distinguishes the initial load, and records the last successful update', async () => {
      vi.useFakeTimers()
      vi.setSystemTime(new Date('2026-08-26T08:00:00Z'))
      const pending = deferred<string>()
      const request = useAsyncData<string>()

      expectTypeOf(request.error.value).toEqualTypeOf<AppError | null>()
      expect(request.data.value).toBeNull()
      expect(request.isInitialLoading.value).toBe(false)
      expect(request.isRefreshing.value).toBe(false)
      expect(request.isStale.value).toBe(false)
      expect(request.lastSuccessfulAt.value).toBeNull()

      const execution = request.execute(() => pending.promise)
      expect(request.isInitialLoading.value).toBe(true)
      expect(request.isRefreshing.value).toBe(false)

      pending.resolve('first snapshot')
      await execution

      expect(request.data.value).toBe('first snapshot')
      expect(request.error.value).toBeNull()
      expect(request.isInitialLoading.value).toBe(false)
      expect(request.isRefreshing.value).toBe(false)
      expect(request.isStale.value).toBe(false)
      expect(request.lastSuccessfulAt.value).not.toBeNull()
    })

    it('keeps an initial failure typed and does not label missing data as stale', async () => {
      const failure = networkError()
      const request = useAsyncData<string>()

      await request.execute(() => Promise.reject(failure))

      expect(request.data.value).toBeNull()
      expect(request.error.value).toBe(failure)
      expect(request.error.value?.kind).toBe('network')
      expect(request.isInitialLoading.value).toBe(false)
      expect(request.isRefreshing.value).toBe(false)
      expect(request.isStale.value).toBe(false)
      expect(request.lastSuccessfulAt.value).toBeNull()
    })
  })

  describe('refresh and retry', () => {
    it('preserves usable data and its timestamp when a refresh fails', async () => {
      vi.useFakeTimers()
      vi.setSystemTime(new Date('2026-08-26T08:00:00Z'))
      const request = useAsyncData<string>()
      await request.execute(() => Promise.resolve('last good snapshot'))
      const lastSuccessfulAt = request.lastSuccessfulAt.value
      const refresh = deferred<string>()

      const execution = request.execute(() => refresh.promise)

      expect(request.data.value).toBe('last good snapshot')
      expect(request.isInitialLoading.value).toBe(false)
      expect(request.isRefreshing.value).toBe(true)
      expect(request.isStale.value).toBe(false)

      const failure = networkError('Refresh failed')
      refresh.reject(failure)
      await execution

      expect(request.data.value).toBe('last good snapshot')
      expect(request.error.value).toBe(failure)
      expect(request.isRefreshing.value).toBe(false)
      expect(request.isStale.value).toBe(true)
      expect(request.lastSuccessfulAt.value).toBe(lastSuccessfulAt)
    })

    it('retries the last operation and clears stale state only after success', async () => {
      vi.useFakeTimers()
      vi.setSystemTime(new Date('2026-08-26T08:00:00Z'))
      const loader = vi
        .fn<() => Promise<string>>()
        .mockResolvedValueOnce('old snapshot')
        .mockRejectedValueOnce(networkError('Refresh failed'))
        .mockResolvedValueOnce('recovered snapshot')
      const request = useAsyncData<string>()

      await request.execute(loader)
      const firstSuccessfulAt = request.lastSuccessfulAt.value
      await request.execute(loader)

      expect(request.data.value).toBe('old snapshot')
      expect(request.isStale.value).toBe(true)

      vi.setSystemTime(new Date('2026-08-26T08:05:00Z'))
      await request.retry()

      expect(loader).toHaveBeenCalledTimes(3)
      expect(request.data.value).toBe('recovered snapshot')
      expect(request.error.value).toBeNull()
      expect(request.isStale.value).toBe(false)
      expect(request.lastSuccessfulAt.value).not.toEqual(firstSuccessfulAt)
    })
  })

  describe('cancellation and request ordering', () => {
    it('aborts the active request without surfacing an error or making retained data stale', async () => {
      const request = useAsyncData<string>()
      await request.execute(() => Promise.resolve('usable snapshot'))
      let receivedSignal: AbortSignal | undefined

      const execution = request.execute(
        (signal) =>
          new Promise<string>((_resolve, reject) => {
            receivedSignal = signal
            signal.addEventListener(
              'abort',
              () => reject(new DOMException('Superseded', 'AbortError')),
              { once: true }
            )
          })
      )

      expect(request.isRefreshing.value).toBe(true)
      request.cancel()
      await execution

      expect(receivedSignal?.aborted).toBe(true)
      expect(request.data.value).toBe('usable snapshot')
      expect(request.error.value).toBeNull()
      expect(request.isRefreshing.value).toBe(false)
      expect(request.isStale.value).toBe(false)
    })

    it('allows only the latest request to commit data or completion state', async () => {
      const older = deferred<string>()
      const newer = deferred<string>()
      const request = useAsyncData<string>()

      const olderExecution = request.execute(() => older.promise)
      const newerExecution = request.execute(() => newer.promise)

      newer.resolve('newest snapshot')
      await newerExecution
      expect(request.data.value).toBe('newest snapshot')
      expect(request.isInitialLoading.value).toBe(false)

      older.resolve('obsolete snapshot')
      await olderExecution

      expect(request.data.value).toBe('newest snapshot')
      expect(request.error.value).toBeNull()
      expect(request.isInitialLoading.value).toBe(false)
      expect(request.isRefreshing.value).toBe(false)
      expect(request.isStale.value).toBe(false)
    })

    it('keeps a superseded request cancellation silent', async () => {
      const request = useAsyncData<string>()
      let supersededSignal: AbortSignal | undefined
      const firstExecution = request.execute(
        (signal) =>
          new Promise<string>((_resolve, reject) => {
            supersededSignal = signal
            signal.addEventListener(
              'abort',
              () => reject(new DOMException('Superseded', 'AbortError')),
              { once: true }
            )
          })
      )

      const latestExecution = request.execute(() => Promise.resolve('latest snapshot'))
      await Promise.all([firstExecution, latestExecution])

      expect(supersededSignal?.aborted).toBe(true)
      expect(request.data.value).toBe('latest snapshot')
      expect(request.error.value).toBeNull()
      expect(request.isStale.value).toBe(false)
    })
  })
})
