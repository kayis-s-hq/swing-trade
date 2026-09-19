import { beforeEach, describe, expect, it, vi } from 'vitest'

const sharedMocks = vi.hoisted(() => ({ apiRequest: vi.fn() }))
vi.mock('./shared', () => sharedMocks)

import { getStrategyReport } from './strategyReport'

describe('strategyReport api', () => {
  beforeEach(() => {
    sharedMocks.apiRequest.mockReset()
  })

  it('requests the report with a date range', async () => {
    sharedMocks.apiRequest.mockResolvedValue({ variants: [] })
    await getStrategyReport('2026-09-01', '2026-09-19')
    expect(sharedMocks.apiRequest).toHaveBeenCalledWith(
      '/strategy-report?from=2026-09-01&to=2026-09-19',
      { responseContract: 'direct' }
    )
  })

  it('omits the query string when no range is given', async () => {
    sharedMocks.apiRequest.mockResolvedValue({ variants: [] })
    await getStrategyReport()
    expect(sharedMocks.apiRequest).toHaveBeenCalledWith('/strategy-report', {
      responseContract: 'direct',
    })
  })
})
