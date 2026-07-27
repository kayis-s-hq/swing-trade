import { test, expect } from '@playwright/test'

test('signals view loads', async ({ page }) => {
  await page.goto('http://localhost:3003/signals')
  await page.waitForLoadState('networkidle')
  await page.screenshot({ path: '/tmp/signals-view.png', fullPage: true })
  const cards = await page.locator('.grid').locator('> *').count()
  console.log(`Signal cards: ${cards}`)
  const empty = await page
    .locator('text=No signals matching filter')
    .isVisible()
    .catch(() => false)
  console.log(`Empty state: ${empty}`)
  const loading = await page
    .locator('text=Scanning for signals')
    .isVisible()
    .catch(() => false)
  console.log(`Loading: ${loading}`)
  console.log(`Page title: ${await page.title()}`)
  const bodyText = await page.locator('body').textContent()
  const errorMatch = bodyText?.match(/Unexpected error|TypeError|Cannot read/)
  console.log(`JS error in page: ${errorMatch?.[0] || 'none'}`)
})
