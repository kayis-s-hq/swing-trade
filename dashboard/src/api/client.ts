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
  BacktestResult,
  BacktestReportSummary,
  SentimentResult,
  SentimentAccuracyStats,
  AccuracySummary,
  AccuracyByWindow,
  AccuracyByRegime,
  AccuracyBySymbol,
  CalibrationData,
  RollingIC,
  SignalVolumeStats,
  ECEStats,
  NewsArticle,
  CompositeAnalysis,
  AnalysisProgress,
  FullAnalysisResult,
  HolidayListResponse,
  TodayHolidayStatus,
  JobRunResponse,
  JobRunProgressResponse,
  JobRunSummaryResponse,
} from './types'

// ---------------------------------------------------------------------------
// Low-level fetch wrapper — returns raw JSON + status
// ---------------------------------------------------------------------------

interface RawFetchResult {
  ok: boolean
  data: unknown
  error?: string
}

async function rawFetch(
  path: string,
  init?: RequestInit,
  retries = 3
): Promise<RawFetchResult> {
  let lastError: string = ''
  let lastData: unknown = null

  for (let attempt = 0; attempt <= retries; attempt++) {
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
        const text = await response.text().catch(() => '')
        data = { message: text.slice(0, 200) || `Server responded with status ${response.status}` }
      }

      if (!response.ok) {
        lastData = data
        const msg = (data as any)?.message
        lastError =
          msg && msg !== 'Internal Server Error'
            ? msg
            : `Server responded with status ${response.status}`

        // Retry on 5xx or server errors
        if (response.status >= 500 && attempt < retries) {
          const delay = 1000 * Math.pow(2, attempt)
          await new Promise((r) => setTimeout(r, delay))
          continue
        }
        return { ok: false, data: null, error: lastError }
      }

      return { ok: true, data }
    } catch (err: unknown) {
      if (err instanceof DOMException && err.name === 'AbortError') {
        lastError = `Request timeout (${REQUEST_TIMEOUT}ms)`
      } else if (err instanceof TypeError && err.message.includes('fetch')) {
        lastError = 'Unable to connect to the backend server. Is it running?'
      } else {
        lastError = err instanceof Error ? err.message : 'Network error'
      }

      // Retry on network errors
      if (attempt < retries) {
        const delay = 1000 * Math.pow(2, attempt)
        await new Promise((r) => setTimeout(r, delay))
        continue
      }
    } finally {
      clearTimeout(timeoutId)
    }
  }

  return { ok: false, data: null, error: lastError }
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
  strategy?: string
  sentimentScore?: string
}

