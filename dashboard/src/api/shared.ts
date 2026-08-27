import {
  AppError,
  asAppError,
  isAppError,
  safeHumanMessage,
  safeMetadata,
  type AppErrorKind,
} from '../errors/appError'
import { API_BASE_URL, DEFAULT_HEADERS, REQUEST_TIMEOUT } from './config'
import {
  CancelledError,
  MalformedResponseError,
  NetworkError,
  TimeoutError,
} from '../errors/errorClasses'

export type ResponseContract = 'direct' | 'envelope'

export interface ApiRequestOptions<T> extends Omit<RequestInit, 'signal'> {
  responseContract: ResponseContract
  signal?: AbortSignal
  timeoutMs?: number
  validate?: (value: unknown) => value is T
}

interface WireEnvelope {
  success: boolean
  data?: unknown
  error?: unknown
  message?: unknown
  code?: unknown
  correlationId?: unknown
}

export interface ApiSseEventOptions<T> extends Omit<RequestInit, 'signal' | 'method'> {
  method?: string
  signal?: AbortSignal
  timeoutMs?: number
  ignoredEventNames?: readonly string[]
  parseEvent: (payload: unknown, eventName: string) => T
  isTerminal: (event: T, eventName: string) => boolean
}

interface AbortContext {
  signal: AbortSignal
  didTimeout: () => boolean
  cleanup: () => void
}

interface ParsedBackendError {
  message?: unknown
  code?: unknown
  correlationId?: unknown
}

const TRANSIENT_GATEWAY_STATUSES = new Set([502, 503, 504])
const RETRY_DELAY_MS = 250
const DEFAULT_STREAM_TIMEOUT = 600_000

function normalizedMethod(method?: string): string {
  return (method ?? 'GET').toUpperCase()
}

function isReadMethod(method: string): boolean {
  return method === 'GET' || method === 'HEAD'
}

function createAbortContext(
  callerSignal: AbortSignal | undefined,
  timeoutMs: number
): AbortContext {
  const controller = new AbortController()
  let timedOut = false

  const abortFromCaller = () => controller.abort()
  if (callerSignal?.aborted) controller.abort()
  else callerSignal?.addEventListener('abort', abortFromCaller, { once: true })

  const timeoutId = !controller.signal.aborted
    ? setTimeout(() => {
        timedOut = true
        controller.abort()
      }, timeoutMs)
    : undefined

  return {
    signal: controller.signal,
    didTimeout: () => timedOut,
    cleanup: () => {
      if (timeoutId !== undefined) clearTimeout(timeoutId)
      callerSignal?.removeEventListener('abort', abortFromCaller)
    },
  }
}

function requestHeaders(headers?: HeadersInit): Headers {
  const merged = new Headers(DEFAULT_HEADERS)
  if (headers) new Headers(headers).forEach((value, key) => merged.set(key, value))
  return merged
}

function malformedResponse(status?: number, message?: string, outcomeUnknown = false): AppError {
  return new MalformedResponseError({
    message: message ?? 'The backend returned an unexpected response.',
    status,
    retryable: false,
    outcomeUnknown,
  })
}

function classifyStatus(status: number): AppErrorKind {
  if (status === 400 || status === 422) return 'validation'
  if (status === 401) return 'authentication'
  if (status === 403) return 'permission'
  if (status === 404) return 'not-found'
  if (status === 408) return 'timeout'
  if (status === 409) return 'conflict'
  if (status === 429) return 'rate-limit'
  if (status >= 500) return 'server'
  if (status >= 400) return 'validation'
  return 'unknown'
}

function classifyCode(code: string | undefined, status: number): AppErrorKind {
  if (status >= 400) return classifyStatus(status)
  if (!code) return 'unknown'

  const normalized = code.toUpperCase()
  if (/(?:AUTHENTICATION|UNAUTHENTICATED|UNAUTHORIZED|TOKEN_EXPIRED)/.test(normalized)) {
    return 'authentication'
  }
  if (/(?:PERMISSION|FORBIDDEN|ACCESS_DENIED)/.test(normalized)) return 'permission'
  if (/(?:NOT_FOUND|MISSING)/.test(normalized)) return 'not-found'
  if (/(?:CONFLICT|LOCKED|VERSION)/.test(normalized)) return 'conflict'
  if (/(?:RATE_LIMIT|TOO_MANY)/.test(normalized)) return 'rate-limit'
  if (/(?:VALIDATION|INVALID|BAD_REQUEST|RISK_LIMIT)/.test(normalized)) return 'validation'
  if (/(?:SERVER|INTERNAL|UPSTREAM|UNAVAILABLE|FAILED)/.test(normalized)) return 'server'
  return 'unknown'
}

