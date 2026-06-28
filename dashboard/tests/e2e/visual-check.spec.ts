import { test } from '@playwright/test'

const BASE = 'http://localhost:3003'

const pages = [
  { path: '/', label: 'Dashboard' },
  { path: '/positions', label: 'Positions' },
  { path: '/signals', label: 'Signals' },
  { path: '/portfolio', label: 'Portfolio' },
  { path: '/watchlist', label: 'Watchlist' },
  { path: '/data', label: 'Data' },
  { path: '/settings', label: 'Settings' },
]

for (const { path, label } of pages) {
  test(`${label} page`, async ({ page }) => {
    await page.goto(path)
    await page.waitForLoadState('networkidle')
    await page.screenshot({ path: `dashboard/tests/e2e/screenshots/${label.replace(/\s+/g, '-')}.png`, fullPage: true })
  })
}
