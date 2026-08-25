import { rawFetch, errResponse } from './shared'
import type { ApiResponse, AnalysisProgress, FullAnalysisResult, CompositeAnalysis } from './types'

export async function getCompositeAnalysis(
  symbol: string
): Promise<ApiResponse<CompositeAnalysis>> {
  const raw = await rawFetch(`/analysis/analyze?symbol=${encodeURIComponent(symbol)}`, {
    method: 'POST',
  })
  if (!raw.ok) return errResponse(raw.error!)
  return { success: true, data: raw.data as CompositeAnalysis }
}

export async function* runFullAnalysis(
  symbol: string,
  years: number = 3
): AsyncIterable<AnalysisProgress | FullAnalysisResult> {
  const { API_BASE_URL, DEFAULT_HEADERS } = await import('./config')
  const params = new URLSearchParams({
    symbol: encodeURIComponent(symbol),
    years: String(years),
  })
  const url = `${API_BASE_URL}/analysis/run-full?${params}`
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
    throw new Error(`Analysis failed: ${response.statusText} ${text.slice(0, 200)}`)
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
