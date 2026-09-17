import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AppError } from '../errors/appError'
import type { StrategyVariant, PromotionEligibilityResult } from '../api/strategies'

const apiMocks = vi.hoisted(() => ({
  listStrategyTypes: vi.fn(),
  listCurrentStrategies: vi.fn(),
  listStrategyVersions: vi.fn(),
  createStrategyVariant: vi.fn(),
  createStrategyVersion: vi.fn(),
  cloneStrategyVariant: vi.fn(),
  changeStrategyMode: vi.fn(),
  validateStrategyParams: vi.fn(),
  compareBacktests: vi.fn(),
  fetchPromotionEligibility: vi.fn(),
}))

vi.mock('../api/strategies', async () => {
  const actual = await vi.importActual<typeof import('../api/strategies')>('../api/strategies')
  return { ...actual, ...apiMocks }
})

function variant(overrides: Partial<StrategyVariant> = {}): StrategyVariant {
  return {
    variantId: 'V1',
    version: 1,
    strategyType: 'MOMENTUM',
    params: {},
    overlays: {},
    paramsHash: 'hash',
    mode: 'SHADOW',
    paperCapital: 100000,
    isCurrent: true,
    portfolioAction: null,
    notes: null,
    createdAt: '2026-01-01T00:00:00Z',
    ...overrides,
  }
}

function eligibility(overrides: Partial<PromotionEligibilityResult> = {}): PromotionEligibilityResult {
  const condition = { met: true, actualValue: '12', threshold: '10' }
  return {
    challengerVariantId: 'V1',
    championVariantId: 'CHAMP',
    tenureCalendarDays: 45,
    status: 'ELIGIBLE',
    tenureAndSampleSize: condition,
    expectancyVsChampion: condition,
    drawdownGuard: condition,
    walkForwardAndOverfitting: condition,
    ...overrides,
  }
}

async function mountStrategies() {
  const StrategiesView = (await import('./StrategiesView.vue')).default
  return mount(StrategiesView, {
    global: {
      plugins: [createPinia()],
    },
  })
}

function leaderboardSection(wrapper: VueWrapper): HTMLElement {
  const heading = wrapper
    .findAll('h2')
    .find((candidate) => candidate.text().trim() === 'Live shadow leaderboard')
  expect(heading).toBeDefined()
  const section = heading!.element.closest('.card-panel')
  expect(section).not.toBeNull()
  return section as HTMLElement
}

describe('StrategiesView — promotion eligibility checklist', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    apiMocks.listStrategyTypes.mockResolvedValue([])
  })

  it('renders a neutral ELIGIBLE checklist with the four conditions', async () => {
    apiMocks.listCurrentStrategies.mockResolvedValue([
      variant({ variantId: 'CHALLENGER', mode: 'SHADOW' }),
      variant({ variantId: 'CHAMP', mode: 'CHAMPION' }),
    ])
    apiMocks.fetchPromotionEligibility.mockResolvedValue(
      eligibility({ challengerVariantId: 'CHALLENGER' })
    )

    const wrapper = await mountStrategies()
    await flushPromises()
    await flushPromises()

    expect(apiMocks.fetchPromotionEligibility).toHaveBeenCalledWith('CHALLENGER')
    expect(apiMocks.fetchPromotionEligibility).not.toHaveBeenCalledWith('CHAMP')

    const section = leaderboardSection(wrapper)
    expect(section.textContent).toContain('Eligible')
    expect(section.textContent).toContain('Tenure and sample size')
    expect(section.textContent).toContain('Expectancy vs champion')
    expect(section.textContent).toContain('Drawdown guard')
    expect(section.textContent).toContain('Walk-forward and overfitting')
    expect(section.textContent).toContain('12 (threshold: 10)')

    // Explicit plan rule: ELIGIBLE gets no "winner" styling.
    const badge = Array.from(section.querySelectorAll('span')).find(
      (el) => el.textContent?.trim() === 'Eligible'
    )
    expect(badge).toBeDefined()
    expect(badge!.className).not.toMatch(/success|green|winner/i)

    wrapper.unmount()
  })

  it('frames INSUFFICIENT_SAMPLE as "not enough data" rather than a failure', async () => {
    apiMocks.listCurrentStrategies.mockResolvedValue([
      variant({ variantId: 'CHALLENGER', mode: 'SHADOW' }),
      variant({ variantId: 'CHAMP', mode: 'CHAMPION' }),
    ])
    apiMocks.fetchPromotionEligibility.mockResolvedValue(
      eligibility({ challengerVariantId: 'CHALLENGER', status: 'INSUFFICIENT_SAMPLE' })
    )

    const wrapper = await mountStrategies()
    await flushPromises()
    await flushPromises()

    const section = leaderboardSection(wrapper)
    expect(section.textContent).toContain('Insufficient sample')
    expect(section.textContent).toMatch(/not enough data yet/i)
    expect(section.textContent).not.toMatch(/\bfailed\b|\brejected\b|\bdenied\b/i)

    wrapper.unmount()
  })

  it('skips the eligibility call for the current champion and labels it distinctly', async () => {
    apiMocks.listCurrentStrategies.mockResolvedValue([
      variant({ variantId: 'CHAMP', mode: 'CHAMPION' }),
    ])

    const wrapper = await mountStrategies()
    await flushPromises()
    await flushPromises()

    expect(apiMocks.fetchPromotionEligibility).not.toHaveBeenCalled()
    const section = leaderboardSection(wrapper)
    expect(section.textContent).toContain('Current champion')
    expect(section.textContent).toContain(
      'This is the current champion. There is no promotion checklist to compare it against.'
    )

    wrapper.unmount()
  })

  it('shows a loading state, then an error state with a working retry', async () => {
    apiMocks.listCurrentStrategies.mockResolvedValue([
      variant({ variantId: 'CHALLENGER', mode: 'SHADOW' }),
      variant({ variantId: 'CHAMP', mode: 'CHAMPION' }),
    ])
    apiMocks.fetchPromotionEligibility.mockReturnValueOnce(new Promise(() => {}))

    const wrapper = await mountStrategies()
    await flushPromises()

    const loadingSection = leaderboardSection(wrapper)
    expect(loadingSection.textContent).toMatch(/checking promotion eligibility/i)

    wrapper.unmount()
  })

  it('recovers from a failed eligibility check via retry', async () => {
    apiMocks.listCurrentStrategies.mockResolvedValue([
      variant({ variantId: 'CHALLENGER', mode: 'SHADOW' }),
      variant({ variantId: 'CHAMP', mode: 'CHAMPION' }),
    ])
    apiMocks.fetchPromotionEligibility.mockRejectedValueOnce(
      new AppError({ kind: 'server', message: 'Eligibility check failed', retryable: true })
    )

    const wrapper = await mountStrategies()
    await flushPromises()
    await flushPromises()

    let section = leaderboardSection(wrapper)
    expect(section.textContent).toContain('Eligibility check failed')

    apiMocks.fetchPromotionEligibility.mockResolvedValueOnce(
      eligibility({ challengerVariantId: 'CHALLENGER' })
    )
    const retryButton = Array.from(section.querySelectorAll('button')).find(
      (b) => b.textContent?.trim() === 'Retry'
    )
    expect(retryButton).toBeDefined()
    retryButton!.dispatchEvent(new Event('click'))
    await flushPromises()
    await flushPromises()

    section = leaderboardSection(wrapper)
    expect(section.textContent).toContain('Eligible')
    expect(section.textContent).not.toContain('Eligibility check failed')

    wrapper.unmount()
  })
})
