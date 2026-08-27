import { safeHumanMessage, type AppError } from '../errors/appError'
import { MalformedResponseError } from '../errors/errorClasses'
import { apiRequest, apiSseEvents, toNum } from './shared'
import type { Signal } from './types'

interface BackendSignal {
  id: number | null
  symbol: string
  date: string
  signalType: 'BUY' | 'SELL' | 'HOLD'
  confidence: number | string
  reasoning: string
  entryPrice: number | string | null
  stopLoss: number | string | null
  target: number | string | null
  riskRewardRatio: number | string | null
  indicators?: string[] | null
  generatedAt: string | null
  strategy?: string | null
  sentimentScore?: string | null
  sentimentReasoning?: string | null
}

interface BackendGenerateAllResponse {
  signals: BackendSignal[]
  skipped: Array<{ symbol: string; reason: string }>
}

export interface SignalGenerationProgress {
  eventType:
    'STARTED' | 'GENERATING' | 'SENTIMENT_ANALYZING' | 'SIGNAL_DONE' | 'SKIPPED' | 'COMPLETE'
  symbol?: string
  status: 'PROCESSING' | 'DONE' | 'SKIPPED' | 'ERROR'
  message: string
  current?: number
  total?: number
  signal?: Signal
}

const mapSignal = (s: BackendSignal): Signal => ({
  id: s.id === null ? `${s.symbol}-${s.date}` : String(s.id),
  symbol: s.symbol,
  direction: s.signalType as 'BUY' | 'SELL' | 'HOLD',
  confidence: Math.round(toNum(s.confidence) * 100),
  reason: s.reasoning,
  entryPrice: toNum(s.entryPrice),
  stopLoss: toNum(s.stopLoss),
  target: toNum(s.target),
  riskReward: toNum(s.riskRewardRatio),
  timestamp: s.generatedAt ?? s.date,
  status: 'ACTIVE',
  strategy: s.strategy ?? undefined,
  indicators: s.indicators ?? undefined,
  sentimentScore: s.sentimentScore ?? undefined,
  sentimentReasoning: s.sentimentReasoning ?? undefined,
})

export async function getSignals(): Promise<Signal[]> {
  const signals = await apiRequest<BackendSignal[]>('/signals/latest', {
    method: 'GET',
    responseContract: 'direct',
  })
  return signals.map(mapSignal)
}

export async function generateAllSignals(): Promise<{
  signals: Signal[]
  skipped: Array<{ symbol: string; reason: string }>
}> {
  const response = await apiRequest<BackendGenerateAllResponse>('/signals/generate-all', {
    method: 'POST',
    responseContract: 'direct',
  })
  return { signals: (response.signals ?? []).map(mapSignal), skipped: response.skipped ?? [] }
}

export interface SignalStreamOptions {
  signal?: AbortSignal
  timeoutMs?: number
}

const SIGNAL_EVENT_TYPES = new Set<SignalGenerationProgress['eventType']>([
  'STARTED',
  'GENERATING',
  'SENTIMENT_ANALYZING',
  'SIGNAL_DONE',
  'SKIPPED',
  'COMPLETE',
])
const SIGNAL_EVENT_STATUSES = new Set<SignalGenerationProgress['status']>([
  'PROCESSING',
  'DONE',
  'SKIPPED',
  'ERROR',
])

function invalidSignalEvent(message: string): AppError {
  return new MalformedResponseError({
    message,
    retryable: false,
    outcomeUnknown: true,
  })
}

function isNumericWireValue(value: unknown): value is number | string {
  return (
    (typeof value === 'number' && Number.isFinite(value)) ||
    (typeof value === 'string' && value.trim() !== '' && Number.isFinite(Number(value)))
  )
}

function isNullableNumericWireValue(value: unknown): value is number | string | null {
  return value === null || isNumericWireValue(value)
}

function isBackendSignal(value: unknown): value is BackendSignal {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) return false
  const signal = value as Record<string, unknown>
  return (
    (signal.id === null || typeof signal.id === 'number') &&
    typeof signal.symbol === 'string' &&
    typeof signal.date === 'string' &&
    typeof signal.signalType === 'string' &&
    ['BUY', 'SELL', 'HOLD'].includes(signal.signalType) &&
    isNumericWireValue(signal.confidence) &&
    typeof signal.reasoning === 'string' &&
    isNullableNumericWireValue(signal.entryPrice) &&
    isNullableNumericWireValue(signal.stopLoss) &&
    isNullableNumericWireValue(signal.target) &&
    isNullableNumericWireValue(signal.riskRewardRatio) &&
    (signal.generatedAt === null || typeof signal.generatedAt === 'string') &&
    (signal.strategy === undefined ||
      signal.strategy === null ||
      typeof signal.strategy === 'string') &&
    (signal.sentimentScore === undefined ||
      signal.sentimentScore === null ||
      typeof signal.sentimentScore === 'string') &&
    (signal.sentimentReasoning === undefined ||
      signal.sentimentReasoning === null ||
      typeof signal.sentimentReasoning === 'string') &&
    (signal.indicators === undefined ||
      signal.indicators === null ||
      (Array.isArray(signal.indicators) &&
        signal.indicators.every((item) => typeof item === 'string')))
  )
}

