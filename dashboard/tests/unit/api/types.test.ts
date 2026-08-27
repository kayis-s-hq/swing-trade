import { describe, expect, it } from 'vitest'
import type { MarketOverview, PortfolioSummary, Position, Signal } from '../../../src/api/types'

describe('Position', () => {
  it('should have valid position with all fields', () => {
    const position: Position = {
      id: 'pos-001',
      symbol: 'RELIANCE',
      entryPrice: 2500,
      entryDate: '2026-04-01',
      quantity: 10,
      currentPrice: 2600,
      status: 'OPEN',
      pnl: 1000,
      pnlPercent: 4.0,
      stopLoss: 2400,
      target: 2800,
      reason: 'Breakout pattern',
    }
    expect(position.symbol).toBe('RELIANCE')
    expect(position.status).toBe('OPEN')
    expect(position.pnl).toBe(1000)
  })

  it('should validate status values', () => {
    const validStatuses: Position['status'][] = ['OPEN', 'CLOSED', 'STOPPED', 'TARGET_HIT']
    validStatuses.forEach((status) => {
      const position: Position = {
        id: 'pos-001',
        symbol: 'RELIANCE',
        entryPrice: 2500,
        entryDate: '2026-04-01',
        quantity: 10,
        currentPrice: 2600,
        status,
        pnl: 1000,
        pnlPercent: 4.0,
        stopLoss: 2400,
        target: 2800,
        reason: 'Breakout pattern',
      }
      expect(position.status).toBe(status)
    })
  })

  it('should handle CLOSED position', () => {
    const position: Position = {
      id: 'pos-002',
      symbol: 'TCS',
      entryPrice: 3500,
      entryDate: '2026-03-15',
      quantity: 5,
      currentPrice: 3400,
      status: 'CLOSED',
      pnl: -500,
      pnlPercent: -2.86,
      stopLoss: 3300,
      target: 3800,
      reason: 'Target hit',
    }
    expect(position.status).toBe('CLOSED')
    expect(position.pnlPercent).toBeCloseTo(-2.86, 2)
  })
})

describe('Signal', () => {
  it('should have valid signal with all fields', () => {
    const signal: Signal = {
      id: 'sig-001',
      symbol: 'HDFCBANK',
      signalType: 'BUY',
      confidence: 75,
      reasoning: 'Positive sentiment + breakout above resistance',
      entryPrice: 1450,
      stopLoss: 1380,
      target: 1600,
      createdDate: '2026-04-10',
      indicators: ['EMA20', 'RSI', 'Volume'],
    }
    expect(signal.symbol).toBe('HDFCBANK')
    expect(signal.signalType).toBe('BUY')
    expect(signal.confidence).toBe(75)
  })

  it('should validate signal types', () => {
    const validTypes: Signal['signalType'][] = ['BUY', 'SELL', 'HOLD']
    validTypes.forEach((type) => {
      const signal: Signal = {
        id: 'sig-001',
        symbol: 'RELIANCE',
        signalType: type,
        confidence: 75,
        reasoning: 'Technical analysis',
        entryPrice: 2500,
        stopLoss: 2400,
        target: 2800,
        createdDate: '2026-04-10',
        indicators: ['EMA20'],
      }
      expect(signal.signalType).toBe(type)
    })
  })

  it('should handle SELL signal', () => {
    const signal: Signal = {
      id: 'sig-002',
      symbol: 'TCS',
      signalType: 'SELL',
      confidence: 65,
      reasoning: 'Negative sentiment detected',
      entryPrice: 3600,
      stopLoss: 3700,
      target: 3300,
      createdDate: '2026-04-09',
      indicators: ['RSI', 'MACD'],
    }
    expect(signal.signalType).toBe('SELL')
    expect(signal.confidence).toBeGreaterThanOrEqual(0)
    expect(signal.confidence).toBeLessThanOrEqual(100)
  })
})

describe('PortfolioSummary', () => {
  it('should have valid portfolio summary with all metrics', () => {
    const summary: PortfolioSummary = {
      totalValue: 500000,
      totalPnl: 25000,
      totalPnlPercent: 5.26,
      winRate: 65,
      totalTrades: 100,
      averageWin: 3500,
      averageLoss: 1500,
      profitFactor: 2.33,
      maxDrawdown: 15000,
      sharpeRatio: 1.8,
    }
    expect(summary.totalValue).toBe(500000)
    expect(summary.totalPnl).toBe(25000)
    expect(summary.winRate).toBe(65)
  })

  it('should have required fields present', () => {
    const summary: PortfolioSummary = {
      totalValue: 0,
      totalPnl: 0,
      totalPnlPercent: 0,
      winRate: 0,
      totalTrades: 0,
      averageWin: 0,
      averageLoss: 0,
      profitFactor: 0,
      maxDrawdown: 0,
      sharpeRatio: 0,
    }
    expect(summary.totalValue).toBeDefined()
    expect(summary.sharpeRatio).toBeDefined()
  })
})

describe('MarketOverview', () => {
  it('should have valid market overview with current data', () => {
    const overview: MarketOverview = {
      totalPositions: 15,
      openPositions: 8,
      totalValue: 500000,
      todayPnl: 5000,
    }
    expect(overview.totalPositions).toBe(15)
    expect(overview.openPositions).toBe(8)
    expect(overview.todayPnl).toBe(5000)
  })

  it('should handle optional fields gracefully', () => {
    const overview: MarketOverview = {
      totalPositions: 0,
      openPositions: 0,
      totalValue: 0,
      todayPnl: 0,
    }
    expect(overview.totalPositions).toBe(0)
  })
})
