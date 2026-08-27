import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AppError, isAppError } from '../errors/appError'
import { apiRequest } from './shared'

interface RequiredPayload {
  id: string
  status: string
}

const fetchMock = vi.fn<typeof fetch>()

function jsonResponse(body: unknown, init: ResponseInit = {}): Response {
  const headers = new Headers(init.headers)
  if (!headers.has('content-type')) headers.set('content-type', 'application/json')
  return new Response(JSON.stringify(body), { ...init, headers })
}

function textResponse(body: string, init: ResponseInit = {}): Response {
  return new Response(body, init)
}

function isRequiredPayload(value: unknown): value is RequiredPayload {
  if (typeof value !== 'object' || value === null) return false
  const candidate = value as Record<string, unknown>
  return typeof candidate.id === 'string' && typeof candidate.status === 'string'
}

async function captureAppError(promise: Promise<unknown>): Promise<AppError> {
  try {
    await promise
  } catch (error) {
    expect(isAppError(error)).toBe(true)
    return error as AppError
  }
  throw new Error('Expected request to reject with AppError')
}

beforeEach(() => {
  fetchMock.mockReset()
  vi.stubGlobal('fetch', fetchMock)
})

afterEach(() => {
  vi.useRealTimers()
  vi.unstubAllGlobals()
})

describe('apiRequest response contracts', () => {
  it('returns a declared direct payload unchanged', async () => {
    const payload = { id: 'position-1', status: 'OPEN' }
    fetchMock.mockResolvedValueOnce(jsonResponse(payload))

    await expect(
      apiRequest<RequiredPayload>('/positions/position-1', {
        responseContract: 'direct',
        validate: isRequiredPayload,
      })
    ).resolves.toEqual(payload)

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/positions/position-1',
      expect.objectContaining({ method: 'GET', signal: expect.any(AbortSignal) })
    )
  })

  it('unwraps data only when the adapter declares an envelope payload', async () => {
    const payload = { id: 'watchlist-1', status: 'ACTIVE' }
    fetchMock.mockResolvedValueOnce(
      jsonResponse({ success: true, data: payload, error: null, timestamp: '2026-08-26T09:00:00' })
    )

    await expect(
      apiRequest<RequiredPayload>('/watchlist', {
        responseContract: 'envelope',
        validate: isRequiredPayload,
      })
    ).resolves.toEqual(payload)
  })

  it('treats HTTP 200 success:false envelopes as failures', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse({
        success: false,
        data: null,
        error: 'Position cannot be closed',
        message: 'Legacy message must not win',
        code: 'POSITION_LOCKED',
        correlationId: 'corr-200-failure',
      })
    )

    const error = await captureAppError(
      apiRequest<RequiredPayload>('/positions/position-1/close', {
        method: 'POST',
        responseContract: 'envelope',
        validate: isRequiredPayload,
      })
    )

    expect(error).toMatchObject({
      message: 'Position cannot be closed',
      status: 200,
      code: 'POSITION_LOCKED',
      correlationId: 'corr-200-failure',
      retryable: false,
    })
    expect(error.message).not.toContain('Legacy message')
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('rejects a success:false failure marker from a direct mutation response', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse({ success: false, error: 'Position cannot be opened', code: 'RISK_LIMIT' })
    )

    const error = await captureAppError(
      apiRequest('/positions', { method: 'POST', responseContract: 'direct' })
    )

    expect(error).toMatchObject({
      kind: 'validation',
      message: 'Position cannot be opened',
      code: 'RISK_LIMIT',
      outcomeUnknown: false,
    })
  })

  it('falls back to a legacy message only when backend error is absent', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse({
        success: false,
        data: null,
        message: 'Legacy validation message',
        code: 'VALIDATION_FAILED',
      })
    )

    const error = await captureAppError(
      apiRequest('/watchlist', {
        method: 'POST',
        responseContract: 'envelope',
      })
    )

    expect(error).toMatchObject({
      message: 'Legacy validation message',
      code: 'VALIDATION_FAILED',
      retryable: false,
    })
  })
})

