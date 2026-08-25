import { rawFetch, unwrap, errResponse } from './shared'
import type { ApiResponse, WatchlistEntry } from './types'

export async function getWatchlist(): Promise<ApiResponse<WatchlistEntry[]>> {
  const raw = await rawFetch('/watchlist')
  if (!raw.ok) return errResponse(raw.error!)
  return { success: true, data: unwrap<WatchlistEntry[]>(raw) }
}

export async function addToWatchlist(
  symbol: string,
  name?: string,
  exchange?: string
): Promise<ApiResponse<WatchlistEntry>> {
  const params = new URLSearchParams({ symbol: symbol.toUpperCase().trim() })
  if (name) params.set('name', name)
  if (exchange) params.set('exchange', exchange)
  const raw = await rawFetch(`/watchlist?${params}`, { method: 'POST' })
  if (!raw.ok) return errResponse(raw.error!)
  return { success: true, data: unwrap<WatchlistEntry>(raw) }
}

export async function removeFromWatchlist(symbol: string): Promise<ApiResponse<string>> {
  const raw = await rawFetch(`/watchlist/${symbol}`, { method: 'DELETE' })
  if (!raw.ok) return errResponse(raw.error!)
  return { success: true, data: unwrap<string>(raw) }
}

export async function toggleWatchlistActive(
  symbol: string,
  activate: boolean
): Promise<ApiResponse<WatchlistEntry>> {
  const raw = await rawFetch(`/watchlist/${symbol}/toggle?activate=${activate}`, {
    method: 'PATCH',
  })
  if (!raw.ok) return errResponse(raw.error!)
  return { success: true, data: unwrap<WatchlistEntry>(raw) }
}
