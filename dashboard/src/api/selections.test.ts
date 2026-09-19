import { beforeEach, describe, expect, it, vi } from 'vitest'

const sharedMocks = vi.hoisted(() => ({ apiRequest: vi.fn() }))
vi.mock('./shared', () => sharedMocks)

import { latestTournament, listSignalSelections, type SignalSelection } from './selections'

function selection(symbol: string, selectionDate: string): SignalSelection {
  return {
    id: 1,
    symbol,
    selectionDate,
    winnerVariantId: 'pullback-v1',
    winnerVersion: 1,
    winnerConfidence: 0.75,
    candidates: [],
    reason: null,
    status: 'PENDING',
    statusDetail: null,
  }
}

describe('selections api', () => {
  beforeEach(() => sharedMocks.apiRequest.mockReset())

  it('requests the selections endpoint with a date range', async () => {
    sharedMocks.apiRequest.mockResolvedValue([])
    await listSignalSelections('2026-09-01', '2026-09-19')
    expect(sharedMocks.apiRequest).toHaveBeenCalledWith(
      '/signal-selections?from=2026-09-01&to=2026-09-19',
      { responseContract: 'direct' }
    )
  })

  it('keeps only the latest tournament date, sorted by symbol', () => {
    const rows = [selection('SBIN', '2026-09-17'), selection('INFY', '2026-09-18'), selection('BSE', '2026-09-18')]
    expect(latestTournament(rows).map((s) => s.symbol)).toEqual(['BSE', 'INFY'])
    expect(latestTournament([])).toEqual([])
  })
})
