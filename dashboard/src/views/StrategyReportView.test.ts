import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { StrategyReport } from '../api/strategyReport'

const apiMocks = vi.hoisted(() => ({
  getStrategyReport: vi.fn(),
  getArbitrationComparison: vi.fn(),
}))
vi.mock('../api/strategyReport', () => apiMocks)

import StrategyReportView from './StrategyReportView.vue'

const report: StrategyReport = {
  from: '2026-08-20',
  to: '2026-09-19',
  totals: {
    tournaments: 4,
    executed: 2,
    blocked: 1,
    pending: 1,
    selectedTradesClosed: 2,
    selectedPnl: 60,
    avgRegretPct: 7,
  },
  variants: [
    {
      variantId: 'breakout-v1',
      strategyType: 'BREAKOUT',
      mode: 'SHADOW',
      signalsGenerated: 6,
      buySignals: 3,
      timesSelected: 3,
      selectionRatePct: 75,
      selectedTrades: 2,
      selectedWins: 1,
      selectedWinRatePct: 50,
      selectedPnl: 60,
      shadowTrades: 4,
      shadowWins: 3,
      shadowWinRatePct: 75,
      shadowPnl: 210,
      agreementRatePct: 40,
      avgRegretPct: null,
      sentimentVetoes: 1,
      llmVetoes: 0,
      otherBlocks: 0,
    },
  ],
}

describe('StrategyReportView', () => {
  beforeEach(() => {
    apiMocks.getStrategyReport.mockReset()
    apiMocks.getArbitrationComparison.mockReset()
    apiMocks.getArbitrationComparison.mockResolvedValue({
      from: '2026-08-20',
      to: '2026-09-19',
      tournaments: 0,
      rules: [],
    })
  })

  it('compares arbitration rules when tournaments have been recorded', async () => {
    apiMocks.getStrategyReport.mockResolvedValue(report)
    apiMocks.getArbitrationComparison.mockResolvedValue({
      from: '2026-08-20',
      to: '2026-09-19',
      tournaments: 4,
      rules: [
        {
          rule: 'HIGHEST_CONFIDENCE',
          decisions: 4,
          decisionsWithOutcome: 3,
          avgReturnPct: -1.5,
          winRatePct: 33.3,
          differsFromHighestConfidence: 0,
        },
        {
          rule: 'EVIDENCE_RANKED',
          decisions: 4,
          decisionsWithOutcome: 3,
          avgReturnPct: 2.2,
          winRatePct: 66.7,
          differsFromHighestConfidence: 2,
        },
      ],
    })
    const wrapper = mount(StrategyReportView)
    await flushPromises()
    const section = wrapper.find('[aria-label="Arbitration rules"]')
    expect(section.text()).toContain('EVIDENCE_RANKED')
    expect(section.text()).toContain('2.2%')
    expect(section.text()).toContain('66.7%')
  })

  it('renders totals, wins-by-variant and the leaderboard row', async () => {
    apiMocks.getStrategyReport.mockResolvedValue(report)
    const wrapper = mount(StrategyReportView)
    await flushPromises()
    const text = wrapper.text()
    expect(text).toContain('Strategy report')
    expect(text).toContain('breakout-v1')
    expect(text).toContain('3 (75.0%)')
    expect(text).toContain('₹210')
    expect(wrapper.find('[aria-label="Strategy leaderboard"]').exists()).toBe(true)
    expect(wrapper.find('[aria-label="Wins by variant"]').exists()).toBe(true)
  })

  it('shows an error message when loading fails', async () => {
    apiMocks.getStrategyReport.mockRejectedValue(new Error('boom'))
    const wrapper = mount(StrategyReportView)
    await flushPromises()
    expect(wrapper.find('[role="alert"]').text()).toContain('could not be loaded')
  })

  it('shows empty states when there are no winners or variants', async () => {
    apiMocks.getStrategyReport.mockResolvedValue({
      ...report,
      variants: [],
      totals: { ...report.totals, tournaments: 0 },
    })
    const wrapper = mount(StrategyReportView)
    await flushPromises()
    expect(wrapper.text()).toContain('No tournament winners')
    expect(wrapper.text()).toContain('No active variants')
  })
})
