import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { Position } from '../api/types'
import { AppError, type AppErrorKind } from '../errors/appError'
import PositionsView from './PositionsView.vue'

const apiMocks = vi.hoisted(() => ({
  getPositions: vi.fn(),
  getClosedPositions: vi.fn(),
  closePosition: vi.fn(),
  executeTrade: vi.fn(),
}))

vi.mock('../api/client', () => apiMocks)
vi.mock('../api/positions', () => apiMocks)

const openPosition: Position = {
  id: 'position-1',
  symbol: 'RELIANCE',
  entryPrice: 2500,
  currentPrice: 2550,
  quantity: 4,
  status: 'OPEN',
  pnl: 200,
  pnlPercent: 2,
  entryDate: '2026-08-25T10:00:00',
  stopLoss: 2400,
  target: 2700,
  direction: 'LONG',
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
  return mount(PositionsView, {
    attachTo: document.body,
    global: {
      stubs: {
        Teleport: true,
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

async function openNewPositionForm(wrapper: VueWrapper) {
  await button(wrapper, 'New Position').trigger('click')
  await wrapper.get('input[placeholder="e.g. RELIANCE"]').setValue('RELIANCE')

  const numericInputs = wrapper.findAll('form input[type="number"]')
  await numericInputs[0].setValue('4')
  await numericInputs[1].setValue('2500')
  await numericInputs[2].setValue('2400')
  await numericInputs[3].setValue('2700')
  await wrapper.get('textarea').setValue('Breakout above resistance')
}

beforeEach(() => {
  vi.clearAllMocks()
  apiMocks.getPositions.mockResolvedValue([])
  apiMocks.getClosedPositions.mockResolvedValue([])
  apiMocks.executeTrade.mockResolvedValue(openPosition)
  apiMocks.closePosition.mockResolvedValue({ ...openPosition, status: 'CLOSED' })
})

afterEach(() => {
  document.body.replaceChildren()
})

describe('PositionsView — mutation correctness', () => {
  it('keeps every new-position field and focuses an inline error when creation fails', async () => {
    apiMocks.executeTrade.mockRejectedValue(appError('Risk limit exceeded'))
    const wrapper = mountView()
    await flushPromises()
    await openNewPositionForm(wrapper)

    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(apiMocks.executeTrade).toHaveBeenCalledWith(
      expect.objectContaining({
        symbol: 'RELIANCE',
        quantity: 4,
        price: 2500,
        stopPrice: 2400,
        target: 2700,
        entryReason: 'Breakout above resistance',
      })
    )
    expect(wrapper.get('h2').text()).toBe('New Position')
    expect(wrapper.get('input[placeholder="e.g. RELIANCE"]').element).toHaveProperty(
      'value',
      'RELIANCE'
    )
    expect(wrapper.findAll('form input[type="number"]')[3].element).toHaveProperty('value', '2700')
    expect(wrapper.get('textarea').element).toHaveProperty('value', 'Breakout above resistance')

    const alert = wrapper.get('[role="alert"]')
    expect(alert.text()).toMatch(/couldn.t create position/i)
    expect(alert.text()).toContain('Risk limit exceeded')
    expect(document.activeElement).toBe(alert.element)
    wrapper.unmount()
  })

  it('keeps the close target and exit reason when closing fails', async () => {
    apiMocks.getPositions.mockResolvedValue([openPosition])
    apiMocks.closePosition.mockRejectedValue(appError('Position is locked', { kind: 'conflict' }))
    const wrapper = mountView()
    await flushPromises()

    await button(wrapper, 'Close').trigger('click')
    const reasonInput = wrapper.get('input[placeholder="e.g. Stop loss hit, target reached"]')
    await reasonInput.setValue('Manual risk reduction')
    await wrapper.findAll('form')[0].trigger('submit')
    await flushPromises()

    expect(apiMocks.closePosition).toHaveBeenCalledWith('RELIANCE', 'Manual risk reduction')
    expect(wrapper.text()).toContain('Close Position')
    expect(wrapper.text()).toContain('RELIANCE')
    expect(reasonInput.element).toHaveProperty('value', 'Manual risk reduction')
    const alert = wrapper.get('[role="alert"]')
    expect(alert.text()).toMatch(/couldn.t close position/i)
    expect(alert.text()).toContain('Position is locked')
    expect(document.activeElement).toBe(alert.element)
    wrapper.unmount()
  })

  it('offers a positions refresh instead of retrying an order whose outcome is unknown', async () => {
    apiMocks.executeTrade.mockRejectedValue(
      appError('Connection closed after submission', {
        kind: 'network',
        outcomeUnknown: true,
      })
    )
    const wrapper = mountView()
    await flushPromises()
    await openNewPositionForm(wrapper)
    apiMocks.getPositions.mockClear()
    apiMocks.getClosedPositions.mockClear()

    await wrapper.get('form').trigger('submit')
    await flushPromises()

    const alert = wrapper.get('[role="alert"]')
    expect(alert.text()).toMatch(/order.*could not be confirmed|order.*couldn.t be confirmed/i)
    expect(wrapper.get('input[placeholder="e.g. RELIANCE"]').element).toHaveProperty(
      'value',
      'RELIANCE'
    )
    expect(
      wrapper.findAll('button').some((candidate) => /retry order/i.test(candidate.text()))
    ).toBe(false)

    await button(wrapper, 'Refresh positions').trigger('click')
    await flushPromises()

    expect(apiMocks.getPositions).toHaveBeenCalledTimes(1)
    expect(apiMocks.getClosedPositions).toHaveBeenCalledTimes(1)
    expect(apiMocks.executeTrade).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })

  it('reports confirmed creation and warns that the retained list is stale when refresh fails', async () => {
    apiMocks.getPositions
      .mockResolvedValueOnce([])
      .mockRejectedValueOnce(appError('Positions refresh failed', { kind: 'network' }))
    apiMocks.getClosedPositions.mockResolvedValue([])
    const wrapper = mountView()
    await flushPromises()
    await openNewPositionForm(wrapper)

    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(apiMocks.executeTrade).toHaveBeenCalledTimes(1)
    expect(wrapper.findAll('h2').some((heading) => heading.text() === 'New Position')).toBe(false)
    expect(wrapper.text()).toMatch(/position (created|opened)/i)
    expect(wrapper.text()).toMatch(/positions.*(stale|out of date)|refresh failed/i)
    wrapper.unmount()
  })
})
