import { rawFetch, errResponse } from './shared'
import type { ApiResponse, HealthStatus } from './types'

export async function checkHealth(): Promise<ApiResponse<{ status: string; components: Record<string, any> }>> {
  const raw = await rawFetch('/health')
  if (!raw.ok) return errResponse(raw.error!)
  return { success: true, data: raw.data as { status: string; components: Record<string, any> } }
}

export async function getHealthStatus(): Promise<ApiResponse<HealthStatus>> {
  const raw = await rawFetch('/health/full')
  if (!raw.ok) return errResponse(raw.error!)
  return { success: true, data: raw.data as HealthStatus }
}