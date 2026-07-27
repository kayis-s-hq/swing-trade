<template>
  <div class="p-6 animate-fade-in">
    <div class="mb-6">
      <h1 class="font-display text-2xl font-semibold text-text-primary">Settings</h1>
      <p class="mt-1 text-sm text-text-muted">Broker connections and trading configuration</p>
    </div>

    <div class="max-w-2xl space-y-6">
      <!-- Auth Success/Error Banner -->
      <div
        v-if="authResultBanner"
        class="rounded-lg p-4 text-sm font-medium"
        :class="
          authResultBanner === 'success' ? 'bg-success-bg text-success' : 'bg-danger-bg text-danger'
        "
      >
        {{
          authResultBanner === 'success'
            ? 'Fyers connected successfully!'
            : 'Fyers authentication failed. Please try again.'
        }}
        <button class="ml-2 opacity-60 hover:opacity-100" @click="authResultBanner = null">
          &times;
        </button>
      </div>

      <!-- Broker Connection -->
      <div class="card-panel p-5">
        <h2 class="mb-4 text-base font-semibold text-text-primary">Broker Connection</h2>

        <!-- Broker Selection -->
        <div class="mb-4 flex gap-3">
          <button
            v-for="b in brokers"
            :key="b.value"
            class="flex-1 rounded-lg border p-3 text-sm font-medium transition-all"
            :class="
              settings.selectedBroker === b.value
                ? 'border-brand bg-brand-subtle text-brand'
                : 'border-border-subtle text-text-muted hover:border-border-default hover:text-text-primary'
            "
            @click="settings.selectedBroker = b.value"
          >
            {{ b.label }}
          </button>
        </div>

        <!-- Connection Status -->
        <div
          v-if="settings.selectedBroker === 'fyers'"
          class="rounded-lg border p-4"
          :class="
            fyersConnected
              ? 'border-success/50 bg-success-bg'
              : 'border-border-subtle bg-bg-primary/50'
          "
        >
          <div class="flex items-center justify-between">
            <div class="flex items-center gap-2">
              <span
                class="h-2.5 w-2.5 rounded-full"
                :class="fyersConnected ? 'bg-success pulse-dot' : 'bg-danger'"
              />
              <span
                class="text-sm font-medium"
                :class="fyersConnected ? 'text-success' : 'text-text-muted'"
              >
                {{ fyersConnected ? 'Connected' : 'Disconnected' }}
              </span>
            </div>
            <span v-if="fyersConnected && fyersStatus?.clientId" class="text-xs text-text-muted">
              ID: {{ fyersStatus.clientId }}
            </span>
          </div>
        </div>

        <!-- Connect Button -->
        <div v-if="settings.selectedBroker === 'fyers' && !fyersConnected" class="mt-4 space-y-3">
          <button
            :disabled="authing"
            class="w-full rounded-md bg-brand px-4 py-2.5 text-sm font-semibold text-brand-text transition-colors hover:bg-brand-hover disabled:opacity-50"
            @click="startFyersAuth"
          >
            {{ authing ? 'Opening Fyers...' : 'Connect Fyers Account' }}
          </button>

          <!-- Manual Auth Code -->
          <div v-if="showAuthCodeInput" class="flex gap-2">
            <input
              v-model="authCodeInput"
              placeholder="Paste auth code from browser"
              class="flex-1 rounded-md border border-border-subtle bg-bg-primary px-3 py-2 text-sm text-text-primary placeholder:text-text-muted focus:border-brand focus:outline-none"
              @keydown.enter="submitAuthCode"
            />
            <button
              :disabled="authing"
              class="rounded-md border border-brand bg-brand-subtle px-4 py-2 text-sm font-medium text-brand transition-colors hover:bg-brand/20 disabled:opacity-50"
              @click="submitAuthCode"
            >
              Submit
            </button>
          </div>
          <p v-if="showAuthCodeInput" class="text-xs text-text-muted">
            Complete login on Fyers, then paste the auth code here.
          </p>
        </div>

        <!-- Disconnect -->
        <div v-if="settings.selectedBroker === 'fyers' && fyersConnected" class="mt-4">
          <button
            class="rounded-md border border-danger/30 bg-danger-bg px-4 py-2 text-sm font-medium text-danger transition-colors hover:bg-danger/10"
            @click="disconnectFyers"
          >
            Disconnect
          </button>
        </div>

        <!-- Upstox Placeholder -->
        <div
          v-if="settings.selectedBroker === 'upstox'"
          class="mt-4 rounded-lg border border-border-subtle p-4 text-center"
        >
          <p class="text-sm text-text-muted">Upstox integration coming soon.</p>
        </div>

        <!-- Yahoo Finance -->
        <div v-if="settings.selectedBroker === 'yahoo'" class="mt-4 space-y-3">
          <div
            class="flex items-center justify-between rounded-lg border p-4"
            :class="
              yahooConnected
                ? 'border-success/50 bg-success-bg'
                : 'border-border-subtle bg-bg-primary/50'
            "
          >
            <div class="flex items-center gap-2">
              <span
                class="h-2.5 w-2.5 rounded-full"
                :class="yahooConnected ? 'bg-success pulse-dot' : 'bg-danger'"
              />
              <span
                class="text-sm font-medium"
                :class="yahooConnected ? 'text-success' : 'text-text-muted'"
              >
                {{ yahooConnected ? 'Connected' : 'Disconnected' }}
              </span>
            </div>
            <span v-if="yahooConnected" class="text-xs text-text-muted">Free · No API Key</span>
          </div>
          <p class="text-xs text-text-muted">
            Yahoo Finance provides free market data for Indian equities (NSE/BSE). No authentication
            required.
          </p>
        </div>
      </div>

      <!-- LLM & Intelligence Settings -->
      <div class="card-panel p-5">
        <h2 class="mb-4 text-base font-semibold text-text-primary">LLM & Intelligence</h2>

        <!-- vLLM Configuration -->
        <div class="space-y-4 mb-6">
          <h3 class="text-sm font-medium text-text-secondary">vLLM Endpoint</h3>
          <div class="flex gap-2">
            <input
              v-model="llmSettings.vllmBaseUrl"
              placeholder="https://gpuhub:8443/v1"
              class="flex-1 rounded-md border border-border-subtle bg-bg-primary px-3 py-2 text-sm text-text-primary placeholder:text-text-muted focus:border-brand focus:outline-none"
            />
            <button
              :disabled="testingLlm"
              class="rounded-md border border-brand bg-brand-subtle px-4 py-2 text-sm font-medium text-brand transition-colors hover:bg-brand/20 disabled:opacity-50"
              @click="testLlmConnection"
            >
              {{ testingLlm ? 'Testing...' : 'Test' }}
            </button>
          </div>

          <div class="flex gap-2">
            <input
              v-model="llmSettings.model"
              placeholder="Qwen3-30B-AWQ"
              class="flex-1 rounded-md border border-border-subtle bg-bg-primary px-3 py-2 text-sm text-text-primary placeholder:text-text-muted focus:border-brand focus:outline-none"
            />
            <span class="self-center text-xs text-text-muted">Model name</span>
          </div>
        </div>

        <!-- PDF Extraction -->
        <div class="space-y-4 mb-6">
          <h3 class="text-sm font-medium text-text-secondary">PDF Extraction (Pi 5)</h3>
          <div class="flex gap-2">
            <input
              v-model="llmSettings.pdfBaseUrl"
              placeholder="http://pi5-ip:8080"
              class="flex-1 rounded-md border border-border-subtle bg-bg-primary px-3 py-2 text-sm text-text-primary placeholder:text-text-muted focus:border-brand focus:outline-none"
            />
            <button
              :disabled="testingPdf"
              class="rounded-md border border-brand bg-brand-subtle px-4 py-2 text-sm font-medium text-brand transition-colors hover:bg-brand/20 disabled:opacity-50"
              @click="testPdfExtraction"
            >
              {{ testingPdf ? 'Testing...' : 'Test' }}
            </button>
          </div>

          <div class="flex gap-2">
            <input
              v-model="llmSettings.pdfModel"
              placeholder="gemma-4-E2B"
              class="flex-1 rounded-md border border-border-subtle bg-bg-primary px-3 py-2 text-sm text-text-primary placeholder:text-text-muted focus:border-brand focus:outline-none"
            />
            <span class="self-center text-xs text-text-muted">Model name</span>
          </div>
        </div>

        <!-- Discord Configuration -->
        <div class="space-y-4">
          <h3 class="text-sm font-medium text-text-secondary">Discord Notifications</h3>
          <div class="flex items-center justify-between rounded-lg border border-border-subtle p-3">
            <span class="text-sm">Enable Discord</span>
            <label class="relative inline-flex items-center cursor-pointer">
              <input v-model="discordSettings.enabled" type="checkbox" class="sr-only peer" />
              <div
                class="w-9 h-5 bg-gray-700 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:rounded-full after:h-4 after:w-4 after:transition-all peer-checked:bg-brand"
              />
            </label>
          </div>
          <div class="flex gap-2">
            <input
              v-model="discordSettings.webhookUrl"
              placeholder="https://discord.com/api/webhooks/..."
              class="flex-1 rounded-md border border-border-subtle bg-bg-primary px-3 py-2 text-sm text-text-primary placeholder:text-text-muted focus:border-brand focus:outline-none"
            />
            <button
              :disabled="testingDiscord"
              class="rounded-md border border-brand bg-brand-subtle px-4 py-2 text-sm font-medium text-brand transition-colors hover:bg-brand/20 disabled:opacity-50"
              @click="testDiscordWebhook"
            >
              {{ testingDiscord ? 'Testing...' : 'Test' }}
            </button>
          </div>
        </div>
      </div>

      <!-- Trading Configuration -->
      <div class="card-panel p-5">
        <h2 class="mb-4 text-base font-semibold text-text-primary">Trading Configuration</h2>
        <div class="grid grid-cols-2 gap-4 text-sm">
          <div class="rounded-lg border border-border-subtle p-3">
            <p class="text-xs text-text-muted">Mode</p>
            <select
              v-model="settings.tradingConfig.mode"
              class="mt-1 w-full rounded-md border border-border-subtle bg-bg-primary px-2 py-1 text-sm text-text-primary focus:border-brand focus:outline-none"
            >
              <option value="paper">Paper Trading</option>
              <option value="live">Live Trading</option>
            </select>
          </div>
          <div class="rounded-lg border border-border-subtle p-3">
            <p class="text-xs text-text-muted">Max Position Size</p>
            <div class="mt-1 flex items-center gap-1">
              <input
                v-model.number="settings.tradingConfig.maxPositionSize"
                type="number"
                min="1"
                max="100"
                class="w-20 rounded-md border border-border-subtle bg-bg-primary px-2 py-1 text-sm text-text-primary focus:border-brand focus:outline-none"
              />
              <span class="text-xs text-text-muted">%</span>
            </div>
          </div>
          <div class="rounded-lg border border-border-subtle p-3">
            <p class="text-xs text-text-muted">Stop Loss</p>
            <div class="mt-1 flex items-center gap-1">
              <input
                v-model.number="settings.tradingConfig.stopLoss"
                type="number"
                min="1"
                max="50"
                class="w-20 rounded-md border border-border-subtle bg-bg-primary px-2 py-1 text-sm text-text-primary focus:border-brand focus:outline-none"
              />
              <span class="text-xs text-text-muted">%</span>
            </div>
          </div>
          <div class="rounded-lg border border-border-subtle p-3">
            <p class="text-xs text-text-muted">Take Profit</p>
            <div class="mt-1 flex items-center gap-1">
              <input
                v-model.number="settings.tradingConfig.takeProfit"
                type="number"
                min="1"
                max="200"
                class="w-20 rounded-md border border-border-subtle bg-bg-primary px-2 py-1 text-sm text-text-primary focus:border-brand focus:outline-none"
              />
              <span class="text-xs text-text-muted">%</span>
            </div>
          </div>
        </div>
      </div>

      <!-- Save Button -->
      <button
        :disabled="saving"
        class="w-full rounded-md bg-brand px-4 py-3 text-sm font-semibold text-brand-text transition-colors hover:bg-brand-hover disabled:opacity-50"
        :class="saved ? 'bg-success' : ''"
        @click="handleSave"
      >
        {{ saving ? 'Saving...' : saved ? 'Saved!' : 'Save All Settings' }}
      </button>

      <!-- Health Status -->
      <div class="card-panel p-5">
        <h2 class="mb-4 text-base font-semibold text-text-primary">System Health</h2>
        <div v-if="healthStatus" class="space-y-2">
          <div
            v-for="(comp, key) in healthStatus.components"
            :key="key"
            class="flex items-center justify-between rounded-lg border border-border-subtle/50 p-3"
          >
            <span class="text-sm font-medium text-text-secondary">{{ key }}</span>
            <span
              class="inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-medium"
              :class="healthColor(comp.status)"
            >
              <span class="h-1.5 w-1.5 rounded-full" :class="healthDot(comp.status)" />
              {{ comp.status }}
            </span>
          </div>
        </div>
        <div v-else class="flex items-center justify-center py-8">
          <LoadingSpinner :message="'Checking system...'" :small="true" />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  getFyersLoginUrl,
  getFyersStatus,
  fyersAuthCode,
  fyersLogout,
  testDiscordWebhook as apiTestDiscordWebhook,
} from '../api/client'
import type { FyersStatus, HealthStatus } from '../api/types'
import LoadingSpinner from '../components/LoadingSpinner.vue'
import {
  getSettings,
  loadSettings,
  saveSettings,
  saveLlmSettings,
  saveDiscordSettings,
} from '../stores/settings'

