import { apiRequest } from './shared'
import type {
  JobRunResponse,
  JobRunProgressResponse,
  JobRunSummaryResponse,
  StartJobRunRequest,
} from './types'

export async function startJobRun(
  triggerType = 'MANUAL',
  request?: StartJobRunRequest
): Promise<JobRunResponse> {
  const params = new URLSearchParams({ triggerType })
  return apiRequest<JobRunResponse>(`/job/runs/start?${params}`, {
    method: 'POST',
    responseContract: 'direct',
    ...(request ? { body: JSON.stringify(request) } : {}),
  })
}

export async function getJobRunProgress(runId: string): Promise<JobRunProgressResponse> {
  return apiRequest<JobRunProgressResponse>(`/job/runs/${runId}/progress`, {
    responseContract: 'direct',
  })
}

export async function getJobRunSummary(runId: string): Promise<JobRunSummaryResponse> {
  return apiRequest<JobRunSummaryResponse>(`/job/runs/${runId}/summary`, {
    responseContract: 'direct',
  })
}

export async function listJobRuns(): Promise<JobRunResponse[]> {
  return apiRequest<JobRunResponse[]>('/job/runs', { responseContract: 'direct' })
}

export async function cancelJobRun(runId: string): Promise<void> {
  await apiRequest<unknown>(`/job/runs/${runId}/cancel`, {
    method: 'POST',
    responseContract: 'direct',
  })
}
