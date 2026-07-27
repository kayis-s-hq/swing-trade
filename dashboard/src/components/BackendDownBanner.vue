<template>
  <div
    v-if="!appState.backendUp"
    class="bg-warning/10 border-b border-warning/30 px-4 py-2 flex items-center justify-between gap-3"
  >
    <div class="flex items-center gap-2 text-xs">
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
      <span class="text-warning font-medium">Backend unreachable</span>
      <span class="text-text-muted truncate">{{ appState.backendError }}</span>
    </div>
    <div class="flex items-center gap-2 flex-shrink-0">
      <button
        class="text-xs font-medium text-warning hover:text-warning/80 transition-colors"
        @click="retry"
      >
        Retry
      </button>
      <button
        class="h-5 w-5 flex items-center justify-center rounded text-text-muted hover:text-text-primary transition-colors"
        @click="dismiss"
      >
        <svg
          class="h-3.5 w-3.5"
          fill="none"
          viewBox="0 0 24 24"
          stroke="currentColor"
          stroke-width="2"
        >
          <path stroke-linecap="round" stroke-linejoin="round" d="M6 18L18 6M6 6l12 12" />
        </svg>
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { getAppState } from '@/stores/appState'

const appState = getAppState()

function retry() {
  import('@/stores/appState').then(({ startHealthPolling }) => startHealthPolling())
}

function dismiss() {
  appState.backendUp = true
  appState.backendError = ''
}
</script>
