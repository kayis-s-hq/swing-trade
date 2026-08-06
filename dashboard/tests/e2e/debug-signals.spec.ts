import { chromium } from 'playwright'

const browser = await chromium.launch({ headless: true })
const page = await browser.newPage()
page.on('console', (msg) => console.log(`CONSOLE [${msg.type()}]: ${msg.text()}`))

await page.goto('http://localhost:3003/signals', { waitUntil: 'networkidle' })
await page.waitForSelector('.grid', { timeout: 10000 })

// Get card bounding boxes
const cards = await page.locator('.grid > *').all()
console.log(`Cards count: ${cards.length}`)
for (let i = 0; i < cards.length; i++) {
  const card = cards[i]
  const box = await card.boundingBox()
  const bg = await card.evaluate((el) => getComputedStyle(el).backgroundColor)
  const height = await card.evaluate((el) => el.clientHeight)
  const width = await card.evaluate((el) => el.clientWidth)
  const display = await card.evaluate((el) => getComputedStyle(el).display)
  const visibility = await card.evaluate((el) => getComputedStyle(el).visibility)
  console.log(
    `Card ${i}: ${width}x${height}, display=${display}, visibility=${visibility}, bg=${bg}`
  )
  const children = await card.locator('> *').count()
  console.log(`  Visible children: ${children}`)
}

// Check grid height
const grid = await page.locator('.grid').first()
const gridBox = await grid.boundingBox()
console.log(`Grid: ${gridBox?.width}x${gridBox?.height}`)

await browser.close()
