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
    strategyTypes: null as unknown[] | null,
    strategyTypesError: '',
  },
  load: vi.fn(),
  loadStrategyTypes: vi.fn(),
  changeMode: vi.fn(),
  saveConfig: vi.fn(),
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
    get strategyTypes() {
      return storeMock.state.strategyTypes
    },
    get strategyTypesError() {
      return storeMock.state.strategyTypesError
    },
    loadStrategyTypes: storeMock.loadStrategyTypes,
    changeMode: storeMock.changeMode,
    saveConfig: storeMock.saveConfig,
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
    storeMock.state.strategyTypes = null
    storeMock.state.strategyTypesError = ''
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

describe('StrategiesView — mode, champion guard and params editor', () => {
  const champion = { ...config, variantId: 'DEFAULT', mode: 'CHAMPION', current: true }
  const shadow = {
    ...config,
    id: 2,
    variantId: 'RS',
    mode: 'SHADOW',
    current: true,
    params: { period: 14 },
  }
  const types = [
    {
      type: 'PRICE_ACTION',
      paramSchema: {
        params: [
          {
            name: 'period',
            type: 'INT',
            min: 2,
            max: 50,
            defaultValue: 14,
            description: 'Lookback',
            group: 'trend',
          },
          { name: 'useVolume', type: 'BOOL', defaultValue: false, group: 'filters' },
        ],
      },
      warmupBars: 20,
      requiredIndicators: [],
    },
  ]

  beforeEach(() => {
    storeMock.state.strategies = null
    storeMock.state.loading = false
    storeMock.state.error = null
    storeMock.state.strategyTypes = types
    storeMock.state.promotionEligibility = {}
    storeMock.changeMode.mockResolvedValue({ ok: true })
    storeMock.saveConfig.mockResolvedValue({ ok: true })
    vi.clearAllMocks()
    storeMock.changeMode.mockResolvedValue({ ok: true })
    storeMock.saveConfig.mockResolvedValue({ ok: true })
  })

  function btn(wrapper: ReturnType<typeof mountView>, label: string) {
    const match = wrapper.findAll('button').find((b) => b.text().trim() === label)
    if (!match) throw new Error(`no button ${label}`)
    return match
  }

  it('warns when there is no CHAMPION', async () => {
    storeMock.state.strategies = [shadow]
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.get('[data-testid="champion-warning"]').text()).toMatch(/no champion/i)
  })

  it('warns when more than one CHAMPION exists', async () => {
    storeMock.state.strategies = [champion, { ...shadow, mode: 'CHAMPION' }]
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.get('[data-testid="champion-warning"]').text()).toMatch(/2 champions/i)
  })

  it('shows no warning with exactly one CHAMPION', async () => {
    storeMock.state.strategies = [champion, shadow]
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.find('[data-testid="champion-warning"]').exists()).toBe(false)
  })

  it('demotes CHAMPION to SHADOW directly', async () => {
    storeMock.state.strategies = [champion, shadow]
    const wrapper = mountView()
    await flushPromises()
    await wrapper.get('[data-testid="mode-DEFAULT-SHADOW"]').trigger('click')
    expect(storeMock.changeMode).toHaveBeenCalledWith('DEFAULT', 'SHADOW')
  })

  it('requires confirmation before promoting to CHAMPION', async () => {
    storeMock.state.strategies = [champion, shadow]
    const wrapper = mountView()
    await flushPromises()
    await wrapper.get('[data-testid="mode-RS-CHAMPION"]').trigger('click')
    expect(storeMock.changeMode).not.toHaveBeenCalled()
    await btn(wrapper, 'Confirm promote').trigger('click')
    await flushPromises()
    expect(storeMock.changeMode).toHaveBeenCalledWith('RS', 'CHAMPION')
  })

  it('shows a mode-change failure as an alert', async () => {
    storeMock.changeMode.mockResolvedValue({
      ok: false,
      message: 'Not allowed',
      outcomeUnknown: false,
    })
    storeMock.state.strategies = [champion, shadow]
    const wrapper = mountView()
    await flushPromises()
    await wrapper.get('[data-testid="mode-DEFAULT-SHADOW"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-testid="mode-error-DEFAULT"]').text()).toContain('Not allowed')
  })

  it('renders a params form from the type schema and saves a new version', async () => {
    storeMock.state.strategies = [champion, shadow]
    const wrapper = mountView()
    await flushPromises()
    expect(storeMock.loadStrategyTypes).toHaveBeenCalled()

    await wrapper.get('[data-testid="edit-params-RS"]').trigger('click')
    const period = wrapper.get('[data-testid="param-period"]')
    expect(period.attributes('min')).toBe('2')
    expect(period.attributes('max')).toBe('50')
    expect((period.element as HTMLInputElement).value).toBe('14')
    expect(wrapper.find('[data-testid="param-useVolume"]').exists()).toBe(true)

    await period.setValue('21')
    await wrapper.get('[data-testid="param-useVolume"]').setValue(true)
    await wrapper.get('[data-testid="params-form"]').trigger('submit')
    await flushPromises()

    expect(storeMock.saveConfig).toHaveBeenCalledWith(
      expect.objectContaining({
        variantId: 'RS',
        strategyType: 'PRICE_ACTION',
        params: { period: 21, useVolume: true },
        mode: 'SHADOW',
        paperCapital: 500000,
      })
    )
    expect(wrapper.find('[data-testid="params-form"]').exists()).toBe(false)
  })

  it('shows server validation errors inline and keeps the form open', async () => {
    storeMock.saveConfig.mockResolvedValue({
      ok: false,
      message: 'period must be between 2 and 50',
      outcomeUnknown: false,
    })
    storeMock.state.strategies = [champion, shadow]
    const wrapper = mountView()
    await flushPromises()
    await wrapper.get('[data-testid="edit-params-RS"]').trigger('click')
    await wrapper.get('[data-testid="params-form"]').trigger('submit')
    await flushPromises()

    expect(wrapper.get('[data-testid="params-error"]').text()).toContain('period must be between')
    expect(wrapper.get('[data-testid="param-error-period"]').text()).toContain('period must be')
    expect(wrapper.find('[data-testid="param-error-useVolume"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="params-form"]').exists()).toBe(true)
  })

  it('explains when the strategy type schema is unavailable', async () => {
    storeMock.state.strategyTypes = []
    storeMock.state.strategies = [champion, shadow]
    const wrapper = mountView()
    await flushPromises()
    await wrapper.get('[data-testid="edit-params-RS"]').trigger('click')
    expect(wrapper.text()).toMatch(/no parameter schema/i)
  })
})
