<template>
  <aside
    class="flex flex-col border-r border-border-subtle bg-bg-surface transition-all duration-300"
    :class="collapsed ? 'w-16' : 'w-56'"
  >
    <!-- Logo -->
    <div class="flex h-14 items-center border-b border-border-subtle px-4">
      <template v-if="!collapsed">
        <div class="flex items-center gap-2">
          <div class="flex h-7 w-7 items-center justify-center rounded-md bg-brand/10">
            <span class="text-sm font-bold text-brand">S</span>
          </div>
          <span class="font-display text-base font-semibold text-text-primary">SwingTrade</span>
        </div>
      </template>
      <template v-else>
        <div class="mx-auto flex h-7 w-7 items-center justify-center rounded-md bg-brand/10">
          <span class="text-sm font-bold text-brand">S</span>
        </div>
      </template>
    </div>

    <!-- Navigation -->
    <nav class="flex-1 space-y-1 px-2 py-3">
      <router-link
        v-for="item in navItems"
        :key="item.path"
        :to="item.path"
        class="flex items-center gap-3 rounded-md px-3 py-2.5 text-sm font-medium transition-all"
        :class="
          $route.path === item.path
            ? 'bg-brand-subtle text-brand'
            : 'text-text-muted hover:bg-bg-hover hover:text-text-primary'
        "
        :title="collapsed ? item.label : undefined"
      >
        <svg class="h-5 w-5 flex-shrink-0" fill="none" viewBox="0 0 24 24" v-html="item.icon"></svg>
        <template v-if="!collapsed">
          <span class="flex-1">{{ item.label }}</span>
          <span v-if="item.badge" class="ml-1 inline-flex h-5 min-w-[20px] items-center justify-center rounded-full bg-brand/10 px-1.5 text-[10px] font-semibold text-brand">{{ item.badge }}</span>
        </template>
      </router-link>
    </nav>

    <!-- Footer -->
    <div class="border-t border-border-subtle p-3">
      <div v-if="!collapsed" class="rounded-md border border-border-subtle bg-bg-primary/50 p-3">
        <div class="mb-1 flex items-center gap-2">
          <span
            class="inline-block h-2 w-2 rounded-full"
            :class="healthStatus === 'down' ? 'bg-error' : healthStatus === 'degraded' ? 'bg-warning' : 'bg-success pulse-dot'"
          ></span>
          <span class="text-xs font-medium text-text-muted">System Status</span>
        </div>
        <div class="text-xs font-medium" :class="healthStatus === 'down' ? 'text-error' : healthStatus === 'degraded' ? 'text-warning' : 'text-success'">
          {{ healthStatus === 'down' ? 'Backend Down' : healthStatus === 'degraded' ? 'Degraded' : 'Engine Active' }}
        </div>
        <div class="mt-0.5 text-[10px] text-text-muted">Sync {{ lastSync }}</div>
        <div class="mt-2 border-t border-border-subtle pt-2 text-[10px] text-text-muted">
          Broker: <span class="font-medium text-text-secondary">{{ brokerLabel }}</span>
        </div>
      </div>
      <button v-else @click="$emit('toggle')" class="flex h-8 w-8 items-center justify-center rounded-md text-text-muted transition-colors hover:bg-bg-hover hover:text-text-primary">
        <svg class="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 5l7 7-7 7M5 5l7 7-7 7" />
        </svg>
      </button>
    </div>

    <!-- Collapse Toggle -->
    <button v-if="!collapsed" @click="$emit('toggle')" class="absolute right-0 top-16 -translate-x-1/2 rounded-l-md border border-border-subtle bg-bg-surface px-1 py-1 text-text-muted transition-colors hover:text-text-primary">
      <svg class="h-3 w-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 19l-7-7 7-7M15 5l7 7-7 7" />
      </svg>
    </button>
  </aside>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { iconPaths } from './Icons'
import { getSettings, brokerLabels } from '../stores/settings'

const settings = getSettings()
const props = defineProps<{ collapsed: boolean; healthStatus?: '' | 'healthy' | 'degraded' | 'down' }>()
defineEmits<{ toggle: [] }>()

const lastSync = computed(() => {
  const now = new Date()
  return now.toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false })
})

const healthStatus = computed(() => (props.healthStatus || 'healthy') as '' | 'healthy' | 'degraded' | 'down')

const brokerLabel = computed(() => brokerLabels[settings.selectedBroker] || 'Unknown')

const navItems = [
  { path: '/', label: 'Dashboard', icon: iconPaths.dashboard, badge: undefined },
  { path: '/positions', label: 'Positions', icon: iconPaths.positions, badge: undefined },
  { path: '/signals', label: 'Signals', icon: iconPaths.signals, badge: '6' },
  { path: '/portfolio', label: 'Portfolio', icon: iconPaths.portfolio, badge: undefined },
  { path: '/watchlist', label: 'Watchlist', icon: iconPaths.watchlist, badge: undefined },
  { path: '/data', label: 'Data', icon: iconPaths.data, badge: undefined },
  { path: '/settings', label: 'Settings', icon: iconPaths.settings, badge: undefined },
]
</script>
