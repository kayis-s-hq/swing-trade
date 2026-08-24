import { rawFetch, toNum, errResponse } from './shared'
import type { ApiResponse, Position, PortfolioSummary, MarketOverview, EquityPoint } from './types'

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

export async function getPortfolioSummary(): Promise<ApiResponse<PortfolioSummary>> {
  const raw = await rawFetch('/positions/performance')
  if (!raw.ok) return errResponse(raw.error!)
  const perf = raw.data as BackendPerformance
  const totalPnL = toNum(perf.totalPnL ?? 0)
  const totalReturn = toNum(perf.totalReturn)
  return {
    success: true,
    data: {
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
      todayPnlPercent: 0,
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
  const paginated = raw.data as BackendPaginated<BackendPosition>
  const positions: BackendPosition[] = paginated.content ?? []
  return { success: true, data: positions.map(mapPosition) }
}

export async function getTradeHistory(limit: number = 10): Promise<ApiResponse<Position[]>> {
  const raw = await rawFetch('/positions/closed?page=0&size=' + limit)
  if (!raw.ok) return errResponse(raw.error!)
  const paginated = raw.data as BackendPaginated<BackendPosition>
  const positions: BackendPosition[] = paginated.content ?? []
  return { success: true, data: positions.map(mapPosition) }
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

export async function getEquityCurve(
  _range: string = '1M'
): Promise<ApiResponse<{ data: EquityPoint[] }>> {
  const raw = await rawFetch('/positions/closed')
  if (!raw.ok) return errResponse(raw.error!)
  const paginated = raw.data as BackendPaginated<BackendPosition>
  const positions: BackendPosition[] = paginated.content ?? []
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
