import { shallowMount, type VueWrapper } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import Sidebar from './Sidebar.vue'

vi.mock('../stores/settings', () => ({
  getSettings: () => ({ selectedBroker: 'paper' }),
  brokerLabels: { paper: 'Paper Trading' },
}))

let wrapper: VueWrapper | undefined

function mountSidebar(healthStatus?: 'checking' | 'healthy' | 'degraded' | 'unavailable') {
  wrapper = shallowMount(Sidebar, {
    props: { collapsed: false, ...(healthStatus ? { healthStatus } : {}) },
    global: {
      mocks: { $route: { path: '/' } },
      stubs: { RouterLink: true },
    },
  })
  return wrapper
}

describe('Sidebar backend health status', () => {
  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
    vi.clearAllMocks()
  })

  it('defaults to Checking instead of claiming a healthy backend before the first result', () => {
    const status = mountSidebar().get('[role="status"]')

    expect(status.text()).toContain('Checking')
    expect(status.text()).not.toContain('Healthy')
  })

  it.each([
    ['checking', 'Checking'],
    ['healthy', 'Healthy'],
    ['degraded', 'Degraded'],
    ['unavailable', 'Unavailable'],
  ] as const)(
    'renders %s with the accurate visible and accessible label',
    (healthStatus, label) => {
      const status = mountSidebar(healthStatus).get('[role="status"]')

      expect(status.attributes('aria-live')).toBe('polite')
      expect(status.attributes('aria-label')).toBe(`Backend health: ${label}`)
      expect(status.text()).toContain(label)
      expect(status.text()).not.toMatch(/Engine Active|Backend Down/)
    }
  )
})