describe('apiRequest failure classification and safe metadata', () => {
  it.each([
    [400, 'validation'],
    [401, 'authentication'],
    [403, 'permission'],
    [404, 'not-found'],
    [409, 'conflict'],
    [422, 'validation'],
    [429, 'rate-limit'],
    [500, 'server'],
  ] as const)('classifies HTTP %i as %s', async (status, kind) => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse({ message: 'Request failed', code: `HTTP_${status}` }, { status })
    )

    const error = await captureAppError(
      apiRequest('/classification', { responseContract: 'direct' })
    )

    expect(error).toMatchObject({ kind, status, code: `HTTP_${status}` })
  })

  it('parses a structured backend error before legacy message and retains safe identifiers', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse(
        {
          error: {
            message: 'Position was changed by another request',
            code: 'POSITION_VERSION_CONFLICT',
            correlationId: 'body-correlation-id',
            stack: 'java.lang.IllegalStateException: secret-token',
          },
          message: 'Legacy conflict copy',
        },
        {
          status: 409,
          headers: { 'x-request-id': 'header-request-id' },
        }
      )
    )

    const error = await captureAppError(
      apiRequest('/positions/position-1', { method: 'PATCH', responseContract: 'direct' })
    )

    expect(error).toMatchObject({
      kind: 'conflict',
      status: 409,
      code: 'POSITION_VERSION_CONFLICT',
      correlationId: 'body-correlation-id',
      message: 'Position was changed by another request',
    })
    expect(JSON.stringify(error)).not.toContain('secret-token')
    expect(error.message).not.toContain('Legacy conflict copy')
  })

  it('uses a response request ID when the body has no correlation ID', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse(
        { message: 'Not found', code: 'POSITION_NOT_FOUND' },
        { status: 404, headers: { 'x-request-id': 'request-from-header' } }
      )
    )

    const error = await captureAppError(
      apiRequest('/positions/missing', { responseContract: 'direct' })
    )

    expect(error).toMatchObject({
      kind: 'not-found',
      code: 'POSITION_NOT_FOUND',
      correlationId: 'request-from-header',
    })
  })

  it('retains Retry-After on rate-limit failures', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse(
        { message: 'Too many requests', code: 'RATE_LIMITED' },
        { status: 429, headers: { 'retry-after': '12' } }
      )
    )

    const error = await captureAppError(
      apiRequest('/signals/latest', { responseContract: 'direct' })
    )

    expect(error.kind).toBe('rate-limit')
    expect(String(error.retryAfter)).toContain('12')
  })

  it('sanitizes generic server payloads instead of exposing HTML or stack traces', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse(
        {
          error:
            '<html>Internal Server Error</html>\njava.lang.RuntimeException: db_password=hunter2',
          code: 'INTERNAL_ERROR',
        },
        { status: 500 }
      )
    )

    const error = await captureAppError(apiRequest('/portfolio', { responseContract: 'direct' }))
    const rendered = JSON.stringify(error)

    expect(error).toMatchObject({ kind: 'server', status: 500, code: 'INTERNAL_ERROR' })
    expect(error.message).toMatch(/backend|server|request/i)
    expect(rendered).not.toContain('<html>')
    expect(rendered).not.toContain('RuntimeException')
    expect(rendered).not.toContain('hunter2')
  })
})

describe('apiRequest malformed responses', () => {
  it('rejects invalid JSON declared as application/json', async () => {
    fetchMock.mockResolvedValueOnce(
      textResponse('{"id":', { status: 200, headers: { 'content-type': 'application/json' } })
    )

    const error = await captureAppError(apiRequest('/positions', { responseContract: 'direct' }))

    expect(error).toMatchObject({ kind: 'malformed-response', status: 200, retryable: false })
    expect(error.message).not.toContain('SyntaxError')
  })

  it('rejects an unexpected successful HTML content type without exposing its body', async () => {
    fetchMock.mockResolvedValueOnce(
      textResponse('<html><body>nginx route dump: internal-host</body></html>', {
        status: 200,
        headers: { 'content-type': 'text/html' },
      })
    )

    const error = await captureAppError(
      apiRequest('/signals/latest', { responseContract: 'direct' })
    )

    expect(error).toMatchObject({ kind: 'malformed-response', status: 200 })
    expect(JSON.stringify(error)).not.toContain('internal-host')
  })

  it.each([
    [{ data: { id: 'position-1', status: 'OPEN' } }, 'missing success'],
    [{ success: 'yes', data: { id: 'position-1', status: 'OPEN' } }, 'non-boolean success'],
    [{ success: true }, 'missing data'],
  ])('rejects a malformed envelope: %s (%s)', async (body, _description) => {
    fetchMock.mockResolvedValueOnce(jsonResponse(body))

    const error = await captureAppError(apiRequest('/watchlist', { responseContract: 'envelope' }))

    expect(error).toMatchObject({ kind: 'malformed-response', status: 200 })
  })

  it('rejects payloads that omit adapter-required fields', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({ id: 'position-1' }))

    const error = await captureAppError(
      apiRequest<RequiredPayload>('/positions/position-1', {
        responseContract: 'direct',
        validate: isRequiredPayload,
      })
    )

    expect(error).toMatchObject({ kind: 'malformed-response', status: 200, retryable: false })
    expect(JSON.stringify(error)).not.toContain('position-1')
  })
})

