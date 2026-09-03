import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { getHealthStatus } from './health'

const fetchMock = vi.fn<typeof fetch>()

beforeEach(() => {
  fetchMock.mockReset()
  vi.stubGlobal('fetch', fetchMock)
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('getHealthStatus', () => {
  it('parses the direct HealthController response', async () => {
    const responseBody = {
      status: 'UP',
      components: {
        api: { name: 'api', status: 'UP', description: 'REST API Service' },
        database: { name: 'database', status: 'UP', description: 'Database Service' },
      },
    }
    fetchMock.mockResolvedValueOnce(
      new Response(JSON.stringify(responseBody), {
        status: 200,
        headers: { 'content-type': 'application/json' },
      })
    )

    await expect(getHealthStatus()).resolves.toEqual(responseBody)
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/health/full',
      expect.objectContaining({ method: 'GET' })
    )
  })
})
