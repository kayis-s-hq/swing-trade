import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { Position, Signal } from '../api/types'
import { AppError, type AppErrorKind } from '../errors/appError'
import SignalsView from './SignalsView.vue'

const signalApiMocks = vi.hoisted(() => ({
  getSignals: vi.fn(),
  generateAllSignalsStream: vi.fn(),
  clearAllSignals: vi.fn(),
  clearSignalsForSymbol: vi.fn(),
}))

const positionApiMocks = vi.hoisted(() => ({
  executeTrade: vi.fn(),
}))

const settingsMocks = vi.hoisted(() => ({
  getSettings: vi.fn(() => ({
    tradingConfig: { allocationPerPosition: 100000 },
  })),
}))

vi.mock('../api/signals', () => signalApiMocks)
vi.mock('../api/selections', () => ({
  listSignalSelections: vi.fn().mockResolvedValue([]),
  latestTournament: (rows: unknown[]) => rows,
}))
vi.mock('../api/positions', () => positionApiMocks)
vi.mock('../stores/settings', () => settingsMocks)

const signals: Signal[] = [
  {
    id: 'signal-reliance',
    symbol: 'RELIANCE',
    direction: 'BUY',
    confidence: 0.9,
    reason: 'Breakout confirmed',
    entryPrice: 2500,
    stopLoss: 2400,
    target: 2700,
    riskReward: 2,
    timestamp: '2026-08-26T09:00:00',
    status: 'ACTIVE',
  },
  {
    id: 'signal-tcs',
    symbol: 'TCS',
    direction: 'BUY',
    confidence: 0.8,
    reason: 'Momentum confirmed',
    entryPrice: 4000,
    stopLoss: 3900,
    target: 4200,
    riskReward: 2,
    timestamp: '2026-08-26T09:01:00',
    status: 'ACTIVE',
  },
  {
    id: 'signal-infy',
    symbol: 'INFY',
    direction: 'SELL',
    confidence: 0.75,
    reason: 'Support failed',
    entryPrice: 1800,
    stopLoss: 1850,
    target: 1700,
    riskReward: 2,
    timestamp: '2026-08-26T09:02:00',
    status: 'ACTIVE',
  },
]

const executedPosition: Position = {
  id: 'position-1',
  symbol: 'RELIANCE',
  entryPrice: 2500,
  currentPrice: 2500,
  quantity: 40,
  status: 'OPEN',
  pnl: 0,
  pnlPercent: 0,
  entryDate: '2026-08-26T09:05:00',
}

function appError(
  message: string,
  options: { kind?: AppErrorKind; outcomeUnknown?: boolean } = {}
): AppError {
  return new AppError({
    message,
    kind: options.kind ?? 'validation',
    outcomeUnknown: options.outcomeUnknown,
    retryable: false,
  })
}

function mountView(): VueWrapper {
  return mount(SignalsView, {
    global: {
      stubs: {
        ErrorBoundary: {
          props: ['error'],
          template: '<div><slot /><slot v-if="error" name="error" /></div>',
        },
        LoadingSpinner: true,
        SignalCard: {
          props: ['signal', 'selected'],
          emits: ['toggle-selection'],
          template:
            '<article :data-symbol="signal.symbol">{{ signal.symbol }}<input type="checkbox" :checked="selected" @change="$emit(\'toggle-selection\')" /></article>',
        },
      },
    },
  })
}

function button(wrapper: VueWrapper, label: string) {
  const match = wrapper.findAll('button').find((candidate) => candidate.text().trim() === label)
  if (!match) throw new Error(`Button not found: ${label}`)
  return match
}

async function selectSignalIndexes(wrapper: VueWrapper, indexes: number[]) {
  const signalCheckboxes = wrapper.findAll('input[type="checkbox"]').slice(1)
  for (const index of indexes) await signalCheckboxes[index].trigger('change')
}

beforeEach(() => {
  vi.clearAllMocks()
  vi.useFakeTimers()
  signalApiMocks.getSignals.mockResolvedValue(signals)
  signalApiMocks.clearAllSignals.mockResolvedValue({ cleared: signals.length })
  signalApiMocks.clearSignalsForSymbol.mockResolvedValue({ cleared: 1 })
  positionApiMocks.executeTrade.mockResolvedValue(executedPosition)
  vi.stubGlobal(
    'confirm',
    vi.fn(() => true)
  )
  vi.stubGlobal('alert', vi.fn())
})

afterEach(() => {
  vi.useRealTimers()
  vi.unstubAllGlobals()
})

