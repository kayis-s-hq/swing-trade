import { expect, test } from '@playwright/test'
import { fulfillJson, mockDashboardApi } from '../fixtures/api'

test.describe('error management', () => {
  test('initial position load failure shows an accessible retry alert, not an empty state', async ({
    page,
  }) => {
    let attempts = 0
    await mockDashboardApi(page, async (route, path) => {
      if (path === '/positions' && route.request().method() === 'GET') {
        attempts += 1
        // GET retries once in the transport layer; fail both attempts so the
        // view reaches its explicit initial-error state.
        if (attempts <= 2) {
          await route.fulfill({
            status: 500,
            contentType: 'text/html',
            body: '<html><body>internal diagnostics</body></html>',
          })
        } else {
          await fulfillJson(route, {
            content: [],
            totalElements: 0,
            totalPages: 0,
            size: 20,
            number: 0,
          })
        }
        return true
      }
      return false
    })

    await page.goto('/positions')
    const alert = page.getByRole('alert').filter({ hasText: 'Couldn’t load positions' })
    await expect(alert).toContainText('Couldn’t load positions')
    await expect(alert).not.toContainText('internal diagnostics')
    await expect(page.getByText('No positions found')).toHaveCount(0)

    await expect(alert.getByRole('button', { name: 'Retry' })).toBeEnabled()
  })

  test('HTTP 200 success:false keeps the position form open and preserves input', async ({
    page,
  }) => {
    await mockDashboardApi(page, async (route, path) => {
      if (path === '/positions' && route.request().method() === 'POST') {
        await fulfillJson(route, {
          success: false,
          error: 'Position cannot be opened',
          code: 'RISK_LIMIT',
        })
        return true
      }
      return false
    })

    await page.goto('/positions')
    await page.getByRole('button', { name: 'New Position' }).click()
    await page.getByPlaceholder('e.g. RELIANCE').fill('RELIANCE')
    await page.locator('input[type="number"]').nth(1).fill('10')
    await page.getByRole('button', { name: 'Create Position' }).click()

    const alert = page.getByRole('alert').filter({ hasText: 'Couldn’t create position' })
    await expect(alert).toContainText('Couldn’t create position')
    await expect(page.getByRole('heading', { name: 'New Position' })).toBeVisible()
    await expect(page.getByPlaceholder('e.g. RELIANCE')).toHaveValue('RELIANCE')
  })

  test('backend availability banner reports an unreachable service and recovers immediately', async ({
    page,
  }) => {
    let healthAttempts = 0
    await mockDashboardApi(page, async (route, path) => {
      if (path === '/health') {
        healthAttempts += 1
        // Health reads retry once before the unavailable state is committed.
        if (healthAttempts <= 2) await route.abort('failed')
        else await fulfillJson(route, { status: 'UP', components: {} })
        return true
      }
      return false
    })

    await page.goto('/positions')
    const banner = page.getByRole('alert').filter({ hasText: 'Backend unavailable' })
    await expect(banner).toContainText('Can’t reach the backend')
    await banner.getByRole('button', { name: 'Retry backend health check' }).click()
    await expect(banner).toHaveCount(0)
  })
})
