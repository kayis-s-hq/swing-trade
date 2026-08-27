import { afterEach, describe, expect, it, vi } from 'vitest'
import { shallowMount, type VueWrapper } from '@vue/test-utils'
import App from './App.vue'

vi.mock('./components/Sidebar.vue', () => ({
  default: { name: 'Sidebar', template: '<aside />' },
}))
vi.mock('./components/Header.vue', () => ({
  default: { name: 'Header', template: '<header />' },
}))
vi.mock('./components/BackendDownBanner.vue', () => ({
  default: { name: 'BackendDownBanner', template: '<div />' },
}))

const appStateMocks = vi.hoisted(() => ({
  startHealthPolling: vi.fn().mockResolvedValue(undefined),
  stopHealthPolling: vi.fn(),
}))

vi.mock('./stores/appState', () => ({
  getAppState: () => ({
    backendUp: true,
    backendError: '',
    lastHealthCheck: 0,
    healthStatus: 'healthy',
    connectionFailed: false,
  }),
  startHealthPolling: appStateMocks.startHealthPolling,
  stopHealthPolling: appStateMocks.stopHealthPolling,
}))

vi.mock('./stores/settings', () => ({
  getSettings: () => ({
    tradingConfig: { mode: 'paper' },
  }),
}))

let wrapper: VueWrapper | undefined

afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
  vi.clearAllMocks()
  vi.useRealTimers()
})

describe('App notification host', () => {
  it('mounts one global NotificationHost alongside route content', () => {
    vi.useFakeTimers()
    wrapper = shallowMount(App, {
      global: {
        stubs: {
          Sidebar: true,
          Header: true,
          BackendDownBanner: true,
          NotificationHost: true,
          RouterView: true,
        },
      },
    })

    expect(wrapper.findAll('notification-host-stub')).toHaveLength(1)
    expect(wrapper.find('router-view-stub').exists()).toBe(true)
  })
})
