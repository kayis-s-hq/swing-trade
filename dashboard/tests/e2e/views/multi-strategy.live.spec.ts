import { expect, test, type Page } from '@playwright/test'

/* Live smoke test for the multi-strategy views; needs the dev stack (API :8080, dashboard :3003). */

const SHOTS = process.env.PW_SHOTS ?? 'test-results/multi-strategy'

function watch(page: Page) {
  const problems: string[] = []
  page.on('console', (m) => {
    if (m.type() === 'error') problems.push(`console: ${m.text()}`)
  })
  page.on('pageerror', (e) => problems.push(`pageerror: ${e.message}`))
  page.on('response', (r) => {
    if (r.url().includes('/api/') && r.status() >= 400)
      problems.push(`http ${r.status()}: ${r.url()}`)
  })
  return problems
}

async function open(page: Page, path: string, name: string) {
  const problems = watch(page)
  await page.goto(path, { waitUntil: 'networkidle' })
  await page.screenshot({ path: `${SHOTS}/${name}.png`, fullPage: true })
  return problems
}

test('orchestrator shows the signal tournament panel', async ({ page }) => {
  const problems = await open(page, '/orchestrator', 'orchestrator')
  await expect(page.getByLabel('Signal tournament')).toBeVisible()
  await expect(page.getByLabel('Signal tournament')).toContainText('winner')
  expect(problems).toEqual([])
})

test('signals can be grouped by symbol', async ({ page }) => {
  const problems = await open(page, '/signals', 'signals')
  await expect(page.getByRole('button', { name: /by symbol/i })).toBeVisible()
  await page.getByRole('button', { name: /by symbol/i }).click()
  await page.screenshot({ path: `${SHOTS}/signals-grouped.png`, fullPage: true })
  const winnerChip = page.getByTestId('variant-chip').filter({ hasText: 'pullback-v1' }).first()
  await expect(winnerChip).toBeVisible()
  await expect(winnerChip).toContainText('BUY')
  await expect(page.getByTestId('consensus-badge').filter({ hasText: 'BUY' }).first()).toBeVisible()
  expect(problems).toEqual([])
})

test('positions has a portfolio switcher with shadow books', async ({ page }) => {
  const problems = await open(page, '/positions', 'positions')
  const tabs = page.getByRole('tablist', { name: 'Portfolio' })
  await expect(tabs).toContainText('Real (champion)')
  await expect(tabs).toContainText('breakout-v1')
  await tabs.getByRole('tab', { name: /breakout-v1/ }).click()
  await expect(page.getByLabel('Shadow book')).toContainText('SHADOW')
  await expect(tabs.getByRole('tab', { name: /breakout-v1/ })).toHaveAttribute(
    'aria-selected',
    'true'
  )
  await expect(tabs.getByRole('tab', { name: /Real/ })).toHaveAttribute('aria-selected', 'false')
  await page.screenshot({ path: `${SHOTS}/positions-shadow.png`, fullPage: true })
  await tabs.getByRole('tab', { name: /Real/ }).click()
  await expect(page.getByLabel('Shadow book')).toHaveCount(0)
  expect(problems).toEqual([])
})

test('symbol detail shows the strategy matrix', async ({ page }) => {
  const problems = await open(page, '/symbols/ADANIPORTS', 'symbol')
  await expect(page.getByRole('heading', { name: 'ADANIPORTS' })).toBeVisible()
  await expect(page.getByText('Symbol detail', { exact: true })).toBeVisible()
  await expect(page.getByText('Strategy matrix')).toBeVisible()
  await expect(page.getByText('pullback-v1').first()).toBeVisible()
  await expect(page.getByText('Tournament history')).toBeVisible()
  expect(problems).toEqual([])
})

test('strategy report renders leaderboard and arbitration rules', async ({ page }) => {
  const problems = await open(page, '/strategy-report', 'strategy-report')
  await expect(page.getByLabel('Strategy leaderboard')).toBeVisible()
  await expect(page.getByLabel('Arbitration rules')).toBeVisible()
  expect(problems).toEqual([])
})

test('home shows the strategy board', async ({ page }) => {
  const problems = await open(page, '/', 'home')
  await expect(page.getByText('breakout-v1').first()).toBeVisible()
  expect(problems).toEqual([])
})

test('backtest has a portfolio compare tab', async ({ page }) => {
  const problems = await open(page, '/backtest', 'backtest')
  await page.getByText('Portfolio compare', { exact: true }).click()
  await page.screenshot({ path: `${SHOTS}/backtest-compare.png`, fullPage: true })
  expect(problems).toEqual([])
})

test('strategies page still loads', async ({ page }) => {
  const problems = await open(page, '/strategies', 'strategies')
  await expect(page.getByText('Variants').first()).toBeVisible()
  expect(problems).toEqual([])
})
