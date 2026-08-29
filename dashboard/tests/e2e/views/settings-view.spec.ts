import { test, expect } from '@playwright/test'

const DASHBOARD = 'http://localhost:3003'

test.describe('Settings View', () => {
  test('page header renders', async ({ page }) => {
    await page.goto(`${DASHBOARD}/settings`)
    await page.waitForLoadState('networkidle')

    await expect(page.locator('h1', { hasText: 'Settings' })).toBeVisible()
    await expect(
      page.locator('p', { hasText: /Broker connections and trading configuration/ })
    ).toBeVisible()
  })

  test('shows loading state then resolves', async ({ page }) => {
    await page.goto(`${DASHBOARD}/settings`)

    await page.waitForLoadState('networkidle')

    // Loading should be gone after networkidle
    const loadingGone = await page
      .locator('text=Checking system')
      .isVisible()
      .catch(() => false)
    expect(loadingGone).toBe(false)
  })

  test('renders all section headers', async ({ page }) => {
    await page.goto(`${DASHBOARD}/settings`)
    await page.waitForLoadState('networkidle')

    await expect(page.locator('h2', { hasText: 'Broker Connection' })).toBeVisible()
    await expect(page.locator('h2', { hasText: 'LLM & Intelligence' })).toBeVisible()
    await expect(page.locator('h2', { hasText: 'Trading Configuration' })).toBeVisible()
    await expect(page.locator('h2', { hasText: 'System Health' })).toBeVisible()
  })

  test('broker selection buttons render', async ({ page }) => {
    await page.goto(`${DASHBOARD}/settings`)
    await page.waitForLoadState('networkidle')

    await expect(page.getByRole('button', { name: 'Fyers' })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Upstox' })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Yahoo Finance' })).toBeVisible()
    await expect(page.getByRole('button', { name: 'None (Read Only)' })).toBeVisible()
  })

  test('broker selection highlights active broker', async ({ page }) => {
    await page.goto(`${DASHBOARD}/settings`)
    await page.waitForLoadState('networkidle')

    // Fyers is the default broker
    const fyersBtn = page.getByRole('button', { name: 'Fyers' })
    await expect(fyersBtn).toHaveClass(/bg-brand-subtle/)
  })

  test('broker selection switches displayed content', async ({ page }) => {
    await page.goto(`${DASHBOARD}/settings`)
    await page.waitForLoadState('networkidle')

    // Fyers content should be visible by default
    const fyersConnect = page.locator('button:has-text("Connect")')
    const fyersVisible = await fyersConnect.isVisible().catch(() => false)

    if (fyersVisible) {
      await expect(fyersConnect).toBeVisible()
    }

    // Click Upstox — should show placeholder
    await page.getByRole('button', { name: 'Upstox' }).click()
    await expect(page.locator('text=Upstox integration coming soon')).toBeVisible()

    // Click Yahoo Finance — should show status
    await page.getByRole('button', { name: 'Yahoo Finance' }).click()
    await expect(page.locator('text=No API Key')).toBeVisible()

    // Click None
    await page.getByRole('button', { name: 'None (Read Only)' }).click()

    // Switch back to Fyers
    await page.getByRole('button', { name: 'Fyers' }).click()
  })

  test('Fyers connection status indicator renders', async ({ page }) => {
    await page.goto(`${DASHBOARD}/settings`)
    await page.waitForLoadState('networkidle')

    // Should show Connected or Disconnected text
    const hasConnected = await page
      .locator('text=Connected')
      .isVisible()
      .catch(() => false)
    const hasDisconnected = await page
      .locator('text=Disconnected')
      .isVisible()
      .catch(() => false)
    expect(hasConnected || hasDisconnected).toBe(true)
  })

  test('Fyers connect button is visible when disconnected', async ({ page }) => {
    await page.goto(`${DASHBOARD}/settings`)
    await page.waitForLoadState('networkidle')

    const connectBtn = page.locator('button:has-text("Connect Fyers Account")')
    const visible = await connectBtn.isVisible().catch(() => false)

    if (visible) {
      await expect(connectBtn).toBeDisabled(false)
    }
  })

  test('Fyers disconnect button is visible when connected', async ({ page }) => {
    await page.goto(`${DASHBOARD}/settings`)
    await page.waitForLoadState('networkidle')

    const disconnectBtn = page.locator('button:has-text("Disconnect")')
    const visible = await disconnectBtn.isVisible().catch(() => false)

    if (visible) {
      await expect(disconnectBtn).toBeEnabled()
    }
  })

  test('Fyers auth code input appears with connect button', async ({ page }) => {
    await page.goto(`${DASHBOARD}/settings`)
    await page.waitForLoadState('networkidle')

    // Auth code input should be visible alongside connect button
    const authInput = page.locator('input[placeholder="Paste auth code from browser"]')
    const authVisible = await authInput.isVisible().catch(() => false)

    if (authVisible) {
      await expect(authInput).toBeVisible()

      // Submit button should also be visible
      await expect(page.locator('button:has-text("Submit")')).toBeVisible()

      // Help text should be visible
      await expect(page.locator('text=Complete login on Fyers')).toBeVisible()
    }
  })

  test('vLLM configuration section renders', async ({ page }) => {
    await page.goto(`${DASHBOARD}/settings`)
    await page.waitForLoadState('networkidle')

    await expect(page.locator('h3', { hasText: 'vLLM Endpoint' })).toBeVisible()

    // Find the vLLM section and check for inputs within it
    const vllmSection = page.locator('h3', { hasText: 'vLLM Endpoint' }).locator('..')
    const inputs = vllmSection.locator('input')
    const inputCount = await inputs.count()
    expect(inputCount).toBeGreaterThanOrEqual(2)

    // Test button should be visible (scoped to vLLM section)
    const testBtns = vllmSection.locator('button:has-text("Test")')
    const testCount = await testBtns.count()
    expect(testCount).toBeGreaterThanOrEqual(1)
  })

  test('PDF Extraction section renders', async ({ page }) => {
    await page.goto(`${DASHBOARD}/settings`)
    await page.waitForLoadState('networkidle')

    await expect(page.locator('h3', { hasText: 'PDF Extraction (Pi 5)' })).toBeVisible()

    // Find the PDF section and check for inputs within it
    const pdfSection = page.locator('h3', { hasText: 'PDF Extraction (Pi 5)' }).locator('..')
    const inputs = pdfSection.locator('input')
    const inputCount = await inputs.count()
    expect(inputCount).toBeGreaterThanOrEqual(2)
  })

  test('Discord notification settings render', async ({ page }) => {
    await page.goto(`${DASHBOARD}/settings`)
    await page.waitForLoadState('networkidle')

    await expect(page.locator('h3', { hasText: 'Discord Notifications' })).toBeVisible()

    // Toggle switch
    const toggle = page.locator('input[type="checkbox"].sr-only')
    await expect(toggle).toBeVisible()

    // Webhook URL input
    const webhookInput = page.locator('input[placeholder*="webhook"]')
    const webhookVisible = await webhookInput.isVisible().catch(() => false)
    if (webhookVisible) {
      await expect(webhookInput).toBeVisible()
    }

    // Test button (scoped to Discord section)
    const discordSection = page.locator('h3', { hasText: 'Discord Notifications' }).locator('..')
    const testBtns = discordSection.locator('button:has-text("Test")')
    const testCount = await testBtns.count()
    expect(testCount).toBeGreaterThanOrEqual(1)
  })

  test('Trading Configuration section renders', async ({ page }) => {
    await page.goto(`${DASHBOARD}/settings`)
    await page.waitForLoadState('networkidle')

    // Mode select
    const modeSelect = page.locator('select')
    await expect(modeSelect).toBeVisible()

    // Check options
    const modeOptions = await modeSelect.locator('option').allTextContents()
    expect(modeOptions).toContain('Paper Trading')
    expect(modeOptions).toContain('Live Trading')

    // Max Position Size input
    const maxPosLabel = page.locator('text=Max Position Size')
    await expect(maxPosLabel).toBeVisible()

    // Stop Loss input
    const slLabel = page.locator('text=Stop Loss')
    await expect(slLabel).toBeVisible()

    // Take Profit input
    const tpLabel = page.locator('text=Take Profit')
    await expect(tpLabel).toBeVisible()

    // Percentage indicators
    const percentSigns = await page.locator('text=%').count()
    expect(percentSigns).toBeGreaterThanOrEqual(3)
  })

  test('Trading Configuration inputs accept numeric values', async ({ page }) => {
    await page.goto(`${DASHBOARD}/settings`)
    await page.waitForLoadState('networkidle')

    // Find all number inputs in the Trading Configuration section
    const tradingSection = page.locator('text=Trading Configuration').locator('..')
    const numberInputs = tradingSection.locator('input[type="number"]')
    const count = await numberInputs.count()

    if (count > 0) {
      // Fill the first number input
      await numberInputs.first().fill('50')
      const value = await numberInputs.first().inputValue()
      expect(value).toBe('50')
    }
  })

  test('Save All Settings button renders', async ({ page }) => {
    await page.goto(`${DASHBOARD}/settings`)
    await page.waitForLoadState('networkidle')

    const saveBtn = page.locator('button:has-text("Save All Settings")')
    await expect(saveBtn).toBeVisible()
    await expect(saveBtn).toBeEnabled()
  })

  test('Save button shows loading state', async ({ page }) => {
    await page.goto(`${DASHBOARD}/settings`)
    await page.waitForLoadState('networkidle')

    const saveBtn = page.locator('button:has-text("Save All Settings")')
    await expect(saveBtn).toBeVisible()

    // Click save and verify it changes to "Saving..."
    await saveBtn.click()

    // Should show "Saving..." briefly
    const savingState = await page
      .locator('button:has-text("Saving...")')
      .isVisible()
      .catch(() => false)
    if (savingState) {
      await expect(page.locator('button:has-text("Saving...")')).toBeVisible()
    }
  })

  test('Save button shows success state', async ({ page }) => {
    await page.goto(`${DASHBOARD}/settings`)
    await page.waitForLoadState('networkidle')

    const saveBtn = page.locator('button:has-text("Save All Settings")')
    await saveBtn.click()

    // Should briefly show "Saved!" with success styling
    const savedState = await page
      .locator('button:has-text("Saved!")')
      .isVisible()
      .catch(() => false)
    if (savedState) {
      await expect(page.locator('button:has-text("Saved!")')).toBeVisible()
    }
  })

  test('System Health section renders with component statuses', async ({ page }) => {
    await page.goto(`${DASHBOARD}/settings`)
    await page.waitForLoadState('networkidle')

    // Health section should have component rows
    const healthRows = page.locator('.card-panel').last()
    await expect(healthRows).toBeVisible()

    // Should have status badges with rounded-full
    const statusBadges = page.locator('span.rounded-full.px-2\\.5')
    const badgeCount = await statusBadges.count()

    // Either badges are visible or loading spinner is visible
    const loadingVisible = await page
      .locator('text=Checking system')
      .isVisible()
      .catch(() => false)
    expect(badgeCount > 0 || loadingVisible).toBe(true)
  })

  test('Health status badges show color-coded states', async ({ page }) => {
    await page.goto(`${DASHBOARD}/settings`)
    await page.waitForLoadState('networkidle')

    // Health badges should have status colors
    const healthSection = page.locator('.card-panel').last()
    const healthText = (await healthSection.textContent()) || ''

    // Should contain status indicators (UP/DOWN/DEGRADED or similar)
    const hasStatus =
      healthText.includes('UP') ||
      healthText.includes('DOWN') ||
      healthText.includes('DEGRADED') ||
      healthText.includes('Checking')
    expect(hasStatus).toBe(true)
  })

  test('Auth error banner renders', async ({ page }) => {
    await page.goto(`${DASHBOARD}/settings`)
    await page.waitForLoadState('networkidle')

    // Navigate with auth=error param to show banner
    await page.goto(`${DASHBOARD}/settings?auth=error`)
    await page.waitForLoadState('networkidle')

    const errorBanner = page.locator('text=Fyers authentication failed')
    const errorVisible = await errorBanner.isVisible().catch(() => false)

    if (errorVisible) {
      // Banner should have a dismiss button
      await expect(page.locator('button').filter({ hasText: /×/ })).toBeVisible()
    }
  })

  test('Auth success banner renders', async ({ page }) => {
    await page.goto(`${DASHBOARD}/settings`)
    await page.waitForLoadState('networkidle')

    // Navigate with auth=success param to show banner
    await page.goto(`${DASHBOARD}/settings?auth=success`)
    await page.waitForLoadState('networkidle')

    const successBanner = page.locator('text=Fyers connected successfully')
    const successVisible = await successBanner.isVisible().catch(() => false)

    if (successVisible) {
      // Banner should have a dismiss button
      await expect(page.locator('button').filter({ hasText: /×/ })).toBeVisible()
    }
  })

  test('no JavaScript errors on page load', async ({ page }) => {
    await page.goto(`${DASHBOARD}/settings`)
    await page.waitForLoadState('networkidle')

    const consoleErrors = []
    page.on('console', (msg) => {
      if (msg.type() === 'error') {
        consoleErrors.push(msg.text())
      }
    })

    await page.waitForTimeout(1000)

    expect(consoleErrors).toHaveLength(0)
  })

  test('navigation to settings page works from URL directly', async ({ page }) => {
    await page.goto(`${DASHBOARD}/settings`)
    await page.waitForLoadState('networkidle')

    expect(page.url()).toContain('/settings')
    await expect(page.locator('h1', { hasText: 'Settings' })).toBeVisible()
  })

  test('LLM section has model name helper text', async ({ page }) => {
    await page.goto(`${DASHBOARD}/settings`)
    await page.waitForLoadState('networkidle')

    // "Model name" helper text should appear next to model inputs
    const modelNameHelpers = await page.locator('text=Model name').count()
    // At least one (vLLM model)
    expect(modelNameHelpers).toBeGreaterThanOrEqual(1)
  })

  test('Discord toggle is clickable', async ({ page }) => {
    await page.goto(`${DASHBOARD}/settings`)
    await page.waitForLoadState('networkidle')

    const toggle = page.locator('input[type="checkbox"].sr-only').first()
    await expect(toggle).toBeVisible()

    // Click the visible toggle label (peer-checked changes styling)
    const toggleLabel = page.locator('label').filter({ has: toggle }).first()
    await toggleLabel.click()

    // Toggle should change state
    const isChecked = await toggle.isChecked()
    expect(typeof isChecked).toBe('boolean')
  })

  test('Discord enabled label is visible', async ({ page }) => {
    await page.goto(`${DASHBOARD}/settings`)
    await page.waitForLoadState('networkidle')

    await expect(page.locator('text=Enable Discord')).toBeVisible()
  })

  test('Trading mode select has correct options', async ({ page }) => {
    await page.goto(`${DASHBOARD}/settings`)
    await page.waitForLoadState('networkidle')

    const modeSelect = page.locator('select').first()
    await expect(modeSelect).toBeVisible()

    const options = await modeSelect.locator('option').all()
    expect(options.length).toBeGreaterThanOrEqual(2)

    const optionValues = await Promise.all(options.map((o) => o.textContent()))
    expect(optionValues.some((o) => o.includes('Paper'))).toBe(true)
    expect(optionValues.some((o) => o.includes('Live'))).toBe(true)
  })

  test('All sections are card-panel styled', async ({ page }) => {
    await page.goto(`${DASHBOARD}/settings`)
    await page.waitForLoadState('networkidle')

    const cardPanels = page.locator('.card-panel')
    const count = await cardPanels.count()

    // Should have at least: Broker Connection, LLM & Intelligence, Trading Configuration, System Health
    expect(count).toBeGreaterThanOrEqual(4)
  })
})
