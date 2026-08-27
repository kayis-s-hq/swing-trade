import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia, type Pinia } from 'pinia'
import NotificationHost from './NotificationHost.vue'
import { useNotificationsStore } from '../stores/notifications'

let pinia: Pinia
let wrapper: VueWrapper | undefined

function mountHost() {
  wrapper = mount(NotificationHost, {
    global: {
      plugins: [pinia],
      stubs: {
        Teleport: true,
      },
    },
  })
  return wrapper
}

beforeEach(() => {
  pinia = createPinia()
  setActivePinia(pinia)
})

afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
  vi.useRealTimers()
})

describe('NotificationHost', () => {
  it('renders the queued notifications in one labeled region with their semantic roles', () => {
    const store = useNotificationsStore()
    store.notify({
      type: 'error',
      title: 'Couldn’t refresh positions',
      message: 'The last successful values are still shown.',
      duration: null,
    })
    store.notify({
      type: 'success',
      message: 'Settings saved',
      duration: null,
    })

    const host = mountHost()
    const region = host.get('[aria-label="Notifications"]')

    expect(region.text()).toContain('Couldn’t refresh positions')
    expect(region.text()).toContain('The last successful values are still shown.')
    expect(region.text()).toContain('Settings saved')
    expect(region.findAll('[role="alert"]')).toHaveLength(1)
    expect(region.findAll('[role="status"]')).toHaveLength(1)
  })

  it('removes only the notification whose dismiss control was activated', async () => {
    const store = useNotificationsStore()
    store.notify({ type: 'info', message: 'First notification', duration: null })
    store.notify({ type: 'warning', message: 'Second notification', duration: null })
    const host = mountHost()

    await host.findAll('button[aria-label]')[0]!.trigger('click')

    expect(host.text()).not.toContain('First notification')
    expect(host.text()).toContain('Second notification')
    expect(store.notifications).toHaveLength(1)
  })

  it('delegates contextual actions to the store and removes the completed notification', async () => {
    const retry = vi.fn().mockResolvedValue(undefined)
    const store = useNotificationsStore()
    store.notify({
      type: 'error',
      message: 'Refresh failed',
      action: { label: 'Retry now', handler: retry },
      duration: null,
    })
    const host = mountHost()

    const action = host.findAll('button').find((button) => button.text() === 'Retry now')
    expect(action).toBeDefined()
    await action!.trigger('click')
    await flushPromises()

    expect(retry).toHaveBeenCalledTimes(1)
    expect(store.notifications).toHaveLength(0)
    expect(host.text()).not.toContain('Refresh failed')
  })

  it('does not render a toast for a failure designated for inline presentation', () => {
    const store = useNotificationsStore()
    store.notify({
      type: 'error',
      title: 'Couldn’t load positions',
      message: 'Can’t reach the backend. Check the service, then retry.',
      presentation: 'inline',
      dedupeKey: 'positions-load',
      duration: null,
    })

    const host = mountHost()

    expect(host.text()).not.toContain('Couldn’t load positions')
    expect(host.find('[role="alert"]').exists()).toBe(false)
  })
})
