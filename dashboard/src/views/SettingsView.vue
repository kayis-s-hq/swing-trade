<template>
  <div class="p-6 animate-fade-in">
    <div class="mb-6">
      <h1 class="font-display text-2xl font-semibold text-text-primary">Settings</h1>
      <p class="mt-1 text-sm text-text-muted">Broker connections and trading configuration</p>
    </div>

    <div class="max-w-2xl space-y-6">
      <!-- Auth Success/Error Banner -->
      <div v-if="authResultBanner" class="rounded-lg p-4 text-sm font-medium"
        :class="authResultBanner === 'success' ? 'bg-success-bg text-success' : 'bg-danger-bg text-danger'">
        {{ authResultBanner === 'success' ? 'Fyers connected successfully!' : 'Fyers authentication failed. Please try again.' }}
        <button @click="authResultBanner = null" class="ml-2 opacity-60 hover:opacity-100">&times;</button>
      </div>

      <!-- Broker Connection -->
      <div class="card-panel p-5">
        <h2 class="mb-4 text-base font-semibold text-text-primary">Broker Connection</h2>

        <!-- Broker Selection -->
        <div class="mb-4 flex gap-3">
          <button v-for="b in brokers" :key="b.value" @click="settings.selectedBroker = b.value"
            class="flex-1 rounded-lg border p-3 text-sm font-medium transition-all"
            :class="settings.selectedBroker === b.value
              ? 'border-brand bg-brand-subtle text-brand'
              : 'border-border-subtle text-text-muted hover:border-border-default hover:text-text-primary'">
            {{ b.label }}
          </button>
        </div>

        <!-- Connection Status -->
        <div v-if="settings.selectedBroker === 'fyers'" class="rounded-lg border p-4"
          :class="fyersConnected ? 'border-success/50 bg-success-bg' : 'border-border-subtle bg-bg-primary/50'">
          <div class="flex items-center justify-between">
            <div class="flex items-center gap-2">
              <span class="h-2.5 w-2.5 rounded-full" :class="fyersConnected ? 'bg-success pulse-dot' : 'bg-danger'"></span>
              <span class="text-sm font-medium" :class="fyersConnected ? 'text-success' : 'text-text-muted'">
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
          <button @click="startFyersAuth" :disabled="authing"
            class="w-full rounded-md bg-brand px-4 py-2.5 text-sm font-semibold text-brand-text transition-colors hover:bg-brand-hover disabled:opacity-50">
            {{ authing ? 'Opening Fyers...' : 'Connect Fyers Account' }}
          </button>

          <!-- Manual Auth Code -->
          <div v-if="showAuthCodeInput" class="flex gap-2">
            <input v-model="authCodeInput"
              @keydown.enter="submitAuthCode"
              placeholder="Paste auth code from browser"
              class="flex-1 rounded-md border border-border-subtle bg-bg-primary px-3 py-2 text-sm text-text-primary placeholder:text-text-muted focus:border-brand focus:outline-none" />
            <button @click="submitAuthCode" :disabled="authing"
              class="rounded-md border border-brand bg-brand-subtle px-4 py-2 text-sm font-medium text-brand transition-colors hover:bg-brand/20 disabled:opacity-50">
              Submit
            </button>
          </div>
          <p v-if="showAuthCodeInput" class="text-xs text-text-muted">
            Complete login on Fyers, then paste the auth code here.
          </p>
        </div>

        <!-- Disconnect -->
        <div v-if="settings.selectedBroker === 'fyers' && fyersConnected" class="mt-4">
          <button @click="disconnectFyers"
            class="rounded-md border border-danger/30 bg-danger-bg px-4 py-2 text-sm font-medium text-danger transition-colors hover:bg-danger/10">
            Disconnect
          </button>
        </div>

        <!-- Upstox Placeholder -->
        <div v-if="settings.selectedBroker === 'upstox'" class="mt-4 rounded-lg border border-border-subtle p-4 text-center">
          <p class="text-sm text-text-muted">Upstox integration coming soon.</p>
        </div>

        <!-- Yahoo Finance -->
        <div v-if="settings.selectedBroker === 'yahoo'" class="mt-4 space-y-3">
          <div class="flex items-center justify-between rounded-lg border p-4"
            :class="yahooConnected ? 'border-success/50 bg-success-bg' : 'border-border-subtle bg-bg-primary/50'">
            <div class="flex items-center gap-2">
              <span class="h-2.5 w-2.5 rounded-full" :class="yahooConnected ? 'bg-success pulse-dot' : 'bg-danger'"></span>
              <span class="text-sm font-medium" :class="yahooConnected ? 'text-success' : 'text-text-muted'">
                {{ yahooConnected ? 'Connected' : 'Disconnected' }}
              </span>
            </div>
            <span v-if="yahooConnected" class="text-xs text-text-muted">Free · No API Key</span>
          </div>
          <p class="text-xs text-text-muted">
            Yahoo Finance provides free market data for Indian equities (NSE/BSE). No authentication required.
          </p>
        </div>
      </div>

      <!-- Trading Configuration -->
      <div class="card-panel p-5">
        <h2 class="mb-4 text-base font-semibold text-text-primary">Trading Configuration</h2>
        <div class="grid grid-cols-2 gap-4 text-sm">
          <div class="rounded-lg border border-border-subtle p-3">
            <p class="text-xs text-text-muted">Mode</p>
            <select v-model="settings.tradingConfig.mode"
              class="mt-1 w-full rounded-md border border-border-subtle bg-bg-primary px-2 py-1 text-sm text-text-primary focus:border-brand focus:outline-none">
              <option value="paper">Paper Trading</option>
              <option value="live">Live Trading</option>
            </select>
          </div>
          <div class="rounded-lg border border-border-subtle p-3">
            <p class="text-xs text-text-muted">Max Position Size</p>
            <div class="mt-1 flex items-center gap-1">
              <input v-model.number="settings.tradingConfig.maxPositionSize" type="number" min="1" max="100"
                class="w-20 rounded-md border border-border-subtle bg-bg-primary px-2 py-1 text-sm text-text-primary focus:border-brand focus:outline-none" />
              <span class="text-xs text-text-muted">%</span>
            </div>
          </div>
          <div class="rounded-lg border border-border-subtle p-3">
            <p class="text-xs text-text-muted">Stop Loss</p>
            <div class="mt-1 flex items-center gap-1">
              <input v-model.number="settings.tradingConfig.stopLoss" type="number" min="1" max="50"
                class="w-20 rounded-md border border-border-subtle bg-bg-primary px-2 py-1 text-sm text-text-primary focus:border-brand focus:outline-none" />
              <span class="text-xs text-text-muted">%</span>
            </div>
          </div>
          <div class="rounded-lg border border-border-subtle p-3">
            <p class="text-xs text-text-muted">Take Profit</p>
            <div class="mt-1 flex items-center gap-1">
              <input v-model.number="settings.tradingConfig.takeProfit" type="number" min="1" max="200"
                class="w-20 rounded-md border border-border-subtle bg-bg-primary px-2 py-1 text-sm text-text-primary focus:border-brand focus:outline-none" />
              <span class="text-xs text-text-muted">%</span>
            </div>
          </div>
        </div>
      </div>

      <!-- Health Status -->
      <div class="card-panel p-5">
        <h2 class="mb-4 text-base font-semibold text-text-primary">System Health</h2>
        <div v-if="healthStatus" class="space-y-2">
          <div v-for="(comp, key) in healthStatus.components" :key="key"
            class="flex items-center justify-between rounded-lg border border-border-subtle/50 p-3">
            <span class="text-sm font-medium text-text-secondary">{{ key }}</span>
            <span class="inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-medium"
              :class="healthColor(comp.status)">
              <span class="h-1.5 w-1.5 rounded-full" :class="healthDot(comp.status)"></span>
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
import { getFyersLoginUrl, getFyersStatus, fyersAuthCode } from '../api/client'
import type { FyersStatus, HealthStatus } from '../api/types'
import LoadingSpinner from '../components/LoadingSpinner.vue'
import { getSettings } from '../stores/settings'

