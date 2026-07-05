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
  strategy?: string
  indicators?: string[]
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

// ---------------------------------------------------------------------------
// Backtest
// ---------------------------------------------------------------------------

export interface BacktestTrade {
  symbol: string
  entryDate: string
  exitDate: string
  entryPrice: number
  exitPrice: number
  stopLoss: number
  target: number
  quantity: number
  exitReason: string
  pnl: number
  pnlPct: number
  holdingDays: number
}

export interface BacktestResult {
  symbol: string
  totalTrades: number
  winningTrades: number
  losingTrades: number
  winRate: number
  avgGainPct: number
  avgLossPct: number
  maxDrawdownPct: number
  sharpeRatio: number
  totalReturn: number
  expectancy: number
  trades: BacktestTrade[]
}

export interface BacktestReportSummary {
  generatedAt: string
  symbolsBacktested: number
  top10ByWinRate: BacktestResult[]
  top10ByTotalReturn: BacktestResult[]
  overallWinRate: number
  overallSharpeRatio: number
  results: BacktestResult[]
}

// ---------------------------------------------------------------------------
// Sentiment
// ---------------------------------------------------------------------------

export interface SentimentResult {
  id: number
  symbol: string
  date: string
  score: 'POSITIVE' | 'NEUTRAL' | 'NEGATIVE'
  summary: string
  rawContent: string
  confidence: number
  analyzedAt: string
  redFlags: string[]
  catalysts: string[]
}

export interface SentimentAccuracyStats {
  total: number
  correct: number
  accuracy_pct: number
  by_sentiment: Record<string, number>
  by_symbol: Record<string, number>
}

export interface NewsArticle {
  title: string
  link: string
  description: string
  publishedDate: string
  source: string
  rawContent: string
}
