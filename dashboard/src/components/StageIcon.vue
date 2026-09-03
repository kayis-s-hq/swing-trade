<template>
  <span class="inline-flex h-4 w-4 items-center justify-center">
    <!-- COMPLETED: checkmark -->
    <svg
      v-if="status === 'COMPLETED'"
      class="h-4 w-4 text-success"
      fill="none"
      stroke="currentColor"
      viewBox="0 0 24 24"
    >
      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" />
    </svg>
    <!-- ERROR: X -->
    <svg
      v-else-if="status === 'ERROR'"
      class="h-4 w-4 text-danger"
      fill="none"
      stroke="currentColor"
      viewBox="0 0 24 24"
    >
      <path
        stroke-linecap="round"
        stroke-linejoin="round"
        stroke-width="2"
        d="M6 18L18 6M6 6l12 12"
      />
    </svg>
    <!-- RUNNING: spinner -->
    <svg
      v-else-if="status === 'RUNNING'"
      class="h-4 w-4 animate-spin text-brand"
      viewBox="0 0 24 24"
      fill="none"
    >
      <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" />
      <path
        class="opacity-75"
        fill="currentColor"
        d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"
      />
    </svg>
    <!-- PENDING/SKIPPED: dash -->
    <span v-else class="text-text-muted/40">—</span>
  </span>
</template>

<script setup lang="ts">
defineProps<{
  status: string
}>()
</script>
