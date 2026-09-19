import { beforeEach, describe, expect, it, vi } from 'vitest'
import { fetchPromotionEligibility, getStrategies } from './strategies'

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
