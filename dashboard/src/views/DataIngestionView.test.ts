import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { reactive } from 'vue'
import type { IngestionStatus, PullProgress } from '../api/types'
import { AppError, type AppErrorKind } from '../errors/appError'
import DataIngestionView from './DataIngestionView.vue'

const apiMocks = vi.hoisted(() => ({
  getIngestionStatus: vi.fn(),
  triggerDataPull: vi.fn(),
  getPullProgress: vi.fn(),
  cancelDataPull: vi.fn(),
  getFyersStatus: vi.fn(),
  getFyersLoginUrl: vi.fn(),
  setBroker: vi.fn(),
}))

const settingsMocks = vi.hoisted(() => ({
  getSettings: vi.fn(() => ({ selectedBroker: 'yahoo' })),
  loadSettings: vi.fn(),
}))

vi.mock('../api/client', () => apiMocks)
vi.mock('../api/ingestion', () => apiMocks)
vi.mock('../stores/settings', () => settingsMocks)

const ingestionStatus: IngestionStatus = {
  symbol: 'RELIANCE',
  name: 'Reliance Industries',
  exchange: 'NSE',
  isActive: true,
  candleCount: 120,
  earliestCandleDate: '2026-01-01',
  lastCandleDate: '2026-08-25',
  lastSyncedAt: '2026-08-25T18:00:00',
  hasData: true,
  dataQuality: 'excellent',
}

const runningProgress: PullProgress = {
  pullId: 'pull-123',
  status: 'running',
  total: 10,
  completed: 4,
  failed: 0,
  currentSymbol: 'RELIANCE',
  percentComplete: 40,
}

function appError(
  message: string,
  options: { kind?: AppErrorKind; outcomeUnknown?: boolean } = {}
): AppError {
  return new AppError({
    message,
    kind: options.kind ?? 'network',
    outcomeUnknown: options.outcomeUnknown,
    retryable: !options.outcomeUnknown,
  })
}

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((resolver) => {
    resolve = resolver
  })
  return { promise, resolve }
}

function mountView(): VueWrapper {
  return mount(DataIngestionView, {
    global: {
      stubs: {
        ErrorBoundary: {
          props: ['error'],
          template: '<div><slot /><slot v-if="error" name="error" /></div>',
        },
        LoadingSpinner: true,
      },
    },
  })
}

function button(wrapper: VueWrapper, label: string) {
  const match = wrapper.findAll('button').find((candidate) => candidate.text().trim() === label)
  if (!match) throw new Error(`Button not found: ${label}`)
  return match
}

beforeEach(() => {
  vi.clearAllMocks()
  vi.useFakeTimers()
  vi.stubGlobal('alert', vi.fn())
  settingsMocks.getSettings.mockReturnValue({ selectedBroker: 'yahoo' })
  apiMocks.getIngestionStatus.mockResolvedValue([ingestionStatus])
  apiMocks.getFyersStatus.mockResolvedValue({ connected: false, clientId: '' })
  apiMocks.getPullProgress.mockResolvedValue(runningProgress)
  apiMocks.cancelDataPull.mockResolvedValue('Cancellation requested')
  apiMocks.triggerDataPull.mockResolvedValue({
    pullId: 'pull-new',
    message: 'Data pull started',
  })
  apiMocks.setBroker.mockResolvedValue({ selectedBroker: 'yahoo' })
  settingsMocks.loadSettings.mockResolvedValue([])
})

afterEach(() => {
  vi.useRealTimers()
  vi.unstubAllGlobals()
})

