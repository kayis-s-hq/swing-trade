import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import CandidateExplorerView from './CandidateExplorerView.vue'

const api = vi.hoisted(() => ({
  startCandidateScan: vi.fn(),
  getCandidateScan: vi.fn(),
  getCandidateScanResults: vi.fn(),
  getCandidateScanHistory: vi.fn(),
  cancelCandidateScan: vi.fn(),
  openCandidateScanStream: vi.fn(() => null),
}))

vi.mock('../api/candidateScan', () => api)

describe('CandidateExplorerView', () => {
  it('renders an empty state and starts a scan', async () => {
    api.getCandidateScanHistory.mockResolvedValue([])
    const started = {
      runId: 'run-1', status: 'RUNNING', totalSymbols: 2650,
      completedSymbols: 0, failedSymbols: 0, qualifiedSymbols: 0,
      startedAt: '2026-08-31T00:00:00',
    }
    api.startCandidateScan.mockResolvedValue(started)
    api.getCandidateScan.mockResolvedValue(started)
    api.getCandidateScanResults.mockResolvedValue([])

    const wrapper = mount(CandidateExplorerView)
    await flushPromises()

    expect(wrapper.text()).toContain('Find the next pilot symbol')
    await wrapper.get('button').trigger('click')
    await flushPromises()

    expect(api.startCandidateScan).toHaveBeenCalledOnce()
    expect(wrapper.text()).toContain('Cancel scan')
    expect(wrapper.text()).toContain('RUNNING')
  })

  it('renders qualified candidates and activation state', async () => {
    api.getCandidateScanHistory.mockResolvedValue([{
      runId: 'run-2', status: 'COMPLETED', totalSymbols: 2,
      completedSymbols: 2, failedSymbols: 0, qualifiedSymbols: 1,
      startedAt: '2026-08-31T00:00:00',
    }])
    api.getCandidateScan.mockResolvedValue({
      runId: 'run-2', status: 'COMPLETED', totalSymbols: 2,
      completedSymbols: 2, failedSymbols: 0, qualifiedSymbols: 1,
      startedAt: '2026-08-31T00:00:00',
    })
    api.getCandidateScanResults.mockResolvedValue([{
      runId: 'run-2', symbol: 'JSWSTEEL', dataStatus: 'READY', candleCount: 738,
      signalType: 'BUY', totalTrades: 10, winRate: 50, totalReturn: 4.2,
      maxDrawdownPct: 3.1, qualified: true, activated: true,
      reason: 'BUY and backtest gate passed', createdAt: '2026-08-31T00:00:00',
    }])

    const wrapper = mount(CandidateExplorerView)
    await flushPromises()

    expect(wrapper.text()).toContain('JSWSTEEL')
    expect(wrapper.text()).toContain('Activated')
    expect(wrapper.text()).toContain('50.0%')
  })
})
