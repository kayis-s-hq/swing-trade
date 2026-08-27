import { apiRequest } from './shared'
import type { HealthStatus } from './types'

export interface BackendHealthResponse {
  status: string
  components: Record<string, unknown>
}

export async function checkHealth(): Promise<BackendHealthResponse> {
  return apiRequest<BackendHealthResponse>('/health', {
    method: 'GET',
    responseContract: 'direct',
  })
}

export async function getHealthStatus(): Promise<HealthStatus> {
  return apiRequest<HealthStatus>('/health/full', {
    responseContract: 'envelope',
  })
}
