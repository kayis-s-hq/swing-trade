<template>
  <div class="flex flex-col gap-2">
    <div
      v-for="stage in stages"
      :key="stage.stageNumber"
      class="rounded-lg border border-border-subtle/50 transition-colors hover:border-border-default"
      :class="{ 'border-brand/20': expandedStage === stage.stageNumber }"
    >
      <!-- Row header -->
      <button
        class="flex w-full items-center gap-3 px-4 py-3 text-left"
        @click="toggleStage(stage.stageNumber)"
      >
        <!-- Status icon -->
        <div class="shrink-0">
          <svg
            v-if="stage.status === 'completed'"
            class="h-5 w-5 text-success"
            viewBox="0 0 20 20"
            fill="currentColor"
          >
            <path
              fill-rule="evenodd"
              d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 9.586 6.707 7.293a1 1 0 00-1.414 1.414l4 4a1 1 0 001.414 0l4-4z"
              clip-rule="evenodd"
            />
          </svg>
          <svg
            v-else-if="stage.status === 'running'"
            class="h-5 w-5 text-brand animate-spin"
            viewBox="0 0 20 20"
            fill="currentColor"
          >
            <path
              d="M10 3a1 1 0 011 1v5h1a1 1 0 110 2H11v5a1 1 0 11-2 0V11H5a1 1 0 110-2h5V4a1 1 0 011-1z"
            />
          </svg>
          <svg
            v-else-if="stage.status === 'skipped'"
            class="h-5 w-5 text-text-muted/40"
            viewBox="0 0 20 20"
            fill="currentColor"
          >
            <path
              fill-rule="evenodd"
              d="M10 18a8 8 0 100-16 8 8 0 000 16zM7 9a1 1 0 000 2h6a1 1 0 100-2H7z"
              clip-rule="evenodd"
            />
          </svg>
          <svg
            v-else-if="stage.status === 'error'"
            class="h-5 w-5 text-danger"
            viewBox="0 0 20 20"
            fill="currentColor"
          >
            <path
              fill-rule="evenodd"
              d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7 4a1 1 0 11-2 0 1 1 0 012 0zm-1-9a1 1 0 00-1 1v4a1 1 0 102 0V4a1 1 0 00-1-1z"
              clip-rule="evenodd"
            />
          </svg>
        </div>

        <!-- Stage info -->
        <div class="min-w-0 flex-1">
          <div class="flex items-center gap-2">
            <span class="text-xs font-medium text-text-muted">Stage {{ stage.stageNumber }}</span>
            <span class="text-sm font-semibold text-text-primary">{{ stage.stageName }}</span>
          </div>
          <p class="mt-0.5 truncate text-xs text-text-secondary">
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
          <path
            stroke-linecap="round"
            stroke-linejoin="round"
            stroke-width="2"
            d="M19 9l-7 7-7-7"
          />
        </svg>
      </button>

      <!-- Expanded detail -->
      <div
        v-if="expandedStage === stage.stageNumber && stage.details"
        class="border-t border-border-subtle/50"
      >
        <div class="px-4 py-4">
          <!-- News detail -->
          <StageNewsDetail
            v-if="stage.details.type === 'news' || stage.details.type === 'sentiment'"
            :details="stage.details"
          />

          <!-- Technical detail -->
          <StageTechnicalDetail
            v-else-if="stage.details.type === 'technical'"
            :details="stage.details"
          />

          <!-- Fundamentals detail -->
          <StageFundamentalsDetail
            v-else-if="stage.details.type === 'fundamentals'"
            :details="stage.details"
          />

          <!-- Backtest detail -->
          <StageBacktestDetail
            v-else-if="stage.details.type === 'backtest'"
            :details="stage.details"
          />

          <!-- Composite detail -->
          <StageCompositeDetail
            v-else-if="stage.details.type === 'composite'"
            :details="stage.details"
            :synthesis="composite?.synthesis"
          />

          <!-- Synthesis detail -->
          <StageSynthesisDetail
            v-else-if="stage.details.type === 'synthesis'"
            :details="stage.details"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import type { AnalysisProgress, CompositeAnalysis } from '../api/types'
import StageNewsDetail from './StageNewsDetail.vue'
import StageTechnicalDetail from './StageTechnicalDetail.vue'
import StageFundamentalsDetail from './StageFundamentalsDetail.vue'
import StageBacktestDetail from './StageBacktestDetail.vue'
import StageCompositeDetail from './StageCompositeDetail.vue'
import StageSynthesisDetail from './StageSynthesisDetail.vue'

defineProps<{
  stages: AnalysisProgress[]
  composite?: CompositeAnalysis | null
}>()

const expandedStage = ref<number | null>(null)

const toggleStage = (stageNum: number) => {
  expandedStage.value = expandedStage.value === stageNum ? null : stageNum
}
</script>
