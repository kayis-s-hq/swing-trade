<template>
  <slot />
  <div v-if="$slots.error" class="error-boundary-error">
    <slot name="error" />
  </div>
</template>
