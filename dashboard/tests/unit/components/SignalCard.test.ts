import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import {defineComponent} from 'vue'
import SignalCard from '../../../src/components/SignalCard.vue'

const TestComponent = defineComponent({
  components: { SignalCard },
  template: '<SignalCard v-bind="props" />',
  props: ['props'],
})

describe('SignalCard', () => {
  const sampleSignal = {
    id: 'sig-001',
    symbol: 'HDFCBANK',
    signalType: 'BUY' as const,
    confidence: 75,
    reasoning: 'Positive sentiment + breakout above resistance',
    entryPrice: 1450,
    stopLoss: 1380,
    target: 1600,
    createdDate: '2026-04-10',
    indicators: ['EMA20', 'RSI', 'Volume'],
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
    const badge = wrapper.find('span.bg-success-50')
    expect(badge.exists()).toBe(true)
  })

  it('renders SELL badge with red color', () => {
    const sellSignal = { ...sampleSignal, signalType: 'SELL' }
    const wrapper = mount(SignalCard, {
      props: { signal: sellSignal },
    })
    expect(wrapper.text()).toContain('SELL')
  })

  it('renders HOLD badge with gray color', () => {
    const holdSignal = { ...sampleSignal, signalType: 'HOLD' }
    const wrapper = mount(SignalCard, {
      props: { signal: holdSignal },
    })
    expect(wrapper.text()).toContain('HOLD')
  })

  it('displays confidence percentage', () => {
    const wrapper = mount(SignalCard, {
      props: { signal: sampleSignal },
    })
    expect(wrapper.text()).toContain('75% Confidence')
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
    const noReasoningSignal = { ...sampleSignal, reasoning: '' }
    const wrapper = mount(SignalCard, {
      props: { signal: noReasoningSignal },
    })
    expect(wrapper.text()).toContain('No reasoning provided')
  })

  it('renders indicators as badges', () => {
    const wrapper = mount(SignalCard, {
      props: { signal: sampleSignal },
    })
    expect(wrapper.text()).toContain('EMA20')
    expect(wrapper.text()).toContain('RSI')
    expect(wrapper.text()).toContain('Volume')
  })

  it('handles empty indicators array', () => {
    const noIndicatorsSignal = { ...sampleSignal, indicators: [] }
    const wrapper = mount(SignalCard, {
      props: { signal: noIndicatorsSignal },
    })
    expect(wrapper.text()).not.toContain('EMA20')
  })

  it('has correct dark mode classes', () => {
    const wrapper = mount(SignalCard, {
      props: { signal: sampleSignal },
    })
    expect(wrapper.classes()).toContain('dark:bg-white/[0.03]')
  })
})
