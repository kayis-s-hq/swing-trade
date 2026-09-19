import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { WatchlistEntry } from '../api/types'
import { AppError, type AppErrorKind } from '../errors/appError'
import WatchlistView from './WatchlistView.vue'

const apiMocks = vi.hoisted(() => ({
  getWatchlist: vi.fn(),
  getSignals: vi.fn(),
  addToWatchlist: vi.fn(),
  removeFromWatchlist: vi.fn(),
  toggleWatchlistActive: vi.fn(),
}))

vi.mock('../api/client', () => apiMocks)
vi.mock('../api/watchlist', () => apiMocks)
vi.mock('../api/signals', () => apiMocks)
vi.mock('../api/strategies', () => ({
  getStrategies: vi.fn().mockResolvedValue([
    { variantId: 'breakout-v1' },
    { variantId: 'pullback-v1' },
    { variantId: 'squeeze-v1' },
  ]),
}))

const entry: WatchlistEntry = {
  id: 1,
  symbol: 'RELIANCE',
  name: 'Reliance Industries',
  exchange: 'NSE',
  isActive: true,
  candleCount: 120,
}

function appError(message: string, kind: AppErrorKind = 'validation'): AppError {
  return new AppError({
    message,
    kind,
    retryable: false,
  })
}

function mountView(): VueWrapper {
  return mount(WatchlistView, {
    attachTo: document.body,
    global: {
      stubs: {
        ErrorBoundary: {
          props: ['error'],
          template: '<div><slot /><slot v-if="error" name="error" /></div>',
        },
        LoadingSpinner: true,
      },
    },
  })
}

function button(wrapper: VueWrapper, label: string) {
  const match = wrapper.findAll('button').find((candidate) => candidate.text().trim() === label)
  if (!match) throw new Error(`Button not found: ${label}`)
  return match
}

beforeEach(() => {
  vi.clearAllMocks()
  vi.stubGlobal('alert', vi.fn())
  vi.stubGlobal(
    'confirm',
    vi.fn(() => true)
  )
  apiMocks.getWatchlist.mockResolvedValue([entry])
  apiMocks.getSignals.mockResolvedValue([])
  apiMocks.addToWatchlist.mockResolvedValue(entry)
  apiMocks.removeFromWatchlist.mockResolvedValue('Removed RELIANCE')
  apiMocks.toggleWatchlistActive.mockResolvedValue({ ...entry, isActive: false })
})

afterEach(() => {
  vi.unstubAllGlobals()
  document.body.replaceChildren()
})

describe('WatchlistView — mutation failures', () => {
  it('keeps the add form values and focuses an inline error without native alert', async () => {
    apiMocks.addToWatchlist.mockRejectedValue(appError('Symbol is already monitored', 'conflict'))
    const wrapper = mountView()
    await flushPromises()

    await button(wrapper, 'Add Stock').trigger('click')
    const symbolInput = wrapper.get('input[placeholder="e.g. RELIANCE"]')
    const nameInput = wrapper.get('input[placeholder="e.g. Reliance Industries"]')
    await symbolInput.setValue('TCS')
    await nameInput.setValue('Tata Consultancy Services')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(apiMocks.addToWatchlist).toHaveBeenCalledWith('TCS', 'Tata Consultancy Services')
    expect(wrapper.text()).toContain('Add Stock to Watchlist')
    expect(symbolInput.element).toHaveProperty('value', 'TCS')
    expect(nameInput.element).toHaveProperty('value', 'Tata Consultancy Services')
    const alert = wrapper.get('[role="alert"]')
    expect(alert.text()).toMatch(/couldn.t add TCS|couldn.t add stock/i)
    expect(alert.text()).toContain('Symbol is already monitored')
    expect(document.activeElement).toBe(alert.element)
    expect(globalThis.alert).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('keeps the row when removal fails and presents the reason without native alert', async () => {
    apiMocks.removeFromWatchlist.mockRejectedValue(
      appError('Stock is used by an active run', 'conflict')
    )
    const wrapper = mountView()
    await flushPromises()

    await wrapper.get('button[title="Remove RELIANCE"]').trigger('click')
    await flushPromises()

    expect(apiMocks.removeFromWatchlist).toHaveBeenCalledWith('RELIANCE')
    expect(wrapper.text()).toContain('RELIANCE')
    expect(wrapper.text()).toContain('Reliance Industries')
    expect(wrapper.get('[role="alert"]').text()).toContain('Stock is used by an active run')
    expect(globalThis.alert).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('restores the active state when toggle fails and exposes a contextual error', async () => {
    apiMocks.toggleWatchlistActive.mockRejectedValue(
      appError('Watchlist update conflicted', 'conflict')
    )
    const wrapper = mountView()
    await flushPromises()

    await button(wrapper, 'Active').trigger('click')
    await flushPromises()

    expect(apiMocks.toggleWatchlistActive).toHaveBeenCalledWith('RELIANCE', false)
    expect(button(wrapper, 'Active').exists()).toBe(true)
    expect(wrapper.get('[role="alert"]').text()).toMatch(/couldn.t update RELIANCE/i)
    expect(wrapper.get('[role="alert"]').text()).toContain('Watchlist update conflicted')
    expect(globalThis.alert).not.toHaveBeenCalled()
    wrapper.unmount()
  })
})

describe('WatchlistView — signal monitoring', () => {
  it('shows and filters the latest BUY signal for a watched stock', async () => {
    apiMocks.getSignals.mockResolvedValue([
      {
        id: 'signal-1',
        symbol: 'RELIANCE',
        direction: 'BUY',
        confidence: 88,
        reason: 'All entry rules passed',
        entryPrice: 100,
        stopLoss: 95,
        target: 115,
        riskReward: 3,
        timestamp: '2026-08-30',
        status: 'ACTIVE',
      },
    ])
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('1 BUY signal')
    expect(wrapper.text()).toContain('BUY')

    await button(wrapper, 'BUY signals 1').trigger('click')
    expect(wrapper.text()).toContain('RELIANCE')
    expect(wrapper.findAll('tbody tr')).toHaveLength(1)
    wrapper.unmount()
  })
  it('shows a variant consensus badge only when strategy-tagged signals exist', async () => {
    const base = {
      symbol: 'RELIANCE', confidence: 0.8, reason: '', entryPrice: 100, stopLoss: 95, target: 115,
      riskReward: 3, timestamp: '2026-08-30', status: 'ACTIVE',
    }
    apiMocks.getSignals.mockResolvedValue([
      { ...base, id: 's1', direction: 'BUY', strategy: 'breakout-v1' },
      { ...base, id: 's2', direction: 'SELL', strategy: 'squeeze-v1' },
    ])
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.find('[data-testid="consensus-badge"]').text()).toBe('1/2 BUY')
    wrapper.unmount()
  })
})
