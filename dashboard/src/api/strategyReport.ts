import { apiRequest } from './shared'

/** Mirrors backend StrategyAttributionService.VariantReport. */
export interface VariantReport {
  variantId: string
  strategyType: string
  mode: string
  signalsGenerated: number
  buySignals: number
  timesSelected: number
  selectionRatePct: number | null
  selectedTrades: number
  selectedWins: number
  selectedWinRatePct: number | null
  selectedPnl: number | string
  shadowTrades: number
  shadowWins: number
  shadowWinRatePct: number | null
  shadowPnl: number | string
  agreementRatePct: number | null
  avgRegretPct: number | null
  sentimentVetoes: number
  llmVetoes: number
  otherBlocks: number
}

export interface StrategyReportTotals {
  tournaments: number
  executed: number
  blocked: number
  pending: number
  selectedTradesClosed: number
  selectedPnl: number | string
  avgRegretPct: number | null
}

export interface StrategyReport {
  from: string
  to: string
  variants: VariantReport[]
  totals: StrategyReportTotals
}

export async function getStrategyReport(from?: string, to?: string): Promise<StrategyReport> {
  const params = new URLSearchParams()
  if (from) params.set('from', from)
  if (to) params.set('to', to)
  const query = params.toString()
  return apiRequest<StrategyReport>(`/strategy-report${query ? `?${query}` : ''}`, {
    responseContract: 'direct',
  })
}

export interface ArbitrationRuleResult {
  rule: 'HIGHEST_CONFIDENCE' | 'EVIDENCE_RANKED'
  decisions: number
  decisionsWithOutcome: number
  avgReturnPct: number | null
  winRatePct: number | null
  differsFromHighestConfidence: number
}

/** Mirrors backend ArbitrationComparisonService.Comparison: recorded tournaments replayed under each rule. */
export interface ArbitrationComparison {
  from: string
  to: string
  tournaments: number
  rules: ArbitrationRuleResult[]
}

export async function getArbitrationComparison(
  from?: string,
  to?: string
): Promise<ArbitrationComparison> {
  const params = new URLSearchParams()
  if (from) params.set('from', from)
  if (to) params.set('to', to)
  const query = params.toString()
  return apiRequest<ArbitrationComparison>(
    `/strategy-report/arbitration-comparison${query ? `?${query}` : ''}`,
    {
      responseContract: 'direct',
    }
  )
}
