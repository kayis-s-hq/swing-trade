<template>
  <div
    v-if="showBanner"
    role="alert"
    class="flex items-center justify-between gap-3 border-b border-warning/30 bg-warning/10 px-4 py-2"
  >
    <div class="flex min-w-0 items-center gap-2 text-xs">
      <svg
        class="h-3.5 w-3.5 flex-shrink-0 text-warning"
        fill="none"
        viewBox="0 0 24 24"
        stroke="currentColor"
        stroke-width="2"
      >
        <path
          stroke-linecap="round"
          stroke-linejoin="round"
          d="M12 9v3.75m-9.303 3.376c-.866 1.5.217 3.374 1.948 3.374h14.71c1.73 0 2.813-1.874 1.948-3.374L13.949 3.378c-.866-1.5-3.032-1.5-3.898 0L2.697 16.126zM12 15.75h.007v.008H12v-.008z"
        />
      </svg>
      <span class="flex-shrink-0 font-medium text-warning">{{ title }}</span>
      <span class="truncate text-text-muted">{{ message }}</span>
    </div>
    <div class="flex flex-shrink-0 items-center gap-2">
      <button
        type="button"
        aria-label="Retry backend health check"
        :aria-busy="appState.checking"
        :disabled="appState.checking"
        class="text-xs font-medium text-warning transition-colors hover:text-warning/80 disabled:cursor-wait disabled:opacity-60"
        @click="checkHealthNow"
      >
        {{ appState.checking ? 'Checking…' : 'Retry' }}
      </button>
      <button
        type="button"
        aria-label="Dismiss backend status alert"
        class="flex h-5 w-5 items-center justify-center rounded text-text-muted transition-colors hover:text-text-primary"
        @click="dismissBackendBanner"
      >
        <svg
          class="h-3.5 w-3.5"
          fill="none"
          viewBox="0 0 24 24"
          stroke="currentColor"
          stroke-width="2"
          aria-hidden="true"
        >
          <path stroke-linecap="round" stroke-linejoin="round" d="M6 18L18 6M6 6l12 12" />
        </svg>
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { checkHealthNow, dismissBackendBanner, getAppState } from '@/stores/appState'

const appState = getAppState()

const showBanner = computed(
  () =>
    !appState.bannerDismissed &&
    (appState.healthStatus === 'unavailable' || appState.healthStatus === 'degraded')
)
const title = computed(() =>
  appState.healthStatus === 'unavailable' ? 'Backend unavailable' : 'Backend degraded'
)
const message = computed(() =>
  appState.healthStatus === 'unavailable'
    ? 'Can’t reach the backend. Check the service and retry.'
    : 'The backend responded, but one or more health checks are failing.'
)
</script>
