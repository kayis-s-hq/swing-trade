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

export interface ApiResponse<T = unknown> {
  success: boolean
  data?: T
  error?: string
}