interface BackendGenerateAllResponse {
  signals: BackendSignal[]
  skipped: Array<{ symbol: string; reason: string }>
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

const unwrap = <T>(raw: RawFetchResult): T => {
  const resp = raw.data as ApiResponse<T> | null
  return resp?.data as T
}

const mapPosition = (p: BackendPosition): Position => ({
  id: String(p.id),
  symbol: p.symbol,
  entryPrice: toNum(p.entryPrice),
  currentPrice: toNum(p.currentPrice),
  quantity: p.quantity,
  status: p.status === 'STOPPED' || p.status === 'TARGET_HIT' ? 'CLOSED' : p.status,
  pnl: toNum(p.unrealizedPnL),
  pnlPercent: toNum(p.unrealizedPnLPercent),
  entryDate: p.entryDate,
  stopLoss: p.stopLoss != null ? toNum(p.stopLoss) : undefined,
  target: p.target != null ? toNum(p.target) : undefined,
})

const mapSignal = (s: BackendSignal): Signal => ({
  id: String(s.id),
  symbol: s.symbol,
  direction: s.signalType as 'BUY' | 'SELL' | 'HOLD',
  confidence: Math.round(toNum(s.confidence) * 100),
  reason: s.reasoning,
  entryPrice: toNum(s.entryPrice),
  stopLoss: toNum(s.stopLoss),
  target: toNum(s.target),
  riskReward: toNum(s.riskRewardRatio),
  timestamp: s.generatedAt,
  status: 'ACTIVE',
  strategy: s.strategy,
  indicators: s.indicators,
  sentimentScore: s.sentimentScore,
})

// ---------------------------------------------------------------------------
// Public API functions
// ---------------------------------------------------------------------------

export async function getPortfolioSummary(): Promise<ApiResponse<PortfolioSummary>> {
  const raw = await rawFetch('/positions/performance')
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
  const raw = await rawFetch('/positions')
  if (!raw.ok) return errResponse(raw.error!)

  const paginated = raw.data as BackendPaginated<BackendPosition>
  const positions: BackendPosition[] = paginated.content ?? []
  return { success: true, data: positions.map(mapPosition) }
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

export async function generateAllSignals(): Promise<
  ApiResponse<{ signals: Signal[]; skipped: Array<{ symbol: string; reason: string }> }>
> {
  const raw = await rawFetch('/signals/generate-all', { method: 'POST' })
  if (!raw.ok) return errResponse(raw.error!)

  const resp = raw.data as BackendGenerateAllResponse
  return {
    success: true,
    data: { signals: (resp.signals ?? []).map(mapSignal), skipped: resp.skipped ?? [] },
  }
}

// ---------------------------------------------------------------------------
// Signal Generation (SSE streaming)
// ---------------------------------------------------------------------------

export interface SignalGenerationProgress {
  eventType: 'STARTED' | 'GENERATING' | 'SENTIMENT_ANALYZING' | 'SIGNAL_DONE' | 'SKIPPED' | 'COMPLETE'
  symbol?: string
  status: 'PROCESSING' | 'DONE' | 'SKIPPED' | 'ERROR'
  message: string
  current?: number
  total?: number
  signal?: Signal
}

export async function* generateAllSignalsStream(): AsyncIterable<SignalGenerationProgress> {
  const url = `${API_BASE_URL}/signals/generate-all/stream`
  const controller = new AbortController()
  const timeoutId = setTimeout(() => controller.abort(), 60_000)

  const response = await fetch(url, {
    method: 'POST',
    headers: DEFAULT_HEADERS,
    signal: controller.signal,
  })

  if (!response.ok) {
    const text = await response.text().catch(() => '')
    clearTimeout(timeoutId)
    throw new Error(`Signal generation failed: ${response.statusText} ${text.slice(0, 200)}`)
  }

  const reader = response.body!.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let currentEvent = 'progress'

  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })

      const lines = buffer.split('\n')
      buffer = lines.pop() || ''

      for (const line of lines) {
        const trimmed = line.trim()
        const eventMatch = trimmed.match(/^event:\s*(\S+)/)
        if (eventMatch) {
          currentEvent = eventMatch[1]
          continue
        }
        const dataPrefix = 'data:'
        if (trimmed.startsWith(dataPrefix)) {
          try {
            const data = JSON.parse(trimmed.slice(dataPrefix.length).trim())
            yield { ...data, _eventType: currentEvent }
          } catch {
            // Skip malformed JSON
          }
        }
      }
    }
  } finally {
    clearTimeout(timeoutId)
    reader.releaseLock()
  }
}

export async function clearAllSignals(): Promise<ApiResponse<{ cleared: number }>> {
  const raw = await rawFetch('/signals', { method: 'DELETE' })
  if (!raw.ok) return errResponse(raw.error!)
  const resp = raw.data as { cleared: number }
  return { success: true, data: resp }
}

export async function clearSignalsForSymbol(
  symbol: string
): Promise<ApiResponse<{ cleared: number }>> {
  const raw = await rawFetch(`/signals/${symbol}`, { method: 'DELETE' })
  if (!raw.ok) return errResponse(raw.error!)
  const resp = raw.data as { cleared: number }
  return { success: true, data: resp }
}

export async function getSignalsByType(
  type: 'BUY' | 'SELL' | 'HOLD'
): Promise<ApiResponse<Signal[]>> {
  const raw = await rawFetch(`/signals/type/${type}`)
  if (!raw.ok) return errResponse(raw.error!)

  return { success: true, data: (raw.data as BackendSignal[]).map(mapSignal) }
}

