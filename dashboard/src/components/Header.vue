<template>
  <header
    class="flex h-14 items-center justify-between border-b border-border-subtle bg-bg-surface px-6"
  >
    <div class="flex items-center gap-3">
      <button
        class="rounded-md p-2 text-text-muted transition-colors hover:bg-bg-hover hover:text-text-primary"
        @click="$emit('toggle-sidebar')"
      >
        <svg class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path
            stroke-linecap="round"
            stroke-linejoin="round"
            stroke-width="2"
            d="M4 6h16M4 12h16M4 18h16"
          />
        </svg>
      </button>

      <nav class="flex items-center gap-2">
        <span class="text-sm text-text-muted">Home</span>
        <svg class="h-4 w-4 text-text-muted" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7" />
        </svg>
        <span class="text-sm font-semibold text-text-primary">{{ currentPage }}</span>
      </nav>
    </div>

    <div class="flex items-center gap-4">
      <!-- Holiday -->
      <div class="flex items-center gap-2 text-xs text-text-muted">
        <svg
          class="h-3.5 w-3.5 shrink-0"
          fill="none"
          stroke="currentColor"
          viewBox="0 0 24 24"
          stroke-width="1.5"
        >
          <path
            stroke-linecap="round"
            stroke-linejoin="round"
            d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z"
          />
        </svg>
        <span v-if="holidayLoading">Loading...</span>
        <template v-else-if="holidayError">
          <span role="alert">Holiday status unavailable</span>
          <button
            type="button"
            aria-label="Retry holiday status"
            class="rounded px-1.5 py-0.5 font-medium text-brand hover:bg-brand/10"
            @click="loadHolidayStatus"
          >
            Retry
          </button>
        </template>
        <span v-else-if="nextHoliday">{{ nextHoliday.occasion }}</span>
        <span v-else>-</span>
      </div>

      <!-- Market Status -->
      <div class="flex items-center gap-2 rounded-full border border-border-subtle px-3 py-1.5">
        <span class="pulse-dot inline-block h-2 w-2 rounded-full" :class="marketPillClass" />
        <span class="text-xs font-medium" :class="marketPillClass">{{
          marketCountdownState.label || 'Closed'
        }}</span>
      </div>

      <!-- Theme Toggle -->
      <button
        class="flex items-center gap-2 rounded-md border border-border-subtle px-3 py-1.5 text-text-muted transition-colors hover:border-border-default hover:text-text-primary"
        @click="themeStore.toggle"
      >
        <!-- Sun Icon -->
        <svg
          v-if="themeStore.isDark"
          class="h-4 w-4"
          fill="none"
          stroke="currentColor"
          viewBox="0 0 24 24"
        >
          <path
            stroke-linecap="round"
            stroke-linejoin="round"
            stroke-width="2"
            d="M12 3v1m0 16v1m9-9h-1M4 12H3m15.364 6.364l-.707-.707M6.343 6.343l-.707-.707m12.728 0l-.707.707M6.343 17.657l-.707.707M16 12a4 4 0 11-8 0 4 4 0 018 0z"
          />
        </svg>
        <!-- Moon Icon -->
        <svg v-else class="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path
            stroke-linecap="round"
            stroke-linejoin="round"
            stroke-width="2"
            d="M20.354 15.354A9 9 0 018.646 3.646 9.003 9.003 0 0012 21a9.003 9.003 0 008.354-5.646z"
          />
        </svg>
        <span class="text-xs font-medium">{{ themeStore.isDark ? 'Dark' : 'Light' }}</span>
      </button>

      <!-- Broker Connection Status -->
      <div
        v-if="brokerConnected"
        class="flex items-center gap-1.5 rounded-md bg-success-bg px-2.5 py-1"
      >
        <span class="h-1.5 w-1.5 rounded-full bg-success" />
        <span class="text-[11px] font-medium text-success">{{ brokerName }}</span>
      </div>

      <!-- Clock -->
      <div class="hidden text-xs text-text-muted lg:block">
        {{ currentTime }}
      </div>

      <!-- User -->
      <button
        class="flex items-center gap-2 rounded-md border border-border-subtle px-2 py-1.5 transition-colors hover:border-border-default"
      >
        <div class="flex h-7 w-7 items-center justify-center rounded-full bg-brand/10">
          <span class="text-xs font-semibold text-brand">KT</span>
        </div>
        <span class="hidden text-xs font-medium text-text-muted lg:block">Trader</span>
      </button>
    </div>
  </header>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { useThemeStore } from '../stores/theme'
