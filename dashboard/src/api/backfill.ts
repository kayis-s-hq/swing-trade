import { apiRequest } from './shared'

export async function backfillSymbol(symbol: string, years: number = 3): Promise<string> {
  return apiRequest<string>(
    `/ingestion/backfill?symbol=${encodeURIComponent(symbol)}&years=${years}`,
    { method: 'POST', responseContract: 'direct' }
  )
}
