import { mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import BackendDownBanner from './BackendDownBanner.vue'

const bannerMocks = vi.hoisted(() => ({
  state: {
    healthStatus: 'checking' as 'checking' | 'healthy' | 'degraded' | 'unavailable',
    checking: false,
    healthError: null as null | { kind: string; message: string },
    bannerDismissed: false,
  },
  checkHealthNow: vi.fn().mockResolvedValue(undefined),
  dismissBackendBanner: vi.fn(),
}))

vi.mock('@/stores/appState', () => ({
  useAppStateStore: () => ({
    ...bannerMocks.state,
    checkHealthNow: bannerMocks.checkHealthNow,
    dismissBackendBanner: bannerMocks.dismissBackendBanner,
  }),
}))

let wrapper: VueWrapper | undefined

function mountBanner(): VueWrapper {
  wrapper = mount(BackendDownBanner)
  return wrapper
}

describe('BackendDownBanner', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    Object.assign(bannerMocks.state, {
      healthStatus: 'checking',
      checking: false,
      healthError: null,
      bannerDismissed: false,
    })
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
  })

  it.each(['checking', 'healthy'] as const)('stays hidden while backend health is %s', (status) => {
    bannerMocks.state.healthStatus = status

    expect(mountBanner().find('[role="alert"]').exists()).toBe(false)
  })

  it('announces an unavailable backend without exposing the raw network exception', () => {
    bannerMocks.state.healthStatus = 'unavailable'
    bannerMocks.state.healthError = {
      kind: 'network',
      message: 'Failed to fetch http://private-service.internal/health',
    }

    const alert = mountBanner().get('[role="alert"]')

    expect(alert.text()).toContain('Backend unavailable')
    expect(alert.text()).toContain('Can’t reach the backend')
    expect(alert.text()).not.toContain('private-service.internal')
    expect(alert.get('button[aria-label="Retry backend health check"]').text()).toBe('Retry')
    expect(alert.find('button[aria-label="Dismiss backend status alert"]').exists()).toBe(true)
  })

  it('uses degraded wording when the backend responds but reports an unhealthy state', () => {
    bannerMocks.state.healthStatus = 'degraded'
    bannerMocks.state.healthError = {
      kind: 'server',
      message: 'raw upstream details',
    }

    const alert = mountBanner().get('[role="alert"]')

    expect(alert.text()).toContain('Backend degraded')
    expect(alert.text()).not.toContain('Backend unavailable')
    expect(alert.text()).not.toContain('raw upstream details')
  })

  it('runs an immediate health check when Retry is clicked', async () => {
    bannerMocks.state.healthStatus = 'unavailable'
    const currentWrapper = mountBanner()

    await currentWrapper.get('button[aria-label="Retry backend health check"]').trigger('click')

    expect(bannerMocks.checkHealthNow).toHaveBeenCalledTimes(1)
  })

  it('disables and labels Retry as busy while an immediate check is running', () => {
    bannerMocks.state.healthStatus = 'unavailable'
    bannerMocks.state.checking = true

    const retry = mountBanner().get('button[aria-label="Retry backend health check"]')

    expect(retry.attributes('disabled')).toBeDefined()
    expect(retry.attributes('aria-busy')).toBe('true')
    expect(retry.text()).toBe('Checking…')
  })

  it('hides a dismissed occurrence without changing the unavailable state', () => {
    bannerMocks.state.healthStatus = 'unavailable'
    bannerMocks.state.bannerDismissed = true

    expect(mountBanner().find('[role="alert"]').exists()).toBe(false)
    expect(bannerMocks.state.healthStatus).toBe('unavailable')
  })

  it('dismisses the banner without mutating the connectivity status', async () => {
    bannerMocks.state.healthStatus = 'unavailable'
    const currentWrapper = mountBanner()

    await currentWrapper.get('button[aria-label="Dismiss backend status alert"]').trigger('click')

    expect(bannerMocks.dismissBackendBanner).toHaveBeenCalledTimes(1)
    expect(bannerMocks.state.healthStatus).toBe('unavailable')
  })
})
