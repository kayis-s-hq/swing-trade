import { apiRequest } from './shared'
import type { IngestionStatus, PullProgress } from './types'

export async function getIngestionStatus(): Promise<IngestionStatus[]> {
  return apiRequest<IngestionStatus[]>('/data/status', {
    method: 'GET',
    responseContract: 'direct',
  })
}

export async function triggerDataPull(
  yearsBack: number = 1
): Promise<{ pullId: string; message: string }> {
  return apiRequest<{ pullId: string; message: string }>(`/data/pull?yearsBack=${yearsBack}`, {
    method: 'POST',
    responseContract: 'direct',
  })
}

export async function getPullProgress(pullId?: string): Promise<PullProgress> {
  const params = pullId ? `?pullId=${pullId}` : ''
  return apiRequest<PullProgress>(`/data/pull/progress${params}`, {
    method: 'GET',
    responseContract: 'direct',
  })
}

export async function cancelDataPull(): Promise<string> {
  return apiRequest<string>('/data/pull/cancel', {
    method: 'POST',
    responseContract: 'direct',
  })
}
