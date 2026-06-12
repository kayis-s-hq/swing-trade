import type { ApiResponse, PortfolioSummary, MarketOverview, Position, Signal, EquityPoint } from './types'

// Mock data
const mockPortfolioSummary: PortfolioSummary = {
  totalValue: 1523450,
  totalPnl: 4250,
  totalPnlPercent: 12.5,
  winRate: 60,
  totalTrades: 25,
  averageWin: 1850,
  averageLoss: 920,
  profitFactor: 2.01,
  maxDrawdown: 8.5,
  sharpeRatio: 1.75,
}

const mockMarketOverview: MarketOverview = {
  totalPositions: 5,
  openPositions: 3,
  todayPnl: 245,
  todayPnlPercent: 12.5,
}

const mockPositions: Position[] = [
  {
    id: '1',
    symbol: 'RELIANCE',
    entryPrice: 2850,
    currentPrice: 2985.5,
    quantity: 10,
    status: 'OPEN',
    pnl: 1355,
    pnlPercent: 4.75,
    entryDate: '2024-03-15',
    stopLoss: 2750,
    target: 3100,
  },
  {
    id: '2',
    symbol: 'TCS',
    entryPrice: 3650,
    currentPrice: 3520,
    quantity: 5,
    status: 'OPEN',
    pnl: -650,
    pnlPercent: -3.56,
    entryDate: '2024-03-18',
    stopLoss: 3500,
    target: 3850,
  },
  {
    id: '3',
    symbol: 'INFY',
    entryPrice: 1480,
    currentPrice: 1480,
    quantity: 8,
    status: 'CLOSED',
    pnl: 120,
    pnlPercent: 1.01,
    entryDate: '2024-03-01',
    exitDate: '2024-03-10',
    stopLoss: 1420,
    target: 1550,
  },
  {
    id: '4',
    symbol: 'HDFCBANK',
    entryPrice: 1520,
    currentPrice: 1565,
    quantity: 15,
    status: 'OPEN',
    pnl: 675,
    pnlPercent: 2.96,
    entryDate: '2024-03-20',
    stopLoss: 1480,
    target: 1650,
  },
  {
    id: '5',
    symbol: 'SBIN',
    entryPrice: 720,
    currentPrice: 720,
    quantity: 20,
    status: 'CLOSED',
    pnl: -180,
    pnlPercent: -1.25,
    entryDate: '2024-02-28',
    exitDate: '2024-03-05',
    stopLoss: 700,
    target: 780,
  },
]

const mockSignals: Signal[] = [
  {
    id: '1',
    symbol: 'RELIANCE',
    direction: 'BUY',
    confidence: 85,
    reason: 'Bullish divergence on RSI + MACD crossover + volume spike',
    entryPrice: 2850,
    stopLoss: 2750,
    target: 3100,
    riskReward: 2.5,
    timestamp: '2024-03-25T09:15:00Z',
    status: 'ACTIVE',
  },
  {
    id: '2',
    symbol: 'TATASTEEL',
    direction: 'BUY',
    confidence: 72,
    reason: 'Breakout above 200-day MA with increasing volume',
    entryPrice: 145,
    stopLoss: 138,
    target: 162,
    riskReward: 2.43,
    timestamp: '2024-03-25T09:30:00Z',
    status: 'PENDING',
  },
  {
    id: '3',
    symbol: 'WIPRO',
    direction: 'SELL',
    confidence: 68,
    reason: 'Head & shoulders pattern forming + declining volume',
    entryPrice: 420,
    stopLoss: 435,
    target: 385,
    riskReward: 2.33,
    timestamp: '2024-03-25T10:00:00Z',
    status: 'PENDING',
  },
  {
    id: '4',
    symbol: 'HDFCBANK',
    direction: 'BUY',
    confidence: 91,
    reason: 'Strong support bounce + positive sector momentum',
    entryPrice: 1520,
    stopLoss: 1480,
    target: 1650,
    riskReward: 3.25,
    timestamp: '2024-03-25T10:15:00Z',
    status: 'ACTIVE',
  },
  {
    id: '5',
    symbol: 'ICICIBANK',
    direction: 'BUY',
    confidence: 76,
    reason: 'Golden cross forming + accumulation pattern',
    entryPrice: 980,
    stopLoss: 950,
    target: 1060,
    riskReward: 2.67,
    timestamp: '2024-03-25T10:30:00Z',
    status: 'PENDING',
  },
  {
    id: '6',
    symbol: 'AXISBANK',
    direction: 'SELL',
    confidence: 64,
    reason: 'Bearish engulfing + resistance rejection',
    entryPrice: 1050,
    stopLoss: 1070,
    target: 990,
    riskReward: 3.0,
    timestamp: '2024-03-25T11:00:00Z',
    status: 'PENDING',
  },
]

const mockEquityPoints: EquityPoint[] = Array.from({ length: 60 }, (_, i) => ({
  date: new Date(Date.now() - (60 - i) * 86400000).toISOString().split('T')[0],
  value: 1000000 + Math.sin(i / 8) * 150000 + i * 5000 + Math.random() * 20000,
}))

// Simulate API delay
const delay = (ms: number) => new Promise(resolve => setTimeout(resolve, ms))

export async function getPortfolioSummary(): Promise<ApiResponse<PortfolioSummary>> {
  await delay(200)
  return { success: true, data: mockPortfolioSummary }
}

export async function getMarketOverview(): Promise<ApiResponse<MarketOverview>> {
  await delay(150)
  return { success: true, data: mockMarketOverview }
}

export async function getPositions(): Promise<ApiResponse<Position[]>> {
  await delay(250)
  return { success: true, data: mockPositions }
}

export async function getSignals(): Promise<ApiResponse<Signal[]>> {
  await delay(200)
  return { success: true, data: mockSignals }
}

export async function getEquityCurve(range: string = '1M'): Promise<ApiResponse<{ data: EquityPoint[] }>> {
  await delay(180)
  const points: Record<string, number> = { '1W': 7, '1M': 30, '3M': 90, '6M': 180, '1Y': 365, ALL: 60 }
  const count = points[range] ?? 30
  return { success: true, data: { data: mockEquityPoints.slice(-count) } }
}

export async function getTradeHistory(limit: number = 10): Promise<ApiResponse<Position[]>> {
  await delay(220)
  return { success: true, data: mockPositions.slice(0, limit) }
}
