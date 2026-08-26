import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { describe, expect, it, vi } from 'vitest'

const storeMocks = vi.hoisted(() => {
  const state = {
    selectedBroker: 'fyers',
    tradingConfig: {
      mode: 'paper',
      maxPositionSize: 10,
      stopLoss: 5,
      takeProfit: 15,
      allocationPerPosition: 100000,
    },
    llmSettings: {
      llmBackend: 'local',
      llmBaseUrl: '',
      openaiBaseUrl: '',
      openaiModel: '',
      openaiApiKey: '',
      ollamaBaseUrl: '',
      ollamaModel: '',
      ollamaApiKey: '',
      llamacppModel: '',
      pdfBaseUrl: '',
      pdfModel: '',
    },
    discordSettings: { webhookUrl: '', enabled: false },
  }

  return {
    getSettings: vi.fn(() => state),
    loadSettings: vi.fn().mockResolvedValue(['LLM settings', 'Trading settings']),
    saveSettings: vi.fn().mockResolvedValue(true),
    saveLlmSettings: vi.fn().mockResolvedValue(true),
    saveDiscordSettings: vi.fn().mockResolvedValue(true),
  }
})

const apiMocks = vi.hoisted(() => ({
  getFyersLoginUrl: vi.fn(),
  getFyersStatus: vi.fn().mockResolvedValue({ success: false }),
  fyersAuthCode: vi.fn(),
  fyersLogout: vi.fn(),
  testDiscordWebhook: vi.fn(),
  testPiConnection: vi.fn(),
  testOpenAiConnection: vi.fn(),
  testOllamaConnection: vi.fn(),
  startPiServer: vi.fn(),
  stopPiServer: vi.fn(),
  getPiServerStatus: vi.fn().mockResolvedValue({ success: false }),
  getHealthStatus: vi.fn().mockResolvedValue({ success: false }),
}))

vi.mock('../stores/settings', () => storeMocks)
vi.mock('../api/client', () => apiMocks)

describe('SettingsView — load warnings', () => {
  it('shows failed settings sections through the existing warning toast', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/settings', component: { template: '<div />' } }],
    })
    await router.push('/settings')
    await router.isReady()

    const SettingsView = (await import('./SettingsView.vue')).default
    const wrapper = mount(SettingsView, {
      global: {
        plugins: [router],
        stubs: {
          LoadingSpinner: true,
          Toast: {
            props: ['message', 'type'],
            template: '<div data-testid="settings-toast" :data-type="type">{{ message }}</div>',
          },
        },
      },
    })

    await flushPromises()

    const toast = wrapper.get('[data-testid="settings-toast"]')
    expect(toast.attributes('data-type')).toBe('warning')
    expect(toast.text()).toBe('Failed to load: LLM settings, Trading settings. Showing defaults.')
    wrapper.unmount()
  })
})
