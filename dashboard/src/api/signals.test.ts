import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AppError, isAppError } from '../errors/appError'
import { generateAllSignalsStream } from './signals'
import type { SignalGenerationProgress } from './signals'

const fetchMock = vi.fn<typeof fetch>()
const encoder = new TextEncoder()

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

describe('generateAllSignalsStream', () => {
  it('yields valid progress and requires a COMPLETE terminal payload', async () => {
    fetchMock.mockResolvedValueOnce(
      sseResponse(
        [
          'event: progress',
          'data: {"eventType":"STARTED","status":"PROCESSING","message":"Starting","current":0,"total":1}',
          '',
          'event: progress',
          'data: {"eventType":"COMPLETE","status":"DONE","message":"Complete","current":1,"total":1}',
          '',
          '',
        ].join('\n')
      )
    )

    const events = await collect<SignalGenerationProgress>(generateAllSignalsStream())

    expect(events.map((event) => event.eventType)).toEqual(['STARTED', 'COMPLETE'])
  })

  it('accepts nullable SignalResponse fields that the backend domain permits', async () => {
    fetchMock.mockResolvedValueOnce(
      sseResponse(
        [
          'event: signal',
          'data: {"eventType":"SIGNAL_DONE","symbol":"RELIANCE","status":"DONE","message":"Signal generated for RELIANCE","current":1,"total":1,"signal":{"id":null,"symbol":"RELIANCE","date":"2026-08-26","signalType":"HOLD","confidence":"0.62","reasoning":"No trade setup","entryPrice":null,"stopLoss":null,"target":null,"riskRewardRatio":null,"indicators":[],"generatedAt":"2026-08-26","strategy":null,"sentimentScore":null,"sentimentReasoning":null}}',
          '',
          'event: progress',
          'data: {"eventType":"COMPLETE","status":"DONE","message":"Done: 1 signals, 0 skipped","current":1,"total":1}',
          '',
          '',
        ].join('\n')
      )
    )

    const events = await collect<SignalGenerationProgress>(generateAllSignalsStream())

    expect(events).toHaveLength(2)
    expect(events[0]).toMatchObject({
      eventType: 'SIGNAL_DONE',
      symbol: 'RELIANCE',
      status: 'DONE',
      signal: {
        symbol: 'RELIANCE',
        direction: 'HOLD',
        reason: 'No trade setup',
      },
    })
  })

  it('still rejects a null required SignalResponse field', async () => {
    fetchMock.mockResolvedValueOnce(
      sseResponse(
        [
          'event: signal',
          'data: {"eventType":"SIGNAL_DONE","symbol":"RELIANCE","status":"DONE","message":"Signal generated","signal":{"id":null,"symbol":null,"date":"2026-08-26","signalType":"HOLD","confidence":"0.62","reasoning":"No trade setup","entryPrice":null,"stopLoss":null,"target":null,"riskRewardRatio":null,"indicators":[],"generatedAt":"2026-08-26","strategy":null,"sentimentScore":null}}',
          '',
          'event: progress',
          'data: {"eventType":"COMPLETE","status":"DONE","message":"Complete"}',
          '',
          '',
        ].join('\n')
      )
    )

    const error = await captureStreamError(generateAllSignalsStream())

    expect(error).toMatchObject({ kind: 'malformed-response', retryable: false })
  })

  it('sanitizes exception-like backend progress messages before yielding them', async () => {
    fetchMock.mockResolvedValueOnce(
      sseResponse(
        [
          'event: progress',
          'data: {"eventType":"SKIPPED","symbol":"RELIANCE","status":"SKIPPED","message":"Error: java.lang.IllegalStateException: database_password=hunter2 at com.internal.SignalService.generate(SignalService.java:42)","current":1,"total":1}',
          '',
          'event: progress',
          'data: {"eventType":"COMPLETE","status":"DONE","message":"Done: 0 signals, 1 skipped","current":0,"total":1}',
          '',
          '',
        ].join('\n')
      )
    )

    const events = await collect<SignalGenerationProgress>(generateAllSignalsStream())
    const message = events[0]!.message

    expect(message).toMatch(/couldn.t generate|could not be generated|generation failed/i)
    expect(message).not.toContain('IllegalStateException')
    expect(message).not.toContain('database_password')
    expect(message).not.toContain('SignalService.java')
    expect(message).not.toContain('hunter2')
  })

  it('uses caller abort to reject with a cancelled AppError', async () => {
    const controller = new AbortController()
    fetchMock.mockImplementation((_input, init) => {
      return new Promise<Response>((_resolve, reject) => {
        init?.signal?.addEventListener(
          'abort',
          () => reject(new DOMException('The operation was aborted.', 'AbortError')),
          { once: true }
        )
        setTimeout(() => reject(new Error('Caller signal was not forwarded to stream fetch')), 0)
      })
    })

    const iterable = generateAllSignalsStream({ signal: controller.signal })
    const errorPromise = captureStreamError(iterable)
    controller.abort()
    const error = await errorPromise

    expect(error).toMatchObject({
      kind: 'cancelled',
      retryable: false,
      outcomeUnknown: false,
    })
  })

  it('rejects a successful response whose body is missing', async () => {
    fetchMock.mockResolvedValueOnce(
      new Response(null, { status: 200, headers: { 'content-type': 'text/event-stream' } })
    )

    const error = await captureStreamError(generateAllSignalsStream())

    expect(error).toMatchObject({ kind: 'malformed-response', status: 200 })
  })

  it('rejects malformed event JSON instead of silently skipping it', async () => {
    fetchMock.mockResolvedValueOnce(
      sseResponse(
        [
          'event: progress',
          'data: {"eventType":"STARTED","status":"PROCESSING","message":"Starting"}',
          '',
          'event: progress',
          'data: {"eventType":',
          '',
          'event: progress',
          'data: {"eventType":"COMPLETE","status":"DONE","message":"Complete"}',
          '',
          '',
        ].join('\n')
      )
    )

    const error = await captureStreamError(generateAllSignalsStream())

    expect(error).toMatchObject({ kind: 'malformed-response', retryable: false })
    expect(error.message).not.toContain('SyntaxError')
  })

  it('accepts null signal fields on non-signal progress events', async () => {
    fetchMock.mockResolvedValueOnce(
      sseResponse(
        [
          'event: progress',
          'data: {"eventType":"STARTED","status":"PROCESSING","message":"Starting","symbol":null,"signal":null,"current":0,"total":1}',
          '',
          'event: progress',
          'data: {"eventType":"COMPLETE","status":"DONE","message":"Done","symbol":null,"signal":null,"current":1,"total":1}',
          '',
          '',
        ].join('\n')
      )
    )

    const events = await collect<SignalGenerationProgress>(generateAllSignalsStream())

    expect(events).toHaveLength(2)
    expect(events[0]?.eventType).toBe('STARTED')
    expect(events[1]?.eventType).toBe('COMPLETE')
  })

  it('rejects a stream that closes before the COMPLETE terminal payload', async () => {
    fetchMock.mockResolvedValueOnce(
      sseResponse(
        [
          'event: progress',
          'data: {"eventType":"STARTED","status":"PROCESSING","message":"Starting","current":0,"total":2}',
          '',
          'event: progress',
          'data: {"eventType":"GENERATING","status":"PROCESSING","message":"Generating","symbol":"RELIANCE","current":1,"total":2}',
          '',
          '',
        ].join('\n')
      )
    )

    const error = await captureStreamError(generateAllSignalsStream())

    expect(error).toMatchObject({ kind: 'malformed-response', retryable: false })
    expect(error.message).toMatch(/complete|ended|terminal/i)
  })

  it('rejects an unexpected stream content type without exposing response content', async () => {
    fetchMock.mockResolvedValueOnce(
      new Response('<html>internal proxy route</html>', {
        status: 200,
        headers: { 'content-type': 'text/html' },
      })
    )

    const error = await captureStreamError(generateAllSignalsStream())

    expect(error).toMatchObject({ kind: 'malformed-response', status: 200 })
    expect(JSON.stringify(error)).not.toContain('internal proxy route')
  })

  it('cancels the response reader when iteration is stopped by the consumer', async () => {
    const cancel = vi.fn()
    const stream = new ReadableStream<Uint8Array>({
      start(controller) {
        controller.enqueue(
          encoder.encode(
            'event: progress\ndata: {"eventType":"STARTED","status":"PROCESSING","message":"Starting"}\n\n'
          )
        )
      },
      cancel,
    })
    fetchMock.mockResolvedValueOnce(
      new Response(stream, { headers: { 'content-type': 'text/event-stream' } })
    )

    for await (const event of generateAllSignalsStream()) {
      expect(event).toBeDefined()
      break
    }

    expect(cancel).toHaveBeenCalledTimes(1)
  })
})
