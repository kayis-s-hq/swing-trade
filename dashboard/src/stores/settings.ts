import { reactive } from 'vue'
import { getLlmSettings, setLlmSettings, getDiscordSettings, setDiscordSettings, getTradingSettings, setTradingSettings, saveAllSettings } from '../api/client'

interface TradingConfig {
  mode: 'paper' | 'live'
  maxPositionSize: number
  stopLoss: number
  takeProfit: number
}

interface LlmSettings {
  vllmBaseUrl: string
  model: string
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
  },
  llmSettings: {
    vllmBaseUrl: 'https://u425-84cf-d540ae09.singapore-b.gpuhub.com:8443/v1',
    model: 'Qwen3-30B-AWQ',
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
  }
}

async function loadAll(): Promise<SettingsState> {
  const state: SettingsState = { ...defaults, tradingConfig: { ...defaults.tradingConfig } }

  // Load LLM settings
  try {
    const llmRes = await getLlmSettings()
    if (llmRes.success && llmRes.data) {
      Object.assign(state.llmSettings, {
        vllmBaseUrl: llmRes.data['llm.vllm.base_url'] || state.llmSettings.vllmBaseUrl,
        model: llmRes.data['llm.vllm.model'] || state.llmSettings.model,
        pdfBaseUrl: llmRes.data['llm.pdf.base_url'] || state.llmSettings.pdfBaseUrl,
        pdfModel: llmRes.data['llm.pdf.model'] || state.llmSettings.pdfModel,
      })
    }
  } catch { /* ignore */ }

  // Load Discord settings
  try {
    const discordRes = await getDiscordSettings()
    if (discordRes.success && discordRes.data) {
      state.discordSettings.webhookUrl = discordRes.data['discord.webhook.url'] || state.discordSettings.webhookUrl
      state.discordSettings.enabled = discordRes.data['discord.webhook.enabled'] === 'true'
    }
  } catch { /* ignore */ }

  // Load trading config
  try {
    const tradingRes = await getTradingSettings()
    if (tradingRes.success && tradingRes.data) {
      Object.assign(state.tradingConfig, parseTradingConfig(tradingRes.data))
    }
  } catch { /* ignore */ }

  return state
}

const state = reactive<SettingsState>(defaults as SettingsState)
let loaded = false

export async function loadSettings() {
  if (loaded) return
  const fresh = await loadAll()
  Object.assign(state, fresh)
  loaded = true
}

export async function saveSettings(): Promise<boolean> {
  const body = {
    broker: state.selectedBroker,
    llm: {
      'llm.vllm.base_url': state.llmSettings.vllmBaseUrl,
      'llm.vllm.model': state.llmSettings.model,
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
    'llm.vllm.base_url': state.llmSettings.vllmBaseUrl,
    'llm.vllm.model': state.llmSettings.model,
    'llm.pdf.base_url': state.llmSettings.pdfBaseUrl,
    'llm.pdf.model': state.llmSettings.pdfModel,
  }
  const res = await setLlmSettings(settings)
  if (!res.success) {
    console.error('Failed to save LLM settings:', res.error)
    return false
  }
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