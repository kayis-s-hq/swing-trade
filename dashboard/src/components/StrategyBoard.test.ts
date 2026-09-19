import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { Signal } from '../api/types'

const apiMocks = vi.hoisted(() => ({ getStrategies: vi.fn() }))
vi.mock('../api/strategies', () => apiMocks)

import StrategyBoard from './StrategyBoard.vue'

const variant = (variantId: string, mode: string) => ({
  variantId,
  version: 1,
  strategyType: 'BREAKOUT',
  mode,
  current: true,
})

const signal = (id: string, strategy: string, direction: Signal['direction'], timestamp: string): Signal => ({
  id, symbol: 'SBIN', direction, confidence: 0.7, reason: '', entryPrice: 1, stopLoss: 1, target: 1,
  riskReward: 1, timestamp, status: 'ACTIVE', strategy,
})

const stubs = { 'router-link': { template: '<a><slot /></a>' } }

describe('StrategyBoard', () => {
  beforeEach(() => {
    apiMocks.getStrategies.mockReset()
  })

  it('lists active variants with mode and latest-date BUY counts, hiding OFF variants', async () => {
    apiMocks.getStrategies.mockResolvedValue([
      variant('breakout-v1', 'SHADOW'),
      variant('pullback-v1', 'BACKTEST_ONLY'),
      variant('old-v1', 'OFF'),
    ])
    const wrapper = mount(StrategyBoard, {
      props: {
        signals: [
          signal('1', 'breakout-v1', 'BUY', '2026-09-18T09:00:00'),
          signal('2', 'breakout-v1', 'BUY', '2026-09-17T09:00:00'),
          signal('3', 'pullback-v1', 'SELL', '2026-09-18T09:00:00'),
        ],
      },
      global: { stubs },
    })
    await flushPromises()
    const rows = wrapper.findAll('[data-testid="strategy-board-row"]')
    expect(rows).toHaveLength(2)
    expect(rows[0].text()).toContain('breakout-v1')
    expect(rows[0].text()).toContain('1 BUY')
    expect(rows[0].text()).toContain('SHADOW')
    expect(rows[1].text()).toContain('0 BUY')
  })

  it('shows an empty state when the strategies request fails', async () => {
    apiMocks.getStrategies.mockImplementation(() => Promise.reject(new Error('down')))
    const wrapper = mount(StrategyBoard, { props: { signals: [] }, global: { stubs } })
    await flushPromises()
    expect(wrapper.text()).toContain('No strategy variants configured')
  })
})
