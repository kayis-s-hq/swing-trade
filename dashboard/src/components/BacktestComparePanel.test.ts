import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const apiMocks = vi.hoisted(() => ({
  listStrategyTypes: vi.fn(),
  listCurrentStrategies: vi.fn(),
  compareBacktests: vi.fn(),
}))
vi.mock('../api/strategies', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../api/strategies')>()),
  ...apiMocks,
}))

import BacktestComparePanel from './BacktestComparePanel.vue'

const variant = (variantId: string, mode: string) => ({
  variantId, version: 1, strategyType: 'BREAKOUT', mode, params: {}, overlays: {}, paramsHash: 'h',
  paperCapital: 100000, isCurrent: true, portfolioAction: null, notes: null, createdAt: '2026-09-19T00:00:00',
})

describe('BacktestComparePanel', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    Object.values(apiMocks).forEach((m) => m.mockReset())
    apiMocks.listStrategyTypes.mockResolvedValue([])
    apiMocks.listCurrentStrategies.mockResolvedValue([variant('breakout-v1', 'SHADOW'), variant('old-v1', 'OFF')])
  })

  it('lists non-OFF variants, runs a comparison and renders backend-shaped metrics', async () => {
    apiMocks.compareBacktests.mockResolvedValue({
      variants: [{
        variantId: 'breakout-v1', version: 1, strategyType: 'BREAKOUT', deflatedSharpeRatio: 0, trialsUsedForDsr: 1,
        walkForwardUnstable: false, sharpeStdDevAcrossFolds: null,
        folds: [{
          fold: 0, windowStart: '2023-09-01', windowEnd: '2026-09-19', equityCurve: [],
          metrics: { sharpeRatio: -2.6, totalTrades: 25, winRatePct: 40, winRateCiLowPct: 23.4, winRateCiHighPct: 59.3 },
        }],
      }],
    })
    const wrapper = mount(BacktestComparePanel, { props: { showPicker: true } })
    await flushPromises()

    const boxes = wrapper.findAll('[aria-label="Variants to compare"] input[type="checkbox"]')
    expect(boxes).toHaveLength(1)
    await boxes[0].setValue(true)
    await wrapper.find('button').trigger('click')
    await flushPromises()

    expect(apiMocks.compareBacktests).toHaveBeenCalledWith(
      expect.objectContaining({ variants: [{ id: 'breakout-v1' }], costsOn: true })
    )
    const text = wrapper.text()
    expect(text).toContain('-2.600')
    expect(text).toContain('25.000')
    expect(text).toContain('40.000 [23.400, 59.300]')
  })

  it('disables Run until a variant is selected', async () => {
    const wrapper = mount(BacktestComparePanel, { props: { showPicker: true } })
    await flushPromises()
    expect(wrapper.find('button').attributes('disabled')).toBeDefined()
  })
})
