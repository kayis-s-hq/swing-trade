import { beforeEach, describe, expect, it } from 'vitest'
import { AppError } from '../errors/appError'
import { clearRuntimeErrors, getRuntimeErrorState, reportRuntimeError } from './runtimeErrors'

describe('runtimeErrors', () => {
  beforeEach(() => {
    clearRuntimeErrors()
  })

  it('records an unexpected exception as a typed, safe runtime failure with reporting context', () => {
    const cause = new Error('private render implementation detail')

    const record = reportRuntimeError(cause, {
      source: 'vue',
      info: 'render function',
      route: '/positions',
    })

    expect(record.error).toBeInstanceOf(AppError)
    expect(record.error).toMatchObject({
      kind: 'runtime',
      cause,
      retryable: false,
      outcomeUnknown: false,
    })
    expect(record.error.message).not.toContain(cause.message)
    expect(record).toMatchObject({
      source: 'vue',
      info: 'render function',
      route: '/positions',
    })
    expect(getRuntimeErrorState().errors).toEqual([record])
  })

  it('records the same exception object only once when Vue and router handlers both see it', () => {
    const cause = new Error('same propagated failure')

    reportRuntimeError(cause, {
      source: 'vue',
      info: 'render function',
    })
    reportRuntimeError(cause, {
      source: 'router',
      route: '/signals',
    })

    expect(getRuntimeErrorState().errors).toHaveLength(1)
    expect(getRuntimeErrorState().errors[0]).toMatchObject({ source: 'vue' })
  })

  it('does not collapse distinct exception occurrences that happen to share a message', () => {
    reportRuntimeError(new Error('chunk unavailable'), { source: 'router', route: '/signals' })
    reportRuntimeError(new Error('chunk unavailable'), { source: 'router', route: '/signals' })

    expect(getRuntimeErrorState().errors).toHaveLength(2)
  })
})