describe('DataIngestionView — pull cancellation and polling', () => {
  it('continues polling while cancellation is pending and stops only after confirmation', async () => {
    const cancellation = deferred<string>()
    apiMocks.cancelDataPull.mockReturnValue(cancellation.promise)
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('40%')

    await button(wrapper, 'Cancel Pull').trigger('click')
    await vi.advanceTimersByTimeAsync(1000)
    await flushPromises()

    expect(apiMocks.cancelDataPull).toHaveBeenCalledTimes(1)
    expect(apiMocks.getPullProgress).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('Cancel Pull')
    expect(wrapper.text()).toContain('40%')

    cancellation.resolve('Cancellation requested')
    await flushPromises()
    const progressCallsAfterConfirmation = apiMocks.getPullProgress.mock.calls.length
    await vi.advanceTimersByTimeAsync(3000)
    await flushPromises()

    expect(apiMocks.getPullProgress).toHaveBeenCalledTimes(progressCallsAfterConfirmation)
    expect(wrapper.text()).not.toContain('Cancel Pull')
    expect(wrapper.text()).toContain('Pull Yahoo Finance Historical Data')
    wrapper.unmount()
  })

  it('retains progress after ambiguous cancellation and offers status refresh, never cancellation retry', async () => {
    apiMocks.cancelDataPull.mockRejectedValue(
      appError('Connection closed after cancellation request', { outcomeUnknown: true })
    )
    const wrapper = mountView()
    await flushPromises()
    apiMocks.getPullProgress.mockClear()

    await button(wrapper, 'Cancel Pull').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('40%')
    expect(wrapper.text()).toMatch(
      /cancellation.*could not be confirmed|cancellation.*couldn.t be confirmed/i
    )
    expect(
      wrapper.findAll('button').some((candidate) => /retry cancel/i.test(candidate.text()))
    ).toBe(false)
    expect(globalThis.alert).not.toHaveBeenCalled()

    await button(wrapper, 'Refresh pull status').trigger('click')
    await flushPromises()

    expect(apiMocks.getPullProgress).toHaveBeenCalledTimes(1)
    expect(apiMocks.cancelDataPull).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })

  it('keeps the last progress stale, deduplicates polling warnings, and retries immediately', async () => {
    const updatedProgress: PullProgress = {
      ...runningProgress,
      completed: 6,
      currentSymbol: 'TCS',
      percentComplete: 60,
    }
    apiMocks.getPullProgress
      .mockResolvedValueOnce(runningProgress)
      .mockRejectedValueOnce(appError('Polling unavailable'))
      .mockRejectedValueOnce(appError('Polling unavailable'))
      .mockResolvedValueOnce(updatedProgress)
    const wrapper = mountView()
    await flushPromises()

    await vi.advanceTimersByTimeAsync(1000)
    await flushPromises()
    await vi.advanceTimersByTimeAsync(1000)
    await flushPromises()

    expect(wrapper.text()).toContain('40%')
    expect(wrapper.text()).toMatch(/progress.*stale|last progress.*out of date/i)
    const pollingWarnings = wrapper
      .findAll('[role="alert"], [role="status"]')
      .filter((element) =>
        /progress refresh failed|couldn.t refresh pull progress/i.test(element.text())
      )
    expect(pollingWarnings).toHaveLength(1)
    expect(globalThis.alert).not.toHaveBeenCalled()

    await button(wrapper, 'Retry now').trigger('click')
    await flushPromises()

    expect(apiMocks.getPullProgress).toHaveBeenCalledTimes(4)
    expect(wrapper.text()).toContain('60%')
    expect(wrapper.text()).not.toMatch(/progress.*stale|last progress.*out of date/i)
    wrapper.unmount()
  })
})

describe('DataIngestionView — persisted broker selection', () => {
  it('loads settings before showing the connection and pull source', async () => {
    const state = reactive({ selectedBroker: 'fyers' })
    settingsMocks.getSettings.mockReturnValue(state)
    settingsMocks.loadSettings.mockImplementation(async () => {
      state.selectedBroker = 'yahoo'
      return []
    })
    apiMocks.getPullProgress.mockResolvedValue({ status: 'idle' })

    const wrapper = mountView()
    await flushPromises()

    expect(settingsMocks.loadSettings).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('Yahoo Finance')
    expect(wrapper.text()).toContain('Free data — no authentication required')
    expect(wrapper.text()).not.toContain('Fyers Connection')
    expect(wrapper.text()).not.toContain('Not connected — authenticate to pull data')
    expect(apiMocks.setBroker).toHaveBeenCalledWith('yahoo')
    wrapper.unmount()
  })
})
