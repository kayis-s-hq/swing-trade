<template>
  <header class="flex h-14 items-center justify-between border-b border-border-subtle bg-bg-surface px-6">
    <div class="flex items-center gap-3">
      <button @click="$emit('toggle-sidebar')" class="rounded-md p-2 text-text-muted transition-colors hover:bg-bg-hover hover:text-text-primary">
        <svg class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 6h16M4 12h16M4 18h16" />
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
      <!-- Theme Toggle -->
      <button
        @click="themeStore.toggle"
        class="flex items-center gap-2 rounded-md border border-border-subtle px-3 py-1.5 text-text-muted transition-colors hover:border-border-default hover:text-text-primary"
      >
        <!-- Sun Icon -->
        <svg v-if="themeStore.isDark" class="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 3v1m0 16v1m9-9h-1M4 12H3m15.364 6.364l-.707-.707M6.343 6.343l-.707-.707m12.728 0l-.707.707M6.343 17.657l-.707.707M16 12a4 4 0 11-8 0 4 4 0 018 0z" />
        </svg>
        <!-- Moon Icon -->
        <svg v-else class="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M20.354 15.354A9 9 0 018.646 3.646 9.003 9.003 0 0012 21a9.003 9.003 0 008.354-5.646z" />
        </svg>
        <span class="text-xs font-medium">{{ themeStore.isDark ? 'Dark' : 'Light' }}</span>
      </button>

      <!-- Market Status -->
      <div class="flex items-center gap-2 rounded-full border border-border-subtle px-3 py-1.5">
        <span class="pulse-dot inline-block h-2 w-2 rounded-full" :class="marketStatus === 'OPEN' ? 'bg-success' : 'bg-danger'"></span>
        <span class="text-xs font-medium" :class="marketStatus === 'OPEN' ? 'text-success' : 'text-danger'">{{ marketStatus }}</span>
      </div>

      <!-- Broker Connection Status -->
      <div v-if="brokerConnected" class="flex items-center gap-1.5 rounded-md bg-success-bg px-2.5 py-1">
        <span class="h-1.5 w-1.5 rounded-full bg-success"></span>
        <span class="text-[11px] font-medium text-success">{{ brokerName }}</span>
      </div>

      <!-- Clock -->
      <div class="hidden text-xs text-text-muted lg:block">{{ currentTime }}</div>

      <!-- User -->
      <button class="flex items-center gap-2 rounded-md border border-border-subtle px-2 py-1.5 transition-colors hover:border-border-default">
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
import { getFyersStatus } from '../api/client'


defineProps<{ sidebarCollapsed: boolean }>()
defineEmits<{ 'toggle-sidebar': [] }>()

const route = useRoute()
const currentTime = ref('')
const themeStore = useThemeStore()
const settings = getSettings()
const brokerConnected = ref(false)
const brokerName = computed(() => brokerLabels[settings.selectedBroker] || 'Trader')

const currentPage = computed(() => {
  const name = route.name as string | undefined
  return name ? name.charAt(0).toUpperCase() + name.slice(1).toLowerCase() : 'Dashboard'
})

const marketStatus = computed(() => {
  const hour = new Date().getHours()
  return (hour >= 9 && hour < 16) ? 'OPEN' : 'CLOSED'
})

let timer: number
const updateTime = () => {
  currentTime.value = new Date().toLocaleTimeString('en-US', {
    hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false,
  })
}

const checkBroker = async () => {
  if (settings.selectedBroker !== 'fyers') {
    brokerConnected.value = true
    return
  }
  const res = await getFyersStatus()
  if (res.success && res.data) {
    brokerConnected.value = res.data.connected
  }
}

onMounted(() => { updateTime(); timer = window.setInterval(updateTime, 1000); checkBroker() })
onUnmounted(() => { clearInterval(timer) })
</script>