function fallbackMessage(kind: AppErrorKind): string {
  switch (kind) {
    case 'network':
      return 'The backend could not be reached.'
    case 'timeout':
      return 'The request took too long.'
    case 'authentication':
      return 'Authentication is required.'
    case 'permission':
      return 'Permission was denied.'
    case 'validation':
      return 'The request was not valid.'
    case 'not-found':
      return 'The requested item was not found.'
    case 'conflict':
      return 'The request conflicted with the current state.'
    case 'rate-limit':
      return 'Too many requests were sent.'
    case 'server':
      return 'The backend could not complete the request.'
    case 'malformed-response':
      return 'The backend returned an unexpected response.'
    case 'cancelled':
      return 'The request was cancelled.'
    case 'runtime':
    case 'unknown':
      return 'The request failed unexpectedly.'
  }
}

function recordValue(value: unknown): Record<string, unknown> | undefined {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : undefined
}

function parsedBackendError(payload: unknown): ParsedBackendError {
  const body = recordValue(payload)
  if (!body) return {}

  const nested = recordValue(body.error)
  const errorMessage = typeof body.error === 'string' ? body.error : nested?.message

  return {
    message: errorMessage ?? body.message,
    code: nested?.code ?? body.code,
    correlationId:
      nested?.correlationId ??
      nested?.requestId ??
      body.correlationId ??
      body.requestId ??
      body.traceId,
  }
}

function responseCorrelationId(response: Response): string | undefined {
  return (
    safeMetadata(response.headers.get('x-correlation-id')) ??
    safeMetadata(response.headers.get('x-request-id')) ??
    safeMetadata(response.headers.get('request-id'))
  )
}

function responseRetryAfter(response: Response): number | string | undefined {
  const value = response.headers.get('retry-after')?.trim()
  if (!value) return undefined
  if (/^\d+$/.test(value)) return Number(value)
  return safeMetadata(value)
}

function errorFromPayload(
  payload: unknown,
  response: Response,
  method: string,
  fallbackKind?: AppErrorKind
): AppError {
  const parsed = parsedBackendError(payload)
  const code = safeMetadata(parsed.code)
  const correlationId = safeMetadata(parsed.correlationId) ?? responseCorrelationId(response)
  const kind = fallbackKind ?? classifyCode(code, response.status)
  const read = isReadMethod(method)
  const retryable =
    kind === 'network' ||
    kind === 'rate-limit' ||
    (kind === 'timeout' && read) ||
    (kind === 'server' && read && TRANSIENT_GATEWAY_STATUSES.has(response.status))

  return new AppError({
    kind,
    message: safeHumanMessage(parsed.message, fallbackMessage(kind)),
    status: response.status,
    code,
    correlationId,
    retryAfter: responseRetryAfter(response),
    retryable,
    outcomeUnknown: false,
  })
}

function isJsonContentType(contentType: string): boolean {
  return /(?:^|\s|;)application\/(?:[\w.-]+\+)?json(?:\s*;|$)/i.test(contentType)
}

async function responseText(response: Response): Promise<string> {
  return response.text()
}

function parseJson(text: string, status: number, outcomeUnknown: boolean): unknown {
  try {
    return JSON.parse(text) as unknown
  } catch {
    throw malformedResponse(status, undefined, outcomeUnknown)
  }
}

async function parseSuccessfulJson(
  response: Response,
  allowEmpty: boolean,
  outcomeUnknown: boolean
): Promise<unknown> {
  if (response.status === 204 || response.status === 205) return undefined

  const contentType = response.headers.get('content-type') ?? ''
  if (!isJsonContentType(contentType)) {
    if (allowEmpty && !contentType) return undefined
    throw malformedResponse(response.status, undefined, outcomeUnknown)
  }

  const text = await responseText(response)
  if (text.trim().length === 0) {
    if (allowEmpty) return undefined
    throw malformedResponse(response.status, undefined, outcomeUnknown)
  }
  return parseJson(text, response.status, outcomeUnknown)
}

async function parseFailedResponse(response: Response): Promise<unknown> {
  const contentType = response.headers.get('content-type') ?? ''
  const text = await responseText(response).catch(() => '')
  if (!text.trim()) return undefined

  if (isJsonContentType(contentType)) {
    try {
      return JSON.parse(text) as unknown
    } catch {
      return undefined
    }
  }

  return { message: text }
}

function validatePayload<T>(
  payload: unknown,
  validate: ((value: unknown) => value is T) | undefined,
  status: number,
  outcomeUnknown: boolean
): T {
  if (!validate) return payload as T

  try {
    if (validate(payload)) return payload
  } catch {
    // A validator exception is still an invalid wire payload, not a runtime UI failure.
  }

  throw malformedResponse(status, undefined, outcomeUnknown)
}

