import { beforeEach, describe, expect, it, vi } from 'vitest'
import { getStrategies } from './strategies'

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
