import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import GateEffectivenessView from './GateEffectivenessView.vue'

const apiMock = vi.hoisted(() => ({
  getGateEffectiveness: vi.fn(),
}))

vi.mock('../api/signals', () => apiMock)

describe('GateEffectivenessView', () => {
  beforeEach(() => {
    apiMock.getGateEffectiveness.mockReset()
  })

  it('loads the selected gate and renders decision metrics', async () => {
    apiMock.getGateEffectiveness.mockResolvedValue({
      from: '2026-01-01',
      to: '2026-01-31',
      symbol: null,
      strategy: null,
      regime: null,
      auditCount: 2,
      verdicts: {
        ALLOW: {
          count: 2,
          meanForwardReturnPct: { '1': 1.25, '5': 2.5 },
          returnObservationCounts: { '1': 2, '5': 2 },
          realizedPnl: 150.5,
        },
      },
      byStrategy: { DEFAULT: {} },
      byRegime: {},
    })

    const wrapper = mount(GateEffectivenessView, {
      global: { stubs: { LoadingSpinner: true } },
    })
    await flushPromises()

    expect(apiMock.getGateEffectiveness).toHaveBeenCalledWith(
      expect.objectContaining({ gate: 'SENTIMENT' })
    )
    expect(wrapper.text()).toContain('Gate effectiveness')
    expect(wrapper.text()).toContain('ALLOW')
    expect(wrapper.text()).toContain('1.25%')
    expect(wrapper.text()).toContain('Rs. 150.50')
    wrapper.unmount()
  })
})
