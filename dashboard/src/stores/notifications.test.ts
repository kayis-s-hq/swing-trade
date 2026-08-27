import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useNotificationsStore } from './notifications'

describe('notifications store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.clearAllTimers()
    vi.useRealTimers()
  })

  describe('queue management', () => {
    it('queues notifications in insertion order and dismisses a selected item', () => {
      const store = useNotificationsStore()
      const firstId = store.notify({
        type: 'success',
        message: 'Position created',
        duration: null,
      })
      const secondId = store.notify({
        type: 'warning',
        message: 'Refresh failed; retained values may be stale',
        duration: null,
      })

      expect(firstId).toEqual(expect.any(String))
      expect(secondId).toEqual(expect.any(String))
      expect(store.notifications.map(({ message }) => message)).toEqual([
        'Position created',
        'Refresh failed; retained values may be stale',
      ])

      store.dismiss(firstId!)

      expect(store.notifications.map(({ id }) => id)).toEqual([secondId])
    })
  })

  describe('timer ownership', () => {
    it('auto-dismisses a notification after its configured duration', () => {
      const store = useNotificationsStore()
      store.notify({ type: 'info', message: 'Data pull started', duration: 1_000 })

      vi.advanceTimersByTime(999)
      expect(store.notifications).toHaveLength(1)

      vi.advanceTimersByTime(1)
      expect(store.notifications).toHaveLength(0)
    })

    it('keeps notifications with a null duration until explicit dismissal', () => {
      const store = useNotificationsStore()
      store.notify({
        type: 'error',
        message: 'Order status could not be confirmed',
        duration: null,
      })

      vi.advanceTimersByTime(60_000)

      expect(store.notifications).toHaveLength(1)
    })
  })

  describe('deduplication', () => {
    it('coalesces repeated notifications with the same dedupe key and restarts expiry', () => {
      const store = useNotificationsStore()
      const firstId = store.notify({
        type: 'warning',
        message: 'Backend health check failed',
        dedupeKey: 'backend-health-failed',
        duration: 1_000,
      })
      vi.advanceTimersByTime(750)

      const duplicateId = store.notify({
        type: 'warning',
        message: 'Backend health check failed again',
        dedupeKey: 'backend-health-failed',
        duration: 1_000,
      })

      expect(duplicateId).toBe(firstId)
      expect(store.notifications).toHaveLength(1)
      expect(store.notifications[0]?.message).toBe('Backend health check failed again')

      vi.advanceTimersByTime(750)
      expect(store.notifications).toHaveLength(1)
      vi.advanceTimersByTime(250)
      expect(store.notifications).toHaveLength(0)
    })

    it('coalesces an identical type and message when no explicit key is supplied', () => {
      const store = useNotificationsStore()

      store.notify({ type: 'info', message: 'Refreshing positions', duration: null })
      store.notify({ type: 'info', message: 'Refreshing positions', duration: null })

      expect(store.notifications).toHaveLength(1)
    })
  })

  describe('contextual actions', () => {
    it('runs the selected action once and dismisses its notification', async () => {
      const action = vi.fn().mockResolvedValue(undefined)
      const store = useNotificationsStore()
      const id = store.notify({
        type: 'error',
        message: 'Signals could not be refreshed',
        duration: null,
        action: { label: 'Retry', handler: action },
      })

      await store.runAction(id!)

      expect(action).toHaveBeenCalledTimes(1)
      expect(store.notifications).toHaveLength(0)
    })
  })

  describe('inline error ownership', () => {
    it('does not enqueue a toast when the failure is already presented inline', () => {
      const store = useNotificationsStore()

      const id = store.notify({
        type: 'error',
        title: 'Couldn’t load positions',
        message: 'Can’t reach the backend. Check the service, then retry.',
        presentation: 'inline',
        dedupeKey: 'positions-load',
        duration: null,
      })

      expect(id).toBeNull()
      expect(store.notifications).toHaveLength(0)
    })
  })
})