export async function getLatestSignalForSymbol(
  symbol: string
): Promise<ApiResponse<Signal | null>> {
  const raw = await rawFetch(`/signals/symbol/${symbol}`)
  if (!raw.ok) return errResponse(raw.error!)

  const data = raw.data as BackendSignal[]
  const latest = data.length > 0 ? data[data.length - 1] : null
  if (!latest) return { success: true, data: null }

  return { success: true, data: mapSignal(latest) }
}

export async function getHighConfidenceSignals(
  minConfidence: number = 0.7
): Promise<ApiResponse<Signal[]>> {
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

export async function closePosition(
  symbol: string,
  exitReason?: string
): Promise<ApiResponse<Position>> {
  const raw = await rawFetch(`/positions/${symbol}/close`, {
    method: 'POST',
    body: exitReason ? JSON.stringify({ exitReason }) : undefined,
  })
  if (!raw.ok) return errResponse(raw.error!)

  return { success: true, data: mapPosition(raw.data as BackendPosition) }
}

export interface ExecuteTradeParams {
  symbol: string
  quantity: number
  direction: 'LONG' | 'SHORT'
  orderType: 'MARKET' | 'LIMIT' | 'STOP_LOSS' | 'TAKE_PROFIT' | 'STOP' | 'STOP_LIMIT'
  price?: number
  limitPrice?: number
  stopPrice?: number
  target?: number
  entryReason?: string
}

export async function executeTrade(params: ExecuteTradeParams): Promise<ApiResponse<Position>> {
  const body: Record<string, unknown> = {
    symbol: params.symbol.toUpperCase().trim(),
    quantity: params.quantity,
    direction: params.direction,
    orderType: params.orderType,
  }
  if (params.price != null) body.price = params.price
  if (params.limitPrice != null) body.limitPrice = params.limitPrice
  if (params.stopPrice != null) body.stopPrice = params.stopPrice
  if (params.target != null) body.target = params.target
  if (params.entryReason) body.entryReason = params.entryReason

  const raw = await rawFetch('/positions', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!raw.ok) return errResponse(raw.error!)

  return { success: true, data: mapPosition(raw.data as BackendPosition) }
}

export async function generatePriceActionSignal(symbol: string): Promise<ApiResponse<Signal>> {
  const raw = await rawFetch(`/signals/price-action/${symbol}/generate`, { method: 'POST' })
  if (!raw.ok) return errResponse(raw.error!)
  return { success: true, data: mapSignal(raw.data as BackendSignal) }
}

export async function triggerScan(): Promise<
  ApiResponse<{ signalsFound: number; status: string }>
> {
  const raw = await rawFetch('/signals/scan', { method: 'POST' })
  if (!raw.ok) return errResponse(raw.error!)
  return { success: true, data: raw.data as { signalsFound: number; status: string } }
}

export async function checkHealth(): Promise<
  ApiResponse<{ status: string; components: Record<string, any> }>
> {
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

export async function fyersLogout(): Promise<ApiResponse<FyersStatus>> {
  const raw = await rawFetch('/fyers/logout', { method: 'POST' })
  if (!raw.ok) return errResponse(raw.error!)
  return { success: true, data: raw.data as unknown as FyersStatus }
}

// ---------------------------------------------------------------------------
// Equity curve — reconstruct from closed positions
// ---------------------------------------------------------------------------

export async function getEquityCurve(
  _range: string = '1M'
): Promise<ApiResponse<{ data: EquityPoint[] }>> {
  const raw = await rawFetch('/positions/closed')
  if (!raw.ok) return errResponse(raw.error!)

  const paginated = raw.data as BackendPaginated<BackendPosition>
  const positions: BackendPosition[] = paginated.content ?? []

  // Build cumulative P&L from closed trades sorted by entry date
  const sorted = positions
    .filter((p) => p.status === 'CLOSED' || p.status === 'STOPPED' || p.status === 'TARGET_HIT')
    .sort((a, b) => a.entryDate.localeCompare(b.entryDate))

  if (sorted.length === 0) {
    return { success: true, data: { data: [] } }
  }

  let cumulative = 0
  const points: EquityPoint[] = sorted.map((p) => {
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

// ---------------------------------------------------------------------------
// Data Ingestion
// ---------------------------------------------------------------------------

export async function getIngestionStatus(): Promise<ApiResponse<IngestionStatus[]>> {
  const raw = await rawFetch('/data/status')
  if (!raw.ok) return errResponse(raw.error!)
  return { success: true, data: unwrap<IngestionStatus[]>(raw) }
}

export async function triggerDataPull(
  yearsBack: number = 1
): Promise<ApiResponse<{ pullId: string; message: string }>> {
  const raw = await rawFetch(`/data/pull?yearsBack=${yearsBack}`, { method: 'POST' })
  if (!raw.ok) return errResponse(raw.error!)
  return { success: true, data: unwrap<{ pullId: string; message: string }>(raw) }
}

export async function getPullProgress(pullId?: string): Promise<ApiResponse<PullProgress>> {
  const params = pullId ? `?pullId=${pullId}` : ''
  const raw = await rawFetch(`/data/pull/progress${params}`)
  if (!raw.ok) return errResponse(raw.error!)
  return { success: true, data: unwrap<PullProgress>(raw) }
}

export async function cancelDataPull(): Promise<ApiResponse<string>> {
  const raw = await rawFetch('/data/pull/cancel', { method: 'POST' })
  if (!raw.ok) return errResponse(raw.error!)
  return { success: true, data: unwrap<string>(raw) }
}

// ---------------------------------------------------------------------------
// Settings
// ---------------------------------------------------------------------------

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

// ---------------------------------------------------------------------------
// Trading Configuration
// ---------------------------------------------------------------------------

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

// ---------------------------------------------------------------------------
// Unified Save
// ---------------------------------------------------------------------------

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

// ---------------------------------------------------------------------------
// Sentiment
// ---------------------------------------------------------------------------

export async function getSentimentLatest(symbol: string): Promise<ApiResponse<SentimentResult>> {
  const raw = await rawFetch(`/sentiment/${symbol}/latest`)
  if (!raw.ok) return errResponse(raw.error!)
  const resp = raw.data as { success: boolean; data: SentimentResult; error?: string }
  return { success: true, data: resp.data }
}

export async function getSentimentHistory(
  symbol: string,
  page = 0,
  size = 20
): Promise<ApiResponse<SentimentResult[]>> {
  const raw = await rawFetch(`/sentiment/${symbol}/history?page=${page}&size=${size}`)
  if (!raw.ok) return errResponse(raw.error!)
  const resp = raw.data as { success: boolean; data: SentimentResult[]; error?: string }
  return { success: true, data: resp.data }
}

export async function getAccuracyStats(): Promise<ApiResponse<SentimentAccuracyStats>> {
  const raw = await rawFetch('/sentiment/accuracy')
  if (!raw.ok) return errResponse(raw.error!)
  const resp = raw.data as { success: boolean; data: SentimentAccuracyStats; error?: string }
  return { success: true, data: resp.data }
}

export async function getAccuracySummary(): Promise<ApiResponse<AccuracySummary>> {
  const raw = await rawFetch('/sentiment/accuracy/summary')
  if (!raw.ok) return errResponse(raw.error!)
  const resp = raw.data as { success: boolean; data: AccuracySummary; error?: string }
  return { success: true, data: resp.data }
}

export async function getAccuracyByWindow(): Promise<ApiResponse<AccuracyByWindow[]>> {
  const raw = await rawFetch('/sentiment/accuracy/by-window')
  if (!raw.ok) return errResponse(raw.error!)
  const resp = raw.data as { success: boolean; data: AccuracyByWindow[]; error?: string }
  return { success: true, data: resp.data }
}

export async function getAccuracyByRegime(): Promise<ApiResponse<AccuracyByRegime[]>> {
  const raw = await rawFetch('/sentiment/accuracy/by-regime')
  if (!raw.ok) return errResponse(raw.error!)
  const resp = raw.data as { success: boolean; data: AccuracyByRegime[]; error?: string }
  return { success: true, data: resp.data }
}

export async function getAccuracyBySymbol(): Promise<ApiResponse<AccuracyBySymbol[]>> {
  const raw = await rawFetch('/sentiment/accuracy/by-symbol')
  if (!raw.ok) return errResponse(raw.error!)
  const resp = raw.data as { success: boolean; data: AccuracyBySymbol[]; error?: string }
  return { success: true, data: resp.data }
}

export async function getCalibration(): Promise<ApiResponse<CalibrationData[]>> {
  const raw = await rawFetch('/sentiment/accuracy/calibration')
  if (!raw.ok) return errResponse(raw.error!)
  const resp = raw.data as { success: boolean; data: CalibrationData[]; error?: string }
  return { success: true, data: resp.data }
}

export async function getRollingIC(windowDays = 30): Promise<ApiResponse<RollingIC[]>> {
  const raw = await rawFetch(`/sentiment/accuracy/rolling-ic?windowDays=${windowDays}`)
  if (!raw.ok) return errResponse(raw.error!)
  const resp = raw.data as { success: boolean; data: RollingIC[]; error?: string }
  return { success: true, data: resp.data }
}

export async function getSignalVolume(): Promise<ApiResponse<SignalVolumeStats>> {
  const raw = await rawFetch('/sentiment/accuracy/signal-volume')
  if (!raw.ok) return errResponse(raw.error!)
  const resp = raw.data as { success: boolean; data: SignalVolumeStats; error?: string }
  return { success: true, data: resp.data }
}

export async function getECE(): Promise<ApiResponse<ECEStats>> {
  const raw = await rawFetch('/sentiment/accuracy/ece')
  if (!raw.ok) return errResponse(raw.error!)
  const resp = raw.data as { success: boolean; data: ECEStats; error?: string }
  return { success: true, data: resp.data }
}

export async function triggerSentimentAnalysis(
  symbol: string
): Promise<ApiResponse<SentimentResult>> {
  const raw = await rawFetch(`/sentiment/${symbol}/analyse`, { method: 'POST' })
  if (!raw.ok) return errResponse(raw.error!)
  const resp = raw.data as { success: boolean; data: SentimentResult; error?: string }
  return { success: true, data: resp.data }
}

export async function getLatestNews(symbol: string): Promise<ApiResponse<NewsArticle[]>> {
  const raw = await rawFetch(`/news/${symbol}/latest`)
  if (!raw.ok) return errResponse(raw.error!)
  const resp = raw.data as { success: boolean; data: NewsArticle[]; error?: string }
  return { success: true, data: resp.data }
}

// ---------------------------------------------------------------------------
// Backtest
// ---------------------------------------------------------------------------

export async function runBacktest(
  symbol: string,
  exchange: string = 'NSE'
): Promise<ApiResponse<BacktestResult>> {
  const params = new URLSearchParams({ symbol: symbol.toUpperCase().trim(), exchange })
  const raw = await rawFetch(`/backtest/run?${params}`, { method: 'POST' })
  if (!raw.ok) return errResponse(raw.error!)
  return { success: true, data: raw.data as BacktestResult }
}

export async function runBacktestAll(
  exchange: string = 'NSE'
): Promise<ApiResponse<BacktestReportSummary>> {
  const raw = await rawFetch(`/backtest/run-all?exchange=${exchange}`, { method: 'POST' })
  if (!raw.ok) return errResponse(raw.error!)
  return { success: true, data: raw.data as BacktestReportSummary }
}

export async function listBacktestReports(): Promise<ApiResponse<string[]>> {
  const raw = await rawFetch('/backtest/reports')
  if (!raw.ok) return errResponse(raw.error!)
  return { success: true, data: raw.data as string[] }
}

export async function getBacktestReport(
  filename: string
): Promise<ApiResponse<BacktestReportSummary>> {
  const raw = await rawFetch(`/backtest/reports/${filename}`)
  if (!raw.ok) return errResponse(raw.error!)
  return { success: true, data: raw.data as BacktestReportSummary }
}

export async function getCompositeAnalysis(
  symbol: string
): Promise<ApiResponse<CompositeAnalysis>> {
  const raw = await rawFetch(`/analysis/analyze?symbol=${encodeURIComponent(symbol)}`, {
    method: 'POST',
  })
  if (!raw.ok) return errResponse(raw.error!)
  return { success: true, data: raw.data as CompositeAnalysis }
}

export async function backfillSymbol(
  symbol: string,
  years: number = 3
): Promise<ApiResponse<string>> {
  const raw = await rawFetch(
    `/ingestion/backfill?symbol=${encodeURIComponent(symbol)}&years=${years}`,
    { method: 'POST' }
  )
  if (!raw.ok) return errResponse(raw.error!)
  return { success: true, data: unwrap<string>(raw) }
}

// ---------------------------------------------------------------------------
// Full Analysis Orchestration (SSE)
// ---------------------------------------------------------------------------

export async function* runFullAnalysis(
  symbol: string,
  years: number = 3
): AsyncIterable<AnalysisProgress | FullAnalysisResult> {
  const params = new URLSearchParams({
    symbol: encodeURIComponent(symbol),
    years: String(years),
  })

  const url = `${API_BASE_URL}/analysis/run-full?${params}`
  const controller = new AbortController()
  const timeoutId = setTimeout(() => controller.abort(), 60_000)

  const response = await fetch(url, {
    method: 'POST',
    headers: DEFAULT_HEADERS,
    signal: controller.signal,
  })

  if (!response.ok) {
    const text = await response.text().catch(() => '')
    clearTimeout(timeoutId)
    throw new Error(`Analysis failed: ${response.statusText} ${text.slice(0, 200)}`)
  }

  const reader = response.body!.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let currentEvent = 'progress'

  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })

      const lines = buffer.split('\n')
      buffer = lines.pop() || ''

      for (const line of lines) {
        const trimmed = line.trim()
        // SSE spec: "event: started" or "event:started" — handle both
        const eventMatch = trimmed.match(/^event:\s*(\S+)/)
        if (eventMatch) {
          currentEvent = eventMatch[1]
          continue
        }
        // "data: {...}" — parse and yield
        const dataPrefix = 'data:'
        if (trimmed.startsWith(dataPrefix)) {
          try {
            const data = JSON.parse(trimmed.slice(dataPrefix.length).trim())
            yield { ...data, _eventType: currentEvent }
          } catch {
            // Skip malformed JSON, continue processing
          }
        }
      }
    }
  } finally {
    clearTimeout(timeoutId)
    reader.releaseLock()
  }
}

// ---------------------------------------------------------------------------
// NSE Holidays
// ---------------------------------------------------------------------------

export async function getTodayHolidayStatus(): Promise<ApiResponse<TodayHolidayStatus>> {
  const raw = await rawFetch('/holidays/today')
  if (!raw.ok) return errResponse(raw.error!)
  const resp = raw.data as { success: boolean; data: TodayHolidayStatus; error?: string }
  return { success: true, data: resp.data }
}

export async function getUpcomingHolidays(): Promise<ApiResponse<HolidayListResponse>> {
  const raw = await rawFetch('/holidays')
  if (!raw.ok) return errResponse(raw.error!)
  const resp = raw.data as { success: boolean; data: HolidayListResponse; error?: string }
  return { success: true, data: resp.data }
}

// ---------------------------------------------------------------------------
// Job Orchestrator
// ---------------------------------------------------------------------------

export async function startJobRun(triggerType = 'MANUAL'): Promise<ApiResponse<JobRunResponse>> {
  const params = new URLSearchParams({ triggerType })
  const raw = await rawFetch(`/job/runs/start?${params}`, { method: 'POST' })
  if (!raw.ok) return errResponse(raw.error!)
  return { success: true, data: raw.data as JobRunResponse }
}

export async function getJobRunProgress(runId: string): Promise<ApiResponse<JobRunProgressResponse>> {
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
