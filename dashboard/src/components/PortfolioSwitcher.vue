<template>
  <div class="mb-4 flex flex-wrap items-center gap-2" role="tablist" aria-label="Portfolio">
    <button
      v-for="option in options"
      :key="option.id"
      role="tab"
      :aria-selected="modelValue === option.id"
      class="rounded-full border px-3 py-1 text-xs font-medium transition-colors"
      :class="
        modelValue === option.id
          ? 'border-brand bg-brand/10 text-brand'
          : 'border-border-subtle text-text-muted hover:text-text-primary'
      "
      @click="emit('update:modelValue', option.id)"
    >
      {{ option.label }}
      <span
        v-if="option.id !== REAL"
        class="ml-1 rounded bg-warning-subtle px-1 text-[10px] uppercase text-warning"
        >shadow</span
      >
    </button>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  listPaperPortfolios,
  shadowPortfolios,
  type PaperPortfolioSummary,
} from '../api/portfolios'

const REAL = 'REAL'
defineProps<{ modelValue: string }>()
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()

const portfolios = ref<PaperPortfolioSummary[]>([])

const options = computed(() => [
  { id: REAL, label: 'Real (champion)' },
  ...shadowPortfolios(portfolios.value).map((p) => ({
    id: p.portfolioId,
    label: p.portfolioId === 'selected' ? 'Selected (tournament)' : p.portfolioId,
  })),
])

// Supplementary: failing to list shadow books must leave the Real tab fully usable.
onMounted(async () => {
  try {
    portfolios.value = await listPaperPortfolios()
  } catch {
    portfolios.value = []
  }
})
</script>
