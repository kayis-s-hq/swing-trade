import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import MetricCard from '../../../src/components/MetricCard.vue'

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

  it('renders trend indicator when provided', () => {
    const wrapper = mount(MetricCard, {
      props: {
        title: 'Test',
        value: '123',
        trend: { value: '5%', isPositive: true },
      },
    })
    expect(wrapper.text()).toContain('▲ 5%')
  })

  it('renders downward trend when negative', () => {
    const wrapper = mount(MetricCard, {
      props: {
        title: 'Test',
        value: '123',
        trend: { value: '3%', isPositive: false },
      },
    })
    expect(wrapper.text()).toContain('▼ 3%')
  })

  it('hides trend when trend is null', () => {
    const wrapper = mount(MetricCard, {
      props: { title: 'Test', value: '123', trend: undefined },
    })
    expect(wrapper.text()).not.toContain('+')
  })

  it('has border-r class for panel layout', () => {
    const wrapper = mount(MetricCard, {
      props: { title: 'Test', value: '123' },
    })
    expect(wrapper.classes()).toContain('border-r')
  })

  it('hides trend div when trend is undefined', () => {
    const wrapper = mount(MetricCard, {
      props: { title: 'Test', value: '123', trend: undefined },
    })
    expect(wrapper.find('.mt-0\\.5.flex').exists()).toBe(false)
  })
})
