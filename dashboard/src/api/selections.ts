import { apiRequest } from './shared'

export interface SelectionCandidate {
  variantId: string
  version: number
  signal: 'BUY' | 'SELL' | 'HOLD'
  confidence: number | string | null
  selected: boolean
}

/** Mirrors backend SignalSelectionController.SelectionResponse (signal tournament winner per symbol/day). */
export interface SignalSelection {
  id: number
  symbol: string
  selectionDate: string
  winnerVariantId: string
  winnerVersion: number
  winnerConfidence: number | string
  candidates: SelectionCandidate[]
  reason: string | null
  status: 'PENDING' | 'EXECUTED' | 'BLOCKED'
  statusDetail: string | null
}

export async function listSignalSelections(from?: string, to?: string): Promise<SignalSelection[]> {
  const params = new URLSearchParams()
  if (from) params.set('from', from)
  if (to) params.set('to', to)
  const query = params.toString()
  return apiRequest<SignalSelection[]>(`/signal-selections${query ? `?${query}` : ''}`, {
    responseContract: 'direct',
  })
}

/** Keeps only the most recent selection date's rows (the latest tournament), sorted by symbol. */
export function latestTournament(selections: SignalSelection[]): SignalSelection[] {
  if (selections.length === 0) return []
  const latest = selections.reduce((max, s) => (s.selectionDate > max ? s.selectionDate : max), '')
  return selections.filter((s) => s.selectionDate === latest).sort((a, b) => a.symbol.localeCompare(b.symbol))
}
