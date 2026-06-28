import { API_BASE_URL, DEFAULT_HEADERS, REQUEST_TIMEOUT } from './config'
import type {
  ApiResponse,
  PortfolioSummary,
  MarketOverview,
  Position,
  Signal,
  EquityPoint,
  HealthStatus,
  FyersStatus,
  FyersLoginUrl,
  WatchlistEntry,
  IngestionStatus,
  PullProgress,
} from './types'

// ---------------------------------------------------------------------------
// Low-level fetch wrapper — returns raw JSON + status
// ---------------------------------------------------------------------------

interface RawFetchResult {
  ok: boolean
  data: unknown
  error?: string
}

async function rawFetch(path: string, init?: RequestInit): Promise<RawFetchResult> {
  const controller = new AbortController()
  const timeoutId = setTimeout(() => controller.abort(), REQUEST_TIMEOUT)

  try {
    const response = await fetch(`${API_BASE_URL}${path}`, {
      ...init,
      headers: { ...DEFAULT_HEADERS, ...init?.headers },
      signal: controller.signal,
    })

    let data: unknown = null
    const contentType = response.headers.get('content-type') || ''

    if (contentType.includes('application/json')) {
      try {
        data = await response.json()
      } catch {
        data = null
      }
    } else {
      // Non-JSON response (HTML error page, plain text, etc.)
      const text = await response.text().catch(() => '')
      data = { message: text.slice(0, 200) || `Server responded with status ${response.status}` }
    }

    if (!response.ok) {
      const msg = (data as any)?.message
      return {
        ok: false,
        data: null,
        error: msg && msg !== 'Internal Server Error'
          ? msg
          : `Server responded with status ${response.status}`,
      }
    }

    return { ok: true, data }
  } catch (err: unknown) {
    if (err instanceof DOMException && err.name === 'AbortError') {
      return { ok: false, data: null, error: `Request timeout (${REQUEST_TIMEOUT}ms)` }
    }
    if (err instanceof TypeError && err.message.includes('fetch')) {
      return { ok: false, data: null, error: 'Unable to connect to the backend server. Is it running?' }
    }
    return { ok: false, data: null, error: err instanceof Error ? err.message : 'Network error' }
  } finally {
    clearTimeout(timeoutId)
  }
}

// ---------------------------------------------------------------------------
// Backend DTO types (internal mapping layer)
// ---------------------------------------------------------------------------

interface BackendPosition {
  id: number
  symbol: string
  entryPrice: number | string
  entryDate: string
  quantity: number
  stopLoss: number | string | null
  target: number | string | null
  status: 'OPEN' | 'CLOSED' | 'STOPPED' | 'TARGET_HIT'
  entryReason?: string
  currentPrice: number | string | null
  unrealizedPnL: number | string | null
  unrealizedPnLPercent: number | string | null
  totalValue?: number | string
}

interface BackendSignal {
  id: number
  symbol: string
  date: string
  signalType: 'BUY' | 'SELL' | 'HOLD'
  confidence: number | string
  reasoning: string
  entryPrice: number | string
  stopLoss: number | string
  target: number | string
  riskRewardRatio: number | string
  indicators?: string[]
  generatedAt: string
}

interface BackendPerformance {
  totalReturn: number | string
  annualizedReturn: number | string
  sharpeRatio: number | string
  maxDrawdown: number | string
  totalTrades: number
  winningTrades: number
  losingTrades: number
  winRate: number | string
  averageWin: number | string
  averageLoss: number | string
  profitFactor: number | string
  totalPnL: number | string
  closedTrades: number
  asOfDate: string
}

interface BackendPositionStats {
  totalPositions: number
  openPositions: number
  closedPositions: number
  totalValue: number
  totalPnL: number
}

