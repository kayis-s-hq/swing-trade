import { apiRequest } from './shared'

export interface PaperPortfolioSummary {
  portfolioId: string
  initialCapital: number | string
  currentCapital: number | string
  realizedPnl: number | string | null
  openPositionCount: number
}

export interface ShadowPositionView {
  portfolioId: string
  symbol: string
  entryDate: string
  entryPrice: number | string | null
  stopLoss: number | string | null
  target: number | string | null
  quantity: number
  status: 'OPEN' | 'CLOSED'
  exitDate: string | null
  exitPrice: number | string | null
  exitReason: string | null
  pnl: number | string | null
}

export interface StrategyMatrixRow {
  variantId: string
  strategyType: string
  mode: string
  latestSignal: 'BUY' | 'SELL' | 'HOLD' | null
  latestConfidence: number | string | null
  latestSignalDate: string | null
  openPosition: ShadowPositionView | null
  closedTrades: number
  realizedPnl: number | string
  winRate: number | null
}

export interface TournamentHistoryRow {
  selectionDate: string
  winnerVariantId: string
  winnerConfidence: number | string
  status: string
}

export interface StrategyMatrix {
  symbol: string
  strategies: StrategyMatrixRow[]
  tournaments: TournamentHistoryRow[]
}

/** Portfolio ids that are not shadow books: the shared engine that backs the real Positions data. */
export const REAL_PORTFOLIO_ID = 'default'

export async function listPaperPortfolios(): Promise<PaperPortfolioSummary[]> {
  return apiRequest<PaperPortfolioSummary[]>('/paper-portfolios', { responseContract: 'direct' })
}

export async function listPortfolioPositions(portfolioId: string): Promise<ShadowPositionView[]> {
  return apiRequest<ShadowPositionView[]>(
    `/paper-portfolios/${encodeURIComponent(portfolioId)}/positions`,
    { responseContract: 'direct' }
  )
}

export async function getStrategyMatrix(symbol: string): Promise<StrategyMatrix> {
  return apiRequest<StrategyMatrix>(`/symbols/${encodeURIComponent(symbol)}/strategy-matrix`, {
    responseContract: 'direct',
  })
}

/** Shadow books are every portfolio except the real/default one, "selected" first. */
export function shadowPortfolios(all: PaperPortfolioSummary[]): PaperPortfolioSummary[] {
  return all
    .filter((p) => p.portfolioId !== REAL_PORTFOLIO_ID)
    .sort((a, b) => {
      if (a.portfolioId === 'selected') return -1
      if (b.portfolioId === 'selected') return 1
      return a.portfolioId.localeCompare(b.portfolioId)
    })
}
