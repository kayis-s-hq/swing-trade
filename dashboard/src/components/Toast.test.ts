import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'
import { mount, VueWrapper } from '@vue/test-utils'
import { nextTick } from 'vue'
import Toast from './Toast.vue'

interface ToastTestProps {
  message: string
  type: 'success' | 'error' | 'warning' | 'info'
  duration: number
}

function mountToast(
  props: ToastTestProps = { message: 'Saved', type: 'success', duration: 4000 }
): VueWrapper {
  return mount(Toast, { props })
}

function findToast(): HTMLElement | null {
  return document.body.querySelector('.fixed.bottom-6.right-6')
}

describe('Toast', () => {
  beforeEach(() => {
    document.body.innerHTML = ''
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('renders message with correct type styling', async () => {
    const wrapper = mountToast({ message: 'Saved', type: 'success', duration: 3000 })
    await nextTick()
    const toast = findToast()
    expect(toast?.textContent).toContain('Saved')
    expect(toast?.querySelector('svg')).toBeTruthy()
    wrapper.unmount()
  })

  it('auto-dismisses after duration', async () => {
    vi.useFakeTimers()
    const wrapper = mountToast({ message: 'Dismiss', type: 'info', duration: 100 })
    await nextTick()
    let toast = findToast()
    expect(toast).toBeTruthy()
    expect(toast?.className).toContain('opacity-100')
    await vi.advanceTimersByTimeAsync(150)
    toast = findToast()
    expect(toast?.className).toContain('opacity-0')
    wrapper.unmount()
  })

  it('shows error type with danger styling', () => {
    const wrapper = mountToast({ message: 'Error!', type: 'error', duration: 3000 })
    const toast = findToast()
    expect(toast?.className).toContain('danger')
    wrapper.unmount()
  })

  it('aria-live set to assertive for errors', () => {
    const wrapper = mountToast({ message: 'Error!', type: 'error', duration: 3000 })
    const toast = findToast() as HTMLElement
    expect(toast.getAttribute('role')).toBe('alert')
    expect(toast.getAttribute('aria-live')).toBe('assertive')
    wrapper.unmount()
  })

  it('aria-live set to polite for non-errors', () => {
    const wrapper = mountToast({ message: 'Info', type: 'info', duration: 3000 })
    const toast = findToast() as HTMLElement
    expect(toast.getAttribute('aria-live')).toBe('polite')
    wrapper.unmount()
  })

  it('teleports to body', () => {
    const wrapper = mountToast({ message: 'Teleport', type: 'success', duration: 3000 })
    expect(document.body.contains(findToast()!)).toBe(true)
    wrapper.unmount()
  })
})
