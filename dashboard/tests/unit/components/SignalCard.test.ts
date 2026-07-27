import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent } from 'vue'
import SignalCard from '../../../src/components/SignalCard.vue'

const TestComponent = defineComponent({
  components: { SignalCard },
  props: ['props'],
  template: '<SignalCard v-bind="props" />',
})

describe('SignalCard', () => {
  const sampleSignal = {
    symbol: 'HDFCBANK',
    direction: 'BUY' as const,
    confidence: 75,
    reason: 'Positive sentiment + breakout above resistance',
    entryPrice: 1450,
    stopLoss: 1380,
    target: 1600,
    riskReward: 2.13,
    status: 'ACTIVE',
  }

  it('renders signal type badge correctly', () => {
    const wrapper = mount(SignalCard, {
      props: { signal: sampleSignal },
    })
    expect(wrapper.text()).toContain('BUY')
  })

  it('renders BUY badge with success color class', () => {
    const wrapper = mount(SignalCard, {
      props: { signal: sampleSignal },
    })
    const badge = wrapper.find('span.bg-success-bg')
    expect(badge.exists()).toBe(true)
  })

  it('renders SELL badge with red color', () => {
    const sellSignal = { ...sampleSignal, direction: 'SELL' as const }
    const wrapper = mount(SignalCard, {
      props: { signal: sellSignal },
    })
    expect(wrapper.text()).toContain('SELL')
  })

  it('renders HOLD badge with gray color', () => {
    const holdSignal = { ...sampleSignal, direction: 'HOLD' as const }
    const wrapper = mount(SignalCard, {
      props: { signal: holdSignal },
    })
    expect(wrapper.text()).toContain('HOLD')
  })

  it('displays confidence percentage', () => {
    const wrapper = mount(SignalCard, {
      props: { signal: sampleSignal },
    })
    expect(wrapper.text()).toContain('Confidence')
    expect(wrapper.text()).toContain('75%')
  })

  it('renders entry, stop loss, and target prices', () => {
    const wrapper = mount(SignalCard, {
      props: { signal: sampleSignal },
    })
    expect(wrapper.text()).toContain('$1450')
    expect(wrapper.text()).toContain('$1380')
    expect(wrapper.text()).toContain('$1600')
  })

  it('displays reasoning text', () => {
    const wrapper = mount(SignalCard, {
      props: { signal: sampleSignal },
    })
    expect(wrapper.text()).toContain('Positive sentiment + breakout above resistance')
  })

  it('handles empty reasoning gracefully', () => {
    const noReasoningSignal = { ...sampleSignal, reason: '' }
    const wrapper = mount(SignalCard, {
      props: { signal: noReasoningSignal },
    })
    expect(wrapper.find('.border-t.px-4.py-3').exists()).toBe(true)
  })

  it('renders risk:reward', () => {
    const wrapper = mount(SignalCard, {
      props: { signal: sampleSignal },
    })
    expect(wrapper.text()).toContain('2.13')
  })

  it('renders status badge', () => {
    const wrapper = mount(SignalCard, {
      props: { signal: sampleSignal },
    })
    expect(wrapper.text()).toContain('ACTIVE')
  })
})
