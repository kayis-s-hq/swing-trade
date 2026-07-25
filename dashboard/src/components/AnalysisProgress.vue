<script setup lang="ts">
import { computed, ref } from 'vue'
import type { AnalysisProgress as AnalysisProgressType, CompositeAnalysis } from '../api/types'
import StageSummaryCard from './StageSummaryCard.vue'

interface Props {
  stages: AnalysisProgressType[]
  currentStage: number
  isComplete: boolean
  durationMs?: number
  error: string | null
  composite?: CompositeAnalysis | null
}

const props = defineProps<Props>()

const expandedStage = ref<number | null>(null)
const detailView = ref<Record<number, 'summary' | 'details'>>({})

function handleToggleStage(stageNumber: number) {
  if (expandedStage.value === stageNumber) {
    expandedStage.value = null
  } else {
    expandedStage.value = stageNumber
    if (!(stageNumber in detailView.value)) {
      detailView.value[stageNumber] = 'summary'
    }
  }
}

function toggleDetailView(stageNumber: number, view: 'summary' | 'details') {
  detailView.value[stageNumber] = view
}

function formatStageDetails(stage: AnalysisProgressType): string {
  return JSON.stringify({
    stage: stage.stageName,
    number: stage.stageNumber,
    status: stage.status,
    message: stage.message,
    timestamp: stage.timestamp,
  }, null, 2)
}

const filteredStages = computed(() => {
  const stages = props.stages.filter(s => s.stageNumber < 100)
  // Remove 'running' events that have a subsequent 'completed' event for the same stage
  const result: typeof props.stages = []
  for (let i = 0; i < stages.length; i++) {
    const s = stages[i]
    if (s.status === 'running') {
      // Keep only if no later completed/error event exists for this stage
      const hasCompletion = stages.slice(i + 1).some(later => later.stageNumber === s.stageNumber && (later.status === 'completed' || later.status === 'error' || later.status === 'skipped'))
      if (!hasCompletion) result.push(s)
    } else {
      result.push(s)
    }
  }
  return result
})

const stageIcons = (status: string) => {
  if (status === 'running') return '⟳'
  if (status === 'completed') return '✓'
  if (status === 'error') return '✗'
  return '—'
}

const stageColors = (status: string, isCurrent: boolean) => {
  if (status === 'error') return 'text-danger'
  if (status === 'completed') return 'text-success'
  if (isCurrent) return 'text-brand'
  return 'text-text-muted'
}
</script>

<template>
  <div class="card-panel p-5 animate-fade-in">
    <!-- Progress bar -->
    <div class="mb-4">
      <div class="mb-2 flex items-center justify-between">
        <span class="text-sm font-medium text-text-primary">Analysis Progress</span>
        <span v-if="isComplete && durationMs != null" class="text-xs text-text-muted">
          Completed in {{ (durationMs / 1000).toFixed(1) }}s
        </span>
      </div>
      <div class="h-2 overflow-hidden rounded bg-bg-hover">
        <div
          class="h-full rounded bg-brand transition-all duration-500"
          :style="{ width: `${(filteredStages.filter(s => s.status === 'completed').length / Math.max(filteredStages.length, 1)) * 100}%` }"
        />
      </div>
    </div>

    <!-- Stage list -->
    <div class="space-y-2">
      <div
        v-for="stage in filteredStages"
        :key="stage.stageNumber"
        class="cursor-pointer rounded-md transition-colors"
        :class="[
          stage.stageNumber === currentStage && !isComplete ? 'bg-bg-hover' : '',
          expandedStage === stage.stageNumber ? 'bg-bg-hover' : 'hover:bg-bg-hover/50'
        ]"
        @click="handleToggleStage(stage.stageNumber)"
      >
        <!-- Row header -->
        <div class="flex items-center gap-3 px-3 py-2">
          <!-- Status icon -->
          <span
            class="flex h-6 w-6 shrink-0 items-center justify-center rounded-full text-xs"
            :class="stageColors(stage.status, stage.stageNumber === currentStage && !isComplete)"
          >
            <span v-if="stage.status === 'running' && !isComplete" class="animate-spin">⟳</span>
            <span v-else>{{ stageIcons(stage.status) }}</span>
          </span>

          <!-- Stage info -->
          <div class="min-w-0 flex-1">
            <div class="flex items-center gap-2">
              <span
                class="text-sm font-medium"
                :class="stageColors(stage.status, stage.stageNumber === currentStage && !isComplete)"
              >
                {{ stage.stageName }}
              </span>
              <span v-if="stage.status === 'skipped'" class="text-xs italic text-text-muted">skipped</span>
            </div>
            <p v-if="stage.message" class="text-xs text-text-muted">
              {{ stage.message }}
            </p>
          </div>

          <!-- Chevron -->
          <svg
            class="h-4 w-4 shrink-0 text-text-muted transition-transform"
            :class="{ 'rotate-180': expandedStage === stage.stageNumber }"
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
          >
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7" />
          </svg>
        </div>

        <!-- Expandable detail panel -->
        <div
          v-if="expandedStage === stage.stageNumber"
          class="animate-fade-in border-t border-border-subtle/50 px-3 py-3"
        >
          <!-- Tab toggle -->
          <div class="mb-3 flex gap-1 rounded bg-bg-primary p-1">
            <button
              @click.stop="toggleDetailView(stage.stageNumber, 'summary')"
              class="rounded px-3 py-1 text-xs font-medium transition-colors"
              :class="detailView[stage.stageNumber] === 'summary' ? 'bg-brand/10 text-brand' : 'text-text-muted hover:text-text-primary'"
            >
              Summary
            </button>
            <button
              @click.stop="toggleDetailView(stage.stageNumber, 'details')"
              class="rounded px-3 py-1 text-xs font-medium transition-colors"
              :class="detailView[stage.stageNumber] === 'details' ? 'bg-brand/10 text-brand' : 'text-text-muted hover:text-text-primary'"
            >
              Details
            </button>
          </div>

          <!-- Summary tab -->
          <div v-if="detailView[stage.stageNumber] === 'summary'" class="space-y-2">
            <StageSummaryCard :stage="stage" :composite="composite ?? null" />
          </div>

          <!-- Details tab -->
          <div v-else class="max-h-60 overflow-y-auto rounded bg-bg-primary p-3 text-xs font-mono text-text-secondary">
            <pre class="whitespace-pre-wrap break-words">{{ formatStageDetails(stage) }}</pre>
          </div>
        </div>
      </div>
    </div>

    <!-- Error state -->
    <div v-if="error" class="mt-4 rounded-md border border-danger/30 bg-danger/5 p-3">
      <p class="text-sm text-danger">{{ error }}</p>
    </div>

    <!-- Complete state summary -->
    <div v-if="isComplete && !error" class="mt-4 text-center">
      <p class="text-sm font-medium text-success">Analysis complete</p>
    </div>
  </div>
</template>