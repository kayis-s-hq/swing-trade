import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { JobRunResponse } from './types'

const sharedMocks = vi.hoisted(() => ({
  rawFetch: vi.fn(),
  errResponse: vi.fn((error: string) => ({ success: false, error })),
}))

vi.mock('./shared', () => sharedMocks)

import { startJobRun } from './job'

const response: JobRunResponse = {
  runId: 'run-123',
  triggerType: 'MANUAL',
  status: 'RUNNING',
  startedAt: '2026-08-25T10:00:00',
  completedAt: null,
  symbolsCount: 2,
  completedCount: 0,
  failedCount: 0,
  errorMessage: null,
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('job API — startJobRun', () => {
  it('posts the manual trigger to the current job-run route and returns its payload', async () => {
    sharedMocks.rawFetch.mockResolvedValue({ ok: true, data: response })

    await expect(startJobRun()).resolves.toEqual({ success: true, data: response })
    expect(sharedMocks.rawFetch).toHaveBeenCalledWith('/job/runs/start?triggerType=MANUAL', {
      method: 'POST',
    })
  })

  it('preserves an API error for the view to display', async () => {
    sharedMocks.rawFetch.mockResolvedValue({ ok: false, data: null, error: 'Run unavailable' })

    await expect(startJobRun()).resolves.toEqual({ success: false, error: 'Run unavailable' })
    expect(sharedMocks.errResponse).toHaveBeenCalledWith('Run unavailable')
  })
})
