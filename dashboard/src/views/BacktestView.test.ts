import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import BacktestView from './BacktestView.vue'

const apiMocks = vi.hoisted(() => ({
  runBacktest: vi.fn(),
  runBacktestAll: vi.fn(),
  listBacktestReports: vi.fn(),
  getBacktestReport: vi.fn(),
  getWatchlist: vi.fn(),
}))

vi.mock('../api/client', () => apiMocks)
vi.mock('../api/watchlist', () => ({ getWatchlist: apiMocks.getWatchlist }))

const entries = [
  {
    symbol: 'RELIANCE',
    name: 'Reliance Industries',
    exchange: 'NSE',
    isActive: true,
  },
  {
    symbol: 'TCS',
    name: 'Tata Consultancy Services',
    exchange: 'NSE',
    isActive: true,
  },
  {
    symbol: 'OLDSTOCK',
    name: 'Inactive Stock',
    exchange: 'NSE',
    isActive: false,
  },
]

beforeEach(() => {
  vi.clearAllMocks()
  apiMocks.getWatchlist.mockResolvedValue(entries)
  apiMocks.listBacktestReports.mockResolvedValue([])
})

function mountView() {
  return mount(BacktestView, {
    global: {
      stubs: {
        MetricCard: true,
        ErrorMessage: true,
      },
    },
  })
}

describe('BacktestView — watchlist symbol selector', () => {
  it('loads active watchlist symbols and selects the first by default', async () => {
    const wrapper = mountView()
    await flushPromises()

    const input = wrapper.get('[role="combobox"]')
    expect((input.element as HTMLInputElement).value).toBe('RELIANCE')
    await input.trigger('focus')
    expect(wrapper.text()).toContain('Reliance Industries')
    expect(wrapper.text()).toContain('Tata Consultancy Services')
    expect(wrapper.text()).not.toContain('Inactive Stock')
    wrapper.unmount()
  })

  it('filters and selects a watchlist symbol', async () => {
    const wrapper = mountView()
    await flushPromises()
    const input = wrapper.get('[role="combobox"]')

    await input.setValue('tcs')
    expect(wrapper.text()).toContain('Tata Consultancy Services')
    expect(wrapper.text()).not.toContain('Reliance Industries')

    await wrapper.get('[role="option"]').trigger('mousedown')
    expect((input.element as HTMLInputElement).value).toBe('TCS')
    wrapper.unmount()
  })

  it('does not run a backtest for text that was not selected', async () => {
    const wrapper = mountView()
    await flushPromises()
    await wrapper.get('[role="combobox"]').setValue('UNKNOWN')
    await wrapper.get('form').trigger('submit')

    expect(apiMocks.runBacktest).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('Select a symbol from the active watchlist.')
    wrapper.unmount()
  })

  it('keeps the selected symbol displayed on refocus instead of clearing it', async () => {
    // Regression: refocusing the field used to clear the displayed text while
    // leaving the actual selection unchanged, making it look like the
    // selection had been lost.
    const wrapper = mountView()
    await flushPromises()
    const input = wrapper.get('[role="combobox"]')

    await input.trigger('focus')

    expect((input.element as HTMLInputElement).value).toBe('RELIANCE')
    // Browsing the full list on refocus (not filtered down to just the
    // current selection) still works.
    expect(wrapper.text()).toContain('Tata Consultancy Services')
    wrapper.unmount()
  })
})
