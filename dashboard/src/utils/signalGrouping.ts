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
 * (SHADOW HOLDs are not persisted by the backend). `selections` marks the tournament winner;
 * the legacy chip never counts toward consensus.
 */
export function groupSignalsBySymbol(
  signals: Signal[],
  selections: SignalSelection[] = [],
  variantIds?: ReadonlySet<string>
): SymbolSignalGroup[] {
  // When the registered variant ids are known, any other strategy name (legacy engines such
  // as PRICE_ACTION or DEFAULT) folds into the single "legacy" chip instead of posing as a variant.
  const idFor = (signal: Signal): string =>
    signal.strategy && (!variantIds || variantIds.has(signal.strategy))
      ? signal.strategy
      : LEGACY_VARIANT_ID

  const winners = new Map(selections.map((s) => [s.symbol, s.winnerVariantId]))
  const latest = new Map<string, Signal>()
  for (const signal of signals) {
    const key = `${signal.symbol}|${idFor(signal)}`
    const current = latest.get(key)
    if (!current || signal.timestamp > current.timestamp) latest.set(key, signal)
  }

  const bySymbol = new Map<string, VariantChip[]>()
  for (const signal of latest.values()) {
    const variantId = idFor(signal)
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
      // The legacy chip is shown for context but is not a variant vote.
      const voters = sorted.filter((c) => c.variantId !== LEGACY_VARIANT_ID)
      const buyCount = voters.filter((c) => c.direction === 'BUY').length
      return {
        symbol,
        chips: sorted,
        buyCount,
        totalVariants: voters.length,
        consensusLabel: consensusLabel(buyCount, voters.length),
        winnerVariantId: winners.get(symbol) ?? null,
      }
    })
    .sort((a, b) => a.symbol.localeCompare(b.symbol))
}
