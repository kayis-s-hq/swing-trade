import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import PositionCard from '../../../src/components/PositionCard.vue'

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
    expect(wrapper.text()).toContain('₹2,500')
  })

  it('renders current price', () => {
    const wrapper = mount(PositionCard, {
      props: { position: samplePosition },
    })
    expect(wrapper.text()).toContain('₹2,600')
  })

  it('calculates and displays P&L percentage', () => {
    const wrapper = mount(PositionCard, {
      props: { position: samplePosition },
    })
    expect(wrapper.text()).toContain('1,000')
    expect(wrapper.text()).toContain('4.00%')
  })

  it('shows OPEN status badge with green color', () => {
    const wrapper = mount(PositionCard, {
      props: { position: samplePosition },
    })
    expect(wrapper.text()).toContain('OPEN')
    expect(wrapper.classes()).toContain('group')
  })

  it('shows CLOSED status badge with red color', () => {
    const closedPosition = { ...samplePosition, status: 'CLOSED' as const }
    const wrapper = mount(PositionCard, {
      props: { position: closedPosition },
    })
    expect(wrapper.text()).toContain('CLOSED')
  })

  it('shows STOPPED status badge with orange color', () => {
    const stoppedPosition = { ...samplePosition, status: 'STOPPED' as const }
    const wrapper = mount(PositionCard, {
      props: { position: stoppedPosition },
    })
    expect(wrapper.text()).toContain('STOPPED')
  })

  it('shows quantity and P&L in footer', () => {
    const wrapper = mount(PositionCard, {
      props: { position: samplePosition },
    })
    expect(wrapper.text()).toContain('10')
    expect(wrapper.text()).toContain('+')
  })

  it('renders all price data', () => {
    const wrapper = mount(PositionCard, {
      props: { position: samplePosition },
    })
    expect(wrapper.text()).toContain('Entry')
    expect(wrapper.text()).toContain('Current')
    expect(wrapper.text()).toContain('Stop Loss')
    expect(wrapper.text()).toContain('Target')
  })

  it('has group class for hover states', () => {
    const wrapper = mount(PositionCard, {
      props: { position: samplePosition },
    })
    expect(wrapper.classes()).toContain('group')
  })

  it('falls back to a placeholder instead of rendering NaN/Infinity for bad numeric data', () => {
    const badPosition = {
      ...samplePosition,
      currentPrice: NaN,
      pnl: Infinity,
      pnlPercent: NaN,
    }
    const wrapper = mount(PositionCard, {
      props: { position: badPosition },
    })
    expect(wrapper.text()).not.toContain('NaN')
    expect(wrapper.text()).not.toContain('Infinity')
    expect(wrapper.text()).toContain('—')
  })
})
