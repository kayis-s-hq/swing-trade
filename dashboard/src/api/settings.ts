import { rawFetch, unwrap, errResponse } from './shared'
import type { ApiResponse } from './types'

export async function getSettings(): Promise<ApiResponse<{ selectedBroker: string }>> {
  const raw = await rawFetch('/settings')
  if (!raw.ok) return errResponse(raw.error!)
  return { success: true, data: unwrap<{ selectedBroker: string }>(raw) }
}

export async function setBroker(broker: string): Promise<ApiResponse<{ selectedBroker: string }>> {
  const raw = await rawFetch('/settings/broker', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ broker }),
  })
  if (!raw.ok) return errResponse(raw.error!)
  return { success: true, data: unwrap<{ selectedBroker: string }>(raw) }
}

export async function getLlmSettings(): Promise<ApiResponse<Record<string, string>>> {
  const raw = await rawFetch('/settings/llm')
  if (!raw.ok) return errResponse(raw.error!)
  return { success: true, data: unwrap<Record<string, string>>(raw) }
}

export async function setLlmSettings(
  settings: Record<string, string>
): Promise<ApiResponse<Record<string, string>>> {
  const raw = await rawFetch('/settings/llm', {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(settings),
  })
  if (!raw.ok) return errResponse(raw.error!)
  return { success: true, data: unwrap<Record<string, string>>(raw) }
}

export async function getDiscordSettings(): Promise<ApiResponse<Record<string, string>>> {
  const raw = await rawFetch('/settings/discord')
  if (!raw.ok) return errResponse(raw.error!)
  return { success: true, data: unwrap<Record<string, string>>(raw) }
}

export async function setDiscordSettings(
  settings: Record<string, string>
): Promise<ApiResponse<Record<string, string>>> {
  const raw = await rawFetch('/settings/discord', {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(settings),
  })
  if (!raw.ok) return errResponse(raw.error!)
  return { success: true, data: unwrap<Record<string, string>>(raw) }
}

export async function testDiscordWebhook(): Promise<ApiResponse<{ success: boolean }>> {
  const raw = await rawFetch('/settings/test/discord', { method: 'POST' })
  if (!raw.ok) return errResponse(raw.error!)
  return { success: true, data: unwrap<{ success: boolean }>(raw) }
}

export async function testPiConnection(): Promise<
  ApiResponse<{ success: boolean; message: string }>
> {
  const raw = await rawFetch('/settings/test/pi', { method: 'POST' })
  if (!raw.ok) return errResponse(raw.error!)
  return { success: true, data: unwrap<{ success: boolean; message: string }>(raw) }
}

export async function testOpenAiConnection(): Promise<
  ApiResponse<{ success: boolean; message: string }>
> {
  const raw = await rawFetch('/settings/test/openai', { method: 'POST' })
  if (!raw.ok) return errResponse(raw.error!)
  return { success: true, data: unwrap<{ success: boolean; message: string }>(raw) }
}

export async function getGpuHubSettings(): Promise<ApiResponse<Record<string, string>>> {
  const raw = await rawFetch('/settings/gpuhub')
  if (!raw.ok) return errResponse(raw.error!)
  return { success: true, data: unwrap<Record<string, string>>(raw) }
}

export async function setGpuHubSettings(
  settings: Record<string, string>
): Promise<ApiResponse<Record<string, string>>> {
  const raw = await rawFetch('/settings/gpuhub', {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(settings),
  })
  if (!raw.ok) return errResponse(raw.error!)
  return { success: true, data: unwrap<Record<string, string>>(raw) }
}

export async function startPiServer(): Promise<
  ApiResponse<{ success: boolean; running: boolean; message: string }>
> {
  const raw = await rawFetch('/settings/pi/start', { method: 'POST' })
  if (!raw.ok) return errResponse(raw.error!)
  return {
    success: true,
    data: unwrap<{ success: boolean; running: boolean; message: string }>(raw),
  }
}

export async function stopPiServer(): Promise<
  ApiResponse<{ success: boolean; running: boolean; message: string }>
> {
  const raw = await rawFetch('/settings/pi/stop', { method: 'POST' })
  if (!raw.ok) return errResponse(raw.error!)
  return {
    success: true,
    data: unwrap<{ success: boolean; running: boolean; message: string }>(raw),
  }
}

export async function getPiServerStatus(): Promise<
  ApiResponse<{ success: boolean; running: boolean; message: string }>
> {
  const raw = await rawFetch('/settings/pi/status')
  if (!raw.ok) return errResponse(raw.error!)
  return {
    success: true,
    data: unwrap<{ success: boolean; running: boolean; message: string }>(raw),
  }
}

export async function getTradingSettings(): Promise<ApiResponse<Record<string, string>>> {
  const raw = await rawFetch('/settings/trading')
  if (!raw.ok) return errResponse(raw.error!)
  return { success: true, data: unwrap<Record<string, string>>(raw) }
}

export async function setTradingSettings(
  settings: Record<string, string>
): Promise<ApiResponse<Record<string, string>>> {
  const raw = await rawFetch('/settings/trading', {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(settings),
  })
  if (!raw.ok) return errResponse(raw.error!)
  return { success: true, data: unwrap<Record<string, string>>(raw) }
}

export async function saveAllSettings(body: {
  broker?: string
  llm?: Record<string, string>
  discord?: Record<string, string>
  trading?: Record<string, string>
}): Promise<ApiResponse<Record<string, string>>> {
  const raw = await rawFetch('/settings/save', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!raw.ok) return errResponse(raw.error!)
  return { success: true, data: unwrap<Record<string, string>>(raw) }
}
