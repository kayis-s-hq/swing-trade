import { onScopeDispose, ref } from 'vue'
import { defineStore } from 'pinia'

export type NotificationType = 'success' | 'error' | 'warning' | 'info'
export type NotificationPresentation = 'toast' | 'inline'

export interface NotificationAction {
  label: string
  handler: () => void | Promise<void>
}

export interface NotificationInput {
  type: NotificationType
  title?: string
  message: string
  duration?: number | null
  dedupeKey?: string
  action?: NotificationAction
  presentation?: NotificationPresentation
}

export interface Notification extends NotificationInput {
  id: string
  duration: number | null
  presentation: 'toast'
}

let nextNotificationId = 0

export const useNotificationsStore = defineStore('notifications', () => {
  const notifications = ref<Notification[]>([])
  const timers = new Map<string, ReturnType<typeof setTimeout>>()

  function clearTimer(id: string): void {
    const timer = timers.get(id)
    if (timer) clearTimeout(timer)
    timers.delete(id)
  }

  function dismiss(id: string): void {
    clearTimer(id)
    notifications.value = notifications.value.filter((notification) => notification.id !== id)
  }

  function schedule(notification: Notification): void {
    clearTimer(notification.id)
    if (notification.duration === null) return
    timers.set(
      notification.id,
      setTimeout(() => dismiss(notification.id), notification.duration)
    )
  }

  function dedupeIdentity(input: NotificationInput): string {
    return input.dedupeKey ?? `${input.type}:${input.message}`
  }

  function notify(input: NotificationInput): string | null {
    if (input.presentation === 'inline') return null

    const identity = dedupeIdentity(input)
    const duplicateIndex = notifications.value.findIndex(
      (notification) => dedupeIdentity(notification) === identity
    )
    const duration = input.duration === undefined ? 4_000 : input.duration

    if (duplicateIndex >= 0) {
      const existing = notifications.value[duplicateIndex]!
      const updated: Notification = {
        ...existing,
        ...input,
        id: existing.id,
        duration,
        presentation: 'toast',
      }
      notifications.value.splice(duplicateIndex, 1, updated)
      schedule(updated)
      return existing.id
    }

    const notification: Notification = {
      ...input,
      id: `notification-${++nextNotificationId}`,
      duration,
      presentation: 'toast',
    }
    notifications.value.push(notification)
    schedule(notification)
    return notification.id
  }

  async function runAction(id: string): Promise<void> {
    const notification = notifications.value.find((item) => item.id === id)
    if (!notification?.action) return

    await notification.action.handler()
    dismiss(id)
  }

  onScopeDispose(() => {
    timers.forEach((timer) => clearTimeout(timer))
    timers.clear()
  }, true)

  return { notifications, notify, dismiss, runAction }
})
