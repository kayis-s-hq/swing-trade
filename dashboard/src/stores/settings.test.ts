import { beforeEach, describe, expect, it, vi } from 'vitest'

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

beforeEach(() => {
  vi.resetModules()
  vi.clearAllMocks()

  apiMocks.getLlmSettings.mockResolvedValue({ success: true, data: {} })
  apiMocks.getDiscordSettings.mockResolvedValue({
    success: true,
    data: { 'discord.webhook.enabled': 'false' },
  })
  apiMocks.getTradingSettings.mockResolvedValue({ success: true, data: {} })
  apiMocks.setLlmSettings.mockResolvedValue({ success: true, data: {} })
  apiMocks.setDiscordSettings.mockResolvedValue({ success: true, data: {} })
  apiMocks.setTradingSettings.mockResolvedValue({ success: true, data: {} })
  apiMocks.saveAllSettings.mockResolvedValue({ success: true, data: {} })
})

describe('settings store — LLM configuration', () => {
  it('uses API-provided values, normalizes the legacy backend, and does not hydrate secrets', async () => {
    apiMocks.getLlmSettings.mockResolvedValue({
      success: true,
      data: {
        'llm.backend': 'gpuhub',
        'llm.base_url': 'http://configured-local/v1',
        'openai.base_url': 'https://configured-openai.example/v1',
        'openai.model': 'configured-openai-model',
        'openai.api_key': 'must-not-reach-state',
        'ollama.base_url': 'http://configured-ollama/v1',
        'ollama.model': 'configured-ollama-model',
        'ollama.api_key': 'must-not-reach-state-either',
        'llamacpp.model': '/configured/models/current.gguf',
        'llm.pdf.base_url': 'http://configured-pdf',
        'llm.pdf.model': 'configured-pdf-model',
      },
    })

    const { getSettings, loadSettings } = await import('./settings')
    expect(getSettings().llmSettings.openaiBaseUrl).toBe('')
    expect(getSettings().llmSettings.llamacppModel).toBe('')

    await expect(loadSettings()).resolves.toEqual([])

    expect(getSettings().llmSettings).toMatchObject({
      llmBackend: 'openai',
      llmBaseUrl: 'http://configured-local/v1',
      openaiBaseUrl: 'https://configured-openai.example/v1',
      openaiModel: 'configured-openai-model',
      openaiApiKey: '',
      ollamaBaseUrl: 'http://configured-ollama/v1',
      ollamaModel: 'configured-ollama-model',
      ollamaApiKey: '',
      llamacppModel: '/configured/models/current.gguf',
      pdfBaseUrl: 'http://configured-pdf',
      pdfModel: 'configured-pdf-model',
    })
  })

  it.each(['local', 'pi_ssh', 'openai', 'ollama'] as const)(
    'preserves the supported backend value %s',
    async (backend) => {
      apiMocks.getLlmSettings.mockResolvedValue({
        success: true,
        data: { 'llm.backend': backend },
      })

      const { getSettings, loadSettings } = await import('./settings')
      await loadSettings()

      expect(getSettings().llmSettings.llmBackend).toBe(backend)
    }
  )

  it('omits blank secrets and clears entered secrets after a successful save', async () => {
    const { getSettings, saveLlmSettings } = await import('./settings')
    const llmSettings = getSettings().llmSettings
    llmSettings.llmBackend = 'openai'

    await expect(saveLlmSettings()).resolves.toBe(true)
    const blankSecretPayload = apiMocks.setLlmSettings.mock.calls[0][0]
    expect(blankSecretPayload).not.toHaveProperty('openai.api_key')
    expect(blankSecretPayload).not.toHaveProperty('ollama.api_key')

    llmSettings.openaiApiKey = '  openai-secret  '
    llmSettings.ollamaApiKey = 'ollama-secret'
    await expect(saveLlmSettings()).resolves.toBe(true)

    expect(apiMocks.setLlmSettings).toHaveBeenLastCalledWith(
      expect.objectContaining({
        'openai.api_key': 'openai-secret',
        'ollama.api_key': 'ollama-secret',
      })
    )
    expect(llmSettings.openaiApiKey).toBe('')
    expect(llmSettings.ollamaApiKey).toBe('')
  })
})

describe('settings store — load warnings', () => {
  it('returns failed sections for the UI without logging console warnings', async () => {
    apiMocks.getLlmSettings.mockRejectedValue(new Error('LLM unavailable'))
    apiMocks.getDiscordSettings.mockResolvedValue({ success: false, error: 'Discord unavailable' })
    apiMocks.getTradingSettings.mockRejectedValue(new Error('Trading unavailable'))
    const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => undefined)

    const { loadSettings } = await import('./settings')

    await expect(loadSettings()).resolves.toEqual([
      'LLM settings',
      'Discord settings',
      'Trading settings',
    ])
    expect(warnSpy).not.toHaveBeenCalled()
    warnSpy.mockRestore()
  })
})