const settings = getSettings()
const llmSettings = settings.llmSettings
const discordSettings = settings.discordSettings
const fyersStatus = ref<FyersStatus | null>(null)
const fyersConnected = computed(() => fyersStatus.value?.connected ?? false)
const healthStatus = ref<HealthStatus | null>(null)
const authResultBanner = ref<'success' | 'error' | null>(null)

const authing = ref(false)
const showAuthCodeInput = ref(false)
const authCodeInput = ref('')
const testingLlm = ref(false)
const testingPdf = ref(false)
const testingDiscord = ref(false)
const saving = ref(false)
const saved = ref(false)
let pollTimer: number | null = null

const brokers = [
  { value: 'fyers' as const, label: 'Fyers' },
  { value: 'upstox' as const, label: 'Upstox' },
  { value: 'yahoo' as const, label: 'Yahoo Finance' },
  { value: 'none' as const, label: 'None (Read Only)' },
]

const yahooConnected = computed(() => {
  if (!healthStatus.value) return false
  const data = healthStatus.value.components['data'] || healthStatus.value.components['market-data']
  return data && data.status === 'UP'
})

const healthColor = (status: string) => {
  if (status === 'UP') return 'bg-success-bg text-success'
  if (status === 'DOWN') return 'bg-danger-bg text-danger'
  if (status === 'DEGRADED') return 'bg-warning-bg text-warning'
  return 'bg-bg-hover text-text-muted'
}

