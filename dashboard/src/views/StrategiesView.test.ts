import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import StrategiesView from './StrategiesView.vue'

const storeMock = vi.hoisted(() => ({
  state: {
    strategies: null as unknown[] | null,
    loading: false,
    error: null as Error | null,
    errorMessage: '',
  },
  load: vi.fn(),
  retry: vi.fn(),
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
    load: storeMock.load,
    retry: storeMock.retry,
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
})
