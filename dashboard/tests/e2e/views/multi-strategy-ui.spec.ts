import { test, expect } from '@playwright/test'
import { fulfillJson, mockDashboardApi } from '../fixtures/api'

function strategy(variantId: string, mode: string, params: Record<string, unknown> = {}) {
  return {
    id: 1,
    variantId,
    version: 1,
    strategyType: 'RSI',
    params,
    overlays: {},
    paramsHash: 'h',
    mode,
    paperCapital: 100000,
    current: true,
    notes: null,
    createdAt: '2026-09-21T00:00:00Z',
  }
}

const strategyTypes = [
  {
    type: 'RSI',
    paramSchema: {
      params: [{ name: 'period', type: 'INT', min: 2, max: 50, defaultValue: 14, group: 'trend' }],
    },
    warmupBars: 20,
    requiredIndicators: ['RSI'],
  },
]

const run = {
  runId: 'run-1',
  triggerType: 'MANUAL',
  status: 'COMPLETED_WITH_WARNINGS',
  startedAt: '2026-09-21T10:00:00',
  completedAt: '2026-09-21T10:01:00',
  symbolsCount: 1,
  completedCount: 1,
  failedCount: 0,
  errorMessage: null,
}

test.describe('multi-strategy orchestration UI (mocked API)', () => {
  test('orchestrator: scoped start posts filters and matrix renders degraded state', async ({
    page,
  }) => {
    let startBody: unknown
    await mockDashboardApi(page, async (route, path) => {
      if (path === '/strategy-configs') {
        await fulfillJson(route, {
          success: true,
          data: [strategy('ALPHA', 'SHADOW'), strategy('BETA', 'CHAMPION')],
        })
        return true
      }
      if (path === '/job/runs') {
        await fulfillJson(route, [run])
        return true
      }
      if (path === '/job/runs/run-1/progress') {
        await fulfillJson(route, {
          runId: 'run-1',
          status: 'COMPLETED_WITH_WARNINGS',
          totalSymbols: 1,
          completedSymbols: 1,
          failedSymbols: 0,
          startedAt: run.startedAt,
          stages: [
            {
              symbol: 'TCS',
              stageName: 'SIGNAL',
              status: 'COMPLETED',
              startedAt: run.startedAt,
              durationMs: 4,
              details: {
                strategies: [
                  { variantId: 'ALPHA', version: 1, outcome: 'EVALUATED', signal: 'BUY' },
                  { variantId: 'BETA', version: 1, outcome: 'SKIPPED', reason: 'bad params' },
                ],
              },
            },
            {
              symbol: 'TCS',
              stageName: 'SENTIMENT',
              status: 'DEGRADED',
              startedAt: run.startedAt,
              durationMs: 4,
              details: { source: 'KEYWORD_FALLBACK', reason: 'LLM returned 0 chars' },
            },
          ],
        })
        return true
      }
      if (path === '/job/runs/run-1/summary') {
        await fulfillJson(route, {}, 404)
        return true
      }
      if (path.startsWith('/job/runs/start')) {
        startBody = route.request().postDataJSON()
        await fulfillJson(route, { ...run, status: 'RUNNING', completedAt: null })
        return true
      }
      return false
    })

    await page.goto('/orchestrator')
    await expect(page.getByTestId('start-panel')).toBeVisible()
    await expect(page.getByTestId('cell-TCS-ALPHA')).toContainText('BUY')
    await expect(page.getByTestId('cell-TCS-BETA')).toContainText('bad params')
    await expect(page.getByTestId('warnings-banner')).toContainText('degraded stage')

    await page.getByRole('checkbox', { name: /ALPHA/ }).check()
    await page.getByTestId('symbol-filter').fill('tcs')
    await page.getByTestId('quick-run').check()
    await page.getByLabel('Run job orchestrator').click()

    await expect
      .poll(() => startBody)
      .toEqual({
        symbols: ['TCS'],
        variantIds: ['ALPHA'],
        skipLlm: true,
      })
  })

  test('strategies: champion warning, mode toggle and inline validation error', async ({
    page,
  }) => {
    let modeBody: unknown
    await mockDashboardApi(page, async (route, path) => {
      if (path === '/strategy-configs') {
        await fulfillJson(route, {
          success: true,
          data: [strategy('ALPHA', 'SHADOW', { period: 14 }), strategy('BETA', 'SHADOW')],
        })
        return true
      }
      if (path === '/strategy-types') {
        await fulfillJson(route, { success: true, data: strategyTypes })
        return true
      }
      if (path === '/strategy-configs/ALPHA/mode') {
        modeBody = route.request().postDataJSON()
        await fulfillJson(route, { success: true, data: strategy('ALPHA', 'CHAMPION') })
        return true
      }
      if (path === '/strategy-configs/ALPHA' && route.request().method() === 'PUT') {
        await fulfillJson(
          route,
          { status: 400, message: 'period must be between 2 and 50', fieldErrors: [] },
          400
        )
        return true
      }
      return false
    })

    await page.goto('/strategies')
    await expect(page.getByTestId('champion-warning')).toContainText(/no champion/i)

    await page.getByTestId('mode-ALPHA-CHAMPION').click()
    await page.getByRole('button', { name: 'Confirm promote' }).click()
    await expect.poll(() => modeBody).toEqual({ mode: 'CHAMPION' })

    await page.getByTestId('edit-params-ALPHA').click()
    await expect(page.getByTestId('param-period')).toHaveValue('14')
    await page.getByTestId('param-period').fill('99')
    await page.getByRole('button', { name: 'Save new version' }).click()
    await expect(page.getByTestId('param-error-period')).toContainText('between 2 and 50')
  })
})