const healthDot = (status: string) => {
  if (status === 'UP') return 'bg-success'
  if (status === 'DOWN') return 'bg-danger'
  if (status === 'DEGRADED') return 'bg-warning'
  return 'bg-text-muted'
}

const refreshFyersStatus = async () => {
  const res = await getFyersStatus()
  if (res.success && res.data) fyersStatus.value = res.data
}

const pollFyersStatus = () => {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
  pollTimer = window.setInterval(async () => {
    try {
      const res = await getFyersStatus()
      if (res.success && res.data && res.data.connected) {
        if (pollTimer) {
          clearInterval(pollTimer)
          pollTimer = null
        }
        authResultBanner.value = 'success'
        showAuthCodeInput.value = false
        fyersStatus.value = res.data
        // Try to close the popup
        // (may not work due to CORS, but the user can close it manually)
      }
    } catch {
      // ignore polling errors
    }
  }, 2000) as unknown as number
}

const refreshHealth = async () => {
  const { getHealthStatus } = await import('../api/client')
  const res = await getHealthStatus()
  if (res.success && res.data) healthStatus.value = res.data
}

const startFyersAuth = async () => {
  authing.value = true
  try {
    const res = await getFyersLoginUrl()
    if (!res.success || !res.data) throw new Error(res.error ?? 'Failed to get login URL')

    const popup = window.open(
      res.data.url,
      'fyers-auth',
      'width=600,height=700,left=' +
        Math.round(window.screen.width / 2 - 300) +
        ',top=' +
        Math.round(window.screen.height / 2 - 350)
    )
    if (!popup) {
      authResultBanner.value = 'error'
      authing.value = false
      throw new Error('Popup blocked. Please allow popups for this site.')
    }

    // Fallback: poll for status in case postMessage is blocked by CORS
    pollFyersStatus()

    popup.addEventListener('load', () => {
      if (pollTimer) {
        clearInterval(pollTimer)
        pollTimer = null
      }
    })
  } catch (err: unknown) {
    authResultBanner.value = 'error'
  } finally {
    authing.value = false
  }
}

