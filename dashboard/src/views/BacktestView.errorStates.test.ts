import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AppError } from '../errors/appError'

function legacySuccess<T>(data: T) {
  return { success: true as const, data }
}

const apiMocks = vi.hoisted(() => ({
  runBacktest: vi.fn(),
  runBacktestAll: vi.fn(),
  listBacktestReports: vi.fn(),
  getBacktestReport: vi.fn(),
}))

vi.mock('../api/client', () => apiMocks)
vi.mock('../api/backtest', () => apiMocks)

interface Deferred<T> {
  promise: Promise<T>
  resolve: (value: T) => void
}

function deferred<T>(): Deferred<T> {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((resolvePromise) => {
    resolve = resolvePromise
  })
  return { promise, resolve }
}

function backtestResult(symbol: string) {
  return {
    symbol,
    totalTrades: 4,
    winningTrades: 3,
    losingTrades: 1,
    winRate: 75,
    avgGainPct: 4,
    avgLossPct: 2,
    maxDrawdownPct: 5,
    sharpeRatio: 1.5,
    totalReturn: 12,
    expectancy: 2.5,
    trades: [],
  }
}

function report(symbol: string, generatedAt: string) {
  const result = backtestResult(symbol)
  return {
    generatedAt,
    symbolsBacktested: 1,
    top10ByWinRate: [result],
    top10ByTotalReturn: [result],
    overallWinRate: 75,
    overallSharpeRatio: 1.5,
    results: [result],
  }
}

function savedReportsCard(wrapper: VueWrapper): HTMLElement {
  const heading = wrapper
    .findAll('h2, h3')
    .find((candidate) => candidate.text().trim() === 'Saved Reports')
  expect(heading).toBeDefined()
  const card = heading!.element.closest('.card-panel')
  expect(card).not.toBeNull()
  return card as HTMLElement
}

function viewButtonFor(wrapper: VueWrapper, filename: string) {
  const listItem = wrapper.findAll('li').find((candidate) => candidate.text().includes(filename))
  expect(listItem, `Expected report row for ${filename}`).toBeDefined()
  return listItem!.get('button')
}

async function mountBacktest() {
  const BacktestView = (await import('./BacktestView.vue')).default
  return mount(BacktestView, {
    global: {
      stubs: {
        MetricCard: {
          props: ['title', 'value'],
          template: '<div class="metric-card">{{ title }}: {{ value }}</div>',
        },
      },
    },
  })
}

describe('BacktestView — saved report states', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('distinguishes an initial report-list failure from a confirmed empty list', async () => {
    apiMocks.listBacktestReports
      .mockRejectedValueOnce(
        new AppError({ kind: 'network', message: 'Saved reports unavailable', retryable: true })
      )
      .mockResolvedValueOnce(legacySuccess([]))

    const wrapper = await mountBacktest()
    await flushPromises()

    const failedCard = savedReportsCard(wrapper)
    expect(failedCard.textContent).toMatch(/saved reports couldn.t be loaded/i)
    expect(failedCard.textContent).not.toContain('No saved reports yet.')

    const retry = wrapper
      .findAll('button')
      .find((button) => failedCard.contains(button.element) && button.text().trim() === 'Retry')
    expect(retry).toBeDefined()
    await retry!.trigger('click')
    await flushPromises()

    const emptyCard = savedReportsCard(wrapper)
    expect(emptyCard.textContent).toContain('No saved reports yet.')
    expect(emptyCard.textContent).not.toMatch(/couldn.t be loaded/i)

    wrapper.unmount()
  })

  it('ignores an older report response that resolves after the latest selection', async () => {
    const oldReport = deferred<ReturnType<typeof legacySuccess<ReturnType<typeof report>>>>()
    const latestReport = deferred<ReturnType<typeof legacySuccess<ReturnType<typeof report>>>>()
    apiMocks.listBacktestReports.mockResolvedValue(
      legacySuccess(['old-report.json', 'latest-report.json'])
    )
    apiMocks.getBacktestReport
      .mockReturnValueOnce(oldReport.promise)
      .mockReturnValueOnce(latestReport.promise)

    const wrapper = await mountBacktest()
    await flushPromises()

    await viewButtonFor(wrapper, 'old-report.json').trigger('click')
    await viewButtonFor(wrapper, 'latest-report.json').trigger('click')

    latestReport.resolve(legacySuccess(report('TCS', '2026-08-26T09:05:00Z')))
    await flushPromises()
    expect(wrapper.text()).toContain('TCS')

    oldReport.resolve(legacySuccess(report('RELIANCE', '2026-08-25T09:05:00Z')))
    await flushPromises()

    expect(wrapper.text()).toContain('TCS')
    expect(wrapper.text()).not.toContain('RELIANCE')
    expect(apiMocks.getBacktestReport).toHaveBeenNthCalledWith(1, 'old-report.json')
    expect(apiMocks.getBacktestReport).toHaveBeenNthCalledWith(2, 'latest-report.json')

    wrapper.unmount()
  })
})
