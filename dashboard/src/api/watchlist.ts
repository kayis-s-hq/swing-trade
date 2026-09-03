import { apiRequest } from './shared'
import type { WatchlistEntry } from './types'

export async function getWatchlist(): Promise<WatchlistEntry[]> {
  return apiRequest<WatchlistEntry[]>('/watchlist', {
    method: 'GET',
    responseContract: 'envelope',
  })
}

export async function addToWatchlist(
  symbol: string,
  name?: string,
  exchange?: string
): Promise<WatchlistEntry> {
  const params = new URLSearchParams({ symbol: symbol.toUpperCase().trim() })
  if (name) params.set('name', name)
  if (exchange) params.set('exchange', exchange)
  return apiRequest<WatchlistEntry>(`/watchlist?${params}`, {
    method: 'POST',
    responseContract: 'envelope',
  })
}

export async function removeFromWatchlist(symbol: string): Promise<string> {
  return apiRequest<string>(`/watchlist/${symbol}`, {
    method: 'DELETE',
    responseContract: 'envelope',
  })
}

export async function toggleWatchlistActive(
  symbol: string,
  activate: boolean
): Promise<WatchlistEntry> {
  return apiRequest<WatchlistEntry>(`/watchlist/${symbol}/toggle?activate=${activate}`, {
    method: 'PATCH',
    responseContract: 'envelope',
  })
}
