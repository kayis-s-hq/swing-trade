import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent, h, nextTick, onMounted, ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AppError } from '../errors/appError'
import RuntimeErrorBoundary from './RuntimeErrorBoundary.vue'

const runtimeErrorMocks = vi.hoisted(() => ({
  reportRuntimeError: vi.fn(),
}))

vi.mock('../stores/runtimeErrors', () => runtimeErrorMocks)

describe('RuntimeErrorBoundary', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('captures a descendant exception once and replaces it with safe fallback content', async () => {
    const globalErrorHandler = vi.fn()
    const privateMessage = 'Cannot read secret route implementation state'
    const FailingChild = defineComponent({
      name: 'FailingChild',
      setup() {
        throw new Error(privateMessage)
      },
      render: () => h('p', 'must not render'),
    })

    const wrapper = mount(RuntimeErrorBoundary, {
      slots: { default: () => h(FailingChild) },
      global: { config: { errorHandler: globalErrorHandler } },
    })
    await nextTick()

    const fallback = wrapper.get('[role="alert"]')
    expect(fallback.text()).toContain('This page couldn’t be displayed')
    expect(fallback.text()).toContain('Try this page again')
    expect(fallback.text()).toContain('Reload dashboard')
    expect(wrapper.text()).not.toContain(privateMessage)
    expect(wrapper.text()).not.toContain('must not render')
    expect(runtimeErrorMocks.reportRuntimeError).toHaveBeenCalledTimes(1)
    expect(runtimeErrorMocks.reportRuntimeError).toHaveBeenCalledWith(
      expect.any(Error),
      expect.objectContaining({ source: 'boundary' })
    )
    expect(globalErrorHandler).not.toHaveBeenCalled()
  })

  it('does not invoke the failing child slot again while its fallback is active', async () => {
    let renderCount = 0
    const FailingChild = defineComponent({
      props: { nonce: { type: Number, required: true } },
      setup(props) {
        return () => {
          renderCount += 1
          void props.nonce
          throw new Error('render failed')
        }
      },
    })
    const Harness = defineComponent({
      props: { nonce: { type: Number, required: true } },
      setup(props) {
        return () =>
          h(RuntimeErrorBoundary, null, {
            default: () => h(FailingChild, { nonce: props.nonce }),
          })
      },
    })

    const wrapper = mount(Harness, { props: { nonce: 1 } })
    await nextTick()
    expect(renderCount).toBe(1)

    await wrapper.setProps({ nonce: 2 })
    await nextTick()

    expect(wrapper.text()).toContain('This page couldn’t be displayed')
    expect(renderCount).toBe(1)
    expect(runtimeErrorMocks.reportRuntimeError).toHaveBeenCalledTimes(1)
  })

  it('remounts the route content only after the user resets the boundary', async () => {
    const shouldThrow = ref(true)
    let renderCount = 0
    const RecoverableChild = defineComponent({
      setup() {
        return () => {
          renderCount += 1
          if (shouldThrow.value) throw new Error('temporary route failure')
          return h('p', 'Recovered route content')
        }
      },
    })

    const wrapper = mount(RuntimeErrorBoundary, {
      slots: { default: () => h(RecoverableChild) },
    })
    await nextTick()
    expect(renderCount).toBe(1)

    shouldThrow.value = false
    await nextTick()
    expect(renderCount).toBe(1)

    const retryButton = wrapper
      .findAll('button')
      .find((button) => button.text() === 'Try this page again')
    expect(retryButton).toBeDefined()
    await retryButton!.trigger('click')
    await nextTick()

    expect(wrapper.text()).toContain('Recovered route content')
    expect(wrapper.text()).not.toContain('This page couldn’t be displayed')
    expect(renderCount).toBe(2)
  })

  it('leaves handled API failures in local request state instead of treating them as runtime errors', async () => {
    const requestFailure = new AppError({
      kind: 'server',
      message: 'private upstream response',
      status: 503,
    })
    const RequestStateChild = defineComponent({
      setup() {
        const message = ref('Loading positions')
        onMounted(async () => {
          try {
            await Promise.reject(requestFailure)
          } catch {
            message.value = 'Couldn’t load positions. Retry.'
          }
        })
        return () => h('div', { role: 'alert' }, message.value)
      },
    })

    const wrapper = mount(RuntimeErrorBoundary, {
      slots: { default: () => h(RequestStateChild) },
    })
    await flushPromises()

    expect(wrapper.get('[role="alert"]').text()).toBe('Couldn’t load positions. Retry.')
    expect(wrapper.text()).not.toContain('This page couldn’t be displayed')
    expect(runtimeErrorMocks.reportRuntimeError).not.toHaveBeenCalled()
  })
})