const submitAuthCode = async () => {
  if (!authCodeInput.value.trim()) return
  authing.value = true
  try {
    const res = await fyersAuthCode(authCodeInput.value.trim())
    if (!res.success) throw new Error(res.error ?? 'Auth failed')
    fyersStatus.value = res.data ?? null
    showAuthCodeInput.value = false
    authCodeInput.value = ''
  } catch (err: unknown) {
    alert(err instanceof Error ? err.message : 'Auth failed')
  } finally {
    authing.value = false
  }
}

const disconnectFyers = async () => {
  const res = await fyersLogout()
  fyersStatus.value = res.success && res.data ? res.data : null
}

const handleSave = async () => {
  saving.value = true
  saved.value = false
  try {
    const ok = await saveSettings()
    if (ok) {
      saved.value = true
      setTimeout(() => {
        saved.value = false
      }, 2000)
    }
  } catch (err: unknown) {
    alert(err instanceof Error ? err.message : 'Failed to save settings')
  } finally {
    saving.value = false
  }
}

const testLlmConnection = async () => {
  testingLlm.value = true
  try {
    const ok = await saveLlmSettings()
    if (ok) {
      alert('LLM settings saved. Test analysis will run on next signal.')
    } else {
      alert('Failed to save LLM settings.')
    }
  } catch (err: unknown) {
    alert(err instanceof Error ? err.message : 'Failed to save LLM settings')
  } finally {
    testingLlm.value = false
  }
}

