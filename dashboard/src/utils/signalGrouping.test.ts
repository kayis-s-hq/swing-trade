import { describe, expect, it } from 'vitest'
import type { Signal } from '../api/types'
import type { SignalSelection } from '../api/selections'
import { consensusLabel, groupSignalsBySymbol } from './signalGrouping'

function signal(
  symbol: string,
  strategy: string | undefined,
  direction: Signal['direction'],
  timestamp: string,
  confidence = 0.6
): Signal {
  return {
    id: `${symbol}-${strategy}-${timestamp}`,
    symbol,
    direction,
    confidence,
    reason: '',
    entryPrice: 1,
    stopLoss: 1,
    target: 1,
    riskReward: 1,
    timestamp,
    status: 'ACTIVE',
    strategy,
  }
}

const selection = (symbol: string, winnerVariantId: string): SignalSelection => ({
  id: 1,
  symbol,
  selectionDate: '2026-09-18',
  winnerVariantId,
  winnerVersion: 1,
  winnerConfidence: 0.8,
  candidates: [],
  reason: null,
  status: 'PENDING',
  statusDetail: null,
})

describe('groupSignalsBySymbol', () => {
  it('builds one row per symbol with a chip per variant and a consensus label', () => {
    const groups = groupSignalsBySymbol([
      signal('SBIN', 'breakout-v1', 'BUY', '2026-09-18T09:00:00'),
      signal('SBIN', 'pullback-v1', 'BUY', '2026-09-18T09:00:00'),
      signal('SBIN', 'squeeze-v1', 'SELL', '2026-09-18T09:00:00'),
      signal('INFY', 'squeeze-v1', 'HOLD', '2026-09-18T09:00:00'),
    ])
    expect(groups.map((g) => g.symbol)).toEqual(['INFY', 'SBIN'])
    const sbin = groups[1]
    expect(sbin.chips.map((c) => c.variantId)).toEqual(['breakout-v1', 'pullback-v1', 'squeeze-v1'])
    expect(sbin.buyCount).toBe(2)
    expect(sbin.consensusLabel).toBe('2/3 BUY')
    expect(groups[0].consensusLabel).toBe('0/1 BUY')
  })

  it('keeps only the most recent signal per variant', () => {
    const [group] = groupSignalsBySymbol([
      signal('SBIN', 'breakout-v1', 'BUY', '2026-09-17T09:00:00'),
      signal('SBIN', 'breakout-v1', 'SELL', '2026-09-18T09:00:00'),
    ])
    expect(group.chips).toHaveLength(1)
    expect(group.chips[0].direction).toBe('SELL')
  })

  it('marks the tournament winner and labels strategy-less signals as legacy', () => {
    const [group] = groupSignalsBySymbol(
      [
        signal('SBIN', 'pullback-v1', 'BUY', '2026-09-18T09:00:00'),
        signal('SBIN', undefined, 'SELL', '2026-09-18T09:00:00'),
      ],
      [selection('SBIN', 'pullback-v1')]
    )
    expect(group.winnerVariantId).toBe('pullback-v1')
    expect(group.chips.find((c) => c.variantId === 'pullback-v1')?.selected).toBe(true)
    expect(group.chips.find((c) => c.variantId === 'legacy')?.selected).toBe(false)
  })

  it('formats an empty consensus as blank', () => {
    expect(consensusLabel(0, 0)).toBe('')
    expect(groupSignalsBySymbol([])).toEqual([])
  })

  it('folds non-variant strategies into one legacy chip that never counts as a vote', () => {
    const variants = new Set(['pullback-v1', 'breakout-v1'])
    const groups = groupSignalsBySymbol(
      [
        signal('SBIN', 'PRICE_ACTION', 'SELL', '2026-09-18T10:00'),
        signal('SBIN', 'DEFAULT', 'BUY', '2026-09-18T11:00'),
        signal('SBIN', 'pullback-v1', 'BUY', '2026-09-18T12:00'),
        signal('INFY', 'PRICE_ACTION', 'BUY', '2026-09-18T10:00'),
      ],
      [],
      variants
    )
    const sbin = groups.find((g) => g.symbol === 'SBIN')!
    expect(sbin.chips.map((c) => c.variantId).sort()).toEqual(['legacy', 'pullback-v1'])
    expect(sbin.consensusLabel).toBe('1/1 BUY')
    const infy = groups.find((g) => g.symbol === 'INFY')!
    expect(infy.consensusLabel).toBe('')
  })
})
