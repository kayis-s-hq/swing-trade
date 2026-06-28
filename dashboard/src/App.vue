<template>
  <div class="flex h-screen w-full overflow-hidden bg-bg-primary">
    <!-- Sidebar -->
    <Sidebar :collapsed="sidebarCollapsed" :health-status="appState.healthStatus" @toggle="sidebarCollapsed = !sidebarCollapsed" />

    <!-- Main Content -->
    <div class="flex flex-1 flex-col overflow-hidden">
      <!-- Backend Down Banner -->
      <BackendDownBanner />

      <!-- Header -->
      <Header :sidebar-collapsed="sidebarCollapsed" @toggle-sidebar="sidebarCollapsed = !sidebarCollapsed" />

      <!-- Page Content -->
      <main class="flex-1 overflow-auto bg-bg-primary">
        <router-view />
      </main>

      <!-- Bottom Status Bar -->
      <div class="flex h-8 items-center justify-between border-t border-border-subtle bg-bg-surface px-4 text-xs text-text-muted">
        <div class="flex items-center gap-3">
          <span class="flex items-center gap-1.5">
            <span
              class="inline-block h-1.5 w-1.5 rounded-full"
              :class="appState.healthStatus === 'down' ? 'bg-error' : 'bg-success pulse-dot'"
            ></span>
            <span :class="appState.healthStatus === 'down' ? 'text-error' : appState.healthStatus === 'degraded' ? 'text-warning' : ''">
              {{ appState.healthStatus === 'down' ? 'Backend Down' : appState.healthStatus === 'degraded' ? 'Degraded' : 'Engine Active' }}
            </span>
          </span>
          <span class="text-border-subtle">│</span>
          <span>NSE/BSE</span>
          <span class="text-border-subtle">│</span>
          <span>{{ settings.tradingConfig.mode === 'live' ? 'Live Trading' : 'Paper Trading' }}</span>
        </div>
        <div class="flex items-center gap-3">
          <span>Market {{ marketStatus }}</span>
          <span class="text-border-subtle">│</span>
          <span>{{ currentTime }}</span>
          <span class="text-border-subtle">│</span>
          <span>v1.0.0</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import Sidebar from './components/Sidebar.vue'
import Header from './components/Header.vue'
import BackendDownBanner from './components/BackendDownBanner.vue'
import { getAppState, startHealthPolling, stopHealthPolling } from './stores/appState'
import { getSettings } from './stores/settings'

const appState = getAppState()
const settings = getSettings()
const sidebarCollapsed = ref(false)
const currentTime = ref('')

const marketStatus = computed(() => {
  const hour = new Date().getHours()
  return (hour >= 9 && hour < 16) ? 'OPEN' : 'CLOSED'
})

let timer: number
const updateTime = () => {
  currentTime.value = new Date().toLocaleTimeString('en-US', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  })
}

onMounted(() => {
  updateTime()
  timer = window.setInterval(updateTime, 1000)
  startHealthPolling()
})

onUnmounted(() => {
  clearInterval(timer)
  stopHealthPolling()
})
</script>
