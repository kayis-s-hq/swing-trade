<template>
  <article
    :role="type === 'error' ? 'alert' : 'status'"
    :aria-live="type === 'error' ? 'assertive' : 'polite'"
    class="flex items-start gap-2.5 rounded-lg border px-4 py-3 text-sm shadow-2xl backdrop-blur-sm"
    :class="typeStyles[type]"
  >
    <svg
      class="mt-0.5 h-4 w-4 flex-shrink-0"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      aria-hidden="true"
      v-html="icons[type]"
    />
    <div class="min-w-0 flex-1">
      <p v-if="title" class="font-semibold">
        {{ title }}
      </p>
      <p :class="title ? 'mt-0.5 text-xs' : 'font-medium'">
        {{ message }}
      </p>
      <button
        v-if="actionLabel"
        type="button"
        class="mt-2 text-xs font-semibold underline underline-offset-2"
        @click="$emit('action')"
      >
        {{ actionLabel }}
      </button>
    </div>
    <button
      type="button"
      aria-label="Dismiss notification"
      class="flex h-5 w-5 flex-shrink-0 items-center justify-center rounded transition-colors hover:bg-current/10"
      @click="$emit('dismiss')"
    >
      <svg
        class="h-3.5 w-3.5"
        fill="none"
        viewBox="0 0 24 24"
        stroke="currentColor"
        stroke-width="2"
        aria-hidden="true"
      >
        <path stroke-linecap="round" stroke-linejoin="round" d="M6 18L18 6M6 6l12 12" />
      </svg>
    </button>
  </article>
</template>

<script setup lang="ts">
type ToastType = 'success' | 'error' | 'warning' | 'info'

withDefaults(
  defineProps<{
    title?: string
    message: string
    type?: ToastType
    actionLabel?: string
    duration?: number
  }>(),
  {
    title: undefined,
    type: 'info',
    actionLabel: undefined,
    duration: undefined,
  }
)

defineEmits<{
  dismiss: []
  action: []
}>()

const typeStyles: Record<ToastType, string> = {
  success: 'bg-success/20 border-success/30 text-success',
  error: 'bg-danger/20 border-danger/30 text-danger',
  warning: 'bg-warning/20 border-warning/30 text-warning',
  info: 'bg-info/20 border-info/30 text-info',
}

const icons: Record<ToastType, string> = {
  success:
    '<path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M9 12.75L11.25 15 15 9.75M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />',
  error:
    '<path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M12 9v3.75m9-.75a9 9 0 11-18 0 9 9 0 0118 0zm-9 3.75h.008v.008H12v-.008z" />',
  warning:
    '<path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M12 9v3.75m-9.303 3.376c-.866 1.5.217 3.374 1.948 3.374h14.71c1.73 0 2.813-1.874 1.948-3.374L13.949 3.378c-.866-1.5-3.032-1.5-3.898 0L2.697 16.126zM12 15.75h.007v.008H12v-.008z" />',
  info: '<path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M11.25 11.25l.041-.02a.75.75 0 011.063.852l-.708 2.836a.75.75 0 001.063.853l.041-.021M21 12a9 9 0 11-18 0 9 9 0 0118 0zm-9-3.75h.008v.008H12V8.25z" />',
}
</script>
