import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AppError } from '../errors/appError'

function legacySuccess<T>(data: T) {
  return { success: true as const, data }
}

const apiMocks = vi.hoisted(() => ({
  getSettings: vi.fn(),
  getLlmSettings: vi.fn(),
  setLlmSettings: vi.fn(),
  getDiscordSettings: vi.fn(),
  setDiscordSettings: vi.fn(),
  getTradingSettings: vi.fn(),
  setTradingSettings: vi.fn(),
  getScanningSettings: vi.fn(),
  setScanningSettings: vi.fn(),
  saveAllSettings: vi.fn(),
}))

vi.mock('../api/client', () => apiMocks)
vi.mock('../api/settings', () => apiMocks)

beforeEach(() => {
  vi.resetModules()
  vi.clearAllMocks()

  // getSettings() has responseContract: 'envelope', so apiRequest already
  // unwraps it - the resolved value is the plain data, never {success, data}.
  apiMocks.getSettings.mockResolvedValue({ selectedBroker: 'fyers' })

  apiMocks.getDiscordSettings.mockResolvedValue(
    legacySuccess({
      'discord.webhook.url': 'https://configured.example/webhook',
      'discord.webhook.enabled': 'true',
    })
  )
  apiMocks.getTradingSettings.mockResolvedValue(
    legacySuccess({
      'trading.mode': 'live',
      'trading.max_position_size': '7',
      'trading.stop_loss': '3',
      'trading.take_profit': '11',
      'trading.allocation_per_position': '75000',
      'trading.initial_capital': '500000',
    })
  )
  apiMocks.getScanningSettings.mockResolvedValue(
    legacySuccess({ 'candidate-scan.max-concurrent': '3' })
  )
  apiMocks.setLlmSettings.mockResolvedValue(legacySuccess({}))
  apiMocks.setDiscordSettings.mockResolvedValue(legacySuccess({}))
  apiMocks.setTradingSettings.mockResolvedValue(legacySuccess({}))
  apiMocks.saveAllSettings.mockResolvedValue(legacySuccess({}))
})

describe('settings store — unconfirmed defaults', () => {
  it('loads independent confirmed sections but refuses to save defaults for a failed section', async () => {
    apiMocks.getLlmSettings.mockRejectedValue(
      new AppError({ kind: 'network', message: 'Stored LLM settings unavailable', retryable: true })
    )

    const { getSettings, loadSettings, saveSettings } = await import('./settings')

    await expect(loadSettings()).resolves.toEqual(['LLM settings'])

    const settings = getSettings()
    expect(settings.discordSettings).toEqual({
      webhookUrl: 'https://configured.example/webhook',
      enabled: true,
    })
    expect(settings.tradingConfig).toMatchObject({
      mode: 'live',
      maxPositionSize: 7,
      stopLoss: 3,
      takeProfit: 11,
      allocationPerPosition: 75000,
      initialCapital: 500000,
    })

    await expect(saveSettings()).resolves.toBe(false)
    expect(apiMocks.saveAllSettings).not.toHaveBeenCalled()
  })

  it('reports a malformed broker payload as a failed section, not a silent default', async () => {
    // Regression: a response that arrives but omits selectedBroker (or sends an
    // unrecognized value) used to fall through both branches - no state update,
    // and nothing pushed to failedSections. The store silently kept the default
    // broker with no "unconfirmed defaults" warning shown to the user.
    apiMocks.getSettings.mockResolvedValue({ selectedBroker: 'not-a-real-broker' })
    apiMocks.getLlmSettings.mockResolvedValue(legacySuccess({}))

    const { getSettings, loadSettings } = await import('./settings')

    await expect(loadSettings()).resolves.toEqual(['Broker settings'])
    expect(getSettings().selectedBroker).toBe('yahoo') // unchanged from the in-memory default
  })
})
