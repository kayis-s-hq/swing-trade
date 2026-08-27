import { describe, expect, it } from 'vitest'
import { AppError, asAppError, formatAppError, isAppError } from './appError'

describe('AppError', () => {
  describe('typed failure metadata', () => {
    it('preserves the failure classification and diagnostic metadata', () => {
      const cause = new Error('socket closed')
      const error = new AppError({
        kind: 'server',
        message: 'The service is unavailable',
        status: 503,
        code: 'UPSTREAM_UNAVAILABLE',
        correlationId: 'req-42',
        retryAfter: 30,
        technicalDetail: 'upstream connection reset',
        cause,
        retryable: true,
        outcomeUnknown: false,
      })

      expect(error).toBeInstanceOf(Error)
      expect(error.name).toBe('AppError')
      expect(error).toMatchObject({
        kind: 'server',
        message: 'The service is unavailable',
        status: 503,
        code: 'UPSTREAM_UNAVAILABLE',
        correlationId: 'req-42',
        retryAfter: 30,
        technicalDetail: 'upstream connection reset',
        cause,
        retryable: true,
        outcomeUnknown: false,
      })
      expect(isAppError(error)).toBe(true)
      expect(isAppError(cause)).toBe(false)
    })
  })

  describe('asAppError', () => {
    it('returns an existing AppError without replacing its metadata', () => {
      const original = new AppError({
        kind: 'conflict',
        message: 'Position changed',
        status: 409,
        code: 'POSITION_VERSION_CONFLICT',
      })

      expect(asAppError(original)).toBe(original)
    })

    it('classifies fetch TypeErrors as network failures without exposing native copy', () => {
      const nativeError = new TypeError('Failed to fetch at https://internal.example/api')

      const error = asAppError(nativeError)
      const formatted = formatAppError(error, {
        title: 'Couldn’t load positions',
        operation: 'read',
      })

      expect(error).toMatchObject({ kind: 'network', cause: nativeError, retryable: true })
      expect(formatted.message).toBe('Can’t reach the backend. Check the service, then retry.')
      expect(JSON.stringify(formatted)).not.toContain('internal.example')
    })

    it('classifies AbortError as cancellation unless transport identifies a timeout', () => {
      const nativeError = new DOMException('The operation was aborted.', 'AbortError')

      expect(asAppError(nativeError)).toMatchObject({
        kind: 'cancelled',
        cause: nativeError,
        retryable: false,
        outcomeUnknown: false,
      })
    })

    it('wraps unexpected runtime values as unknown failures and retains the cause', () => {
      const nativeError = new Error('Cannot read private implementation detail')

      const error = asAppError(nativeError)
      const formatted = formatAppError(error, {
        title: 'Couldn’t load dashboard data',
        operation: 'read',
      })

      expect(error).toMatchObject({ kind: 'unknown', cause: nativeError })
      expect(formatted.title).toBe('Couldn’t load dashboard data')
      expect(formatted.message).not.toContain('private implementation detail')
    })
  })

  describe('operation-aware formatting', () => {
    it('offers an explicit retry for retryable read failures', () => {
      const error = new AppError({
        kind: 'timeout',
        message: 'Request timeout (30000ms)',
        retryable: true,
      })

      const formatted = formatAppError(error, {
        title: 'Couldn’t load signals',
        operation: 'read',
      })

      expect(formatted).toMatchObject({
        title: 'Couldn’t load signals',
        message: 'The request took too long. Try again.',
        action: { label: 'Retry' },
      })
    })

    it('requires status refresh instead of blind retry when a mutation outcome is unknown', () => {
      const error = new AppError({
        kind: 'timeout',
        message: 'Request timeout (30000ms)',
        retryable: false,
        outcomeUnknown: true,
      })

      const formatted = formatAppError(error, {
        title: 'Order status couldn’t be confirmed',
        operation: 'mutation',
        refreshLabel: 'Refresh positions',
      })

      expect(formatted.title).toBe('Order status couldn’t be confirmed')
      expect(formatted.message).toContain('couldn’t be confirmed')
      expect(formatted.action).toMatchObject({ label: 'Refresh positions' })
      expect(JSON.stringify(formatted.action)).not.toMatch(/retry order/i)
    })

    it('shows only safe status, code, and correlation metadata in formatted details', () => {
      const error = new AppError({
        kind: 'server',
        message: '<html><body>Internal Server Error</body></html>',
        status: 503,
        code: 'UPSTREAM_FAILURE',
        correlationId: 'corr-safe-7',
        technicalDetail: 'java.lang.IllegalStateException: database_password=hunter2',
        retryable: true,
      })

      const formatted = formatAppError(error, {
        title: 'Couldn’t load portfolio',
        operation: 'read',
      })
      const rendered = JSON.stringify(formatted)
      const details = JSON.stringify(formatted.details)

      expect(formatted.message).not.toContain('<html>')
      expect(formatted.message).not.toContain('Internal Server Error')
      expect(details).toContain('503')
      expect(details).toContain('UPSTREAM_FAILURE')
      expect(details).toContain('corr-safe-7')
      expect(rendered).not.toContain('IllegalStateException')
      expect(rendered).not.toContain('hunter2')
    })

    it('does not expose raw exception or HTML content for malformed responses', () => {
      const error = new AppError({
        kind: 'malformed-response',
        message: '<!doctype html><title>Proxy failure</title>',
        technicalDetail: 'SyntaxError: Unexpected token < in JSON at position 0',
      })

      const formatted = formatAppError(error, {
        title: 'Couldn’t load settings',
        operation: 'read',
      })
      const rendered = JSON.stringify(formatted)

      expect(formatted.message).toMatch(/unexpected response/i)
      expect(rendered).not.toContain('<!doctype')
      expect(rendered).not.toContain('SyntaxError')
    })
  })
})
