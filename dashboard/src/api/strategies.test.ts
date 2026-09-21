import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  changeStrategyMode,
  fetchPromotionEligibility,
  getStrategies,
  getStrategyTypes,
  saveStrategyConfig,
} from './strategies'

const apiRequestMock = vi.hoisted(() => vi.fn())
vi.mock('./shared', () => ({ apiRequest: apiRequestMock }))

const strategy = {
  id: 1,
  variantId: 'DEFAULT',
  version: 2,
  strategyType: 'PRICE_ACTION',
  params: {},
  overlays: {},
  paramsHash: 'a'.repeat(64),
  mode: 'CHAMPION',
  paperCapital: 500000,
  current: true,
  notes: null,
  createdAt: '2026-09-16T10:00:00Z',
}

describe('getStrategies', () => {
  beforeEach(() => vi.clearAllMocks())

  it('requests current strategy configurations through the shared API transport', async () => {
    apiRequestMock.mockResolvedValue([strategy])
    const signal = new AbortController().signal

    await expect(getStrategies(signal)).resolves.toEqual([strategy])
    expect(apiRequestMock).toHaveBeenCalledWith('/strategy-configs', {
      method: 'GET',
      responseContract: 'envelope',
      signal,
      validate: expect.any(Function),
    })
  })
})

const promotionEligibilityResponse = {
  challengerVariantId: 'RS_NIFTY',
  championVariantId: 'DEFAULT',
  status: 'INSUFFICIENT_SAMPLE',
  conditions: [
    {
      name: 'sample_size',
      met: false,
      actualValue: '5d tenure, 0 closed trades',
      threshold: '>=60d and >=30 trades',
      note: null,
    },
  ],
  notes: ['Challenger has not accumulated enough SHADOW tenure or closed paper trades yet.'],
  dataLimitations: ['Closed-trade P&L is not yet attributable per strategy variant.'],
}

describe('fetchPromotionEligibility', () => {
  beforeEach(() => vi.clearAllMocks())

  it('requests the promotion-eligibility endpoint for the given variant', async () => {
    apiRequestMock.mockResolvedValue(promotionEligibilityResponse)
    const signal = new AbortController().signal

    await expect(fetchPromotionEligibility('RS_NIFTY', signal)).resolves.toEqual(
      promotionEligibilityResponse
    )
    expect(apiRequestMock).toHaveBeenCalledWith(
      '/strategy-configs/RS_NIFTY/promotion-eligibility',
      {
        method: 'GET',
        responseContract: 'envelope',
        signal,
        validate: expect.any(Function),
      }
    )
  })
})

describe('strategy types and mutations', () => {
  beforeEach(() => vi.clearAllMocks())

  it('loads strategy types from the envelope endpoint', async () => {
    const types = [
      {
        type: 'RSI',
        paramSchema: { params: [{ name: 'period', type: 'INT', min: 2, max: 50 }] },
        warmupBars: 20,
        requiredIndicators: ['RSI'],
      },
    ]
    apiRequestMock.mockResolvedValue(types)
    await expect(getStrategyTypes()).resolves.toEqual(types)
    expect(apiRequestMock).toHaveBeenCalledWith(
      '/strategy-types',
      expect.objectContaining({ method: 'GET', responseContract: 'envelope' })
    )
  })

  it('rejects a malformed strategy-types payload', async () => {
    apiRequestMock.mockImplementation(
      async (_p: string, o: { validate: (v: unknown) => boolean }) => {
        if (!o.validate([{ type: 1 }])) throw new Error('invalid')
      }
    )
    await expect(getStrategyTypes()).rejects.toThrow('invalid')
  })

  it('changes mode with PUT /mode and encodes the variant id', async () => {
    apiRequestMock.mockResolvedValue(strategy)
    await changeStrategyMode('A/B', 'SHADOW')
    expect(apiRequestMock).toHaveBeenCalledWith(
      '/strategy-configs/A%2FB/mode',
      expect.objectContaining({
        method: 'PUT',
        responseContract: 'envelope',
        body: JSON.stringify({ mode: 'SHADOW' }),
      })
    )
  })

  it('saves a new immutable version with PUT', async () => {
    apiRequestMock.mockResolvedValue(strategy)
    const request = {
      variantId: 'DEFAULT',
      strategyType: 'PRICE_ACTION',
      params: { period: 14 },
      mode: 'SHADOW' as const,
      paperCapital: 500000,
    }
    await saveStrategyConfig(request)
    expect(apiRequestMock).toHaveBeenCalledWith(
      '/strategy-configs/DEFAULT',
      expect.objectContaining({ method: 'PUT', body: JSON.stringify(request) })
    )
  })
})
