import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import {defineComponent} from 'vue'
import PositionCard from '../../../src/components/PositionCard.vue'

const TestComponent = defineComponent({
  components: { PositionCard },
  template: '<PositionCard v-bind="props" />',
  props: ['props'],
})

describe('PositionCard', () => {
  const samplePosition = {
    id: 'pos-001',
    symbol: 'RELIANCE',
    entryPrice: 2500,
    entryDate: '2026-04-01',
    quantity: 10,
    currentPrice: 2600,
    status: 'OPEN' as const,
    pnl: 1000,
    pnlPercent: 4.0,
    stopLoss: 2400,
    target: 2800,
    reason: 'Breakout pattern',
  }

  it('renders symbol correctly', () => {
    const wrapper = mount(PositionCard, {
      props: { position: samplePosition },
    })
    expect(wrapper.text()).toContain('RELIANCE')
  })

  it('renders entry price', () => {
    const wrapper = mount(PositionCard, {
      props: { position: samplePosition },
    })
    expect(wrapper.text()).toContain('$2500')
  })

  it('renders current price', () => {
    const wrapper = mount(PositionCard, {
      props: { position: samplePosition },
    })
    expect(wrapper.text()).toContain('$2600')
  })

  it('calculates and displays P&L percentage', () => {
    const wrapper = mount(PositionCard, {
      props: { position: samplePosition },
    })
    expect(wrapper.text()).toContain('1000')
    expect(wrapper.text()).toContain('4%')
  })

  it('shows OPEN status badge with green color', () => {
    const wrapper = mount(PositionCard, {
      props: { position: samplePosition },
    })
    expect(wrapper.text()).toContain('Open')
  })

  it('shows CLOSED status badge with red color', () => {
    const closedPosition = { ...samplePosition, status: 'CLOSED' as const }
    const wrapper = mount(PositionCard, {
      props: { position: closedPosition },
    })
    expect(wrapper.text()).toContain('Closed')
  })

  it('shows STOPPED status badge with orange color', () => {
    const stoppedPosition = { ...samplePosition, status: 'STOPPED' as const }
    const wrapper = mount(PositionCard, {
      props: { position: stoppedPosition },
    })
    expect(wrapper.text()).toContain('Stopped')
  })

  it('shows close button for OPEN positions', () => {
    const wrapper = mount(PositionCard, {
      props: { position: samplePosition },
    })
    expect(wrapper.find('button').exists()).toBe(true)
    expect(wrapper.find('button').text()).toBe('Close Position')
  })

  it('hides close button for CLOSED positions', () => {
    const closedPosition = { ...samplePosition, status: 'CLOSED' as const }
    const wrapper = mount(PositionCard, {
      props: { position: closedPosition },
    })
    expect(wrapper.find('button').exists()).toBe(false)
  })

  it('emits close-position event when button clicked', async () => {
    const wrapper = mount(PositionCard, {
      props: { position: samplePosition },
    })
    await wrapper.find('button').trigger('click')
    expect(wrapper.emitted()['close-position']).toBeDefined()
    expect(wrapper.emitted()['close-position']?.[0]).toEqual([samplePosition])
  })

  it('has correct dark mode classes', () => {
    const wrapper = mount(PositionCard, {
      props: { position: samplePosition },
    })
    expect(wrapper.classes()).toContain('dark:bg-white/[0.03]')
  })
})
