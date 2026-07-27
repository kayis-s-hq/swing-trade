<template>
  <div
    class="flex flex-col items-center justify-center gap-4 border border-danger/20 bg-danger-bg/30 p-8"
  >
    <svg class="h-8 w-8 text-danger/60" fill="none" stroke="currentColor" viewBox="0 0 24 24">
      <path
        stroke-linecap="round"
        stroke-linejoin="round"
        stroke-width="1.5"
        d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 2.34-3.173l-1.166-8.838C12.883 2.666 5.117 2.666 3.44 4.162L2.274 13c-.162 1.506.8 3.173 2.34 3.173z"
      />
    </svg>
    <p class="text-[10px] uppercase tracking-[0.2em] text-danger/80">
      {{ message }}
    </p>
    <button
      v-if="showRetry"
      class="border border-danger/30 bg-danger/10 px-3 py-1.5 text-[10px] font-medium uppercase tracking-wider text-danger transition-colors hover:bg-danger/20"
      @click="$emit('retry')"
    >
      {{ retryText ?? 'Retry' }}
    </button>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  message: string
  showRetry?: boolean
  retryText?: string
}>()

defineEmits<{ retry: [] }>()
</script>