function unwrapEnvelope<T>(
  payload: unknown,
  response: Response,
  method: string,
  validate?: (value: unknown) => value is T
): T {
  const outcomeUnknown = !isReadMethod(method)
  const envelope = recordValue(payload) as
    (Record<string, unknown> & Partial<WireEnvelope>) | undefined
  if (!envelope || typeof envelope.success !== 'boolean') {
    throw malformedResponse(response.status, undefined, outcomeUnknown)
  }

  if (!envelope.success) throw errorFromPayload(envelope, response, method)
  if (!Object.prototype.hasOwnProperty.call(envelope, 'data')) {
    throw malformedResponse(response.status, undefined, outcomeUnknown)
  }

  return validatePayload(envelope.data, validate, response.status, outcomeUnknown)
}

function transportError(
  error: unknown,
  context: AbortContext,
  callerSignal: AbortSignal | undefined,
  method: string,
  cancellationOutcomeUnknown = !isReadMethod(method)
): AppError {
  const mutation = !isReadMethod(method)

  if (context.didTimeout()) {
    return new TimeoutError({
      message: 'The request took too long.',
      cause: error,
      retryable: !mutation,
      outcomeUnknown: mutation,
    })
  }

  if (
    callerSignal?.aborted ||
    (typeof error === 'object' && error !== null && 'name' in error && error.name === 'AbortError')
  ) {
    return new CancelledError({
      message: 'The request was cancelled.',
      cause: error,
      retryable: false,
      outcomeUnknown: cancellationOutcomeUnknown,
    })
  }

  if (error instanceof TypeError) {
    return new NetworkError({
      message: 'The backend could not be reached.',
      cause: error,
      retryable: !mutation,
      outcomeUnknown: mutation,
    })
  }

  return asAppError(error, { retryable: false, outcomeUnknown: false })
}

async function waitBeforeRetry(signal?: AbortSignal): Promise<void> {
  if (signal?.aborted) {
    throw new AppError({
      kind: 'cancelled',
      message: 'The request was cancelled.',
      retryable: false,
    })
  }

  await new Promise<void>((resolve, reject) => {
    const timeoutId = setTimeout(() => {
      signal?.removeEventListener('abort', abort)
      resolve()
    }, RETRY_DELAY_MS)
    const abort = () => {
      clearTimeout(timeoutId)
      signal?.removeEventListener('abort', abort)
      reject(
        new AppError({
          kind: 'cancelled',
          message: 'The request was cancelled.',
          retryable: false,
        })
      )
    }
    signal?.addEventListener('abort', abort, { once: true })
  })
}

export async function apiRequest<T>(path: string, options: ApiRequestOptions<T>): Promise<T> {
  const {
    responseContract,
    timeoutMs = REQUEST_TIMEOUT,
    validate,
    signal: callerSignal,
    ...requestInit
  } = options
  const method = normalizedMethod(requestInit.method)
  const canRetry = isReadMethod(method)

  for (let attempt = 0; attempt < 2; attempt += 1) {
    const context = createAbortContext(callerSignal, timeoutMs)

    try {
      const response = await fetch(`${API_BASE_URL}${path}`, {
        ...requestInit,
        method,
        headers: requestHeaders(requestInit.headers),
        signal: context.signal,
      })

      if (canRetry && attempt === 0 && TRANSIENT_GATEWAY_STATUSES.has(response.status)) {
        await response.body?.cancel().catch(() => undefined)
        context.cleanup()
        await waitBeforeRetry(callerSignal)
        continue
      }

      if (!response.ok) {
        const payload = await parseFailedResponse(response)
        throw errorFromPayload(payload, response, method)
      }

      const outcomeUnknown = !isReadMethod(method)
      const payload = await parseSuccessfulJson(response, method === 'HEAD', outcomeUnknown)
      if (responseContract === 'envelope') {
        return unwrapEnvelope(payload, response, method, validate)
      }
      if (
        typeof payload === 'object' &&
        payload !== null &&
        'success' in payload &&
        payload.success === false
      ) {
        throw errorFromPayload(payload, response, method)
      }
      return validatePayload(payload, validate, response.status, outcomeUnknown)
    } catch (error) {
      const appError = isAppError(error)
        ? error
        : transportError(error, context, callerSignal, method)

      if (canRetry && attempt === 0 && appError.kind === 'network') {
        context.cleanup()
        await waitBeforeRetry(callerSignal)
        continue
      }
      throw appError
    } finally {
      context.cleanup()
    }
  }

  throw new AppError({
    kind: 'unknown',
    message: 'The request failed unexpectedly.',
    retryable: false,
  })
}

export const toNum = (v: number | string | null | undefined): number => {
  if (v === null || v === undefined) return 0
  return typeof v === 'string' ? parseFloat(v) : v
}

function parseSseError(payload: unknown, response: Response, method: string): AppError {
  const code = safeMetadata(parsedBackendError(payload).code)
  const inferred = classifyCode(code, response.status)
  const kind = inferred === 'unknown' ? 'server' : inferred
  return errorFromPayload(payload, response, method, kind)
}

