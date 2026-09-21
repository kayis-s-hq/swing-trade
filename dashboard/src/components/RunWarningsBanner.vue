<template>
  <div
    v-if="visible"
    class="mt-3 rounded-md border border-warning/30 bg-warning/5 p-3 text-xs text-warning"
    role="status"
    data-testid="warnings-banner"
  >
    <p class="font-medium">
      Warnings:
      <span v-if="degradedCount > 0">{{ plural(degradedCount, 'degraded stage') }}</span>
      <span v-if="degradedCount > 0 && skippedCount > 0">, </span>
      <span v-if="skippedCount > 0">{{ plural(skippedCount, 'skipped strategy') }}</span>
    </p>
    <ul class="mt-1 list-disc space-y-0.5 pl-4">
      <li v-for="line in lines" :key="line">{{ line }}</li>
    </ul>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { JobRunStageResponse, JobRunSummaryResponse } from '../api/types'
import { collectRunWarnings } from '../utils/runMatrix'

const props = defineProps<{
  rows: JobRunStageResponse[]
  summary: JobRunSummaryResponse | null
}>()

const local = computed(() => collectRunWarnings(props.rows))
const degradedCount = computed(() => props.summary?.degradedStages ?? local.value.degraded)
const skippedCount = computed(() => props.summary?.skippedStrategies ?? 0)

const lines = computed<string[]>(() => {
  const s = props.summary
  const out: string[] = []
  for (const d of s?.degradedStageBreakdown ?? []) {
    out.push(`${d.stage}: ${d.reason} (${d.count})`)
  }
  for (const k of s?.skippedStrategyBreakdown ?? []) {
    out.push(`${k.variantId} ${k.outcome.toLowerCase()}: ${k.reason} (${k.symbols} symbols)`)
  }
  return out.length ? out : local.value.messages
})

const visible = computed(
  () => degradedCount.value > 0 || skippedCount.value > 0 || lines.value.length > 0
)

const plural = (n: number, noun: string) => `${n} ${noun}${n === 1 ? '' : 's'}`
</script>
