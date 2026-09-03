import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AppError } from '../errors/appError'

function legacySuccess<T>(data: T) {
  return data
}

const apiMocks = vi.hoisted(() => ({
  getPortfolioSummary: vi.fn(),
  getTradeHistory: vi.fn(),
  getEquityCurve: vi.fn(),
}))

vi.mock('../api/client', () => apiMocks)
vi.mock('../api/positions', () => apiMocks)

const oldSummary = {
  totalValue: 100000,
  totalPnl: 5000,
  totalPnlPercent: 5,
  winRate: 60,
  totalTrades: 10,
  averageWin: 4,
  averageLoss: 2,
  profitFactor: 2,
  maxDrawdown: 5,
  sharpeRatio: 1.3,
}

const newSummary = { ...oldSummary, totalValue: 200000, totalPnl: 9000 }
const oldEquity = { data: [{ date: '2026-08-25', value: 100 }] }
const newEquity = { data: [{ date: '2026-08-26', value: 250 }] }

const oldTrade = {
  id: 'trade-old',
  symbol: 'RELIANCE',
  entryPrice: 2800,
  currentPrice: 2850,
  quantity: 2,
  status: 'CLOSED',
  pnl: 100,
  pnlPercent: 1.8,
  entryDate: '2026-08-20',
}

const newTrade = { ...oldTrade, id: 'trade-new', symbol: 'TCS' }

function findCard(wrapper: VueWrapper, heading: string): HTMLElement {
  const headingWrapper = wrapper
    .findAll('h2, h3')
    .find((candidate) => candidate.text().trim() === heading)
  expect(headingWrapper, `Expected heading "${heading}"`).toBeDefined()
  const card = headingWrapper!.element.closest('.card-panel')
  expect(card, `Expected "${heading}" to be inside a card`).not.toBeNull()
  return card as HTMLElement
}

async function mountPortfolio() {
  const PortfolioView = (await import('./PortfolioView.vue')).default
  return mount(PortfolioView, {
    global: {
      stubs: {
        LoadingSpinner: { template: '<div>Loading portfolio data...</div>' },
        ErrorBoundary: { template: '<div><slot /><slot name="error" /></div>' },
        PerformanceMetrics: {
          props: ['portfolioSummary', 'equityPoints'],
          emits: ['range-change'],
          template:
            '<section data-testid="performance-snapshot">{{ portfolioSummary?.totalValue ?? "none" }}|{{ equityPoints[0]?.value ?? "none" }}</section>',
        },
      },
    },
  })
}

function pageRefreshButton(wrapper: VueWrapper) {
  const button = wrapper
    .findAll('button')
    .find((candidate) => candidate.text().trim() === 'Refresh')
  expect(button).toBeDefined()
  return button!
}

describe('PortfolioView — explicit section state', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-08-26T09:00:00.000Z'))
    apiMocks.getPortfolioSummary.mockResolvedValue(legacySuccess(oldSummary))
    apiMocks.getEquityCurve.mockResolvedValue(legacySuccess(oldEquity))
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('shows a persistent initial trade-history error instead of a false empty state', async () => {
    apiMocks.getTradeHistory
      .mockRejectedValueOnce(
        new AppError({
          kind: 'network',
          message: 'Trade history unavailable',
          retryable: true,
        })
      )
      .mockResolvedValueOnce(legacySuccess([]))

    const wrapper = await mountPortfolio()
    await flushPromises()

    const failedCard = findCard(wrapper, 'Trade History')
    expect(failedCard.textContent).toMatch(/couldn.t load trade history/i)
    expect(failedCard.textContent).not.toContain('No trade history')

    const retry = wrapper
      .findAll('button')
      .find((button) => failedCard.contains(button.element) && button.text().trim() === 'Retry')
    expect(retry).toBeDefined()
    await retry!.trigger('click')
    await flushPromises()

    const emptyCard = findCard(wrapper, 'Trade History')
    expect(emptyCard.textContent).toContain('No trade history')
    expect(emptyCard.textContent).not.toMatch(/couldn.t load trade history/i)
    expect(apiMocks.getTradeHistory).toHaveBeenCalledTimes(2)
    expect(apiMocks.getPortfolioSummary).toHaveBeenCalledTimes(1)
    expect(apiMocks.getEquityCurve).toHaveBeenCalledTimes(1)

    wrapper.unmount()
  })

  it('retains the last atomic performance snapshot when one refresh part fails', async () => {
    apiMocks.getTradeHistory
      .mockResolvedValueOnce(legacySuccess([oldTrade]))
      .mockResolvedValueOnce(legacySuccess([newTrade]))
    apiMocks.getPortfolioSummary
      .mockResolvedValueOnce(legacySuccess(oldSummary))
      .mockResolvedValueOnce(legacySuccess(newSummary))
    apiMocks.getEquityCurve
      .mockResolvedValueOnce(legacySuccess(oldEquity))
      .mockRejectedValueOnce(
        new AppError({
          kind: 'network',
          message: 'Equity curve unavailable',
          retryable: true,
        })
      )
      .mockResolvedValueOnce(legacySuccess(newEquity))
    apiMocks.getPortfolioSummary.mockResolvedValueOnce(legacySuccess(newSummary))

    const wrapper = await mountPortfolio()
    await flushPromises()

    expect(wrapper.get('[data-testid="performance-snapshot"]').text()).toBe('100000|100')
    expect(wrapper.text()).toContain('RELIANCE')

    vi.setSystemTime(new Date('2026-08-26T09:10:00.000Z'))
    await pageRefreshButton(wrapper).trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-testid="performance-snapshot"]').text()).toBe('100000|100')
    expect(wrapper.text()).toContain('TCS')
    expect(wrapper.text()).toMatch(/refresh failed/i)
    expect(wrapper.text()).toMatch(/last updated/i)
    expect(wrapper.get('time').attributes('datetime')).toBe('2026-08-26T09:00:00.000Z')

    const retry = wrapper.findAll('button').find((candidate) => candidate.text().trim() === 'Retry')
    expect(retry).toBeDefined()
    await retry!.trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-testid="performance-snapshot"]').text()).toBe('200000|250')
    expect(wrapper.get('time').attributes('datetime')).toBe('2026-08-26T09:10:00.000Z')
    expect(apiMocks.getPortfolioSummary).toHaveBeenCalledTimes(3)
    expect(apiMocks.getEquityCurve).toHaveBeenCalledTimes(3)
    expect(apiMocks.getTradeHistory).toHaveBeenCalledTimes(2)

    wrapper.unmount()
  })
})
