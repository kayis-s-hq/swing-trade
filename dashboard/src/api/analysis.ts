import { safeHumanMessage, type AppError } from '../errors/appError'
import { MalformedResponseError } from '../errors/errorClasses'
import { apiRequest, apiSseEvents } from './shared'
import type { AnalysisProgress, FullAnalysisResult, CompositeAnalysis } from './types'

export async function getCompositeAnalysis(symbol: string): Promise<CompositeAnalysis> {
  return apiRequest<CompositeAnalysis>(`/analysis/analyze?symbol=${encodeURIComponent(symbol)}`, {
    method: 'POST',
    responseContract: 'direct',
  })
}

export interface AnalysisStreamOptions {
  signal?: AbortSignal
  timeoutMs?: number
}

const ANALYSIS_STATUSES = new Set<AnalysisProgress['status']>([
  'running',
  'completed',
  'skipped',
  'error',
])

function invalidAnalysisEvent(message: string): AppError {
  return new MalformedResponseError({
    message,
    retryable: false,
    outcomeUnknown: true,
  })
}

function safeAnalysisProgressMessage(
  message: string,
  stageName: string,
  status: AnalysisProgress['status']
): string {
  const fallback =
    status === 'error'
      ? `The ${stageName || 'analysis'} stage couldn’t be completed.`
      : 'Analysis progress was updated.'
  return safeHumanMessage(message, fallback)
}

function analysisEventFromWire(
  payload: unknown,
  eventName: string
): AnalysisProgress | FullAnalysisResult {
  if (typeof payload !== 'object' || payload === null || Array.isArray(payload)) {
    throw invalidAnalysisEvent('The analysis stream contained an invalid event.')
  }

  const event = payload as Record<string, unknown>
  if (eventName === 'complete') {
    if (
      typeof event.symbol !== 'string' ||
      typeof event.durationMs !== 'number' ||
      !Array.isArray(event.progress) ||
      (event.composite !== null &&
        (typeof event.composite !== 'object' || Array.isArray(event.composite)))
    ) {
      throw invalidAnalysisEvent('The analysis stream contained an invalid completion event.')
    }

    const progress = event.progress.map((item) => {
      if (typeof item !== 'object' || item === null || Array.isArray(item)) return item
      const stage = item as Record<string, unknown>
      if (
        typeof stage.message !== 'string' ||
        typeof stage.stageName !== 'string' ||
        typeof stage.status !== 'string' ||
        !ANALYSIS_STATUSES.has(stage.status as AnalysisProgress['status'])
      ) {
        return item
      }
      return {
        ...stage,
        message: safeAnalysisProgressMessage(
          stage.message,
          stage.stageName,
          stage.status as AnalysisProgress['status']
        ),
      }
    })

    return { ...event, progress, _eventType: 'complete' } as unknown as FullAnalysisResult
  }

  if (
    eventName !== 'progress' ||
    typeof event.stageNumber !== 'number' ||
    typeof event.stageName !== 'string' ||
    typeof event.status !== 'string' ||
    !ANALYSIS_STATUSES.has(event.status as AnalysisProgress['status']) ||
    typeof event.message !== 'string' ||
    typeof event.timestamp !== 'string'
  ) {
    throw invalidAnalysisEvent('The analysis stream contained an invalid progress event.')
  }

  return {
    ...event,
    message: safeAnalysisProgressMessage(
      event.message,
      event.stageName,
      event.status as AnalysisProgress['status']
    ),
    _eventType: 'progress',
  } as unknown as AnalysisProgress
}

export function runFullAnalysis(
  symbol: string,
  years: number = 3,
  options: AnalysisStreamOptions = {}
): AsyncIterable<AnalysisProgress | FullAnalysisResult> {
  const params = new URLSearchParams({ symbol, backfillYears: String(years) })
  return apiSseEvents(`/analysis/run-full?${params}`, {
    method: 'POST',
    signal: options.signal,
    timeoutMs: options.timeoutMs,
    ignoredEventNames: ['started', 'ping'],
    parseEvent: analysisEventFromWire,
    isTerminal: (_event, eventName) => eventName === 'complete',
  })
}
