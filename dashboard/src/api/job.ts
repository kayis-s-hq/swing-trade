import { rawFetch, errResponse } from './shared'
import type {
  ApiResponse,
  JobRunResponse,
  JobRunProgressResponse,
  JobRunSummaryResponse,
} from './types'

export async function startJobRun(triggerType = 'MANUAL'): Promise<ApiResponse<JobRunResponse>> {
  const params = new URLSearchParams({ triggerType })
  const raw = await rawFetch(`/job/runs/start?${params}`, { method: 'POST' })
  if (!raw.ok) return errResponse(raw.error!)
  return { success: true, data: raw.data as JobRunResponse }
}

export async function getJobRunProgress(
  runId: string
): Promise<ApiResponse<JobRunProgressResponse>> {
  const raw = await rawFetch(`/job/runs/${runId}/progress`)
  if (!raw.ok) return errResponse(raw.error!)
  return { success: true, data: raw.data as JobRunProgressResponse }
}

export async function getJobRunSummary(runId: string): Promise<ApiResponse<JobRunSummaryResponse>> {
  const raw = await rawFetch(`/job/runs/${runId}/summary`)
  if (!raw.ok) return errResponse(raw.error!)
  return { success: true, data: raw.data as JobRunSummaryResponse }
}

export async function listJobRuns(): Promise<ApiResponse<JobRunResponse[]>> {
  const raw = await rawFetch('/job/runs')
  if (!raw.ok) return errResponse(raw.error!)
  return { success: true, data: raw.data as JobRunResponse[] }
}

export async function cancelJobRun(runId: string): Promise<ApiResponse<void>> {
  const raw = await rawFetch(`/job/runs/${runId}/cancel`, { method: 'POST' })
  if (!raw.ok) return errResponse(raw.error!)
  return { success: true, data: undefined }
}
