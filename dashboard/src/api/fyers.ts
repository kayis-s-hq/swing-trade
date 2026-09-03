import { apiRequest } from './shared'
import type { FyersStatus, FyersLoginUrl } from './types'

export async function getFyersLoginUrl(): Promise<FyersLoginUrl> {
  return apiRequest<FyersLoginUrl>('/fyers/login', { responseContract: 'direct' })
}

export async function fyersAuthCode(authCode: string): Promise<FyersStatus> {
  const data = await apiRequest<{ status: string }>('/fyers/auth', {
    method: 'POST',
    body: JSON.stringify({ authCode }),
    responseContract: 'direct',
  })
  return { connected: data.status === 'success', clientId: '' }
}

export async function getFyersStatus(): Promise<FyersStatus> {
  return apiRequest<FyersStatus>('/fyers/status', { responseContract: 'direct' })
}

export async function fyersLogout(): Promise<FyersStatus> {
  return apiRequest<FyersStatus>('/fyers/logout', {
    method: 'POST',
    responseContract: 'direct',
  })
}
