import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import CandidateExplorerView from './CandidateExplorerView.vue'

const api = vi.hoisted(() => ({
  startCandidateScan: vi.fn(),
  getCandidateScan: vi.fn(),
  getCandidateScanResults: vi.fn(),
  getCandidateScanHistory: vi.fn(),
  getCandidateScanSettings: vi.fn(),
  updateCandidateScanSettings: vi.fn(),
  cancelCandidateScan: vi.fn(),
  pauseCandidateScan: vi.fn(),
  resumeCandidateScan: vi.fn(),
  openCandidateScanStream: vi.fn(() => null),
}))

vi.mock('../api/candidateScan', () => api)

describe('CandidateExplorerView', () => {
  const defaultSettings = {
    'candidate-scan.min-win-rate': '45',
    'candidate-scan.min-total-return': '0',
    'candidate-scan.max-concurrent': '3',
    'candidate-scan.backfill-years': '3',
  }

  beforeEach(() => {
    vi.clearAllMocks()
    api.getCandidateScanSettings.mockResolvedValue(defaultSettings)
    api.openCandidateScanStream.mockReturnValue(null)
  })

  it('renders an empty state and starts a scan', async () => {
    api.getCandidateScanHistory.mockResolvedValue([])
    const started = {
      runId: 'run-1',
      status: 'RUNNING',
      totalSymbols: 2650,
      completedSymbols: 0,
      failedSymbols: 0,
      qualifiedSymbols: 0,
      startedAt: '2026-08-31T00:00:00',
    }
    api.startCandidateScan.mockResolvedValue(started)
    api.getCandidateScan.mockResolvedValue(started)
    api.getCandidateScanResults.mockResolvedValue({ items: [], total: 0, offset: 0, limit: 10 })

    const wrapper = mount(CandidateExplorerView)
    await flushPromises()

    expect(wrapper.text()).toContain('Find the next pilot symbol')
    await wrapper.get('button[data-test="scan-toggle"]').trigger('click')
    await flushPromises()

    expect(api.startCandidateScan).toHaveBeenCalledOnce()
    expect(wrapper.text()).toContain('Cancel scan')
    expect(wrapper.text()).toContain('RUNNING')
  })

  it('renders qualified candidates and activation state', async () => {
    api.getCandidateScanHistory.mockResolvedValue([
      {
        runId: 'run-2',
        status: 'COMPLETED',
        totalSymbols: 2,
        completedSymbols: 2,
        failedSymbols: 0,
        qualifiedSymbols: 1,
        startedAt: '2026-08-31T00:00:00',
      },
    ])
    api.getCandidateScan.mockResolvedValue({
      runId: 'run-2',
      status: 'COMPLETED',
      totalSymbols: 2,
      completedSymbols: 2,
      failedSymbols: 0,
      qualifiedSymbols: 1,
      startedAt: '2026-08-31T00:00:00',
    })
    api.getCandidateScanResults.mockResolvedValue({
      items: [
        {
          runId: 'run-2',
          symbol: 'JSWSTEEL',
          dataStatus: 'READY',
          candleCount: 738,
          signalType: 'BUY',
          totalTrades: 10,
          winRate: 50,
          totalReturn: 4.2,
          maxDrawdownPct: 3.1,
          qualified: true,
          activated: true,
          reason: 'BUY and backtest gate passed',
          createdAt: '2026-08-31T00:00:00',
        },
      ],
      total: 1,
      offset: 0,
      limit: 10,
    })

    const wrapper = mount(CandidateExplorerView)
    await flushPromises()

    expect(wrapper.text()).toContain('JSWSTEEL')
    expect(wrapper.text()).toContain('Activated')
    expect(wrapper.text()).toContain('50.0%')
  })

  it('pauses and resumes an active scan', async () => {
    const running = {
      runId: 'run-3',
      status: 'RUNNING',
      totalSymbols: 100,
      completedSymbols: 20,
      failedSymbols: 0,
      qualifiedSymbols: 1,
      startedAt: '2026-08-31T00:00:00',
    }
    const paused = { ...running, status: 'PAUSED' }
    api.getCandidateScanHistory.mockResolvedValue([running])
    api.getCandidateScan.mockResolvedValue(running)
    api.getCandidateScanResults.mockResolvedValue({ items: [], total: 0, offset: 0, limit: 10 })
    api.pauseCandidateScan.mockResolvedValue(paused)
    api.resumeCandidateScan.mockResolvedValue(running)

    const wrapper = mount(CandidateExplorerView)
    await flushPromises()

    await wrapper.get('button:nth-of-type(2)').trigger('click')
    await flushPromises()
    expect(api.pauseCandidateScan).toHaveBeenCalledWith('run-3')
    expect(wrapper.text()).toContain('Resume scan')
    expect(wrapper.text()).toContain('PAUSED')

    const resumeButton = wrapper.findAll('button').find((button) => button.text() === 'Resume scan')
    expect(resumeButton).toBeDefined()
    await resumeButton!.trigger('click')
    await flushPromises()
    expect(api.resumeCandidateScan).toHaveBeenCalledWith('run-3')
    expect(wrapper.text()).toContain('Pause scan')

    wrapper.unmount()
  })

  it('saves scan settings from the configuration panel', async () => {
    api.getCandidateScanHistory.mockResolvedValue([])
    api.updateCandidateScanSettings.mockResolvedValue(defaultSettings)

    const wrapper = mount(CandidateExplorerView)
    await flushPromises()

    const configureButton = wrapper
      .findAll('button')
      .find((button) => button.text() === 'Configure')
    await configureButton!.trigger('click')
    await wrapper.findAll('input')[2].setValue('6')
    const saveButton = wrapper.findAll('button').find((button) => button.text() === 'Save settings')
    await saveButton!.trigger('click')
    await flushPromises()

    expect(api.updateCandidateScanSettings).toHaveBeenCalledWith(
      expect.objectContaining({
        'candidate-scan.max-concurrent': '6',
      })
    )
    expect(wrapper.text()).toContain('Saved.')
  })
})
