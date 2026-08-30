/* This exploratory spec intentionally logs diagnostics and inspects dynamic API data. */
/* eslint-disable no-console, @typescript-eslint/no-explicit-any */
import { test, expect } from '@playwright/test'

test('orchestrator page — full feature check', async ({ page }) => {
  const consoleMessages: string[] = []
  page.on('console', (msg) => consoleMessages.push(msg.text()))

  await page.goto('http://localhost:3003/orchestrator')
  await page.waitForTimeout(4000)

  // Print all console messages
  console.log('\n=== CONSOLE MESSAGES ===')
  consoleMessages.forEach((m) => console.log('  ', m))

  // 1. Check page loaded without error
  const errorBanner = page.locator('text=AN UNEXPECTED ERROR OCCURRED')
  await expect(errorBanner).not.toBeVisible()
  console.log('[PASS] No error banner')

  // 2. Check header elements
  await expect(page.locator('h1:has-text("Job Orchestrator")')).toBeVisible()
  console.log('[PASS] Header visible')

  // 3. Check buttons exist
  await expect(page.locator('button:has-text("Run")')).toBeVisible()
  await expect(page.locator('button:has-text("History")')).toBeVisible()
  console.log('[PASS] Buttons visible')

  // 4. Check table structure
  const tableRows = await page.locator('table tbody tr').all()
  console.log(`Table rows: ${tableRows.length}`)
  expect(tableRows.length).toBeGreaterThan(0)

  // 5. Check stage columns have icons (SVGs)
  for (let i = 0; i < tableRows.length; i++) {
    const cells = await tableRows[i].locator('td').all()
    let svgCount = 0
    for (let j = 1; j <= 7; j++) {
      // cols 1-7 are stage columns
      if (j < cells.length) {
        svgCount += await cells[j].locator('svg').count()
      }
    }
    const innerHTML = await cells[1].innerHTML()
    console.log(
      `Row ${i} (${await tableRows[i].locator('td').first().innerText()}): ${svgCount} SVGs, innerHTML="${innerHTML}"`
    )
    if (i < 3) {
      // only assert first few rows
      expect(svgCount).toBeGreaterThan(0)
    }
  }

  // 6. Check past runs table
  const pastRunsRows = await page.locator('table tbody tr').all()
  const hasPastRun = pastRunsRows.some((r) =>
    r
      .locator('td')
      .first()
      .innerText()
      .then((t) => t.includes(':'))
  )
  console.log(`Past runs visible: ${hasPastRun}`)

  // 7. Click a row to expand (accordion)
  const firstRow = page.locator('table tbody tr').first()
  await firstRow.click()
  await page.waitForTimeout(500)

  // Check for expanded detail row using the grid container
  const detailCards = page.locator('.grid .rounded-md.border.bg-bg-surface.p-3')
  const cardCount = await detailCards.count()
  console.log(`Detail stage cards: ${cardCount}`)
  expect(cardCount).toBe(7) // all 7 stages

  // 8. Click again to collapse
  await firstRow.click()
  await page.waitForTimeout(500)
  const detailAfterCollapse = await detailCards.count()
  console.log(`After collapse: ${detailAfterCollapse} detail cards`)
  expect(detailAfterCollapse).toBe(0)

  // 10. Verify API data directly
  const apiData = await page.evaluate(async () => {
    const res = await fetch('http://localhost:8080/api/job/runs')
    const data = await res.json()
    // API returns an array of run summaries
    if (!Array.isArray(data) || data.length === 0) return { runs: 0 }
    const runId = data[0].runId
    const progRes = await fetch(`http://localhost:8080/api/job/runs/${runId}/progress`)
    const prog = await progRes.json()
    return {
      runs: data.length,
      runId: prog.runId,
      status: prog.status,
      totalSymbols: prog.totalSymbols,
      stagesCount: prog.stages?.length,
      symbols: [...new Set(prog.stages?.map((s: any) => s.symbol))],
      sampleStage: prog.stages?.[0],
    }
  })
  console.log(`\n=== API VERIFICATION ===`)
  console.log(JSON.stringify(apiData, null, 2))
  expect(apiData.runs).toBeGreaterThan(0)
  expect(['RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED']).toContain(apiData.status)
  expect(apiData.totalSymbols).toBeGreaterThan(0)
  expect(apiData.stagesCount).toBeGreaterThan(0)
})
