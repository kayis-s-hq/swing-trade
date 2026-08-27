import { reactive } from 'vue'
import { RuntimeAppError } from '../errors/errorClasses'
import type { AppError } from '../errors/appError'

export type RuntimeErrorSource = 'vue' | 'router' | 'boundary'

export interface RuntimeErrorContext {
  source: RuntimeErrorSource
  info?: string
  route?: string
}

export interface RuntimeErrorRecord extends RuntimeErrorContext {
  error: AppError
  occurredAt: number
}

const state = reactive<{ errors: RuntimeErrorRecord[] }>({ errors: [] })
let reportedObjects = new WeakMap<object, RuntimeErrorRecord>()

export function reportRuntimeError(
  cause: unknown,
  context: RuntimeErrorContext
): RuntimeErrorRecord {
  if ((typeof cause === 'object' && cause !== null) || typeof cause === 'function') {
    const existing = reportedObjects.get(cause as object)
    if (existing) return existing
  }

  const record: RuntimeErrorRecord = {
    error: new RuntimeAppError({
      cause,
      retryable: false,
      outcomeUnknown: false,
    }),
    source: context.source,
    ...(context.info ? { info: context.info } : {}),
    ...(context.route ? { route: context.route } : {}),
    occurredAt: Date.now(),
  }

  state.errors.push(record)
  if ((typeof cause === 'object' && cause !== null) || typeof cause === 'function') {
    reportedObjects.set(cause as object, record)
  }
  return record
}

export function getRuntimeErrorState() {
  return state
}

export function clearRuntimeErrors(): void {
  state.errors.splice(0)
  reportedObjects = new WeakMap<object, RuntimeErrorRecord>()
}