export async function* apiSseEvents<T>(
  path: string,
  options: ApiSseEventOptions<T>
): AsyncIterable<T> {
  const {
    method: methodOption,
    signal: callerSignal,
    timeoutMs = DEFAULT_STREAM_TIMEOUT,
    ignoredEventNames = [],
    parseEvent,
    isTerminal,
    ...requestInit
  } = options
  const method = normalizedMethod(methodOption ?? 'POST')
  const ignoredEvents = new Set(ignoredEventNames.map((eventName) => eventName.toLowerCase()))
  const context = createAbortContext(callerSignal, timeoutMs)
  let reader: ReadableStreamDefaultReader<Uint8Array> | undefined
  let reachedEnd = false
  let sawTerminal = false

  try {
    let response: Response
    try {
      response = await fetch(`${API_BASE_URL}${path}`, {
        ...requestInit,
        method,
        headers: requestHeaders(requestInit.headers),
        signal: context.signal,
      })
    } catch (error) {
      throw transportError(error, context, callerSignal, method, false)
    }

    if (!response.ok) {
      const payload = await parseFailedResponse(response)
      throw errorFromPayload(payload, response, method)
    }

    const contentType = response.headers.get('content-type') ?? ''
    if (!/^text\/event-stream(?:\s*;|$)/i.test(contentType)) {
      await response.body?.cancel().catch(() => undefined)
      throw malformedResponse(response.status, undefined, true)
    }
    if (!response.body) throw malformedResponse(response.status, undefined, true)

    reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    let eventName = 'message'
    let dataLines: string[] = []

    const dispatch = (): { value: T; eventName: string } | undefined => {
      if (dataLines.length === 0) {
        eventName = 'message'
        return undefined
      }

      const dispatchedName = eventName
      const rawData = dataLines.join('\n')
      eventName = 'message'
      dataLines = []

      if (ignoredEvents.has(dispatchedName.toLowerCase())) return undefined

      let payload: unknown
      try {
        payload = JSON.parse(rawData) as unknown
      } catch {
        throw malformedResponse(response.status, 'The event stream contained malformed JSON.', true)
      }

      if (dispatchedName.toLowerCase() === 'error') {
        throw parseSseError(payload, response, method)
      }

      try {
        return { value: parseEvent(payload, dispatchedName), eventName: dispatchedName }
      } catch (error) {
        if (isAppError(error)) throw error
        throw malformedResponse(
          response.status,
          'The event stream contained an invalid event.',
          true
        )
      }
    }

    const processLine = (line: string): { value: T; eventName: string } | undefined => {
      if (line === '') return dispatch()
      if (line.startsWith(':')) return undefined

      const separator = line.indexOf(':')
      const field = separator >= 0 ? line.slice(0, separator) : line
      let value = separator >= 0 ? line.slice(separator + 1) : ''
      if (value.startsWith(' ')) value = value.slice(1)

      if (field === 'event') eventName = value || 'message'
      else if (field === 'data') dataLines.push(value)
      return undefined
    }

    while (true) {
      let chunk: ReadableStreamReadResult<Uint8Array>
      try {
        chunk = await reader.read()
      } catch (error) {
        throw transportError(error, context, callerSignal, method, false)
      }

      if (chunk.done) {
        reachedEnd = true
        buffer += decoder.decode()
        if (buffer.length > 0) {
          const finalLine = buffer.endsWith('\r') ? buffer.slice(0, -1) : buffer
          const parsed = processLine(finalLine)
          if (parsed) {
            sawTerminal = isTerminal(parsed.value, parsed.eventName)
            yield parsed.value
          }
        }
        const parsed = dispatch()
        if (parsed) {
          sawTerminal = isTerminal(parsed.value, parsed.eventName)
          yield parsed.value
        }
        break
      }

      buffer += decoder.decode(chunk.value, { stream: true })
      let newlineIndex = buffer.indexOf('\n')
      while (newlineIndex >= 0) {
        let line = buffer.slice(0, newlineIndex)
        if (line.endsWith('\r')) line = line.slice(0, -1)
        buffer = buffer.slice(newlineIndex + 1)

        const parsed = processLine(line)
        if (parsed) {
          sawTerminal = isTerminal(parsed.value, parsed.eventName)
          yield parsed.value
          if (sawTerminal) return
        }
        newlineIndex = buffer.indexOf('\n')
      }
    }

    if (!sawTerminal) {
      throw malformedResponse(
        response.status,
        'The event stream ended before its required completion event.',
        true
      )
    }
  } finally {
    context.cleanup()
    if (reader) {
      if (!reachedEnd) await reader.cancel().catch(() => undefined)
      reader.releaseLock()
    }
  }
}
