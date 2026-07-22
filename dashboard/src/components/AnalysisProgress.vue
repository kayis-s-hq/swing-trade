<script setup lang="ts">
import type { AnalysisProgress as AnalysisProgressType } from '../api/types'

interface Props {
  stages: AnalysisProgressType[]
  currentStage: number
  isComplete: boolean
  durationMs?: number
  error: string | null
}

defineProps<Props>()

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
          :style="{ width: `${(stages.filter(s => s.status === 'completed').length / Math.max(stages.filter(s => s.stageNumber < 100).length, 1)) * 100}%` }"
        />
      </div>
    </div>

    <!-- Stage list -->
    <div class="space-y-2">
      <div
        v-for="stage in stages.filter(s => s.stageNumber < 100)"
        :key="stage.stageNumber"
        class="flex items-center gap-3 rounded-md px-3 py-2 transition-colors"
        :class="stage.stageNumber === currentStage && !isComplete ? 'bg-bg-hover' : ''"
      >
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