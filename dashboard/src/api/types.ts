export interface PortfolioSummary {
  totalValue: number
  totalPnl: number
  totalPnlPercent: number
  winRate: number
  totalTrades: number
  averageWin: number
  averageLoss: number
  profitFactor: number
  maxDrawdown: number
  sharpeRatio: number
}

export interface MarketOverview {
  totalPositions: number
  openPositions: number
  todayPnl: number
  todayPnlPercent: number
}

export interface Position {
  id: string
  symbol: string
  entryPrice: number
  currentPrice: number
  quantity: number
  status: 'OPEN' | 'CLOSED'
  pnl: number
  pnlPercent: number
  entryDate: string
  exitDate?: string
  stopLoss?: number
  target?: number
}

export interface Signal {
  id: string
  symbol: string
  direction: 'BUY' | 'SELL'
  confidence: number
  reason: string
  entryPrice: number
  stopLoss: number
  target: number
  riskReward: number
  timestamp: string
  status: 'PENDING' | 'ACTIVE' | 'EXECUTED' | 'EXPIRED'
}

export interface EquityPoint {
  date: string
  value: number
}

export interface HealthStatus {
  status: string
  components: Record<string, HealthComponent>
}

export interface HealthComponent {
  name: string
  status: string
  description: string
  details?: Record<string, unknown>
}

export interface FyersStatus {
  connected: boolean
  clientId: string
}

export interface FyersLoginUrl {
  url: string
  message: string
}

export type BrokerType = 'fyers' | 'upstox' | 'yahoo' | 'none'

export interface ApiResponse<T = unknown> {
  success: boolean
  data?: T
  error?: string
}

// ---------------------------------------------------------------------------
// Watchlist
// ---------------------------------------------------------------------------

export interface WatchlistEntry {
  id?: number
  symbol: string
  name: string
  exchange: string
  isActive: boolean
  addedAt?: string
  lastSyncedAt?: string
  candleCount?: number
}

// ---------------------------------------------------------------------------
// Data Ingestion Status
// ---------------------------------------------------------------------------

export interface IngestionStatus {
  symbol: string
  name: string
  exchange: string
  isActive: boolean
  candleCount: number
  lastCandleDate: string | null
  earliestCandleDate: string | null
  lastSyncedAt: string | null
  hasData: boolean
  dataQuality: string
}

// ---------------------------------------------------------------------------
// Data Pull Progress
// ---------------------------------------------------------------------------

export interface PullProgress {
  pullId: string
  status: string
  total: number
  completed: number
  failed: number
  currentSymbol: string
  percentComplete: number
}
