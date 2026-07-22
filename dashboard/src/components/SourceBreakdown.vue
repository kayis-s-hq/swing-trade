<template>
  <div class="card-panel p-4">
    <div v-if="sources.length === 0" class="text-center py-6 text-sm text-text-muted">
      No sources available
    </div>

    <div v-else class="space-y-4">
      <div
        v-for="(source, index) in sources"
        :key="source.name"
        class="animate-fade-in"
        :style="{ animationDelay: `${index * 60}ms` }"
      >
        <!-- Header: name + weight on left, score on right -->
        <div class="flex items-center justify-between gap-3 mb-1.5">
          <div class="flex-1 min-w-0">
            <div class="flex items-baseline gap-2">
              <span class="text-sm font-medium text-text-primary truncate">{{ source.name }}</span>
              <span class="text-[11px] font-medium text-text-muted">{{ Math.round(source.weight * 100) }}%</span>
            </div>
            <p class="text-xs text-text-muted/70 truncate">{{ source.description }}</p>
          </div>
          <span
            class="flex-shrink-0 text-sm font-mono font-semibold tabular-nums"
            :class="scoreColorClass(source.score)"
          >
            {{ scoreLabel(source.score) }}
          </span>
        </div>

        <!-- Horizontal bar -->
        <div class="relative h-2 rounded bg-bg-primary border border-border-subtle overflow-hidden">
          <!-- Score bar -->
          <div
            class="absolute top-0 bottom-0 rounded-sm transition-all"
            :class="barBgClass(source.score)"
            :style="barStyle(source.score)"
          />
          <!-- Center marker at 50% -->
          <div class="absolute top-0 bottom-0 left-1/2 w-px -translate-x-px bg-text-muted/30 z-10" />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
interface Source {
  name: string
  score: number // -100 to +100
  weight: number // 0.0 to 1.0
  description: string
}

defineProps<{
  sources: Source[]
}>()

const scoreColorClass = (score: number): string => {
  if (score > 0) return 'text-success'
  if (score < 0) return 'text-danger'
  return 'text-text-muted'
}

const barBgClass = (score: number): string => {
  if (score > 0) return 'bg-success'
  if (score < 0) return 'bg-danger'
  return 'bg-text-muted/40'
}

const barStyle = (score: number): Record<string, string> => {
  const absScore = Math.abs(score)
  const pct = (absScore / 100).toFixed(2)

  if (score > 0) {
    return {
      right: '0%',
      width: `${pct}%`,
    }
  }
  if (score < 0) {
    return {
      left: '0%',
      width: `${pct}%`,
    }
  }
  return {
    left: '50%',
    width: '0%',
  }
}

const scoreLabel = (score: number): string => {
  if (score > 0) return `+${score}`
  if (score < 0) return `${score}`
  return '0'
}
</script>