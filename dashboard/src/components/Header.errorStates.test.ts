import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const apiMocks = vi.hoisted(() => ({
  getFyersStatus: vi.fn(),
  getUpcomingHolidays: vi.fn(),
}))

const settingsMocks = vi.hoisted(() => ({
  getSettings: vi.fn(() => ({ selectedBroker: 'yahoo' })),
  brokerLabels: { fyers: 'Fyers', upstox: 'Upstox', yahoo: 'Yahoo Finance', none: 'Paper Only' },
}))

vi.mock('../api/client', () => apiMocks)
vi.mock('../api/fyers', () => ({ getFyersStatus: apiMocks.getFyersStatus }))
vi.mock('../api/holidays', () => ({ getUpcomingHolidays: apiMocks.getUpcomingHolidays }))
vi.mock('../stores/settings', () => settingsMocks)

async function mountHeader() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/portfolio', name: 'portfolio', component: { template: '<div />' } }],
  })
  await router.push('/portfolio')
  await router.isReady()

  const Header = (await import('./Header.vue')).default
  return mount(Header, {
    props: { sidebarCollapsed: false },
    global: { plugins: [createPinia(), router] },
  })
}

describe('Header — compact auxiliary errors', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    localStorage.clear()
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-08-26T05:00:00.000Z'))
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('keeps the header operational while showing a compact holiday error', async () => {
    apiMocks.getUpcomingHolidays.mockRejectedValue(
      new TypeError('Failed to fetch https://internal.example/holidays')
    )

    const wrapper = await mountHeader()
    await flushPromises()

    expect(wrapper.get('header').text()).toContain('Portfolio')
    expect(wrapper.get('header').text()).toContain('Yahoo Finance')

    const auxiliaryError = wrapper.get('header [role="alert"]')
    expect(auxiliaryError.text()).toBe('Holiday status unavailable')
    expect(auxiliaryError.text()).not.toContain('internal.example')

    const retry = wrapper.find('button[aria-label="Retry holiday status"]')
    expect(retry.exists()).toBe(true)
    expect(wrapper.get('header').text()).not.toContain('Loading...')

    wrapper.unmount()
  })
})
