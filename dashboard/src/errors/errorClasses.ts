import { AppError, type AppErrorKind, type AppErrorOptions } from './appError'

type SpecificOptions = Omit<AppErrorOptions, 'kind' | 'message'> & { message?: string }

export class NetworkError extends AppError {
  constructor(options: SpecificOptions = {}) {
    super({
      ...options,
      kind: 'network',
      message: options.message ?? 'The backend could not be reached.',
      retryable: options.retryable ?? true,
    })
  }
}

export class TimeoutError extends AppError {
  constructor(options: SpecificOptions = {}) {
    super({
      ...options,
      kind: 'timeout',
      message: options.message ?? 'The request took too long.',
      retryable: options.retryable ?? true,
    })
  }
}

export class CancelledError extends AppError {
  constructor(options: SpecificOptions = {}) {
    super({
      ...options,
      kind: 'cancelled',
      message: options.message ?? 'The request was cancelled.',
      retryable: false,
    })
  }
}

export class MalformedResponseError extends AppError {
  constructor(options: SpecificOptions = {}) {
    super({
      ...options,
      kind: 'malformed-response',
      message: options.message ?? 'The backend returned an unexpected response.',
      retryable: false,
    })
  }
}

export class RuntimeAppError extends AppError {
  constructor(options: SpecificOptions = {}) {
    super({
      ...options,
      kind: 'runtime',
      message: options.message ?? 'This part of the dashboard could not be displayed.',
      retryable: false,
    })
  }
}

export function isErrorKind(error: unknown, kind: AppErrorKind): error is AppError {
  return error instanceof AppError && error.kind === kind
}
