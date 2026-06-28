<template>
  <div class="flex items-center gap-1.5">
    <span
      v-for="(comp, key) in components"
      :key="key"
      class="inline-block h-2 w-2 rounded-full"
      :class="statusColor(comp.status)"
      :title="`${key}: ${comp.status}`"
    />
    <span class="text-xs font-medium" :class="statusColor(overallStatus)">{{ overallStatus }}</span>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { HealthStatus } from '../api/types'

const props = defineProps<{ health: HealthStatus }>()

const components = computed(() => props.health.components ?? {})

const overallStatus = computed(() => {
  const vals = Object.values(components.value)
  if (vals.some(c => c.status === 'DOWN')) return 'DOWN'
  if (vals.some(c => c.status === 'DEGRADED')) return 'DEGRADED'
  return props.health.status ?? 'UP'
})

const statusColor = (status: string) => {
  if (status === 'UP') return 'bg-success'
  if (status === 'DOWN') return 'bg-danger'
  if (status === 'DEGRADED') return 'bg-warning'
  return 'bg-text-muted'
}
</script>