describe('apiRequest retry policy', () => {
  it('retries a GET network failure once and then returns confirmed success', async () => {
    vi.useFakeTimers()
    fetchMock
      .mockRejectedValueOnce(new TypeError('Failed to fetch'))
      .mockResolvedValueOnce(jsonResponse({ id: 'position-1', status: 'OPEN' }))

    const pending = apiRequest<RequiredPayload>('/positions/position-1', {
      responseContract: 'direct',
      validate: isRequiredPayload,
    })
    await vi.runAllTimersAsync()

    await expect(pending).resolves.toEqual({ id: 'position-1', status: 'OPEN' })
    expect(fetchMock).toHaveBeenCalledTimes(2)
  })

  it.each([502, 503, 504])('retries HTTP %i once for GET requests', async (status) => {
    vi.useFakeTimers()
    fetchMock
      .mockResolvedValueOnce(jsonResponse({ message: 'Gateway unavailable' }, { status }))
      .mockResolvedValueOnce(jsonResponse({ id: 'position-1', status: 'OPEN' }))

    const pending = apiRequest<RequiredPayload>('/positions/position-1', {
      responseContract: 'direct',
      validate: isRequiredPayload,
    })
    await vi.runAllTimersAsync()

    await expect(pending).resolves.toEqual({ id: 'position-1', status: 'OPEN' })
    expect(fetchMock).toHaveBeenCalledTimes(2)
  })

  it('retries HEAD once for a transient gateway response', async () => {
    vi.useFakeTimers()
    fetchMock
      .mockResolvedValueOnce(jsonResponse({ message: 'Gateway unavailable' }, { status: 503 }))
      .mockResolvedValueOnce(jsonResponse({ available: true }))

    const pending = apiRequest<{ available: boolean }>('/health', {
      method: 'HEAD',
      responseContract: 'direct',
    })
    await vi.runAllTimersAsync()

    await expect(pending).resolves.toEqual({ available: true })
    expect(fetchMock).toHaveBeenCalledTimes(2)
  })

  it('stops after one retry when a GET network failure persists', async () => {
    vi.useFakeTimers()
    fetchMock.mockRejectedValue(new TypeError('Failed to fetch internal-host'))

    const errorPromise = captureAppError(apiRequest('/positions', { responseContract: 'direct' }))
    await vi.runAllTimersAsync()
    const error = await errorPromise

    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(error).toMatchObject({ kind: 'network', retryable: true, outcomeUnknown: false })
    expect(error.message).not.toContain('internal-host')
  })

  it('does not retry an ordinary HTTP 500 response', async () => {
    fetchMock.mockResolvedValue(jsonResponse({ message: 'Internal Server Error' }, { status: 500 }))

    const error = await captureAppError(apiRequest('/positions', { responseContract: 'direct' }))

    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(error.kind).toBe('server')
  })

  it.each(['POST', 'PUT', 'PATCH', 'DELETE'])('does not automatically retry %s', async (method) => {
    fetchMock.mockResolvedValue(jsonResponse({ message: 'Gateway unavailable' }, { status: 503 }))

    await captureAppError(apiRequest('/mutation', { method, responseContract: 'direct' }))

    expect(fetchMock).toHaveBeenCalledTimes(1)
  })
})

describe('apiRequest timeout, cancellation, and mutation ambiguity', () => {
  function pendingFetch(_input: RequestInfo | URL, init?: RequestInit): Promise<Response> {
    return new Promise((_resolve, reject) => {
      init?.signal?.addEventListener(
        'abort',
        () => reject(new DOMException('The operation was aborted.', 'AbortError')),
        { once: true }
      )
    })
  }

  it('classifies a transport timeout and does not automatically retry it', async () => {
    vi.useFakeTimers()
    fetchMock.mockImplementation(pendingFetch)

    const errorPromise = captureAppError(
      apiRequest('/signals/latest', {
        responseContract: 'direct',
        timeoutMs: 25,
      })
    )
    await vi.advanceTimersByTimeAsync(25)
    const error = await errorPromise

    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(error).toMatchObject({
      kind: 'timeout',
      retryable: true,
      outcomeUnknown: false,
    })
  })

  it('classifies caller cancellation separately from timeout and never retries it', async () => {
    fetchMock.mockImplementation(pendingFetch)
    const controller = new AbortController()

    const errorPromise = captureAppError(
      apiRequest('/signals/latest', {
        responseContract: 'direct',
        signal: controller.signal,
      })
    )
    controller.abort()
    const error = await errorPromise

    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(error).toMatchObject({
      kind: 'cancelled',
      retryable: false,
      outcomeUnknown: false,
    })
  })

  it('marks an interrupted non-idempotent mutation outcome unknown', async () => {
    fetchMock.mockRejectedValueOnce(new TypeError('Failed to fetch after request upload'))

    const error = await captureAppError(
      apiRequest('/positions/position-1/close', {
        method: 'POST',
        responseContract: 'direct',
      })
    )

    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(error).toMatchObject({
      kind: 'network',
      retryable: false,
      outcomeUnknown: true,
    })
  })

  it('marks a timed-out mutation outcome unknown and does not retry it', async () => {
    vi.useFakeTimers()
    fetchMock.mockImplementation(pendingFetch)

    const errorPromise = captureAppError(
      apiRequest('/trade', {
        method: 'POST',
        responseContract: 'direct',
        timeoutMs: 25,
      })
    )
    await vi.advanceTimersByTimeAsync(25)
    const error = await errorPromise

    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(error).toMatchObject({
      kind: 'timeout',
      retryable: false,
      outcomeUnknown: true,
    })
  })
})
