import { afterEach, describe, expect, it, vi } from 'vitest'
import { mount, type VueWrapper } from '@vue/test-utils'
import { nextTick } from 'vue'
import Toast from './Toast.vue'

interface ToastProps {
  title?: string
  message: string
  type?: 'success' | 'error' | 'warning' | 'info'
  actionLabel?: string
}

const wrappers: VueWrapper[] = []

function mountToast(props: ToastProps = { message: 'Saved', type: 'success' }) {
  const wrapper = mount(Toast, {
    props,
    global: {
      stubs: {
        Teleport: true,
      },
    },
  })
  wrappers.push(wrapper)
  return wrapper
}

afterEach(() => {
  wrappers.splice(0).forEach((wrapper) => wrapper.unmount())
  vi.useRealTimers()
})

describe('Toast', () => {
  describe('presentational lifecycle', () => {
    it('renders until its owner removes it and does not change after time passes', async () => {
      vi.useFakeTimers()
      const wrapper = mountToast({ message: 'Portfolio refreshed', type: 'success' })
      await nextTick()
      const notification = wrapper.get('[role="status"]')
      const renderedNotification = notification.html()

      vi.advanceTimersByTime(60_000)
      await nextTick()

      expect(notification.html()).toBe(renderedNotification)
      expect(wrapper.emitted('dismiss')).toBeUndefined()
    })
  })

  describe('live-region semantics', () => {
    it('uses an assertive alert for errors', () => {
      const wrapper = mountToast({ message: 'Order failed', type: 'error' })
      const notification = wrapper.get('[role="alert"]')

      expect(notification.attributes('aria-live')).toBe('assertive')
      expect(notification.text()).toContain('Order failed')
    })

    it.each(['success', 'warning', 'info'] as const)(
      'uses a polite status for %s notifications',
      (type) => {
        const wrapper = mountToast({ message: `${type} message`, type })
        const notification = wrapper.get('[role="status"]')

        expect(notification.attributes('aria-live')).toBe('polite')
        expect(wrapper.find('[role="alert"]').exists()).toBe(false)
      }
    )
  })

  describe('notification controls', () => {
    it('provides a labeled dismiss control and delegates dismissal to its owner', async () => {
      const wrapper = mountToast({
        title: 'Couldn’t refresh signals',
        message: 'The last successful values are still shown.',
        type: 'warning',
      })

      const dismiss = wrapper.get('button[aria-label]')
      expect(dismiss.attributes('aria-label')).toMatch(/dismiss notification/i)

      await dismiss.trigger('click')

      expect(wrapper.emitted('dismiss')).toHaveLength(1)
    })

    it('renders an optional contextual action and emits it without executing store logic', async () => {
      const wrapper = mountToast({
        message: 'The request took too long.',
        type: 'error',
        actionLabel: 'Retry',
      })

      const action = wrapper.get('button:not([aria-label])')
      expect(action.text()).toBe('Retry')

      await action.trigger('click')

      expect(wrapper.emitted('action')).toHaveLength(1)
    })
  })
})
