<template>
  <div class="border-r border-border-subtle/50 p-2.5 last:border-r-0">
    <span class="text-[8px] uppercase tracking-[0.2em] text-text-muted/40">{{ title }}</span>
    <p class="mt-0.5 text-sm font-semibold tracking-tight text-text-primary">
      {{ value }}
    </p>
    <div v-if="trend" class="mt-0.5 flex items-center gap-1">
      <span class="text-[9px]" :class="trend.isPositive ? 'text-success/70' : 'text-danger/70'">
        {{ trend.isPositive ? '▲' : '▼' }} {{ trend.value }}
      </span>
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  title: string
  value: string | number
  trend?: { value: string; isPositive: boolean }
}>()
</script>
