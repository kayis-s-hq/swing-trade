<template>
  <slot />
  <div v-if="error && $slots.error" class="error-boundary-error">
    <slot name="error" />
  </div>
</template>

<script setup lang="ts">
interface Props {
  error?: boolean
}

defineOptions({ inheritAttrs: false })

withDefaults(defineProps<Props>(), {
  error: false,
})
</script>
