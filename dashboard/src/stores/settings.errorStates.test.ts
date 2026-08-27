import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AppError } from '../errors/appError'

function legacySuccess<T>(data: T) {
  return { success: true as const, data }
}

const apiMocks = vi.hoisted(() => ({
  getLlmSettings: vi.fn(),
  setLlmSettings: vi.fn(),
  getDiscordSettings: vi.fn(),
  setDiscordSettings: vi.fn(),
  getTradingSettings: vi.fn(),
  setTradingSettings: vi.fn(),
  saveAllSettings: vi.fn(),
}))

vi.mock('../api/client', () => apiMocks)
vi.mock('../api/settings', () => apiMocks)

beforeEach(() => {
  vi.resetModules()
  vi.clearAllMocks()

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
    })
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
    })

    await expect(saveSettings()).resolves.toBe(false)
    expect(apiMocks.saveAllSettings).not.toHaveBeenCalled()
  })
})
