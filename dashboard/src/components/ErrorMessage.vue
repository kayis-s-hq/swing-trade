<template>
  <div
    ref="alertElement"
    role="alert"
    :aria-busy="busy"
    :tabindex="focusOnMount ? -1 : undefined"
    class="flex flex-col gap-3 border border-danger/20 bg-danger-bg/30 p-4"
  >
    <div class="flex items-start gap-3">
      <svg
        class="mt-0.5 h-5 w-5 flex-shrink-0 text-danger/70"
        fill="none"
        stroke="currentColor"
        viewBox="0 0 24 24"
        aria-hidden="true"
      >
        <path
          stroke-linecap="round"
          stroke-linejoin="round"
          stroke-width="1.5"
          d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 2.34-3.173l-1.166-8.838C12.883 2.666 5.117 2.666 3.44 4.162L2.274 13c-.162 1.506.8 3.173 2.34 3.173z"
        />
      </svg>
      <div class="min-w-0 flex-1">
        <h2 class="text-sm font-semibold text-danger">
          {{ title }}
        </h2>
        <p class="mt-1 text-xs text-text-muted">
          {{ message }}
        </p>
      </div>
    </div>

    <details
      v-if="details?.length"
      class="text-xs text-text-muted"
    >
      <summary class="cursor-pointer font-medium text-text-secondary">
        Details
      </summary>
      <dl class="mt-2 grid grid-cols-[auto_1fr] gap-x-3 gap-y-1">
        <template
          v-for="detail in details"
          :key="`${detail.label}:${detail.value}`"
        >
          <dt class="font-medium text-text-secondary">
            {{ detail.label }}
          </dt>
          <dd class="break-all">
            {{ detail.value }}
          </dd>
        </template>
      </dl>
    </details>

    <button
      v-if="actionLabel"
      type="button"
      :disabled="busy"
      class="self-start border border-danger/30 bg-danger/10 px-3 py-1.5 text-xs font-medium text-danger transition-colors hover:bg-danger/20 disabled:cursor-wait disabled:opacity-60"
      @click="$emit('action')"
    >
      {{ actionLabel }}
    </button>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import type { FormattedErrorDetail } from '../errors/appError'

const props = withDefaults(
  defineProps<{
    title: string
    message: string
    busy?: boolean
    details?: FormattedErrorDetail[]
    actionLabel?: string
    focusOnMount?: boolean
  }>(),
  {
    busy: false,
    details: undefined,
    actionLabel: undefined,
    focusOnMount: false,
  }
)

defineEmits<{ action: [] }>()

const alertElement = ref<HTMLElement | null>(null)

onMounted(() => {
  if (props.focusOnMount) alertElement.value?.focus()
})
</script>
