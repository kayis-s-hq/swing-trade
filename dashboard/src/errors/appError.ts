export type AppErrorKind =
  | 'network'
  | 'timeout'
  | 'authentication'
  | 'permission'
  | 'validation'
  | 'not-found'
  | 'conflict'
  | 'rate-limit'
  | 'server'
  | 'malformed-response'
  | 'cancelled'
  | 'runtime'
  | 'unknown'

export interface AppErrorOptions {
  kind: AppErrorKind
  message: string
  status?: number
  code?: string
  correlationId?: string
  retryAfter?: number | string
  technicalDetail?: string
  cause?: unknown
  retryable?: boolean
  outcomeUnknown?: boolean
}

export class AppError extends Error {
  readonly kind: AppErrorKind
  readonly status?: number
  readonly code?: string
  readonly correlationId?: string
  readonly retryAfter?: number | string
  readonly technicalDetail?: string
  readonly cause?: unknown
  readonly retryable: boolean
  readonly outcomeUnknown: boolean

  constructor(options: AppErrorOptions) {
    super(options.message)
    this.name = 'AppError'
    this.kind = options.kind
    this.status = options.status
    this.code = options.code
    this.correlationId = options.correlationId
    this.retryAfter = options.retryAfter
    this.technicalDetail = options.technicalDetail
    this.cause = options.cause
    this.retryable = options.retryable ?? defaultRetryable(options.kind, options.status)
    this.outcomeUnknown = options.outcomeUnknown ?? false

    Object.setPrototypeOf(this, new.target.prototype)
  }
}

export interface AppErrorDefaults {
  kind?: AppErrorKind
  message?: string
  status?: number
  code?: string
  correlationId?: string
  retryAfter?: number | string
  technicalDetail?: string
  retryable?: boolean
  outcomeUnknown?: boolean
}

export type AppErrorOperation = 'read' | 'mutation'

export interface FormatAppErrorOptions {
  title: string
  operation: AppErrorOperation
  refreshLabel?: string
  retryLabel?: string
}

export interface FormattedErrorAction {
  label: string
  kind: 'retry' | 'refresh'
}

export interface FormattedErrorDetail {
  label: 'Status' | 'Code' | 'Correlation ID'
  value: string
}

export interface FormattedAppError {
  title: string
  message: string
  details?: FormattedErrorDetail[]
  action?: FormattedErrorAction
}

const UNSAFE_MESSAGE_PATTERNS = [
  /<\/?(?:html|body|title|script|style|pre|head)\b/i,
  /<!doctype\s+html/i,
  /\b(?:java\.|javax\.|org\.springframework\.)/i,
  /\b(?:[A-Za-z_$][\w$]*(?:Error|Exception)|exception|stack\s*trace|traceback)\s*:/i,
  /(?:^|\s)at\s+[\w.$<>]+\([^)]*:\d+\)/i,
  /https?:\/\//i,
  /(?:password|passwd|secret|token|api[_-]?key|authorization)\s*[:=]/i,
]

const GENERIC_SERVER_MESSAGES = new Set([
  'internal server error',
  'bad gateway',
  'service unavailable',
  'gateway timeout',
])

function defaultRetryable(kind: AppErrorKind, status?: number): boolean {
  if (kind === 'network' || kind === 'timeout' || kind === 'rate-limit') return true
  return kind === 'server' && status !== undefined && [502, 503, 504].includes(status)
}

function hasErrorName(value: unknown, name: string): boolean {
  if (typeof value !== 'object' || value === null) return false
  return 'name' in value && (value as { name?: unknown }).name === name
}

export function isAppError(error: unknown): error is AppError {
  return error instanceof AppError
}

export function isSafeHumanMessage(value: unknown): value is string {
  if (typeof value !== 'string') return false
  const message = value.trim()
  if (message.length === 0 || message.length > 500) return false
  if (GENERIC_SERVER_MESSAGES.has(message.toLowerCase())) return false
  return !UNSAFE_MESSAGE_PATTERNS.some((pattern) => pattern.test(message))
}

export function safeHumanMessage(value: unknown, fallback: string): string {
  return isSafeHumanMessage(value) ? value.trim() : fallback
}

