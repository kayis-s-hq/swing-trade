<template>
  <span
    class="inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-medium"
    :class="badgeClass"
  >
    <span class="h-1.5 w-1.5 rounded-full" :class="dotClass" />
    {{ label }}
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  status: string
  label: string
}>()

const badgeClass = computed(() => {
  const s = props.status.toUpperCase()
  switch (s) {
    case 'RUNNING':
      return 'bg-brand-subtle text-brand'
    case 'COMPLETED':
      return 'bg-success-subtle text-success'
    case 'FAILED':
    case 'ERROR':
      return 'bg-danger-subtle text-danger'
    case 'CANCELLED':
      return 'bg-warning-subtle text-warning'
    case 'PENDING':
      return 'bg-bg-primary text-text-muted'
    case 'SKIPPED':
      return 'bg-bg-primary text-text-muted'
    default:
      return 'bg-bg-primary text-text-muted'
  }
})

const dotClass = computed(() => {
  const s = props.status.toUpperCase()
  switch (s) {
    case 'RUNNING':
      return 'bg-brand animate-pulse'
    case 'COMPLETED':
      return 'bg-success'
    case 'FAILED':
    case 'ERROR':
      return 'bg-danger'
    case 'CANCELLED':
      return 'bg-warning'
    default:
      return 'bg-text-muted'
  }
})
</script>
