<template>
  <div class="flex flex-wrap gap-1.5">
    <span
      v-for="chip in chips"
      :key="chip.variantId"
      class="inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[11px] font-medium"
      :class="chipClass(chip)"
      :title="chip.selected ? `${chip.variantId} - tournament winner` : chip.variantId"
      data-testid="variant-chip"
    >
      <span v-if="chip.selected" aria-label="tournament winner">★</span>
      {{ chip.variantId }} {{ chip.direction }} {{ Math.round(chip.confidence * 100) }}%
    </span>
  </div>
</template>

<script setup lang="ts">
import type { VariantChip } from '../utils/signalGrouping'

defineProps<{ chips: VariantChip[] }>()

function chipClass(chip: VariantChip): string {
  const ring = chip.selected ? ' ring-1 ring-success' : ''
  if (chip.direction === 'BUY') return `bg-success-subtle text-success${ring}`
  if (chip.direction === 'SELL') return `bg-danger-subtle text-danger${ring}`
  return `bg-bg-primary text-text-muted${ring}`
}
</script>
