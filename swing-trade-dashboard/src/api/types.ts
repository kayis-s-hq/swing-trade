// Position DTO
export interface Position {
  id: string
  symbol: string
  entryPrice: number
  entryDate: string
  quantity: number
  currentPrice: number
  status: 'OPEN' | 'CLOSED' | 'STOPPED' | 'TARGET_HIT'
  pnl: number
  pnlPercent: number
  stopLoss: number
  target: number
  reason: string
}

// Signal DTO
export interface Signal {
  id: string
  symbol: string
  signalType: 'BUY' | 'SELL' | 'HOLD'
  confidence: number
  reasoning: string
  entryPrice: number
  stopLoss: number
  target: number
  createdDate: string
  indicators: string[]
}

// Portfolio Summary DTO
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

// Market Overview DTO
export interface MarketOverview {
  totalPositions: number
  openPositions: number
  totalValue: number
  todayPnl: number
}

// API Response Wrapper
export interface ApiResponse<T> {
  success: boolean
  data?: T
  error?: string
}

// Trend Data
export interface Trend {
  value: string
  isPositive?: boolean
}

// Equity Curve Data
export interface EquityPoint {
  date: string
  value: number
}
