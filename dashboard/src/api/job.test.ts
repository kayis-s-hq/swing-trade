import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { JobRunResponse } from './types'

const sharedMocks = vi.hoisted(() => ({
  apiRequest: vi.fn(),
}))

vi.mock('./shared', () => sharedMocks)

import { cancelJobRun, startJobRun } from './job'

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

function unknownMutationError() {
  return Object.assign(new Error('Connection closed after request upload'), {
    name: 'AppError',
    kind: 'network',
    outcomeUnknown: true,
    retryable: false,
  })
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('job API — mutation contract', () => {
  it('returns the confirmed start payload directly from the manual trigger route', async () => {
    sharedMocks.apiRequest.mockResolvedValue(response)

    await expect(startJobRun()).resolves.toBe(response)
    expect(sharedMocks.apiRequest).toHaveBeenCalledTimes(1)
    expect(sharedMocks.apiRequest).toHaveBeenCalledWith(
      '/job/runs/start?triggerType=MANUAL',
      expect.objectContaining({ method: 'POST', responseContract: 'direct' })
    )
  })

  it('passes an explicit scheduled trigger without changing the confirmed payload', async () => {
    const scheduled = { ...response, triggerType: 'SCHEDULED' as const }
    sharedMocks.apiRequest.mockResolvedValue(scheduled)

    await expect(startJobRun('SCHEDULED')).resolves.toBe(scheduled)
    expect(sharedMocks.apiRequest).toHaveBeenCalledWith(
      '/job/runs/start?triggerType=SCHEDULED',
      expect.objectContaining({ method: 'POST', responseContract: 'direct' })
    )
  })

  it('propagates an ambiguous start failure unchanged instead of resolving invented run state', async () => {
    const error = unknownMutationError()
    sharedMocks.apiRequest.mockRejectedValue(error)

    await expect(startJobRun()).rejects.toBe(error)
    expect(sharedMocks.apiRequest).toHaveBeenCalledTimes(1)
  })

  it('resolves cancellation only after the backend acknowledgement is confirmed', async () => {
    sharedMocks.apiRequest.mockResolvedValue({ message: 'Run cancelled' })

    await expect(cancelJobRun('run-123')).resolves.toBeUndefined()
    expect(sharedMocks.apiRequest).toHaveBeenCalledTimes(1)
    expect(sharedMocks.apiRequest).toHaveBeenCalledWith(
      '/job/runs/run-123/cancel',
      expect.objectContaining({ method: 'POST', responseContract: 'direct' })
    )
  })

  it('propagates an ambiguous cancellation failure unchanged without retrying the mutation', async () => {
    const error = unknownMutationError()
    sharedMocks.apiRequest.mockRejectedValue(error)

    await expect(cancelJobRun('run-123')).rejects.toBe(error)
    expect(sharedMocks.apiRequest).toHaveBeenCalledTimes(1)
  })
})
