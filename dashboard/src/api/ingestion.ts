import { rawFetch, unwrap, errResponse } from './shared'
import type { ApiResponse, IngestionStatus, PullProgress } from './types'

export async function getIngestionStatus(): Promise<ApiResponse<IngestionStatus[]>> {
  const raw = await rawFetch('/data/status')
  if (!raw.ok) return errResponse(raw.error!)
  return { success: true, data: unwrap<IngestionStatus[]>(raw) }
}

export async function triggerDataPull(
  yearsBack: number = 1
): Promise<ApiResponse<{ pullId: string; message: string }>> {
  const raw = await rawFetch(`/data/pull?yearsBack=${yearsBack}`, { method: 'POST' })
  if (!raw.ok) return errResponse(raw.error!)
  return { success: true, data: unwrap<{ pullId: string; message: string }>(raw) }
}

export async function getPullProgress(pullId?: string): Promise<ApiResponse<PullProgress>> {
  const params = pullId ? `?pullId=${pullId}` : ''
  const raw = await rawFetch(`/data/pull/progress${params}`)
  if (!raw.ok) return errResponse(raw.error!)
  return { success: true, data: unwrap<PullProgress>(raw) }
}

export async function cancelDataPull(): Promise<ApiResponse<string>> {
  const raw = await rawFetch('/data/pull/cancel', { method: 'POST' })
  if (!raw.ok) return errResponse(raw.error!)
  return { success: true, data: unwrap<string>(raw) }
}
