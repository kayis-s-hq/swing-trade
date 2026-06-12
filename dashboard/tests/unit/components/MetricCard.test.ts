import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import {defineComponent} from 'vue'
import MetricCard from '../../../src/components/MetricCard.vue'

// Create a test component that uses MetricCard
const TestComponent = defineComponent({
  components: { MetricCard },
  template: '<MetricCard v-bind="props" />',
  props: ['props'],
})

describe('MetricCard', () => {
  it('renders title correctly', () => {
    const wrapper = mount(MetricCard, {
      props: { title: 'Test Metric', value: '123' },
    })
    expect(wrapper.text()).toContain('Test Metric')
  })

  it('renders value correctly', () => {
    const wrapper = mount(MetricCard, {
      props: { title: 'Revenue', value: '$10,000' },
    })
    expect(wrapper.text()).toContain('$10,000')
  })

  it('renders icon when provided', () => {
    const wrapper = mount(MetricCard, {
      props: {
        title: 'Test',
        value: '123',
        icon: 'M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2m-3 7h3m-3 4h3m-6-4h.01M9 16h.01',
      },
    })
    expect(wrapper.find('svg').exists()).toBe(true)
  })

  it('renders trend indicator when provided', () => {
    const wrapper = mount(MetricCard, {
      props: {
        title: 'Test',
        value: '123',
        trend: { value: '5%', isPositive: true },
      },
    })
    expect(wrapper.text()).toContain('+5%')
  })

  it('hides trend when trend is null', () => {
    const wrapper = mount(MetricCard, {
      props: { title: 'Test', value: '123', trend: undefined },
    })
    expect(wrapper.text()).not.toContain('+')
  })

  it('has correct dark mode classes', () => {
    const wrapper = mount(MetricCard, {
      props: { title: 'Test', value: '123' },
    })
    expect(wrapper.classes()).toContain('dark:bg-white/[0.03]')
  })

  it('shows loading state when loading prop is true', () => {
    const wrapper = mount(MetricCard, {
      props: { title: 'Test', value: '123', loading: true },
    })
    expect(wrapper.find('.animate-pulse').exists()).toBe(true)
  })
})