const testPdfExtraction = async () => {
  testingPdf.value = true
  try {
    const ok = await saveLlmSettings()
    if (ok) {
      alert('PDF extraction settings saved.')
    } else {
      alert('Failed to save PDF settings.')
    }
  } catch (err: unknown) {
    alert(err instanceof Error ? err.message : 'Failed to save PDF settings')
  } finally {
    testingPdf.value = false
  }
}

const testDiscordWebhook = async () => {
  const ok = await saveDiscordSettings()
  if (!ok) {
    alert('Failed to save Discord settings.')
    return
  }
  testingDiscord.value = true
  try {
    const res = await apiTestDiscordWebhook()
    if (res.success && res.data?.success) {
      alert('Discord webhook test successful!')
    } else {
      alert(res.error ?? 'Discord webhook test failed')
    }
  } catch (err: unknown) {
    alert(err instanceof Error ? err.message : 'Discord webhook test failed')
  } finally {
    testingDiscord.value = false
  }
}

const handleMessage = (event: MessageEvent) => {
  if (event.data?.type === 'fyers_auth_success') {
    authResultBanner.value = 'success'
    showAuthCodeInput.value = false
    if (pollTimer) {
      clearInterval(pollTimer)
      pollTimer = null
    }
    refreshFyersStatus()
  } else if (event.data?.type === 'fyers_auth_error') {
    authResultBanner.value = 'error'
    authing.value = false
    showAuthCodeInput.value = false
    if (pollTimer) {
      clearInterval(pollTimer)
      pollTimer = null
    }
  }
}

onMounted(async () => {
  const route = useRoute()
  const router = useRouter()
  const authParam = route.query.auth as string
  if (authParam === 'success' || authParam === 'error') {
    authResultBanner.value = authParam
    router.replace({ query: {} })
  }
  refreshFyersStatus()
  refreshHealth()
  await loadSettings()
  window.addEventListener('message', handleMessage)
})

onUnmounted(() => {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
  window.removeEventListener('message', handleMessage)
})
</script>
