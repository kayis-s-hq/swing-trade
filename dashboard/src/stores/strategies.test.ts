import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AppError } from '../errors/appError'
import { useStrategiesStore } from './strategies'

const api = vi.hoisted(() => ({
  getStrategies: vi.fn(),
  getStrategyTypes: vi.fn(),
  changeStrategyMode: vi.fn(),
  saveStrategyConfig: vi.fn(),
  fetchPromotionEligibility: vi.fn(),
}))
vi.mock('../api/strategies', () => api)

const request = {
  variantId: 'A',
  strategyType: 'RSI',
  params: { period: 14 },
  mode: 'SHADOW' as const,
  paperCapital: 1000,
}

describe('strategies store mutations', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    api.getStrategies.mockResolvedValue([])
  })

  it('loads strategy types', async () => {
    api.getStrategyTypes.mockResolvedValue([{ type: 'RSI' }])
    const store = useStrategiesStore()
    await store.loadStrategyTypes()
    expect(store.strategyTypes).toEqual([{ type: 'RSI' }])
    expect(store.strategyTypesError).toBe('')
  })

  it('records an error when strategy types fail to load', async () => {
    api.getStrategyTypes.mockRejectedValue(
      new AppError({ kind: 'server', message: 'Types unavailable.' })
    )
    const store = useStrategiesStore()
    await store.loadStrategyTypes()
    expect(store.strategyTypes).toBeNull()
    expect(store.strategyTypesError).toBe('Types unavailable.')
  })

  it('changes mode then refreshes the list', async () => {
    api.changeStrategyMode.mockResolvedValue({})
    const store = useStrategiesStore()
    const result = await store.changeMode('A', 'CHAMPION')
    expect(result).toEqual({ ok: true })
    expect(api.changeStrategyMode).toHaveBeenCalledWith('A', 'CHAMPION')
    expect(api.getStrategies).toHaveBeenCalled()
  })

  it('returns the server message and does not refresh when a mutation fails', async () => {
    api.saveStrategyConfig.mockRejectedValue(
      new AppError({ kind: 'validation', message: 'period must be between 2 and 50', status: 400 })
    )
    const store = useStrategiesStore()
    const result = await store.saveConfig(request)
    expect(result).toEqual({
      ok: false,
      message: 'period must be between 2 and 50',
      outcomeUnknown: false,
    })
    expect(api.getStrategies).not.toHaveBeenCalled()
  })

  it('flags an ambiguous outcome so the UI can ask for a refresh', async () => {
    api.changeStrategyMode.mockRejectedValue(
      new AppError({ kind: 'network', message: 'Connection lost.', outcomeUnknown: true })
    )
    const store = useStrategiesStore()
    const result = await store.changeMode('A', 'SHADOW')
    expect(result).toMatchObject({ ok: false, outcomeUnknown: true })
  })
})
