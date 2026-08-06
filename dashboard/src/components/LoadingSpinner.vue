<template>
  <div class="flex flex-col items-center justify-center gap-3">
    <div class="flex h-8 w-8 items-center justify-center">
      <svg class="h-6 w-6 animate-spin text-brand" fill="none" viewBox="0 0 24 24">
        <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="2" />
        <path
          class="opacity-75"
          d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"
          fill="currentColor"
        />
      </svg>
    </div>
    <p class="text-[10px] uppercase tracking-[0.2em] text-text-muted">
      {{ message ?? 'Loading...' }}
    </p>
  </div>
</template>

<script setup lang="ts">
defineProps<{ message?: string }>()
</script>
