import { reactive, watch } from 'vue'

const STORAGE_KEY = 'swingtrade_settings'

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

function load(): SettingsState {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return { ...defaults }
    const parsed = JSON.parse(raw)
    // Validate broker value
    if (!['fyers', 'upstox', 'yahoo', 'none'].includes(parsed.selectedBroker)) {
      parsed.selectedBroker = defaults.selectedBroker
    }
    return { ...defaults, ...parsed, tradingConfig: { ...defaults.tradingConfig, ...(parsed.tradingConfig ?? {}) } }
  } catch {
    return { ...defaults }
  }
}

const state = reactive<SettingsState>(load())

// Persist on every change
watch(state as any, (val) => {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(val))
}, { deep: true })

export function getSettings() {
  return state
}

export function resetSettings() {
  Object.assign(state, load())
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
