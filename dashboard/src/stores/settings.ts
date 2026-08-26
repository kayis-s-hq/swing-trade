import { reactive } from 'vue'
import {
  getLlmSettings,
  setLlmSettings,
  getDiscordSettings,
  setDiscordSettings,
  getTradingSettings,
  setTradingSettings,
  saveAllSettings,
} from '../api/client'

interface TradingConfig {
  mode: 'paper' | 'live'
  maxPositionSize: number
  stopLoss: number
  takeProfit: number
  allocationPerPosition: number
}

type LlmBackend = 'local' | 'pi_ssh' | 'openai' | 'ollama'

interface LlmSettings {
  llmBackend: LlmBackend
  llmBaseUrl: string
  openaiBaseUrl: string
  openaiModel: string
  openaiApiKey: string
  ollamaBaseUrl: string
  ollamaModel: string
  ollamaApiKey: string
  llamacppModel: string
  pdfBaseUrl: string
  pdfModel: string
}

interface DiscordSettings {
  webhookUrl: string
  enabled: boolean
}

interface SettingsState {
  selectedBroker: 'fyers' | 'upstox' | 'yahoo' | 'none'
  tradingConfig: TradingConfig
  llmSettings: LlmSettings
  discordSettings: DiscordSettings
}

const defaults: SettingsState = {
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
  discordSettings: {
    webhookUrl: '',
    enabled: false,
  },
}

const supportedLlmBackends = new Set<LlmBackend>(['local', 'pi_ssh', 'openai', 'ollama'])

function createDefaultState(): SettingsState {
  return {
    selectedBroker: defaults.selectedBroker,
    tradingConfig: { ...defaults.tradingConfig },
    llmSettings: { ...defaults.llmSettings },
    discordSettings: { ...defaults.discordSettings },
  }
}

function parseLlmBackend(raw: string | undefined): LlmBackend {
  // Normalize the legacy persisted value without exposing it as a selectable backend.
  if (raw === 'gpuhub') return 'openai'
  return supportedLlmBackends.has(raw as LlmBackend)
    ? (raw as LlmBackend)
    : defaults.llmSettings.llmBackend
}

function applySettings(target: SettingsState, source: SettingsState) {
  target.selectedBroker = source.selectedBroker
  Object.assign(target.tradingConfig, source.tradingConfig)
  Object.assign(target.llmSettings, source.llmSettings)
  Object.assign(target.discordSettings, source.discordSettings)
}

function parseTradingConfig(raw: Record<string, string>): TradingConfig {
  return {
    mode: (raw['trading.mode'] || 'paper') as 'paper' | 'live',
    maxPositionSize: parseInt(raw['trading.max_position_size'] || '10', 10),
    stopLoss: parseInt(raw['trading.stop_loss'] || '5', 10),
    takeProfit: parseInt(raw['trading.take_profit'] || '15', 10),
    allocationPerPosition: parseInt(raw['trading.allocation_per_position'] || '100000', 10),
  }
}

interface LoadAllResult {
  state: SettingsState
  failedSections: string[]
}

async function loadAll(): Promise<LoadAllResult> {
  const state = createDefaultState()
  const failedSections: string[] = []

  // Load LLM settings
  try {
    const llmRes = await getLlmSettings()
    if (llmRes.success && llmRes.data) {
      // Secret fields intentionally stay blank; a blank input means "keep the configured secret".
      Object.assign(state.llmSettings, {
        llmBackend: parseLlmBackend(llmRes.data['llm.backend']),
        llmBaseUrl: llmRes.data['llm.base_url'] ?? state.llmSettings.llmBaseUrl,
        openaiBaseUrl: llmRes.data['openai.base_url'] ?? state.llmSettings.openaiBaseUrl,
        openaiModel: llmRes.data['openai.model'] ?? state.llmSettings.openaiModel,
        ollamaBaseUrl: llmRes.data['ollama.base_url'] ?? state.llmSettings.ollamaBaseUrl,
        ollamaModel: llmRes.data['ollama.model'] ?? state.llmSettings.ollamaModel,
        llamacppModel: llmRes.data['llamacpp.model'] ?? state.llmSettings.llamacppModel,
        pdfBaseUrl: llmRes.data['llm.pdf.base_url'] ?? state.llmSettings.pdfBaseUrl,
        pdfModel: llmRes.data['llm.pdf.model'] ?? state.llmSettings.pdfModel,
      })
    } else {
      failedSections.push('LLM settings')
    }
  } catch {
    failedSections.push('LLM settings')
  }

  // Load Discord settings
  try {
    const discordRes = await getDiscordSettings()
    if (discordRes.success && discordRes.data) {
      state.discordSettings.webhookUrl =
        discordRes.data['discord.webhook.url'] || state.discordSettings.webhookUrl
      state.discordSettings.enabled = discordRes.data['discord.webhook.enabled'] === 'true'
    } else {
      failedSections.push('Discord settings')
    }
  } catch {
    failedSections.push('Discord settings')
  }

  // Load trading config
  try {
    const tradingRes = await getTradingSettings()
    if (tradingRes.success && tradingRes.data) {
      Object.assign(state.tradingConfig, parseTradingConfig(tradingRes.data))
    } else {
      failedSections.push('Trading settings')
    }
  } catch {
    failedSections.push('Trading settings')
  }

  return { state, failedSections }
}

