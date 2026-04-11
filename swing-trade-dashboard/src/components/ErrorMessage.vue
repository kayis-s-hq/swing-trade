<script setup lang="ts">
import { computed } from 'vue'

export interface ErrorMessageProps {
  message?: string
  detail?: string
  showRetry?: boolean
  retryText?: string
}

const props = withDefaults(defineProps<ErrorMessageProps>(), {
  message: 'An error occurred',
  detail: undefined,
  showRetry: false,
  retryText: 'Try Again',
})

defineEmits<{
  (e: 'retry'): void
}>()

const showContent = computed(() => {
  return props.message && props.message.trim() !== ''
})
</script>

<template>
  <div
    v-if="showContent"
    class="rounded-2xl border border-error-200 bg-error-50 p-6 dark:border-error-900 dark:bg-error-900/20"
  >
    <div class="flex items-start gap-4">
      <div class="flex h-12 w-12 flex-shrink-0 items-center justify-center rounded-full bg-error-100 dark:bg-error-900/30">
        <svg class="h-6 w-6 text-error-600 dark:text-error-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path
            stroke-linecap="round"
            stroke-linejoin="round"
            stroke-width="2"
            d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"
          />
        </svg>
      </div>
      <div class="flex-1">
        <h3 class="text-lg font-medium text-error-800 dark:text-error-200">Error</h3>
        <p class="mt-1 text-sm text-error-700 dark:text-error-300">{{ message }}</p>
        <p
          v-if="detail"
          class="mt-2 text-xs text-error-600 dark:text-error-400 font-mono"
        >{{ detail }}</p>
        <button
          v-if="showRetry"
          @click="$emit('retry')"
          class="mt-4 rounded bg-error-600 px-4 py-2 text-sm font-medium text-white hover:bg-error-700"
        >
          {{ retryText }}
        </button>
      </div>
    </div>
  </div>

  <div
    v-else
    class="rounded-2xl border border-error-200 bg-error-50 p-6 dark:border-error-900 dark:bg-error-900/20"
  >
    <div class="flex items-center gap-3">
      <svg class="h-8 w-8 text-error-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path
          stroke-linecap="round"
          stroke-linejoin="round"
          stroke-width="2"
          d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"
        />
      </svg>
      <p class="text-sm text-error-700 dark:text-error-300">No error details available</p>
    </div>
  </div>
</template>
