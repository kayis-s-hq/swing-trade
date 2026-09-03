import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AppError } from '../errors/appError'

const apiMocks = vi.hoisted(() => ({
  getMarketOverview: vi.fn(),
  getPositions: vi.fn(),
  getPortfolioSummary: vi.fn(),
  getHealthStatus: vi.fn(),
  getEquityCurve: vi.fn(),
  getRiskSummary: vi.fn(),
  getSignals: vi.fn(),
  getWatchlist: vi.fn(),
}))

vi.mock('../api/client', () => apiMocks)
vi.mock('../api/positions', () => ({
  getMarketOverview: apiMocks.getMarketOverview,
  getPositions: apiMocks.getPositions,
  getPortfolioSummary: apiMocks.getPortfolioSummary,
}))
vi.mock('../api/health', () => ({ getHealthStatus: apiMocks.getHealthStatus }))

function legacySuccess<T>(data: T) {
  return data
}

const overview = {
  totalPositions: 1,
  openPositions: 1,
  todayPnl: 1234,
  todayPnlPercent: 1.25,
}

const portfolio = {
  totalValue: 250000,
  totalPnl: 1234,
  totalPnlPercent: 1.25,
  winRate: 62,
  totalTrades: 18,
  averageWin: 4,
  averageLoss: 2,
  profitFactor: 1.8,
  maxDrawdown: 7,
  sharpeRatio: 1.4,
}

const position = {
  id: 'position-1',
  symbol: 'RELIANCE',
  entryPrice: 2800,
  currentPrice: 2850,
  quantity: 10,
  status: 'OPEN',
  pnl: 500,
  pnlPercent: 1.79,
  entryDate: '2026-08-25',
}

function findCard(wrapper: VueWrapper, heading: string): HTMLElement {
  const headingWrapper = wrapper
    .findAll('h2, h3')
    .find((candidate) => candidate.text().trim() === heading)
  expect(headingWrapper, `Expected heading "${heading}"`).toBeDefined()
  const card = headingWrapper!.element.closest('.card-panel')
  expect(card, `Expected "${heading}" to be inside a card`).not.toBeNull()
  return card as HTMLElement
}

async function mountDashboard() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: { template: '<div />' } },
      { path: '/positions', component: { template: '<div />' } },
    ],
  })
  await router.push('/')
  await router.isReady()

  const DashboardView = (await import('./DashboardView.vue')).default
  return mount(DashboardView, {
    global: {
      plugins: [router],
      stubs: {
        LoadingSpinner: { template: '<div>Loading market data...</div>' },
        HealthStatus: { template: '<div data-testid="health-status">Healthy</div>' },
        ErrorBoundary: { template: '<div><slot /><slot name="error" /></div>' },
      },
    },
  })
}

describe('DashboardView — independent section failures', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    apiMocks.getMarketOverview.mockResolvedValue(legacySuccess(overview))
    apiMocks.getPortfolioSummary.mockResolvedValue(legacySuccess(portfolio))
    apiMocks.getHealthStatus.mockResolvedValue(legacySuccess({ status: 'UP', components: {} }))
    apiMocks.getRiskSummary.mockResolvedValue(
      legacySuccess({
        totalExposure: 28000,
        availableCapital: 222000,
        usedCapital: 28000,
        stopLossExposure: 1000,
        numberOfPositions: 1,
      })
    )
    apiMocks.getSignals.mockResolvedValue([])
    apiMocks.getWatchlist.mockResolvedValue([])
    apiMocks.getEquityCurve.mockResolvedValue({ data: [] })
  })

  it('keeps successful cards visible and retries only the failed positions section', async () => {
    apiMocks.getPositions
      .mockRejectedValueOnce(
        new AppError({
          kind: 'network',
          message: 'Positions endpoint unavailable',
          retryable: true,
        })
      )
      .mockResolvedValueOnce([position])

    const wrapper = await mountDashboard()
    await flushPromises()

    expect(wrapper.text()).toContain('Rs.1,234')
    expect(wrapper.text()).toContain('Rs.250,000')

    const positionsCard = findCard(wrapper, 'Active Positions')
    expect(positionsCard.textContent).toMatch(/couldn.t load positions/i)
    expect(positionsCard.textContent).not.toContain('0 positions')

    const retry = wrapper
      .findAll('button')
      .find((button) => positionsCard.contains(button.element) && button.text().trim() === 'Retry')
    expect(retry).toBeDefined()
    await retry!.trigger('click')
    await flushPromises()

    expect(findCard(wrapper, 'Active Positions').textContent).toContain('RELIANCE')
    expect(apiMocks.getPositions).toHaveBeenCalledTimes(2)
    expect(apiMocks.getMarketOverview).toHaveBeenCalledTimes(1)
    expect(apiMocks.getPortfolioSummary).toHaveBeenCalledTimes(1)
    expect(apiMocks.getHealthStatus).toHaveBeenCalledTimes(1)

    wrapper.unmount()
  })
})
