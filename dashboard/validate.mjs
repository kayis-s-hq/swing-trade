import { test } from '@playwright/test'

/* This validation intentionally avoids screenshots for faster local checks. */

test('dashboard loads on port 3004', async ({ page }) => {
  await page.goto('http://localhost:3004', { waitUntil: 'networkidle' })
  const title = await page.title()
  console.warn('Page title:', title)
  if (!title.includes('SwingTrade')) {
    throw new Error(`Expected title to contain "SwingTrade", got "${title}"`)
  }
})

test('sentiment page loads', async ({ page }) => {
  await page.goto('http://localhost:3004/sentiment', { waitUntil: 'networkidle' })
  const heading = await page.locator('h1:has-text("Sentiment")').first().textContent()
  console.warn('Sentiment heading:', heading)
  if (!heading) {
    throw new Error('Sentiment heading not found')
  }
})