export function safeMetadata(value: unknown): string | undefined {
  if (typeof value !== 'string' && typeof value !== 'number') return undefined
  const normalized = String(value).trim()
  if (!/^[A-Za-z0-9._:/-]{1,160}$/.test(normalized)) return undefined
  return normalized
}

export function asAppError(error: unknown, defaults: AppErrorDefaults = {}): AppError {
  if (isAppError(error)) return error

  if (hasErrorName(error, 'AbortError')) {
    return new AppError({
      kind: defaults.kind ?? 'cancelled',
      message: defaults.message ?? 'The request was cancelled.',
      status: defaults.status,
      code: defaults.code,
      correlationId: defaults.correlationId,
      retryAfter: defaults.retryAfter,
      technicalDetail: defaults.technicalDetail,
      cause: error,
      retryable: defaults.retryable ?? false,
      outcomeUnknown: defaults.outcomeUnknown ?? false,
    })
  }

  if (error instanceof TypeError) {
    return new AppError({
      kind: defaults.kind ?? 'network',
      message: defaults.message ?? 'The backend could not be reached.',
      status: defaults.status,
      code: defaults.code,
      correlationId: defaults.correlationId,
      retryAfter: defaults.retryAfter,
      technicalDetail: defaults.technicalDetail,
      cause: error,
      retryable: defaults.retryable ?? true,
      outcomeUnknown: defaults.outcomeUnknown ?? false,
    })
  }

  return new AppError({
    kind: defaults.kind ?? 'unknown',
    message: defaults.message ?? 'An unexpected error occurred.',
    status: defaults.status,
    code: defaults.code,
    correlationId: defaults.correlationId,
    retryAfter: defaults.retryAfter,
    technicalDetail: defaults.technicalDetail,
    cause: error,
    retryable: defaults.retryable ?? false,
    outcomeUnknown: defaults.outcomeUnknown ?? false,
  })
}

function safeKindMessage(error: AppError): string {
  switch (error.kind) {
    case 'network':
      return 'Can’t reach the backend. Check the service, then retry.'
    case 'timeout':
      return 'The request took too long. Try again.'
    case 'authentication':
      return 'Your session is no longer valid. Sign in again, then retry.'
    case 'permission':
      return 'You don’t have permission to complete this request.'
    case 'validation':
      return safeHumanMessage(error.message, 'Check the entered values and try again.')
    case 'not-found':
      return safeHumanMessage(error.message, 'The requested item could not be found.')
    case 'conflict':
      return safeHumanMessage(
        error.message,
        'The item changed before the request completed. Refresh and try again.'
      )
    case 'rate-limit':
      return 'Too many requests were sent. Wait a moment, then try again.'
    case 'server':
      return 'The backend couldn’t complete the request. Try again.'
    case 'malformed-response':
      return 'The backend returned an unexpected response. Try again.'
    case 'cancelled':
      return 'The request was cancelled.'
    case 'runtime':
      return 'This part of the dashboard couldn’t be displayed.'
    case 'unknown':
      return 'Something went wrong. Try again.'
  }
}

function formattedDetails(error: AppError): FormattedErrorDetail[] | undefined {
  const details: FormattedErrorDetail[] = []

  if (Number.isInteger(error.status) && error.status !== undefined) {
    details.push({ label: 'Status', value: String(error.status) })
  }

  const code = safeMetadata(error.code)
  if (code) details.push({ label: 'Code', value: code })

  const correlationId = safeMetadata(error.correlationId)
  if (correlationId) details.push({ label: 'Correlation ID', value: correlationId })

  return details.length > 0 ? details : undefined
}

export function formatAppError(
  errorLike: unknown,
  options: FormatAppErrorOptions
): FormattedAppError {
  const error = asAppError(errorLike)
  const details = formattedDetails(error)
  let message = safeKindMessage(error)
  let action: FormattedErrorAction | undefined

  if (options.operation === 'mutation' && error.outcomeUnknown) {
    message =
      'The request was interrupted, so the result couldn’t be confirmed. Refresh the current status before trying again.'
    action = { label: options.refreshLabel ?? 'Refresh status', kind: 'refresh' }
  } else if (error.retryable) {
    action = { label: options.retryLabel ?? 'Retry', kind: 'retry' }
  }

  return {
    title: options.title,
    message,
    ...(details ? { details } : {}),
    ...(action ? { action } : {}),
  }
}
