import { afterEach, describe, expect, it } from 'vitest'
import { mount, type VueWrapper } from '@vue/test-utils'
import { nextTick } from 'vue'
import type { FormattedErrorDetail } from '../errors/appError'
import ErrorMessage from './ErrorMessage.vue'

interface ErrorMessageProps {
  title: string
  message: string
  busy?: boolean
  details?: FormattedErrorDetail[]
  actionLabel?: string
  focusOnMount?: boolean
}

const wrappers: VueWrapper[] = []

function mountErrorMessage(
  props: ErrorMessageProps = {
    title: 'Couldn’t load positions',
    message: 'Can’t reach the backend. Check the service, then retry.',
  }
) {
  const wrapper = mount(ErrorMessage, {
    attachTo: document.body,
    props,
  })
  wrappers.push(wrapper)
  return wrapper
}

afterEach(() => {
  wrappers.splice(0).forEach((wrapper) => wrapper.unmount())
  document.body.innerHTML = ''
})

describe('ErrorMessage', () => {
  describe('accessible failure content', () => {
    it('renders a title and message in an alert with explicit busy state', () => {
      const wrapper = mountErrorMessage({
        title: 'Couldn’t load positions',
        message: 'Can’t reach the backend. Check the service, then retry.',
        busy: false,
      })

      const alert = wrapper.get('[role="alert"]')
      expect(alert.attributes('aria-busy')).toBe('false')
      expect(alert.text()).toContain('Couldn’t load positions')
      expect(alert.text()).toContain('Can’t reach the backend. Check the service, then retry.')
    })

    it('exposes optional safe diagnostic details without replacing the primary message', () => {
      const wrapper = mountErrorMessage({
        title: 'Couldn’t load portfolio',
        message: 'The backend returned an unexpected response.',
        details: [
          { label: 'Status', value: '503' },
          { label: 'Code', value: 'UPSTREAM_FAILURE' },
          { label: 'Correlation ID', value: 'corr-safe-7' },
        ],
      })

      const details = wrapper.get('details')
      expect(details.get('summary').text()).toMatch(/details/i)
      expect(details.text()).toContain('Status')
      expect(details.text()).toContain('503')
      expect(details.text()).toContain('Code')
      expect(details.text()).toContain('UPSTREAM_FAILURE')
      expect(details.text()).toContain('Correlation ID')
      expect(details.text()).toContain('corr-safe-7')
      expect(wrapper.get('[role="alert"]').text()).toContain(
        'The backend returned an unexpected response.'
      )
    })
  })

  describe('contextual actions', () => {
    it('emits the contextual action instead of hard-coding retry behavior', async () => {
      const wrapper = mountErrorMessage({
        title: 'Order status couldn’t be confirmed',
        message: 'Refresh positions before placing another order.',
        actionLabel: 'Refresh positions',
      })

      await wrapper.get('button').trigger('click')

      expect(wrapper.get('button').text()).toBe('Refresh positions')
      expect(wrapper.emitted('action')).toHaveLength(1)
      expect(wrapper.emitted('retry')).toBeUndefined()
    })

    it('announces busy state and disables its action while recovery is running', () => {
      const wrapper = mountErrorMessage({
        title: 'Couldn’t refresh signals',
        message: 'The request took too long. Try again.',
        actionLabel: 'Retry',
        busy: true,
      })

      expect(wrapper.get('[role="alert"]').attributes('aria-busy')).toBe('true')
      expect(wrapper.get('button').attributes()).toHaveProperty('disabled')
    })
  })

  describe('optional focus management', () => {
    it('moves focus to a blocking error when requested', async () => {
      const previousControl = document.createElement('button')
      document.body.appendChild(previousControl)
      previousControl.focus()

      const wrapper = mountErrorMessage({
        title: 'Position couldn’t be created',
        message: 'Correct the highlighted values and try again.',
        focusOnMount: true,
      })
      await nextTick()

      const alert = wrapper.get('[role="alert"]')
      expect(alert.attributes('tabindex')).toBe('-1')
      expect(document.activeElement).toBe(alert.element)
    })

    it('does not steal focus for non-blocking refresh failures', async () => {
      const previousControl = document.createElement('button')
      document.body.appendChild(previousControl)
      previousControl.focus()

      mountErrorMessage({
        title: 'Refresh failed',
        message: 'The last successful values are still shown.',
        focusOnMount: false,
      })
      await nextTick()

      expect(document.activeElement).toBe(previousControl)
    })
  })
})
