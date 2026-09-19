import { beforeEach, describe, expect, it, vi } from 'vitest'

const sharedMocks = vi.hoisted(() => ({ apiRequest: vi.fn() }))
vi.mock('./shared', () => sharedMocks)

import { getStrategyMatrix, shadowPortfolios, type PaperPortfolioSummary } from './portfolios'

const p = (portfolioId: string): PaperPortfolioSummary => ({
  portfolioId,
  initialCapital: 100000,
  currentCapital: 100000,
  realizedPnl: 0,
  openPositionCount: 0,
})

describe('portfolios api', () => {
  beforeEach(() => sharedMocks.apiRequest.mockReset())

  it('excludes the real portfolio and puts selected first', () => {
    const result = shadowPortfolios([
      p('squeeze-v1'),
      p('default'),
      p('selected'),
      p('breakout-v1'),
    ])
    expect(result.map((x) => x.portfolioId)).toEqual(['selected', 'breakout-v1', 'squeeze-v1'])
  })

  it('encodes the symbol in the matrix request', async () => {
    sharedMocks.apiRequest.mockResolvedValue({ symbol: 'M&M', strategies: [], tournaments: [] })
    await getStrategyMatrix('M&M')
    expect(sharedMocks.apiRequest).toHaveBeenCalledWith('/symbols/M%26M/strategy-matrix', {
      responseContract: 'direct',
    })
  })
})
