import { apiRequest } from './shared'
import type { IngestionStatus, PullProgress } from './types'

export async function getIngestionStatus(): Promise<IngestionStatus[]> {
  return apiRequest<IngestionStatus[]>('/data/status', {
    method: 'GET',
    responseContract: 'envelope',
  })
}

export async function triggerDataPull(
  yearsBack: number = 3
): Promise<{ pullId: string; message: string }> {
  return apiRequest<{ pullId: string; message: string }>(`/data/pull?yearsBack=${yearsBack}`, {
    method: 'POST',
    responseContract: 'envelope',
  })
}

export async function triggerDataPullRange(
  from: string,
  to: string
): Promise<{ pullId: string; status: string }> {
  return apiRequest<{ pullId: string; status: string }>(
    `/ingestion/range?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`,
    { method: 'POST', responseContract: 'envelope' }
  )
}

export async function ingestSelectedDate(
  symbol: string,
  date: string
): Promise<{ symbol: string; date: string; status: string }> {
  return apiRequest<{ symbol: string; date: string; status: string }>(
    `/ingestion/date?symbol=${encodeURIComponent(symbol)}&date=${encodeURIComponent(date)}`,
    { method: 'POST', responseContract: 'envelope' }
  )
}

export async function getPullProgress(pullId?: string): Promise<PullProgress> {
  const params = pullId ? `?pullId=${pullId}` : ''
  return apiRequest<PullProgress>(`/data/pull/progress${params}`, {
    method: 'GET',
    responseContract: 'envelope',
  })
}

export async function cancelDataPull(): Promise<string> {
  return apiRequest<string>('/data/pull/cancel', {
    method: 'POST',
    responseContract: 'envelope',
  })
}