const state = reactive<SettingsState>(createDefaultState())
let loaded = false

export async function loadSettings(): Promise<string[]> {
  if (loaded) return []
  const { state: fresh, failedSections } = await loadAll()
  applySettings(state, fresh)
  loaded = failedSections.length === 0
  return failedSections
}

function createLlmPayload(): Record<string, string> {
  const settings: Record<string, string> = {
    'llm.base_url': state.llmSettings.llmBaseUrl,
    'llm.backend': state.llmSettings.llmBackend,
    'openai.base_url': state.llmSettings.openaiBaseUrl,
    'openai.model': state.llmSettings.openaiModel,
    'ollama.base_url': state.llmSettings.ollamaBaseUrl,
    'ollama.model': state.llmSettings.ollamaModel,
    'llamacpp.model': state.llmSettings.llamacppModel,
    'llm.pdf.base_url': state.llmSettings.pdfBaseUrl,
    'llm.pdf.model': state.llmSettings.pdfModel,
  }

  const openaiApiKey = state.llmSettings.openaiApiKey.trim()
  const ollamaApiKey = state.llmSettings.ollamaApiKey.trim()
  if (openaiApiKey) settings['openai.api_key'] = openaiApiKey
  if (ollamaApiKey) settings['ollama.api_key'] = ollamaApiKey

  return settings
}

function clearSensitiveInputs() {
  state.llmSettings.openaiApiKey = ''
  state.llmSettings.ollamaApiKey = ''
}

export async function saveSettings(): Promise<boolean> {
  const body = {
    broker: state.selectedBroker,
    llm: createLlmPayload(),
    discord: {
      'discord.webhook.url': state.discordSettings.webhookUrl,
      'discord.webhook.enabled': String(state.discordSettings.enabled),
    },
    trading: {
      'trading.mode': state.tradingConfig.mode,
      'trading.max_position_size': String(state.tradingConfig.maxPositionSize),
      'trading.stop_loss': String(state.tradingConfig.stopLoss),
      'trading.take_profit': String(state.tradingConfig.takeProfit),
      'trading.allocation_per_position': String(state.tradingConfig.allocationPerPosition),
    },
  }

  const res = await saveAllSettings(body)
  if (!res.success) {
    console.error('Failed to save settings:', res.error)
    return false
  }
  clearSensitiveInputs()
  return true
}

// Keep individual save methods for toggle-on-change behavior
export async function saveLlmSettings(): Promise<boolean> {
  const res = await setLlmSettings(createLlmPayload())
  if (!res.success) {
    console.error('Failed to save LLM settings:', res.error)
    return false
  }
  clearSensitiveInputs()
  return true
}

export async function saveDiscordSettings(): Promise<boolean> {
  const settings: Record<string, string> = {
    'discord.webhook.url': state.discordSettings.webhookUrl,
    'discord.webhook.enabled': String(state.discordSettings.enabled),
  }
  const res = await setDiscordSettings(settings)
  if (!res.success) {
    console.error('Failed to save Discord settings:', res.error)
    return false
  }
  return true
}

export async function saveTradingConfig(): Promise<boolean> {
  const settings: Record<string, string> = {
    'trading.mode': state.tradingConfig.mode,
    'trading.max_position_size': String(state.tradingConfig.maxPositionSize),
    'trading.stop_loss': String(state.tradingConfig.stopLoss),
    'trading.take_profit': String(state.tradingConfig.takeProfit),
  }
  const res = await setTradingSettings(settings)
  if (!res.success) {
    console.error('Failed to save trading config:', res.error)
    return false
  }
  return true
}

export function getSettings() {
  return state
}

export function resetSettings() {
  applySettings(state, createDefaultState())
  loaded = false
}

export const brokerLabels: Record<string, string> = {
  fyers: 'Fyers',
  upstox: 'Upstox',
  yahoo: 'Yahoo Finance',
  none: 'Paper Only',
}

export const brokerIcons: Record<string, string> = {
  fyers: 'FY',
  upstox: 'UX',
  yahoo: 'YF',
  none: 'PT',
}