function safeSignalProgressMessage(
  message: string,
  eventType: SignalGenerationProgress['eventType'],
  symbol?: string
): string {
  let fallback = 'Signal generation progress was updated.'
  if (eventType === 'SKIPPED') {
    fallback = symbol ? `Couldn’t generate a signal for ${symbol}.` : 'Signal generation failed.'
  } else if (eventType === 'SIGNAL_DONE') {
    fallback = symbol ? `Signal generated for ${symbol}.` : 'Signal generation completed.'
  } else if (eventType === 'COMPLETE') {
    fallback = 'Signal generation completed.'
  }
  return safeHumanMessage(message, fallback)
}

function signalProgressFromWire(payload: unknown): SignalGenerationProgress {
  if (typeof payload !== 'object' || payload === null || Array.isArray(payload)) {
    throw invalidSignalEvent('The signal stream contained an invalid event.')
  }

  const event = payload as Record<string, unknown>
  if (
    typeof event.eventType !== 'string' ||
    !SIGNAL_EVENT_TYPES.has(event.eventType as SignalGenerationProgress['eventType']) ||
    typeof event.status !== 'string' ||
    !SIGNAL_EVENT_STATUSES.has(event.status as SignalGenerationProgress['status']) ||
    typeof event.message !== 'string' ||
    (event.symbol !== undefined && typeof event.symbol !== 'string') ||
    (event.current !== undefined && typeof event.current !== 'number') ||
    (event.total !== undefined && typeof event.total !== 'number') ||
    (event.eventType === 'COMPLETE' && event.status !== 'DONE')
  ) {
    throw invalidSignalEvent('The signal stream contained an invalid event.')
  }

  const progress = {
    ...event,
    message: safeSignalProgressMessage(
      event.message,
      event.eventType as SignalGenerationProgress['eventType'],
      typeof event.symbol === 'string' ? event.symbol : undefined
    ),
  } as unknown as SignalGenerationProgress
  if (event.signal !== undefined) {
    if (!isBackendSignal(event.signal)) {
      throw invalidSignalEvent('The signal stream contained an invalid signal.')
    }
    progress.signal = mapSignal(event.signal)
  }
  return progress
}

export function generateAllSignalsStream(
  options: SignalStreamOptions = {}
): AsyncIterable<SignalGenerationProgress> {
  return apiSseEvents('/signals/generate-all/stream', {
    method: 'POST',
    signal: options.signal,
    timeoutMs: options.timeoutMs,
    parseEvent: signalProgressFromWire,
    isTerminal: (event) => event.eventType === 'COMPLETE',
  })
}

export async function clearAllSignals(): Promise<{ cleared: number }> {
  return apiRequest<{ cleared: number }>('/signals', {
    method: 'DELETE',
    responseContract: 'direct',
  })
}

export async function clearSignalsForSymbol(symbol: string): Promise<{ cleared: number }> {
  return apiRequest<{ cleared: number }>(`/signals/${symbol}`, {
    method: 'DELETE',
    responseContract: 'direct',
  })
}

export async function getSignalsByType(type: 'BUY' | 'SELL' | 'HOLD'): Promise<Signal[]> {
  const signals = await apiRequest<BackendSignal[]>(`/signals/type/${type}`, {
    responseContract: 'direct',
  })
  return signals.map(mapSignal)
}

export async function getLatestSignalForSymbol(symbol: string): Promise<Signal | null> {
  const data = await apiRequest<BackendSignal[]>(`/signals/symbol/${symbol}`, {
    responseContract: 'direct',
  })
  const latest = data.length > 0 ? data[data.length - 1] : null
  return latest ? mapSignal(latest) : null
}

export async function getHighConfidenceSignals(minConfidence: number = 0.7): Promise<Signal[]> {
  const signals = await apiRequest<BackendSignal[]>(
    `/signals/high-confidence?minConfidence=${minConfidence}`,
    { responseContract: 'direct' }
  )
  return signals.map(mapSignal)
}

export async function generatePriceActionSignal(symbol: string): Promise<Signal> {
  const signal = await apiRequest<BackendSignal>(`/signals/price-action/${symbol}/generate`, {
    method: 'POST',
    responseContract: 'direct',
  })
  return mapSignal(signal)
}

export async function triggerScan(): Promise<{ signalsFound: number; status: string }> {
  return apiRequest<{ signalsFound: number; status: string }>('/signals/scan', {
    method: 'POST',
    responseContract: 'direct',
  })
}
