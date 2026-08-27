import { describe, expect, it } from 'vitest'
import {
  CancelledError,
  MalformedResponseError,
  NetworkError,
  RuntimeAppError,
  TimeoutError,
  isErrorKind,
} from './errorClasses'

describe('typed error classes', () => {
  it('provides safe defaults and preserves shared AppError metadata', () => {
    expect(new NetworkError()).toMatchObject({ kind: 'network', retryable: true })
    expect(new TimeoutError()).toMatchObject({ kind: 'timeout', retryable: true })
    expect(new CancelledError()).toMatchObject({ kind: 'cancelled', retryable: false })
    expect(new MalformedResponseError()).toMatchObject({
      kind: 'malformed-response',
      retryable: false,
    })
    expect(new RuntimeAppError()).toMatchObject({ kind: 'runtime', retryable: false })
  })

  it('identifies an AppError by its stable kind', () => {
    const error = new NetworkError({ status: 503, correlationId: 'req-7' })
    expect(isErrorKind(error, 'network')).toBe(true)
    expect(isErrorKind(error, 'server')).toBe(false)
    expect(isErrorKind(new Error('network'), 'network')).toBe(false)
  })
})