import { getSettings, brokerLabels } from '../stores/settings'
import { getFyersStatus, getUpcomingHolidays } from '../api/client'

defineProps<{ sidebarCollapsed: boolean }>()
defineEmits<{ 'toggle-sidebar': [] }>()

const route = useRoute()
const currentPage = computed(() => {
  const name = route.name
  if (typeof name === 'string') {
    return name.charAt(0).toUpperCase() + name.slice(1)
  }
  return 'Dashboard'
})

const currentTime = ref('')
const themeStore = useThemeStore()
const settings = getSettings()
const brokerConnected = ref(false)
const brokerName = computed(() => brokerLabels[settings.selectedBroker] || 'Trader')

const nextHoliday = ref<{ occasion: string; date: string } | null>(null)
const holidayLoading = ref(true)
const holidayError = ref(false)

interface MarketState {
  label: string
  class: string
}
const marketCountdownState = ref<MarketState>({ label: '', class: '' })
const marketPillClass = computed(() => marketCountdownState.value.class)

const IST_OFFSET = 5.5 * 60 * 60 * 1000

const updateMarketStatus = (ist: Date) => {
  const hour = ist.getHours()
  const minute = ist.getMinutes()
  const day = ist.getDay()
  const nowMinutes = hour * 60 + minute

  const pad = (n: number) => String(n).padStart(2, '0')
  const todayStr = `${ist.getFullYear()}-${pad(ist.getMonth() + 1)}-${pad(ist.getDate())}`
  const isHoliday = nextHoliday.value && nextHoliday.value.date === todayStr

  if (day === 0 || day === 6 || isHoliday) {
    marketCountdownState.value = { label: 'Market closed', class: 'text-text-muted' }
    return
  }

  const marketOpen = 9 * 60 + 15
  const marketClose = 15 * 60 + 30

  if (nowMinutes < marketOpen) {
    const diff = marketOpen - nowMinutes
    const h = Math.floor(diff / 60)
    const m = diff % 60
    marketCountdownState.value = { label: `Opens in ${h}h ${m}m`, class: 'text-warning' }
  } else if (nowMinutes >= marketOpen && nowMinutes < marketClose) {
    const diff = marketClose - nowMinutes
    const h = Math.floor(diff / 60)
    const m = diff % 60
    marketCountdownState.value = { label: `Closes in ${h}h ${m}m`, class: 'text-success' }
  } else {
    marketCountdownState.value = { label: 'Closed', class: 'text-text-muted' }
  }
}

const updateTime = () => {
  const now = new Date()
  const utc = now.getTime() + now.getTimezoneOffset() * 60000
  const ist = new Date(utc + IST_OFFSET)

  currentTime.value = ist.toLocaleTimeString('en-IN', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
    timeZone: 'Asia/Kolkata',
  })

  updateMarketStatus(ist)
}

let timer: number

const checkBroker = async () => {
  if (settings.selectedBroker !== 'fyers') {
    brokerConnected.value = true
    return
  }
  const res = await getFyersStatus()
  brokerConnected.value = res.connected
}

const loadHolidayStatus = async () => {
  holidayLoading.value = true
  holidayError.value = false
  try {
    const resp = await getUpcomingHolidays()
    const holiday = resp.holidays?.[0]
    nextHoliday.value = holiday ? { occasion: holiday.occasion, date: holiday.date } : null
  } catch {
    holidayError.value = true
  } finally {
    holidayLoading.value = false
  }
}

onMounted(() => {
  updateTime()
  timer = window.setInterval(updateTime, 1000)
  void checkBroker()
  void loadHolidayStatus()
})

onUnmounted(() => {
  clearInterval(timer)
})
</script>
