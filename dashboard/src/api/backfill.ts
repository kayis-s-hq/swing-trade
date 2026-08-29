import { apiRequest } from './shared'

export interface BackfillResult {
  symbol: string
  years: number
  candleCount: number
  status: string
}

export async function backfillSymbol(symbol: string, years: number = 3): Promise<BackfillResult> {
  return apiRequest<BackfillResult>(
    `/ingestion/backfill?symbol=${encodeURIComponent(symbol)}&years=${years}`,
    { method: 'POST', responseContract: 'envelope' }
  )
}
