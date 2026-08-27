import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AppError } from '../errors/appError'

function legacySuccess<T>(data: T) {
  return data
}

const apiMocks = vi.hoisted(() => ({
  getAccuracyStats: vi.fn(),
  getAccuracySummary: vi.fn(),
  getAccuracyByWindow: vi.fn(),
  getAccuracyByRegime: vi.fn(),
  getAccuracyBySymbol: vi.fn(),
  getCalibration: vi.fn(),
  getSignalVolume: vi.fn(),
  getECE: vi.fn(),
}))

vi.mock('../api/client', () => apiMocks)
vi.mock('../api/sentiment', () => apiMocks)

function findCard(wrapper: VueWrapper, heading: string): HTMLElement {
  const headingWrapper = wrapper
    .findAll('h2, h3')
    .find((candidate) => candidate.text().trim() === heading)
  expect(headingWrapper, `Expected heading "${heading}"`).toBeDefined()
  const card = headingWrapper!.element.closest('.card-panel')
  expect(card, `Expected "${heading}" to be inside a card`).not.toBeNull()
  return card as HTMLElement
}

async function mountMonitoring() {
  const MonitoringView = (await import('./MonitoringView.vue')).default
  return mount(MonitoringView, {
    global: {
      stubs: {
        LoadingSpinner: { template: '<div>Loading...</div>' },
        ErrorBoundary: { template: '<div><slot /><slot name="error" /></div>' },
        AccuracyMetricCard: {
          props: ['title', 'value', 'subtext'],
          template:
            '<article class="accuracy-card"><h3>{{ title }}</h3><span>{{ value }}</span><small>{{ subtext }}</small></article>',
        },
      },
    },
  })
}

describe('MonitoringView — partial monitoring failures', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    apiMocks.getAccuracyStats.mockResolvedValue(
      legacySuccess({
        total: 20,
        correct: 15,
        accuracy_pct: 0.75,
        by_sentiment: { POSITIVE: 12, NEGATIVE: 8 },
        by_symbol: { RELIANCE: 20 },
      })
    )
    apiMocks.getAccuracySummary.mockResolvedValue(
      legacySuccess({
        total: 20,
        correct: 15,
        accuracy_pct: 0.75,
        directional_accuracy: 0.7,
        avg_confidence: 0.8,
        by_sentiment: {},
        by_symbol: {},
      })
    )
    apiMocks.getAccuracyByWindow.mockResolvedValue(legacySuccess([]))
    apiMocks.getAccuracyByRegime.mockResolvedValue(legacySuccess([]))
    apiMocks.getAccuracyBySymbol.mockResolvedValue(legacySuccess([]))
    apiMocks.getCalibration.mockResolvedValue(legacySuccess([]))
    apiMocks.getECE.mockResolvedValue(legacySuccess({ ece: 0.08, bins: 10 }))
  })

  it('renders successful accuracy cards when signal volume fails and retries only that section', async () => {
    apiMocks.getSignalVolume
      .mockRejectedValueOnce(
        new AppError({
          kind: 'network',
          message: 'Signal volume unavailable',
          retryable: true,
        })
      )
      .mockResolvedValueOnce(
        legacySuccess({
          today_count: 7,
          seven_day_count: 35,
          seven_day_avg: 5,
          thirty_day_avg: 4,
        })
      )

    const wrapper = await mountMonitoring()
    await flushPromises()

    const overallCard = wrapper
      .findAll('.accuracy-card')
      .find((card) => card.text().includes('Overall Accuracy'))
    expect(overallCard).toBeDefined()
    expect(overallCard!.text()).toContain('75.0%')

    const volumeCard = findCard(wrapper, 'Signal Volume')
    expect(volumeCard.textContent).toMatch(/couldn.t load signal volume/i)
    expect(volumeCard.textContent).not.toMatch(/Today\s*0/)

    const retry = wrapper
      .findAll('button')
      .find((button) => volumeCard.contains(button.element) && button.text().trim() === 'Retry')
    expect(retry).toBeDefined()
    await retry!.trigger('click')
    await flushPromises()

    expect(findCard(wrapper, 'Signal Volume').textContent).toMatch(/Today\s*7/)
    expect(apiMocks.getSignalVolume).toHaveBeenCalledTimes(2)
    expect(apiMocks.getAccuracyStats).toHaveBeenCalledTimes(1)
    expect(apiMocks.getAccuracySummary).toHaveBeenCalledTimes(1)
    expect(apiMocks.getAccuracyByWindow).toHaveBeenCalledTimes(1)
    expect(apiMocks.getAccuracyByRegime).toHaveBeenCalledTimes(1)
    expect(apiMocks.getAccuracyBySymbol).toHaveBeenCalledTimes(1)
    expect(apiMocks.getCalibration).toHaveBeenCalledTimes(1)
    expect(apiMocks.getECE).toHaveBeenCalledTimes(1)

    wrapper.unmount()
  })
})
