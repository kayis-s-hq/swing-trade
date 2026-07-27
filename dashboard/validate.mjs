import { test } from '@playwright/test'

test('dashboard loads on port 3004', async ({ page }) => {
  await page.goto('http://localhost:3004', { waitUntil: 'networkidle' })
  await page.screenshot({ path: 'tests/e2e/screenshots/validate-dashboard.png' })
  const title = await page.title()
  console.log('Page title:', title)
  if (!title.includes('SwingTrade')) {
    throw new Error(`Expected title to contain "SwingTrade", got "${title}"`)
  }
})

test('sentiment page loads', async ({ page }) => {
  await page.goto('http://localhost:3004/sentiment', { waitUntil: 'networkidle' })
  await page.screenshot({ path: 'tests/e2e/screenshots/validate-sentiment.png' })
  const heading = await page.locator('h1:has-text("Sentiment")').first().textContent()
  console.log('Sentiment heading:', heading)
  if (!heading) {
    throw new Error('Sentiment heading not found')
  }
})
