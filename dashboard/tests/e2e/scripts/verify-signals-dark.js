/* This diagnostic script intentionally prints browser diagnostics. */
/* eslint-disable no-console */
import { chromium } from 'playwright'
;(async () => {
  const browser = await chromium.launch({ headless: true })
  const page = await browser.newPage()
  await page.goto('http://localhost:3003/signals', { waitUntil: 'networkidle' })

  // Check theme
  const darkClass = await page.evaluate(() => document.documentElement.classList.contains('dark'))
  const bodyBg = await page.evaluate(() => getComputedStyle(document.body).backgroundColor)
  const cards = await page.locator('.grid > *').all()
  console.log(`Dark mode: ${darkClass}, bodyBg: ${bodyBg}`)
  console.log(`Cards: ${cards.length}`)

  for (let i = 0; i < cards.length; i++) {
    const card = cards[i]
    const bg = await card.evaluate((el) => getComputedStyle(el).backgroundColor)
    const height = await card.evaluate((el) => el.clientHeight)
    const symbol = await card
      .locator('text=^[A-Z]+$')
      .first()
      .textContent()
      .catch(() => '?')
    console.log(`Card ${i}: ${height}px, bg=${bg}, symbol=${symbol}`)
  }

  await browser.close()
})()
