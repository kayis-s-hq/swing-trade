<template>
  <div class="card-panel p-4">
    <h4 class="text-xs font-semibold uppercase tracking-wider text-text-muted">
      {{ title }}
    </h4>
    <p class="mt-2 text-3xl font-bold" :class="color">
      {{ value }}
    </p>
    <p v-if="subtext" class="mt-1 text-xs text-text-muted">
      {{ subtext }}
    </p>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  title: string
  value: string
  color?: string
  subtext?: string
}>()
</script>
