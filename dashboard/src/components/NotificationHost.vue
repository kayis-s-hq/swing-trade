<template>
  <Teleport to="body">
    <section
      aria-label="Notifications"
      class="pointer-events-none fixed bottom-6 right-6 z-50 flex w-[min(24rem,calc(100vw-2rem))] flex-col gap-2"
    >
      <Toast
        v-for="notification in notifications"
        :key="notification.id"
        class="pointer-events-auto"
        :title="notification.title"
        :message="notification.message"
        :type="notification.type"
        :action-label="notification.action?.label"
        @dismiss="dismiss(notification.id)"
        @action="runAction(notification.id)"
      />
    </section>
  </Teleport>
</template>

<script setup lang="ts">
import { storeToRefs } from 'pinia'
import Toast from './Toast.vue'
import { useNotificationsStore } from '../stores/notifications'

const store = useNotificationsStore()
const { notifications } = storeToRefs(store)
const { dismiss, runAction } = store
</script>
