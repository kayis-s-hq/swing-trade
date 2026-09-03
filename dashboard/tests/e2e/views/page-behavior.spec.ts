import { test, expect } from '@playwright/test'

const DASHBOARD = 'http://localhost:3003'

const pages = [
  { path: '/', heading: 'Dashboard', marker: 'Market overview' },
  { path: '/positions', heading: 'Positions', marker: 'Active and closed paper trading positions' },
  { path: '/signals', heading: 'Signals', marker: 'Active scanning and signal generation' },
  { path: '/portfolio', heading: 'Portfolio', marker: 'Portfolio' },
  { path: '/watchlist', heading: 'Watchlist', marker: 'Watchlist' },
  {
    path: '/data',
    heading: 'Data Ingestion',
    marker: 'Monitor data quality and pull historical market data',
  },
  {
    path: '/settings',
    heading: 'Settings',
    marker: 'Broker connections and trading configuration',
  },
]

for (const { path, heading, marker } of pages) {
  test(`${heading} route renders its expected shell`, async ({ page }) => {
    await page.goto(`${DASHBOARD}${path}`)
    await page.waitForLoadState('networkidle')

    await expect(page).toHaveTitle(/SwingTrade/i)
    await expect(page.getByRole('heading', { name: heading, exact: true })).toBeVisible()
    await expect(page.locator('body')).toContainText(marker)

    const bodyText = await page.locator('body').textContent()
    expect(bodyText).not.toMatch(/Unexpected error|TypeError|Cannot read/)
  })
}
