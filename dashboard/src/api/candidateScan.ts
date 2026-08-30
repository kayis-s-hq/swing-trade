import { API_BASE_URL } from './config'
import { apiRequest } from './shared'
import type { CandidateScanLogEvent, CandidateScanResult, CandidateScanRun } from './types'

export function startCandidateScan(): Promise<CandidateScanRun> {
  return apiRequest<CandidateScanRun>('/candidate-scans', {
    method: 'POST',
    responseContract: 'direct',
  })
}

export function getCandidateScan(runId: string): Promise<CandidateScanRun> {
  return apiRequest<CandidateScanRun>(`/candidate-scans/${runId}`, {
    responseContract: 'direct',
  })
}

export function getCandidateScanResults(
  runId: string,
  offset = 0,
  limit = 100
): Promise<CandidateScanResult[]> {
  return apiRequest<CandidateScanResult[]>(
    `/candidate-scans/${runId}/results?offset=${offset}&limit=${limit}`,
    { responseContract: 'direct' }
  )
}

export function cancelCandidateScan(runId: string): Promise<CandidateScanRun> {
  return apiRequest<CandidateScanRun>(`/candidate-scans/${runId}/cancel`, {
    method: 'POST',
    responseContract: 'direct',
  })
}

export function getCandidateScanHistory(): Promise<CandidateScanRun[]> {
  return apiRequest<CandidateScanRun[]>('/candidate-scans', {
    responseContract: 'direct',
  })
}

export function openCandidateScanStream(
  runId: string,
  onLog: (event: CandidateScanLogEvent) => void,
  onError?: () => void
): EventSource | null {
  if (typeof EventSource === 'undefined') return null
  const source = new EventSource(`${API_BASE_URL}/candidate-scans/${runId}/stream`)
  source.addEventListener('log', (event) => {
    try {
      onLog(JSON.parse((event as MessageEvent).data) as CandidateScanLogEvent)
    } catch {
      onError?.()
    }
  })
  source.onerror = () => onError?.()
  return source
}
