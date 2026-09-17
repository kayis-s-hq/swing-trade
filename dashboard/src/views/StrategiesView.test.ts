import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import StrategiesView from './StrategiesView.vue'

interface PromotionState {
  loading: boolean
  error: string | null
  result: Record<string, unknown> | null
}

const storeMock = vi.hoisted(() => ({
  state: {
    strategies: null as unknown[] | null,
    loading: false,
    error: null as Error | null,
    errorMessage: '',
    promotionEligibility: {} as Record<string, PromotionState>,
  },
  load: vi.fn(),
  retry: vi.fn(),
  loadPromotionEligibility: vi.fn(),
}))

vi.mock('../stores/strategies', () => ({
  useStrategiesStore: () => ({
    get strategies() {
      return storeMock.state.strategies
    },
    get loading() {
      return storeMock.state.loading
    },
    get error() {
      return storeMock.state.error
    },
    get errorMessage() {
      return storeMock.state.errorMessage
    },
    get promotionEligibility() {
      return storeMock.state.promotionEligibility
    },
    load: storeMock.load,
    retry: storeMock.retry,
    loadPromotionEligibility: storeMock.loadPromotionEligibility,
  }),
}))

const config = {
  id: 1,
  variantId: 'DEFAULT',
  version: 2,
  strategyType: 'PRICE_ACTION',
  params: {},
  overlays: {},
  paramsHash: 'a'.repeat(64),
  mode: 'CHAMPION',
  paperCapital: 500000,
  current: true,
  notes: 'Production strategy',
  createdAt: '2026-09-16T10:00:00Z',
}

function mountView() {
  return mount(StrategiesView)
}

describe('StrategiesView', () => {
  beforeEach(() => {
    storeMock.state.strategies = null
    storeMock.state.loading = false
    storeMock.state.error = null
    storeMock.state.errorMessage = ''
    storeMock.state.promotionEligibility = {}
    vi.clearAllMocks()
  })

  it('loads and displays strategy mode and configuration details', async () => {
    storeMock.state.strategies = [config]
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.text()).toContain('DEFAULT')
    expect(wrapper.text()).toContain('CHAMPION')
    expect(wrapper.text()).toContain('₹5,00,000')
    expect(wrapper.text()).toContain('Production strategy')
  })

  it('renders loading, empty, and retryable error states', async () => {
    storeMock.state.loading = true
    const loadingWrapper = mountView()
    expect(loadingWrapper.text()).toContain('Loading strategies...')
    loadingWrapper.unmount()

    storeMock.state.loading = false
    storeMock.state.strategies = []
    const emptyWrapper = mountView()
    expect(emptyWrapper.text()).toContain('No strategies configured')
    emptyWrapper.unmount()

    storeMock.state.strategies = null
    storeMock.state.error = new Error('unavailable')
    storeMock.state.errorMessage = 'Configuration service unavailable.'
    const errorWrapper = mountView()
    expect(errorWrapper.text()).toContain('Configuration service unavailable.')
    const retry = errorWrapper.findAll('button').find((button) => button.text().trim() === 'Retry')
    expect(retry).toBeDefined()
    await retry!.trigger('click')
    expect(storeMock.retry).toHaveBeenCalledOnce()
  })

  describe('promotion eligibility', () => {
    const champion = { ...config, variantId: 'DEFAULT', mode: 'CHAMPION', current: true }
    const shadow = { ...config, variantId: 'RS_NIFTY', mode: 'SHADOW', current: true }

    it('requests promotion eligibility for active shadow variants but not the champion', async () => {
      storeMock.state.strategies = [champion, shadow]
      mountView()
      await flushPromises()

      expect(storeMock.loadPromotionEligibility).toHaveBeenCalledWith('RS_NIFTY')
      expect(storeMock.loadPromotionEligibility).not.toHaveBeenCalledWith('DEFAULT')
    })

    it('renders the per-condition checklist verbatim from the API response', async () => {
      storeMock.state.strategies = [champion, shadow]
      storeMock.state.promotionEligibility.RS_NIFTY = {
        loading: false,
        error: null,
        result: {
          challengerVariantId: 'RS_NIFTY',
          championVariantId: 'DEFAULT',
          status: 'NOT_ELIGIBLE',
          conditions: [
            {
              name: 'sample_size',
              met: true,
              actualValue: '65d tenure, 40 closed trades',
              threshold: '>=60d and >=30 trades',
              note: null,
            },
          ],
          notes: [],
          dataLimitations: ['Only SHADOW tenure is computed from real data.'],
        },
      }
      const wrapper = mountView()
      await flushPromises()

      expect(wrapper.text()).toContain('sample_size')
      expect(wrapper.text()).toContain('65d tenure, 40 closed trades')
      expect(wrapper.text()).toContain('>=60d and >=30 trades')
      expect(wrapper.text()).toContain('Only SHADOW tenure is computed from real data.')
      expect(wrapper.text()).toContain('Not eligible')
    })

    it('frames INSUFFICIENT_SAMPLE as "not enough data yet", not a failure', async () => {
      storeMock.state.strategies = [champion, shadow]
      storeMock.state.promotionEligibility.RS_NIFTY = {
        loading: false,
        error: null,
        result: {
          challengerVariantId: 'RS_NIFTY',
          championVariantId: 'DEFAULT',
          status: 'INSUFFICIENT_SAMPLE',
          conditions: [],
          notes: [],
          dataLimitations: [],
        },
      }
      const wrapper = mountView()
      await flushPromises()

      expect(wrapper.text()).toContain('Insufficient sample')
      expect(wrapper.text()).toContain('Not enough data yet to judge')
      expect(wrapper.text()).toContain('not a rejection')
    })

    it('shows a loading state for a variant whose eligibility check is in flight', async () => {
      storeMock.state.strategies = [champion, shadow]
      storeMock.state.promotionEligibility.RS_NIFTY = {
        loading: true,
        error: null,
        result: null,
      }
      const wrapper = mountView()
      await flushPromises()

      expect(wrapper.text()).toContain('Checking promotion eligibility...')
    })

    it('shows an error state for a variant whose eligibility check failed', async () => {
      storeMock.state.strategies = [champion, shadow]
      storeMock.state.promotionEligibility.RS_NIFTY = {
        loading: false,
        error: 'The backend could not be reached.',
        result: null,
      }
      const wrapper = mountView()
      await flushPromises()

      expect(wrapper.text()).toContain('The backend could not be reached.')
    })
  })
})
