import { describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

const apiMocks = vi.hoisted(() => ({ listPortfolioPositions: vi.fn() }))
vi.mock('../api/portfolios', () => apiMocks)

import ShadowBookPanel from './ShadowBookPanel.vue'

const stubs = { 'router-link': { template: '<a><slot /></a>' } }

describe('ShadowBookPanel', () => {
  it('summarises open/closed counts and realized P&L', async () => {
    apiMocks.listPortfolioPositions.mockResolvedValue([
      {
        portfolioId: 'selected',
        symbol: 'SBIN',
        entryDate: '2026-09-01',
        entryPrice: 100,
        stopLoss: 95,
        target: 110,
        quantity: 10,
        status: 'OPEN',
        exitDate: null,
        exitPrice: null,
        exitReason: null,
        pnl: null,
      },
      {
        portfolioId: 'selected',
        symbol: 'INFY',
        entryDate: '2026-08-01',
        entryPrice: 100,
        stopLoss: 95,
        target: 110,
        quantity: 10,
        status: 'CLOSED',
        exitDate: '2026-08-05',
        exitPrice: 110,
        exitReason: 'TARGET_HIT',
        pnl: 100,
      },
    ])
    const wrapper = mount(ShadowBookPanel, {
      props: { portfolioId: 'selected' },
      global: { stubs },
    })
    await flushPromises()
    expect(wrapper.text()).toContain('1 open · 1 closed')
    expect(wrapper.text()).toContain('SHADOW')
    expect(wrapper.text()).toContain('TARGET_HIT')
  })

  it('shows an error message when loading fails', async () => {
    apiMocks.listPortfolioPositions.mockRejectedValue(new Error('boom'))
    const wrapper = mount(ShadowBookPanel, { props: { portfolioId: 'x' }, global: { stubs } })
    await flushPromises()
    expect(wrapper.text()).toContain('Could not load')
  })
})