const settings = getSettings()
const fyersStatus = ref<FyersStatus | null>(null)
const fyersConnected = computed(() => fyersStatus.value?.connected ?? false)
const healthStatus = ref<HealthStatus | null>(null)
const authResultBanner = ref<'success' | 'error' | null>(null)

const authing = ref(false)
const showAuthCodeInput = ref(false)
const authCodeInput = ref('')
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
    window.location.href = res.data.url
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

const disconnectFyers = () => {
  fyersStatus.value = null
}

const handleMessage = (event: MessageEvent) => {
  if (event.data?.type === 'fyers_auth_success') {
    authResultBanner.value = 'success'
    showAuthCodeInput.value = false
    if (pollTimer) { clearInterval(pollTimer); pollTimer = null }
    refreshFyersStatus()
  } else if (event.data?.type === 'fyers_auth_error') {
    authResultBanner.value = 'error'
    authing.value = false
    showAuthCodeInput.value = false
    if (pollTimer) { clearInterval(pollTimer); pollTimer = null }
  }
}

onMounted(() => {
  const route = useRoute()
  const router = useRouter()
  const authParam = route.query.auth as string
  if (authParam === 'success' || authParam === 'error') {
    authResultBanner.value = authParam
    refreshFyersStatus()
    router.replace({ query: {} })
  }
  refreshHealth()
  window.addEventListener('message', handleMessage)
})
onUnmounted(() => {
  if (pollTimer) { clearInterval(pollTimer); pollTimer = null }
  window.removeEventListener('message', handleMessage)
})
</script>
