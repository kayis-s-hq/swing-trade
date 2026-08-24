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

interface LlmSettings {
  llmBackend: 'local' | 'pi_ssh' | 'gpuhub' | 'ollama'
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
    llmBaseUrl: 'http://localhost:8080/v1',
    openaiBaseUrl: 'https://api.openai.com/v1',
    openaiModel: 'gpt-4o',
    openaiApiKey: '',
    ollamaBaseUrl: 'http://localhost:11434/v1',
    ollamaModel: 'qwen3:4b',
    ollamaApiKey: '',
    llamacppModel: '/home/dietpi/.synapse/models/Qwen3-4B-Instruct-2507-UD-Q4_K_XL.gguf',
    pdfBaseUrl: '',
    pdfModel: 'gemma-4-E2B',
  },
  discordSettings: {
    webhookUrl: '',
    enabled: false,
  },
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
  const state: SettingsState = { ...defaults, tradingConfig: { ...defaults.tradingConfig } }
  const failedSections: string[] = []

  // Load LLM settings
  try {
    const llmRes = await getLlmSettings()
    if (llmRes.success && llmRes.data) {
      Object.assign(state.llmSettings, {
        llmBackend:
          (llmRes.data['llm.backend'] as 'local' | 'pi_ssh' | 'gpuhub' | 'ollama') ||
          state.llmSettings.llmBackend,
        llmBaseUrl: llmRes.data['llm.base_url'] || state.llmSettings.llmBaseUrl,
        openaiBaseUrl: llmRes.data['openai.base_url'] || state.llmSettings.openaiBaseUrl,
        openaiModel: llmRes.data['openai.model'] || state.llmSettings.openaiModel,
        ollamaBaseUrl: llmRes.data['ollama.base_url'] || state.llmSettings.ollamaBaseUrl,
        ollamaModel: llmRes.data['ollama.model'] || state.llmSettings.ollamaModel,
        llamacppModel: llmRes.data['llamacpp.model'] || state.llmSettings.llamacppModel,
        pdfBaseUrl: llmRes.data['llm.pdf.base_url'] || state.llmSettings.pdfBaseUrl,
        pdfModel: llmRes.data['llm.pdf.model'] || state.llmSettings.pdfModel,
      })
    } else {
      console.warn('Failed to load LLM settings:', llmRes.error)
      failedSections.push('LLM settings')
    }
  } catch (err) {
    console.warn('Failed to load LLM settings:', err)
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
      console.warn('Failed to load Discord settings:', discordRes.error)
      failedSections.push('Discord settings')
    }
  } catch (err) {
    console.warn('Failed to load Discord settings:', err)
    failedSections.push('Discord settings')
  }

  // Load trading config
  try {
    const tradingRes = await getTradingSettings()
    if (tradingRes.success && tradingRes.data) {
      Object.assign(state.tradingConfig, parseTradingConfig(tradingRes.data))
    } else {
      console.warn('Failed to load trading config:', tradingRes.error)
      failedSections.push('trading config')
    }
  } catch (err) {
    console.warn('Failed to load trading config:', err)
    failedSections.push('trading config')
  }

  return { state, failedSections }
}

const state = reactive<SettingsState>(defaults as SettingsState)
let loaded = false

export async function loadSettings(): Promise<string[]> {
  if (loaded) return []
  const { state: fresh, failedSections } = await loadAll()
  Object.assign(state, fresh)
  loaded = true
  return failedSections
}

export async function saveSettings(): Promise<boolean> {
  const body = {
    broker: state.selectedBroker,
    llm: {
      'llm.base_url': state.llmSettings.llmBaseUrl,
      'llm.backend': state.llmSettings.llmBackend,
      'openai.base_url': state.llmSettings.openaiBaseUrl,
      'openai.model': state.llmSettings.openaiModel,
      'openai.api_key': state.llmSettings.openaiApiKey,
      'ollama.base_url': state.llmSettings.ollamaBaseUrl,
      'ollama.model': state.llmSettings.ollamaModel,
      'ollama.api_key': state.llmSettings.ollamaApiKey,
      'llamacpp.model': state.llmSettings.llamacppModel,
      'llm.pdf.base_url': state.llmSettings.pdfBaseUrl,
      'llm.pdf.model': state.llmSettings.pdfModel,
    },
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
  return true
}

// Keep individual save methods for toggle-on-change behavior
export async function saveLlmSettings(): Promise<boolean> {
  const settings: Record<string, string> = {
    'llm.backend': state.llmSettings.llmBackend,
    'llm.base_url': state.llmSettings.llmBaseUrl,
    'openai.base_url': state.llmSettings.openaiBaseUrl,
    'openai.model': state.llmSettings.openaiModel,
    'openai.api_key': state.llmSettings.openaiApiKey,
    'ollama.base_url': state.llmSettings.ollamaBaseUrl,
    'ollama.model': state.llmSettings.ollamaModel,
    'ollama.api_key': state.llmSettings.ollamaApiKey,
    'llamacpp.model': state.llmSettings.llamacppModel,
    'llm.pdf.base_url': state.llmSettings.pdfBaseUrl,
    'llm.pdf.model': state.llmSettings.pdfModel,
  }
  const res = await setLlmSettings(settings)
  if (!res.success) {
    console.error('Failed to save LLM settings:', res.error)
    return false
  }
  return res.success
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
  Object.assign(state, defaults)
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