interface BackendPaginated<T> {
  content: T[]
  totalElements: number
  totalPages: number
  size: number
  number: number
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

const toNum = (v: number | string | null | undefined): number => {
  if (v === null || v === undefined) return 0
  return typeof v === 'string' ? parseFloat(v) : v
}

const errResponse = <T>(error: string): ApiResponse<T> => ({ success: false, error })

const mapPosition = (p: BackendPosition): Position => ({
  id: String(p.id),
  symbol: p.symbol,
  entryPrice: toNum(p.entryPrice),
  currentPrice: toNum(p.currentPrice),
  quantity: p.quantity,
  status: (p.status === 'STOPPED' || p.status === 'TARGET_HIT') ? 'CLOSED' : p.status,
  pnl: toNum(p.unrealizedPnL),
  pnlPercent: toNum(p.unrealizedPnLPercent),
  entryDate: p.entryDate,
  stopLoss: p.stopLoss != null ? toNum(p.stopLoss) : undefined,
  target: p.target != null ? toNum(p.target) : undefined,
})

const mapSignal = (s: BackendSignal): Signal => ({
  id: String(s.id),
  symbol: s.symbol,
  direction: s.signalType === 'HOLD' ? 'BUY' : s.signalType,
  confidence: Math.round(toNum(s.confidence) * 100),
  reason: s.reasoning,
  entryPrice: toNum(s.entryPrice),
  stopLoss: toNum(s.stopLoss),
  target: toNum(s.target),
  riskReward: toNum(s.riskRewardRatio),
  timestamp: s.generatedAt,
  status: 'ACTIVE',
})

// ---------------------------------------------------------------------------
// Public API functions
// ---------------------------------------------------------------------------

export async function getPortfolioSummary(): Promise<ApiResponse<PortfolioSummary>> {
  const raw = await rawFetch('/trades/performance')
  if (!raw.ok) return errResponse(raw.error!)

  const perf = raw.data as BackendPerformance
  const totalPnL = toNum(perf.totalPnL)
  const totalReturn = toNum(perf.totalReturn)

  return {
    success: true,
    data: {
      totalValue: 0, // Would need a separate endpoint or derive from positions
      totalPnl: totalPnL,
      totalPnlPercent: totalReturn,
      winRate: toNum(perf.winRate),
      totalTrades: perf.totalTrades ?? perf.closedTrades ?? 0,
      averageWin: toNum(perf.averageWin),
      averageLoss: toNum(perf.averageLoss),
      profitFactor: toNum(perf.profitFactor),
      maxDrawdown: toNum(perf.maxDrawdown),
      sharpeRatio: toNum(perf.sharpeRatio),
    },
  }
}

export async function getMarketOverview(): Promise<ApiResponse<MarketOverview>> {
  const raw = await rawFetch('/positions/stats')
  if (!raw.ok) return errResponse(raw.error!)

  const stats = raw.data as BackendPositionStats
  return {
    success: true,
    data: {
      totalPositions: stats.totalPositions,
      openPositions: stats.openPositions,
      todayPnl: toNum(stats.totalPnL),
      todayPnlPercent: 0, // Backend doesn't expose daily PnL yet
    },
  }
}

export async function getPositions(): Promise<ApiResponse<Position[]>> {
  const raw = await rawFetch('/trades')
  if (!raw.ok) return errResponse(raw.error!)

  return { success: true, data: (raw.data as BackendPosition[]).map(mapPosition) }
}

export async function getClosedPositions(): Promise<ApiResponse<Position[]>> {
  const raw = await rawFetch('/positions/closed')
  if (!raw.ok) return errResponse(raw.error!)

  // Backend returns paginated response { content, totalElements, ... }
  const paginated = raw.data as BackendPaginated<BackendPosition>
  const positions: BackendPosition[] = paginated.content ?? []

  return { success: true, data: positions.map(mapPosition) }
}

export async function getSignals(): Promise<ApiResponse<Signal[]>> {
  const raw = await rawFetch('/signals/latest')
  if (!raw.ok) return errResponse(raw.error!)

  return { success: true, data: (raw.data as BackendSignal[]).map(mapSignal) }
}

export async function getSignalsByType(type: 'BUY' | 'SELL'): Promise<ApiResponse<Signal[]>> {
  const raw = await rawFetch(`/signals/type/${type}`)
  if (!raw.ok) return errResponse(raw.error!)

  return { success: true, data: (raw.data as BackendSignal[]).map(mapSignal) }
}

export async function getHighConfidenceSignals(minConfidence: number = 0.7): Promise<ApiResponse<Signal[]>> {
  const raw = await rawFetch(`/signals/high-confidence?minConfidence=${minConfidence}`)
  if (!raw.ok) return errResponse(raw.error!)

  return { success: true, data: (raw.data as BackendSignal[]).map(mapSignal) }
}

export async function getTradeHistory(limit: number = 10): Promise<ApiResponse<Position[]>> {
  const raw = await rawFetch('/positions/closed?page=0&size=' + limit)
  if (!raw.ok) return errResponse(raw.error!)

  const paginated = raw.data as BackendPaginated<BackendPosition>
  const positions: BackendPosition[] = paginated.content ?? []

  return { success: true, data: positions.map(mapPosition) }
}

// ---------------------------------------------------------------------------
// Write operations
// ---------------------------------------------------------------------------

export async function closePosition(symbol: string, exitReason?: string): Promise<ApiResponse<Position>> {
  const raw = await rawFetch(`/trades/${symbol}/close`, {
    method: 'POST',
    body: exitReason ? JSON.stringify({ exitReason }) : undefined,
  })
  if (!raw.ok) return errResponse(raw.error!)

  return { success: true, data: mapPosition(raw.data as BackendPosition) }
}

export async function triggerScan(): Promise<ApiResponse<{ signalsFound: number; status: string }>> {
  const raw = await rawFetch('/signals/scan', { method: 'POST' })
  if (!raw.ok) return errResponse(raw.error!)
  return { success: true, data: raw.data as { signalsFound: number; status: string } }
}

export async function checkHealth(): Promise<ApiResponse<{ status: string; components: Record<string, any> }>> {
  const raw = await rawFetch('/health')
  if (!raw.ok) return errResponse(raw.error!)
  return { success: true, data: raw.data as { status: string; components: Record<string, any> } }
}

// ---------------------------------------------------------------------------
// Health status (full)
// ---------------------------------------------------------------------------

export async function getHealthStatus(): Promise<ApiResponse<HealthStatus>> {
  const raw = await rawFetch('/health/full')
  if (!raw.ok) return errResponse(raw.error!)
  return { success: true, data: raw.data as HealthStatus }
}

// ---------------------------------------------------------------------------
// Fyers Auth
// ---------------------------------------------------------------------------

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

// ---------------------------------------------------------------------------
// Equity curve — reconstruct from closed positions
// ---------------------------------------------------------------------------

export async function getEquityCurve(_range: string = '1M'): Promise<ApiResponse<{ data: EquityPoint[] }>> {
  const raw = await rawFetch('/positions/closed')
  if (!raw.ok) return errResponse(raw.error!)

  const paginated = raw.data as BackendPaginated<BackendPosition>
  const positions: BackendPosition[] = paginated.content ?? []

  // Build cumulative P&L from closed trades sorted by entry date
  const sorted = positions
    .filter(p => p.status === 'CLOSED' || p.status === 'STOPPED' || p.status === 'TARGET_HIT')
    .sort((a, b) => a.entryDate.localeCompare(b.entryDate))

  if (sorted.length === 0) {
    return { success: true, data: { data: [] } }
  }

  let cumulative = 0
  const points: EquityPoint[] = sorted.map(p => {
    const pnl = toNum(p.unrealizedPnL)
    cumulative += pnl
    return { date: p.entryDate, value: cumulative }
  })

  return { success: true, data: { data: points } }
}

// ---------------------------------------------------------------------------
// Watchlist
// ---------------------------------------------------------------------------

export async function getWatchlist(): Promise<ApiResponse<WatchlistEntry[]>> {
  const raw = await rawFetch('/watchlist')
  if (!raw.ok) return errResponse(raw.error!)
  return { success: true, data: (raw.data as any).data as WatchlistEntry[] }
}

export async function addToWatchlist(symbol: string, name?: string, exchange?: string): Promise<ApiResponse<WatchlistEntry>> {
  const params = new URLSearchParams({ symbol: symbol.toUpperCase().trim() })
  if (name) params.set('name', name)
  if (exchange) params.set('exchange', exchange)
  const raw = await rawFetch(`/watchlist?${params}`, { method: 'POST' })
  if (!raw.ok) return errResponse(raw.error!)
  return { success: true, data: (raw.data as any).data as WatchlistEntry }
}

export async function removeFromWatchlist(symbol: string): Promise<ApiResponse<string>> {
  const raw = await rawFetch(`/watchlist/${symbol}`, { method: 'DELETE' })
  if (!raw.ok) return errResponse(raw.error!)
  return { success: true, data: (raw.data as any).data as string }
}

export async function toggleWatchlistActive(symbol: string, activate: boolean): Promise<ApiResponse<WatchlistEntry>> {
  const raw = await rawFetch(`/watchlist/${symbol}/toggle?activate=${activate}`, { method: 'PATCH' })
  if (!raw.ok) return errResponse(raw.error!)
  return { success: true, data: (raw.data as any).data as WatchlistEntry }
}

// ---------------------------------------------------------------------------
// Data Ingestion
// ---------------------------------------------------------------------------

export async function getIngestionStatus(): Promise<ApiResponse<IngestionStatus[]>> {
  const raw = await rawFetch('/data/status')
  if (!raw.ok) return errResponse(raw.error!)
  return { success: true, data: (raw.data as any).data as IngestionStatus[] }
}

export async function triggerDataPull(yearsBack: number = 1): Promise<ApiResponse<{ pullId: string; message: string }>> {
  const raw = await rawFetch(`/data/pull?yearsBack=${yearsBack}`, { method: 'POST' })
  if (!raw.ok) return errResponse(raw.error!)
  return { success: true, data: (raw.data as any).data as { pullId: string; message: string } }
}

export async function getPullProgress(pullId?: string): Promise<ApiResponse<PullProgress>> {
  const params = pullId ? `?pullId=${pullId}` : ''
  const raw = await rawFetch(`/data/pull/progress${params}`)
  if (!raw.ok) return errResponse(raw.error!)
  return { success: true, data: (raw.data as any).data as PullProgress }
}

export async function cancelDataPull(): Promise<ApiResponse<string>> {
  const raw = await rawFetch('/data/pull/cancel', { method: 'POST' })
  if (!raw.ok) return errResponse(raw.error!)
  return { success: true, data: (raw.data as any).data as string }
}

// ---------------------------------------------------------------------------
// Settings
// ---------------------------------------------------------------------------

export async function getSettings(): Promise<ApiResponse<{ selectedBroker: string }>> {
  const raw = await rawFetch('/settings')
  if (!raw.ok) return errResponse(raw.error!)
  return { success: true, data: (raw.data as any).data as { selectedBroker: string } }
}

export async function setBroker(broker: string): Promise<ApiResponse<{ selectedBroker: string }>> {
  const raw = await rawFetch('/settings/broker', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ broker }),
  })
  if (!raw.ok) return errResponse(raw.error!)
  return { success: true, data: (raw.data as any).data as { selectedBroker: string } }
}
