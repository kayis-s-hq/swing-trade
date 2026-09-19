import type { Signal } from '../api/types'
import type { SignalSelection } from '../api/selections'

export interface VariantChip {
  variantId: string
  direction: Signal['direction']
  confidence: number
  selected: boolean
}

export interface SymbolSignalGroup {
  symbol: string
  chips: VariantChip[]
  buyCount: number
  totalVariants: number
  /** e.g. "2/3 BUY"; empty when no variant signals exist for the symbol. */
  consensusLabel: string
  winnerVariantId: string | null
}

export const LEGACY_VARIANT_ID = 'legacy'

export function consensusLabel(buyCount: number, totalVariants: number): string {
  return totalVariants === 0 ? '' : `${buyCount}/${totalVariants} BUY`
}

/**
 * Groups signals into one row per symbol. Only each variant's most recent signal counts, so
 * `totalVariants` is the number of variants that emitted a persisted signal for the symbol
 * (SHADOW HOLDs are not persisted by the backend). `selections` marks the tournament winner.
 */
export function groupSignalsBySymbol(
  signals: Signal[],
  selections: SignalSelection[] = []
): SymbolSignalGroup[] {
  const winners = new Map(selections.map((s) => [s.symbol, s.winnerVariantId]))
  const latest = new Map<string, Signal>()
  for (const signal of signals) {
    const key = `${signal.symbol}|${signal.strategy ?? LEGACY_VARIANT_ID}`
    const current = latest.get(key)
    if (!current || signal.timestamp > current.timestamp) latest.set(key, signal)
  }

  const bySymbol = new Map<string, VariantChip[]>()
  for (const signal of latest.values()) {
    const variantId = signal.strategy ?? LEGACY_VARIANT_ID
    const chips = bySymbol.get(signal.symbol) ?? []
    chips.push({
      variantId,
      direction: signal.direction,
      confidence: signal.confidence,
      selected: winners.get(signal.symbol) === variantId,
    })
    bySymbol.set(signal.symbol, chips)
  }

  return [...bySymbol.entries()]
    .map(([symbol, chips]) => {
      const sorted = [...chips].sort((a, b) => a.variantId.localeCompare(b.variantId))
      const buyCount = sorted.filter((c) => c.direction === 'BUY').length
      return {
        symbol,
        chips: sorted,
        buyCount,
        totalVariants: sorted.length,
        consensusLabel: consensusLabel(buyCount, sorted.length),
        winnerVariantId: winners.get(symbol) ?? null,
      }
    })
    .sort((a, b) => a.symbol.localeCompare(b.symbol))
}
