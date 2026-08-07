import { test, expect } from '@playwright/test'

const API = 'http://localhost:8080'

test('paper trading executes trades for buy signals', async ({ request }) => {
  test.setTimeout(120000)
  // 1. Check existing buy signals before run
  const signalsRes = await request.get(`${API}/api/signals/latest`)
  const allSignals = (await signalsRes.json()) as any[]
  const buySignals = allSignals.filter((s: any) => s.signalType === 'BUY')
  console.log(`Existing signals: ${allSignals.length}, BUY signals: ${buySignals.length}`)
  if (buySignals.length > 0) {
    console.log('Sample BUY signals:', JSON.stringify(buySignals.slice(0, 3), null, 2))
  }

  // 2. Start a new orchestrator run
  const startRes = await request.post(`${API}/api/job/runs/start`)
  expect(startRes.status()).toBe(200)
  const startData = (await startRes.json()) as any
  console.log('Run started:', startData.runId)
  const runId = startData.runId

  // 3. Poll for completion
  let progress: any = null
  for (let i = 0; i < 120; i++) {
    const progRes = await request.get(`${API}/api/job/runs/${runId}/progress`)
    progress = await progRes.json()
    console.log(`  Poll ${i + 1}: Status=${progress?.status} | Symbols: ${progress?.completedSymbols}/${progress?.totalSymbols}`)
    if (progress?.status === 'COMPLETED' || progress?.status === 'FAILED') {
      break
    }
    await new Promise(resolve => setTimeout(resolve, 3000))
  }

  console.log(`\nFinal status: ${progress?.status}`)
  console.log(`Symbols: ${progress?.totalSymbols}, Completed: ${progress?.completedSymbols}`)

  // 4. Check PAPER_TRADE stage results
  const paperTradeResults = progress?.stages?.filter((s: any) => s.stageName === 'PAPER_TRADE') || []
  console.log(`\nPAPER_TRADE stages: ${paperTradeResults.length}`)

  let tradesExecuted = 0
  let tradesSkipped = 0
  for (const stage of paperTradeResults) {
    console.log(`  ${stage.symbol}: ${stage.resultSummary} (${stage.status})`)
    if (stage.resultSummary?.includes('trade(s) executed')) {
      const match = stage.resultSummary.match(/^(\d+)/)
      const count = match ? parseInt(match[1]) : 0
      if (count > 0) {
        tradesExecuted++
      } else {
        tradesSkipped++
      }
    }
  }

  console.log(`\nSymbols with trades executed: ${tradesExecuted}, skipped (0 trades): ${tradesSkipped}`)

  // 5. Check positions endpoint
  const positionsRes = await request.get(`${API}/api/positions`)
  expect(positionsRes.status()).toBe(200)
  const positionsData = (await positionsRes.json()) as any
  const positions = positionsData.data || positionsData
  console.log(`Positions: ${positions.length}`)

  // 6. Check trades endpoint
  const tradesRes = await request.get(`${API}/api/trades`)
  expect(tradesRes.status()).toBe(200)
  const tradesData = (await tradesRes.json()) as any
  const trades = tradesData.data || tradesData
  console.log(`Total trades: ${trades.length}`)

  // Assertions
  expect(progress?.status).toBe('COMPLETED')
  expect(paperTradeResults.length).toBeGreaterThan(0)
  expect(paperTradeResults.length).toBe(progress?.totalSymbols)

  // Positions and trades should have valid structure
  if (positions.length > 0) {
    const pos = positions[0]
    expect(pos).toHaveProperty('symbol')
    expect(pos).toHaveProperty('status')
  }
})