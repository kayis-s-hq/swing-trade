import { rawFetch, unwrap, errResponse } from './shared'
import type { ApiResponse } from './types'

export async function backfillSymbol(
  symbol: string,
  years: number = 3
): Promise<ApiResponse<string>> {
  const raw = await rawFetch(
    `/ingestion/backfill?symbol=${encodeURIComponent(symbol)}&years=${years}`,
    { method: 'POST' }
  )
  if (!raw.ok) return errResponse(raw.error!)
  return { success: true, data: unwrap<string>(raw) }
}
