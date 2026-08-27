<template>
  <div v-if="failed" role="alert" class="m-6 rounded-lg border border-danger/30 bg-danger-bg p-5">
    <h2 class="text-base font-semibold text-text-primary">This page couldn’t be displayed</h2>
    <p class="mt-1 text-sm text-text-secondary">
      An unexpected dashboard error occurred. Try this page again or reload the dashboard.
    </p>
    <div class="mt-4 flex flex-wrap gap-2">
      <button
        type="button"
        class="rounded-md bg-brand px-3 py-2 text-sm font-medium text-white hover:bg-brand/90"
        @click="reset"
      >
        Try this page again
      </button>
      <button
        type="button"
        class="rounded-md border border-border-subtle bg-bg-surface px-3 py-2 text-sm font-medium text-text-primary hover:bg-bg-hover"
        @click="reloadDashboard"
      >
        Reload dashboard
      </button>
    </div>
  </div>
  <div v-else :key="generation" class="contents">
    <slot />
  </div>
</template>

<script setup lang="ts">
import { onErrorCaptured, ref, watch } from 'vue'
import { reportRuntimeError } from '../stores/runtimeErrors'

const props = defineProps<{
  resetKey?: string
  route?: string
}>()

const failed = ref(false)
const generation = ref(0)

function reset(): void {
  failed.value = false
  generation.value += 1
}

function reloadDashboard(): void {
  window.location.reload()
}

watch(
  () => props.resetKey,
  (current, previous) => {
    if (current !== previous && failed.value) reset()
  }
)

onErrorCaptured((error, _instance, info) => {
  if (!failed.value) {
    failed.value = true
    reportRuntimeError(error, {
      source: 'boundary',
      info,
      ...(props.route ? { route: props.route } : {}),
    })
  }
  return false
})
</script>
