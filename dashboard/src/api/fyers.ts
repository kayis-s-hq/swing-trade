import { rawFetch, errResponse } from './shared'
import type { ApiResponse, FyersStatus, FyersLoginUrl } from './types'

export async function getFyersLoginUrl(): Promise<ApiResponse<FyersLoginUrl>> {
  const raw = await rawFetch('/fyers/login')
  if (!raw.ok) return errResponse(raw.error!)
  return { success: true, data: raw.data as FyersLoginUrl }
}

export async function fyersAuthCode(authCode: string): Promise<ApiResponse<FyersStatus>> {
  const raw = await rawFetch('/fyers/auth', {
    method: 'POST',
    body: JSON.stringify({ authCode }),
  })
  if (!raw.ok) return errResponse(raw.error!)
  const data = raw.data as { status: string; message: string }
  return { success: true, data: { connected: data.status === 'success', clientId: '' } }
}

export async function getFyersStatus(): Promise<ApiResponse<FyersStatus>> {
  const raw = await rawFetch('/fyers/status')
  if (!raw.ok) return errResponse(raw.error!)
  return { success: true, data: raw.data as unknown as FyersStatus }
}

export async function fyersLogout(): Promise<ApiResponse<FyersStatus>> {
  const raw = await rawFetch('/fyers/logout', { method: 'POST' })
  if (!raw.ok) return errResponse(raw.error!)
  return { success: true, data: raw.data as unknown as FyersStatus }
}