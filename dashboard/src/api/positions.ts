import { apiRequest, toNum } from './shared'
import type { Position, PortfolioSummary, MarketOverview, EquityPoint } from './types'

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

interface BackendPaginated<T> {
  content: T[]
  totalElements: number
  totalPages: number
  size: number
  number: number
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
  totalValue: number | string
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

const mapPosition = (p: BackendPosition): Position => ({
  id: String(p.id),
  brokerType: p.symbol,
  symbol: p.symbol,
  entryPrice: toNum(p.entryPrice),
  currentPrice: toNum(p.currentPrice),
  quantity: p.quantity,
  status: p.status,
  pnl: toNum(p.unrealizedPnL),
  pnlPercent: toNum(p.unrealizedPnLPercent),
  entryDate: p.entryDate,
  exitDate: p.entryDate,
  stopLoss: p.stopLoss != null ? toNum(p.stopLoss) : undefined,
  target: p.target != null ? toNum(p.target) : undefined,
  entryReason: p.entryReason,
  direction: 'LONG',
  averagePrice: toNum(p.entryPrice),
  totalValue: toNum(p.totalValue),
})

export async function getPortfolioSummary(): Promise<PortfolioSummary> {
  const perf = await apiRequest<BackendPerformance>('/positions/performance', {
    responseContract: 'direct',
  })
  const totalPnL = toNum(perf.totalPnL ?? 0)
  const totalReturn = toNum(perf.totalReturn)
  return {
    totalValue: toNum(perf.totalValue),
    totalPnl: totalPnL,
    totalPnlPercent: totalReturn,
    winRate: toNum(perf.winRate),
    totalTrades: perf.totalTrades ?? perf.closedTrades ?? 0,
    averageWin: toNum(perf.averageWin),
    averageLoss: toNum(perf.averageLoss),
    profitFactor: toNum(perf.profitFactor),
    maxDrawdown: toNum(perf.maxDrawdown),
    sharpeRatio: toNum(perf.sharpeRatio),
  }
}

export async function getMarketOverview(): Promise<MarketOverview> {
  const stats = await apiRequest<BackendPositionStats>('/positions/stats', {
    responseContract: 'direct',
  })
  return {
    totalPositions: stats.totalPositions,
    openPositions: stats.openPositions,
    todayPnl: toNum(stats.totalPnL),
    todayPnlPercent: 0,
  }
}

export async function getPositions(): Promise<Position[]> {
  const paginated = await apiRequest<BackendPaginated<BackendPosition>>('/positions', {
    method: 'GET',
    responseContract: 'direct',
  })
  return (paginated.content ?? []).map(mapPosition)
}

export async function getClosedPositions(): Promise<Position[]> {
  const paginated = await apiRequest<BackendPaginated<BackendPosition>>('/positions/closed', {
    method: 'GET',
    responseContract: 'direct',
  })
  return (paginated.content ?? []).map(mapPosition)
}

export async function getTradeHistory(limit: number = 10): Promise<Position[]> {
  const paginated = await apiRequest<BackendPaginated<BackendPosition>>(
    '/positions/closed?page=0&size=' + limit,
    { responseContract: 'direct' }
  )
  const positions: BackendPosition[] = paginated.content ?? []
  return positions.map(mapPosition)
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

export async function closePosition(symbol: string, exitReason?: string): Promise<Position> {
  const position = await apiRequest<BackendPosition>(`/positions/${symbol}/close`, {
    method: 'POST',
    body: exitReason ? JSON.stringify({ exitReason }) : undefined,
    responseContract: 'direct',
  })
  return mapPosition(position)
}

export async function executeTrade(params: ExecuteTradeParams): Promise<Position> {
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

  const position = await apiRequest<BackendPosition>('/positions', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
    responseContract: 'direct',
  })
  return mapPosition(position)
}

export async function getEquityCurve(_range: string = '1M'): Promise<{ data: EquityPoint[] }> {
  const paginated = await apiRequest<BackendPaginated<BackendPosition>>('/positions/closed', {
    responseContract: 'direct',
  })
  const positions: BackendPosition[] = paginated.content ?? []
  const sorted = positions
    .filter((p) => p.status === 'CLOSED' || p.status === 'STOPPED' || p.status === 'TARGET_HIT')
    .sort((a, b) => a.entryDate.localeCompare(b.entryDate))
  if (sorted.length === 0) {
    return { data: [] }
  }
  let cumulative = 0
  const points: EquityPoint[] = sorted.map((p) => {
    const pnl = toNum(p.unrealizedPnL)
    cumulative += pnl
    return { date: p.entryDate, value: cumulative }
  })
  return { data: points }
}
