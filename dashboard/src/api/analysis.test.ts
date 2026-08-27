import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AppError, isAppError } from '../errors/appError'
import { runFullAnalysis } from './analysis'
import type { AnalysisProgress, FullAnalysisResult } from './types'

const fetchMock = vi.fn<typeof fetch>()

function sseResponse(body: string, init: ResponseInit = {}): Response {
  const headers = new Headers(init.headers)
  if (!headers.has('content-type')) headers.set('content-type', 'text/event-stream')
  return new Response(body, { ...init, headers })
}

async function collect<T>(iterable: AsyncIterable<T>): Promise<T[]> {
  const values: T[] = []
  for await (const value of iterable) values.push(value)
  return values
}

async function captureStreamError(iterable: AsyncIterable<unknown>): Promise<AppError> {
  try {
    await collect(iterable)
  } catch (error) {
    expect(isAppError(error)).toBe(true)
    return error as AppError
  }
  throw new Error('Expected stream to reject with AppError')
}

beforeEach(() => {
  fetchMock.mockReset()
  vi.stubGlobal('fetch', fetchMock)
})

afterEach(() => {
  vi.useRealTimers()
  vi.unstubAllGlobals()
})

describe('runFullAnalysis', () => {
  it('accepts a valid complete terminal event', async () => {
    fetchMock.mockResolvedValueOnce(
      sseResponse(
        [
          'event: progress',
          'data: {"stageNumber":1,"stageName":"technical","status":"completed","message":"Technical complete","timestamp":"2026-08-26T09:00:00"}',
          '',
          'event: complete',
          'data: {"symbol":"RELIANCE","durationMs":1200,"progress":[],"composite":{"symbol":"RELIANCE"}}',
          '',
          '',
        ].join('\n')
      )
    )

    const events = await collect<AnalysisProgress | FullAnalysisResult>(runFullAnalysis('RELIANCE'))

    expect(events).toHaveLength(2)
    expect(events[events.length - 1]).toMatchObject({ symbol: 'RELIANCE' })
  })

  it('uses the backend backfillYears request parameter for the POST stream', async () => {
    fetchMock.mockResolvedValueOnce(
      sseResponse(
        [
          'event: complete',
          'data: {"symbol":"RELIANCE","durationMs":1200,"progress":[],"composite":{"symbol":"RELIANCE"}}',
          '',
          '',
        ].join('\n')
      )
    )

    await collect(runFullAnalysis('RELIANCE', 5))

    const [requestUrl, requestInit] = fetchMock.mock.calls[0]!
    const url = new URL(String(requestUrl), 'http://dashboard.test')
    expect(requestInit).toMatchObject({ method: 'POST' })
    expect(url.searchParams.get('symbol')).toBe('RELIANCE')
    expect(url.searchParams.get('backfillYears')).toBe('5')
    expect(url.searchParams.has('years')).toBe(false)
  })

  it('retains the SSE event name on every yielded event for consumers', async () => {
    fetchMock.mockResolvedValueOnce(
      sseResponse(
        [
          'event: progress',
          'data: {"stageNumber":1,"stageName":"technical","status":"completed","message":"Technical complete","timestamp":"2026-08-26T09:00:00"}',
          '',
          'event: complete',
          'data: {"symbol":"RELIANCE","durationMs":1200,"progress":[],"composite":{"symbol":"RELIANCE"}}',
          '',
          '',
        ].join('\n')
      )
    )

    const events = await collect(runFullAnalysis('RELIANCE'))

    expect(events.map((event) => (event as unknown as { _eventType?: string })._eventType)).toEqual(
      ['progress', 'complete']
    )
  })

  it('accepts the backend terminal result with a null composite after orchestration failure', async () => {
    fetchMock.mockResolvedValueOnce(
      sseResponse(
        [
          'event: complete',
          'data: {"symbol":"RELIANCE","durationMs":1200,"progress":[{"stageNumber":99,"stageName":"error","status":"error","message":"Analysis failed","timestamp":"2026-08-26T09:00:00","details":null}],"composite":null}',
          '',
          '',
        ].join('\n')
      )
    )

    const events = await collect(runFullAnalysis('RELIANCE'))

    expect(events).toHaveLength(1)
    expect(events[0]).toMatchObject({
      symbol: 'RELIANCE',
      durationMs: 1200,
      composite: null,
      _eventType: 'complete',
    })
  })

  it('forwards caller abort and reports cancellation without raw browser copy', async () => {
    const controller = new AbortController()
    fetchMock.mockImplementation((_input, init) => {
      return new Promise<Response>((_resolve, reject) => {
        init?.signal?.addEventListener(
          'abort',
          () => reject(new DOMException('The user aborted a request.', 'AbortError')),
          { once: true }
        )
        setTimeout(() => reject(new Error('Caller signal was not forwarded to stream fetch')), 0)
      })
    })

    const iterable = runFullAnalysis('RELIANCE', 3, { signal: controller.signal })
    const errorPromise = captureStreamError(iterable)
    controller.abort()
    const error = await errorPromise

    expect(error).toMatchObject({ kind: 'cancelled', retryable: false })
    expect(error.message).not.toContain('user aborted')
  })

  it('classifies and sanitizes an HTTP stream failure', async () => {
    fetchMock.mockResolvedValueOnce(
      new Response('<html>Internal route</html> java.lang.IllegalStateException: secret', {
        status: 503,
        headers: {
          'content-type': 'text/html',
          'x-request-id': 'analysis-request-9',
        },
      })
    )

    const error = await captureStreamError(runFullAnalysis('RELIANCE'))
    const rendered = JSON.stringify(error)

    expect(error).toMatchObject({
      kind: 'server',
      status: 503,
      correlationId: 'analysis-request-9',
    })
    expect(rendered).not.toContain('Internal route')
    expect(rendered).not.toContain('IllegalStateException')
    expect(rendered).not.toContain('secret')
  })

  it('turns a valid SSE error event into a typed sanitized failure', async () => {
    fetchMock.mockResolvedValueOnce(
      sseResponse(
        [
          'event: error',
          'data: {"message":"java.lang.RuntimeException: database_password=hunter2","code":"ANALYSIS_FAILED","correlationId":"analysis-corr-2"}',
          '',
          '',
        ].join('\n')
      )
    )

    const error = await captureStreamError(runFullAnalysis('RELIANCE'))
    const rendered = JSON.stringify(error)

    expect(error).toMatchObject({
      code: 'ANALYSIS_FAILED',
      correlationId: 'analysis-corr-2',
      retryable: false,
    })
    expect(rendered).not.toContain('RuntimeException')
    expect(rendered).not.toContain('hunter2')
  })

  it('rejects malformed SSE event JSON', async () => {
    fetchMock.mockResolvedValueOnce(
      sseResponse(
        [
          'event: progress',
          'data: {"stageNumber":1,"status":',
          '',
          'event: complete',
          'data: {"symbol":"RELIANCE","durationMs":1200,"progress":[],"composite":{}}',
          '',
          '',
        ].join('\n')
      )
    )

    const error = await captureStreamError(runFullAnalysis('RELIANCE'))

    expect(error).toMatchObject({ kind: 'malformed-response', retryable: false })
  })

  it('rejects a stream that closes without the complete terminal event', async () => {
    fetchMock.mockResolvedValueOnce(
      sseResponse(
        [
          'event: progress',
          'data: {"stageNumber":1,"stageName":"technical","status":"completed","message":"Technical complete","timestamp":"2026-08-26T09:00:00"}',
          '',
          '',
        ].join('\n')
      )
    )

    const error = await captureStreamError(runFullAnalysis('RELIANCE'))

    expect(error).toMatchObject({ kind: 'malformed-response', retryable: false })
    expect(error.message).toMatch(/complete|ended|terminal/i)
  })
})
