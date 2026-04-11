<script setup lang="ts">
export interface LoadingSpinnerProps {
  size?: 'sm' | 'md' | 'lg'
  message?: string
  fullScreen?: boolean
}

withDefaults(defineProps<LoadingSpinnerProps>(), {
  size: 'md',
  message: undefined,
  fullScreen: false,
})

const sizeClasses = {
  sm: 'w-4 h-4 border-2',
  md: 'w-8 h-8 border-3',
  lg: 'w-12 h-12 border-4',
}

const messageClasses = {
  sm: 'text-xs',
  md: 'text-sm',
  lg: 'text-base',
}
</script>

<template>
  <div
    v-if="fullScreen"
    class="fixed inset-0 z-9999 flex items-center justify-center bg-black bg-opacity-50"
  >
    <div class="flex flex-col items-center gap-3">
      <div class="animate-spin rounded-full border-indigo-600 border-t-transparent" :class="sizeClasses[size]"></div>
      <p v-if="message" class="text-sm text-gray-500 dark:text-gray-400">{{ message }}</p>
    </div>
  </div>

  <div
    v-else
    class="flex items-center gap-3"
  >
    <div class="animate-spin rounded-full border-indigo-600 border-t-transparent" :class="sizeClasses[size]"></div>
    <p v-if="message" :class="messageClasses[size]" class="text-gray-500 dark:text-gray-400">{{ message }}</p>
  </div>
</template>
