import { rawFetch, toNum, errResponse } from './shared'
import type { ApiResponse, Signal } from './types'

interface BackendSignal {
  id: number
  symbol: string
  date: string
  signalType: 'BUY' | 'SELL' | 'HOLD'
  confidence: number | string
  reasoning: string
  entryPrice: number | string
  stopLoss: number | string
  target: number | string
  riskRewardRatio: number | string
  indicators?: string[]
  generatedAt: string
  strategy?: string
  sentimentScore?: string
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
  id: String(s.id),
  symbol: s.symbol,
  direction: s.signalType as 'BUY' | 'SELL' | 'HOLD',
  confidence: Math.round(toNum(s.confidence) * 100),
  reason: s.reasoning,
  entryPrice: toNum(s.entryPrice),
  stopLoss: toNum(s.stopLoss),
  target: toNum(s.target),
  riskReward: toNum(s.riskRewardRatio),
  timestamp: s.generatedAt,
  status: 'ACTIVE',
  strategy: s.strategy,
  indicators: s.indicators,
  sentimentScore: s.sentimentScore,
})

export async function getSignals(): Promise<ApiResponse<Signal[]>> {
  const raw = await rawFetch('/signals/latest')
  if (!raw.ok) return errResponse(raw.error!)
  return { success: true, data: (raw.data as BackendSignal[]).map(mapSignal) }
}

export async function generateAllSignals(): Promise<
  ApiResponse<{ signals: Signal[]; skipped: Array<{ symbol: string; reason: string }> }>
> {
  const raw = await rawFetch('/signals/generate-all', { method: 'POST' })
  if (!raw.ok) return errResponse(raw.error!)
  const resp = raw.data as BackendGenerateAllResponse
  return {
    success: true,
    data: { signals: (resp.signals ?? []).map(mapSignal), skipped: resp.skipped ?? [] },
  }
}

export async function* generateAllSignalsStream(): AsyncIterable<SignalGenerationProgress> {
  const { API_BASE_URL } = await import('./config')
  const { DEFAULT_HEADERS } = await import('./config')
  const url = `${API_BASE_URL}/signals/generate-all/stream`
  const controller = new AbortController()
  const timeoutId = setTimeout(() => controller.abort(), 600_000)

  const response = await fetch(url, {
    method: 'POST',
    headers: DEFAULT_HEADERS,
    signal: controller.signal,
  })

  if (!response.ok) {
    const text = await response.text().catch(() => '')
    clearTimeout(timeoutId)
    throw new Error(`Signal generation failed: ${response.statusText} ${text.slice(0, 200)}`)
  }

  const reader = response.body!.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let currentEvent = 'progress'

  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''
      for (const line of lines) {
        const trimmed = line.trim()
        const eventMatch = trimmed.match(/^event:\s*(\S+)/)
        if (eventMatch) {
          currentEvent = eventMatch[1]
          continue
        }
        const dataPrefix = 'data:'
        if (trimmed.startsWith(dataPrefix)) {
          try {
            const data = JSON.parse(trimmed.slice(dataPrefix.length).trim())
            yield { ...data, _eventType: currentEvent }
          } catch {
            // Skip malformed JSON
          }
        }
      }
    }
  } finally {
    clearTimeout(timeoutId)
    reader.releaseLock()
  }
}

export async function clearAllSignals(): Promise<ApiResponse<{ cleared: number }>> {
  const raw = await rawFetch('/signals', { method: 'DELETE' })
  if (!raw.ok) return errResponse(raw.error!)
  const resp = raw.data as { cleared: number }
  return { success: true, data: resp }
}

export async function clearSignalsForSymbol(
  symbol: string
): Promise<ApiResponse<{ cleared: number }>> {
  const raw = await rawFetch(`/signals/${symbol}`, { method: 'DELETE' })
  if (!raw.ok) return errResponse(raw.error!)
  const resp = raw.data as { cleared: number }
  return { success: true, data: resp }
}

export async function getSignalsByType(
  type: 'BUY' | 'SELL' | 'HOLD'
): Promise<ApiResponse<Signal[]>> {
  const raw = await rawFetch(`/signals/type/${type}`)
  if (!raw.ok) return errResponse(raw.error!)
  return { success: true, data: (raw.data as BackendSignal[]).map(mapSignal) }
}

export async function getLatestSignalForSymbol(
  symbol: string
): Promise<ApiResponse<Signal | null>> {
  const raw = await rawFetch(`/signals/symbol/${symbol}`)
  if (!raw.ok) return errResponse(raw.error!)
  const data = raw.data as BackendSignal[]
  const latest = data.length > 0 ? data[data.length - 1] : null
  if (!latest) return { success: true, data: null }
  return { success: true, data: mapSignal(latest) }
}

export async function getHighConfidenceSignals(
  minConfidence: number = 0.7
): Promise<ApiResponse<Signal[]>> {
  const raw = await rawFetch(`/signals/high-confidence?minConfidence=${minConfidence}`)
  if (!raw.ok) return errResponse(raw.error!)
  return { success: true, data: (raw.data as BackendSignal[]).map(mapSignal) }
}

export async function generatePriceActionSignal(symbol: string): Promise<ApiResponse<Signal>> {
  const raw = await rawFetch(`/signals/price-action/${symbol}/generate`, { method: 'POST' })
  if (!raw.ok) return errResponse(raw.error!)
  return { success: true, data: mapSignal(raw.data as BackendSignal) }
}

export async function triggerScan(): Promise<
  ApiResponse<{ signalsFound: number; status: string }>
> {
  const raw = await rawFetch('/signals/scan', { method: 'POST' })
  if (!raw.ok) return errResponse(raw.error!)
  return { success: true, data: raw.data as { signalsFound: number; status: string } }
}