describe('SignalsView — partial batch outcomes', () => {
  it('counts only confirmed executions and keeps failed and unknown signals selected with reasons', async () => {
    positionApiMocks.executeTrade.mockImplementation(({ symbol }: { symbol: string }) => {
      if (symbol === 'RELIANCE') return Promise.resolve(executedPosition)
      if (symbol === 'TCS') return Promise.reject(appError('Risk limit exceeded'))
      return Promise.reject(
        appError('Connection dropped after submission', {
          kind: 'network',
          outcomeUnknown: true,
        })
      )
    })
    const wrapper = mountView()
    await flushPromises()
    await selectSignalIndexes(wrapper, [0, 1, 2])

    await button(wrapper, 'Execute 3').trigger('click')
    await flushPromises()

    expect(positionApiMocks.executeTrade).toHaveBeenCalledTimes(3)
    expect(wrapper.text()).toContain('1 succeeded, 1 failed, 1 unconfirmed')
    expect(wrapper.text()).toContain('TCS: Risk limit exceeded')
    expect(wrapper.text()).toMatch(/INFY:.*could not be confirmed|INFY:.*couldn.t be confirmed/i)
    expect(wrapper.text()).not.toContain('Connection dropped after submission')
    expect(wrapper.text()).toContain('2 selected')

    const signalCheckboxes = wrapper.findAll('input[type="checkbox"]').slice(1)
    expect((signalCheckboxes[0].element as HTMLInputElement).checked).toBe(false)
    expect((signalCheckboxes[1].element as HTMLInputElement).checked).toBe(true)
    expect((signalCheckboxes[2].element as HTMLInputElement).checked).toBe(true)
    expect(
      wrapper.findAll('button').some((candidate) => /retry order/i.test(candidate.text()))
    ).toBe(false)
    expect(wrapper.text()).toContain('Refresh positions')
    wrapper.unmount()
  })

  it('removes only confirmed clears and retains each failed signal and selection', async () => {
    signalApiMocks.getSignals
      .mockResolvedValueOnce(signals.slice(0, 2))
      .mockResolvedValue(signals.slice(1, 2))
    signalApiMocks.clearSignalsForSymbol.mockImplementation((symbol: string) => {
      if (symbol === 'RELIANCE') return Promise.resolve({ cleared: 1 })
      return Promise.reject(appError('Signal is still executing', { kind: 'conflict' }))
    })
    const wrapper = mountView()
    await flushPromises()
    await selectSignalIndexes(wrapper, [0, 1])

    await button(wrapper, 'Clear selected (2)').trigger('click')
    await flushPromises()

    expect(signalApiMocks.clearSignalsForSymbol).toHaveBeenCalledTimes(2)
    expect(wrapper.find('[data-symbol="RELIANCE"]').exists()).toBe(false)
    expect(wrapper.find('[data-symbol="TCS"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('1 cleared, 1 failed')
    expect(wrapper.text()).toContain('TCS: Signal is still executing')
    expect(wrapper.text()).toContain('1 selected')
    const remainingSignalCheckbox = wrapper.findAll('input[type="checkbox"]')[1]
    expect((remainingSignalCheckbox.element as HTMLInputElement).checked).toBe(true)
    wrapper.unmount()
  })

  it('preserves every signal when clearing all fails without using native alert', async () => {
    signalApiMocks.clearAllSignals.mockRejectedValue(
      appError('Signals changed on the server', { kind: 'conflict' })
    )
    const wrapper = mountView()
    await flushPromises()
    await button(wrapper, 'Clear all').trigger('click')
    await flushPromises()

    expect(wrapper.findAll('[data-symbol]').map((card) => card.attributes('data-symbol'))).toEqual([
      'RELIANCE',
      'TCS',
      'INFY',
    ])
    expect(wrapper.text()).not.toContain('selected')
    expect(wrapper.text()).toContain('Signals changed on the server')
    expect(globalThis.alert).not.toHaveBeenCalled()
    wrapper.unmount()
  })
})

describe('SignalsView — variant grouping', () => {
  const variantSignals: Signal[] = [
    { ...signals[0], id: 'v1', strategy: 'breakout-v1' },
    { ...signals[0], id: 'v2', strategy: 'pullback-v1', confidence: 0.7 },
    { ...signals[0], id: 'v3', strategy: 'squeeze-v1', direction: 'SELL' },
  ]

  it('keeps the card list as the default when no signal carries a strategy', async () => {
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.find('[aria-label="Signals by symbol"]').exists()).toBe(false)
    expect(wrapper.find('article').exists()).toBe(true)
  })

  it('groups variant signals by symbol with a consensus badge and toggles back to the list', async () => {
    signalApiMocks.getSignals.mockResolvedValue(variantSignals)
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.findAll('[data-testid="variant-chip"]')).toHaveLength(3)
    expect(wrapper.find('[data-testid="consensus-badge"]').text()).toBe('2/3 BUY')
    expect(wrapper.find('article').exists()).toBe(false)

    await button(wrapper, 'List').trigger('click')
    expect(wrapper.find('article').exists()).toBe(true)
    expect(wrapper.find('[aria-label="Signals by symbol"]').exists()).toBe(false)
  })
})
