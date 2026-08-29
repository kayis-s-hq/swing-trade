import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import SignalCard from '../../../src/components/SignalCard.vue'

const mockRouter = { push: vi.fn(), resolve: vi.fn() }
vi.mock('vue-router', async () => {
  const actual = await vi.importActual('vue-router')
  return {
    ...actual,
    useRouter: () => mockRouter,
  }
})

const sampleSignal = {
  symbol: 'HDFCBANK',
  direction: 'BUY',
  confidence: 75,
  reason: 'Positive sentiment + breakout above resistance',
  entryPrice: 1450,
  stopLoss: 1380,
  target: 1600,
  riskReward: 2.13,
  status: 'ACTIVE',
}

describe('SignalCard', () => {
  it('renders signal symbol', () => {
    const wrapper = mount(SignalCard, { props: { signal: sampleSignal } })
    expect(wrapper.text()).toContain('HDFCBANK')
  })

  it('renders BUY badge with success color class', () => {
    const wrapper = mount(SignalCard, { props: { signal: sampleSignal } })
    const badge = wrapper.find('span.bg-success-bg')
    expect(badge.exists()).toBe(true)
    expect(badge.text()).toContain('BUY')
  })

  it('renders SELL badge with danger color class', () => {
    const sellSignal = { ...sampleSignal, direction: 'SELL' }
    const wrapper = mount(SignalCard, { props: { signal: sellSignal } })
    const badge = wrapper.find('span.bg-danger-bg')
    expect(badge.exists()).toBe(true)
    expect(badge.text()).toContain('SELL')
  })

  it('renders HOLD badge with info color class', () => {
    const holdSignal = { ...sampleSignal, direction: 'HOLD' }
    const wrapper = mount(SignalCard, { props: { signal: holdSignal } })
    const badge = wrapper.find('span.bg-info-bg')
    expect(badge.exists()).toBe(true)
    expect(badge.text()).toContain('HOLD')
  })

  it('displays confidence with percentage bar', () => {
    const wrapper = mount(SignalCard, { props: { signal: sampleSignal } })
    expect(wrapper.text()).toContain('75%')
    expect(wrapper.text()).toContain('Confidence')
    const bar = wrapper.find('div.bg-brand')
    expect(bar.exists()).toBe(true)
  })

  it('renders entry, stop loss, and target prices with rupee symbol', () => {
    const wrapper = mount(SignalCard, { props: { signal: sampleSignal } })
    expect(wrapper.text()).toContain('₹1450')
    expect(wrapper.text()).toContain('₹1380')
    expect(wrapper.text()).toContain('₹1600')
  })

  it('shows dash for missing prices', () => {
    const partialSignal = { ...sampleSignal, entryPrice: 0, stopLoss: 0, target: 0 }
    const wrapper = mount(SignalCard, { props: { signal: partialSignal } })
    expect(wrapper.text()).toContain('—')
  })

  it('displays reasoning text', () => {
    const wrapper = mount(SignalCard, { props: { signal: sampleSignal } })
    expect(wrapper.text()).toContain('Positive sentiment + breakout above resistance')
  })

  it('handles empty reasoning gracefully', () => {
    const noReasoningSignal = { ...sampleSignal, reason: '' }
    const wrapper = mount(SignalCard, { props: { signal: noReasoningSignal } })
    expect(wrapper.find('.border-t.px-4.py-3').exists()).toBe(true)
  })

  it('renders risk:reward ratio', () => {
    const wrapper = mount(SignalCard, { props: { signal: sampleSignal } })
    expect(wrapper.text()).toContain('2.13')
  })

  it('formats risk:reward as 1:X ratio', () => {
    const wrapper = mount(SignalCard, { props: { signal: sampleSignal } })
    expect(wrapper.text()).toContain('1:2.13')
  })

  it('shows dash for missing risk:reward', () => {
    const noRR = { ...sampleSignal, riskReward: 0 }
    const wrapper = mount(SignalCard, { props: { signal: noRR } })
    expect(wrapper.text()).toContain('—')
  })

  it('colors risk:reward based on threshold (>=2 is brand color)', () => {
    const goodRR = { ...sampleSignal, riskReward: 2.5 }
    const wrapper = mount(SignalCard, { props: { signal: goodRR } })
    expect(wrapper.find('span.text-brand').exists()).toBe(true)
  })

  it('colors risk:reward differently when below 2', () => {
    const badRR = { ...sampleSignal, riskReward: 1.5 }
    const wrapper = mount(SignalCard, { props: { signal: badRR } })
    expect(wrapper.find('span.text-brand').exists()).toBe(false)
  })

  it('renders status with correct color for ACTIVE', () => {
    const wrapper = mount(SignalCard, { props: { signal: sampleSignal } })
    expect(wrapper.text()).toContain('ACTIVE')
    expect(wrapper.find('span.text-success').exists()).toBe(true)
  })

  it('renders status with warning color for PENDING', () => {
    const pending = { ...sampleSignal, status: 'PENDING' }
    const wrapper = mount(SignalCard, { props: { signal: pending } })
    expect(wrapper.text()).toContain('PENDING')
    expect(wrapper.find('span.text-warning').exists()).toBe(true)
  })

  it('renders status with info color for EXECUTED', () => {
    const executed = { ...sampleSignal, status: 'EXECUTED' }
    const wrapper = mount(SignalCard, { props: { signal: executed } })
    expect(wrapper.text()).toContain('EXECUTED')
    expect(wrapper.find('span.text-info').exists()).toBe(true)
  })

  it('renders status with muted color for EXPIRED', () => {
    const expired = { ...sampleSignal, status: 'EXPIRED' }
    const wrapper = mount(SignalCard, { props: { signal: expired } })
    expect(wrapper.text()).toContain('EXPIRED')
    expect(wrapper.find('span.text-text-muted').exists()).toBe(true)
  })

  it('renders strategy label', () => {
    const strategySignal = { ...sampleSignal, strategy: 'PRICE_ACTION' }
    const wrapper = mount(SignalCard, { props: { signal: strategySignal } })
    expect(wrapper.text()).toContain('Price Action')
    expect(wrapper.find('span.bg-brand-subtle').exists()).toBe(true)
  })

  it('renders default strategy label for unknown strategy', () => {
    const unknownStrategy = { ...sampleSignal, strategy: 'CUSTOM_STRATEGY' }
    const wrapper = mount(SignalCard, { props: { signal: unknownStrategy } })
    expect(wrapper.text()).toContain('CUSTOM_STRATEGY')
  })

  it('renders sentiment badge for POSITIVE', () => {
    const posSignal = { ...sampleSignal, sentimentScore: 'POSITIVE' }
    const wrapper = mount(SignalCard, { props: { signal: posSignal } })
    expect(wrapper.text()).toContain('POS')
    expect(wrapper.find('span.bg-success-bg').exists()).toBe(true)
  })

  it('renders sentiment badge for NEGATIVE', () => {
    const negSignal = { ...sampleSignal, sentimentScore: 'NEGATIVE' }
    const wrapper = mount(SignalCard, { props: { signal: negSignal } })
    expect(wrapper.text()).toContain('NEG')
    expect(wrapper.find('span.bg-danger-bg').exists()).toBe(true)
  })

  it('renders sentiment badge for NEUTRAL', () => {
    const neutralSignal = { ...sampleSignal, sentimentScore: 'NEUTRAL' }
    const wrapper = mount(SignalCard, { props: { signal: neutralSignal } })
    expect(wrapper.text()).toContain('NEUTRAL')
    expect(wrapper.find('span.bg-info-bg').exists()).toBe(true)
  })

  it('renders sentiment badge for UNKNOWN', () => {
    const unknownSignal = { ...sampleSignal, sentimentScore: 'UNKNOWN' }
    const wrapper = mount(SignalCard, { props: { signal: unknownSignal } })
    expect(wrapper.text()).toContain('UNK')
    expect(wrapper.find('span.bg-warning-bg').exists()).toBe(true)
  })

  it('does not render sentiment badge when score is missing', () => {
    const noSentiment = { ...sampleSignal, sentimentScore: undefined }
    const wrapper = mount(SignalCard, { props: { signal: noSentiment } })
    // The badge span should not exist
    const badges = wrapper.findAll('span.rounded-full.px-2')
    const sentimentBadges = badges.filter((b) => b.text().match(/^(POS|NEG|NEUTRAL|UNK)$/))
    expect(sentimentBadges.length).toBe(0)
  })

  it('renders sentiment reasoning section', () => {
    const signal = { ...sampleSignal, sentimentReasoning: 'Market sentiment is bullish' }
    const wrapper = mount(SignalCard, { props: { signal } })
    expect(wrapper.text()).toContain('Sentiment')
    expect(wrapper.text()).toContain('Market sentiment is bullish')
  })

  it('does not render sentiment section when reasoning is missing', () => {
    const noReasoning = { ...sampleSignal, sentimentReasoning: undefined }
    const wrapper = mount(SignalCard, { props: { signal: noReasoning } })
    expect(wrapper.text()).not.toContain('Sentiment')
  })

  it('renders indicators when present', () => {
    const indicators = ['RSI', 'MACD', 'Volume']
    const signal = { ...sampleSignal, indicators }
    const wrapper = mount(SignalCard, { props: { signal } })
    for (const indicator of indicators) {
      expect(wrapper.text()).toContain(indicator)
    }
  })

  it('does not render indicators section when empty', () => {
    const noIndicators = { ...sampleSignal, indicators: [] }
    const wrapper = mount(SignalCard, { props: { signal: noIndicators } })
    // No indicator badge spans should exist when indicators is empty
    const indicatorSpans = wrapper.findAll('span.rounded')
    // Filter to only those that would be indicator badges (contain indicator-like text)
    const indicatorTexts = indicatorSpans.map((s) => s.text())
    expect(indicatorTexts.some((t) => ['RSI', 'MACD', 'Volume'].includes(t))).toBe(false)
  })

  it('does not render indicators section when undefined', () => {
    const signal = { ...sampleSignal, indicators: undefined }
    const wrapper = mount(SignalCard, { props: { signal } })
    const indicatorTexts = wrapper.findAll('span.rounded').map((s) => s.text())
    expect(indicatorTexts.some((t) => ['RSI', 'MACD', 'Volume'].includes(t))).toBe(false)
  })

  it('clicking card navigates to Sentiment view', async () => {
    const wrapper = mount(SignalCard, {
      props: { signal: sampleSignal },
    })
    await wrapper.trigger('click')
    expect(mockRouter.push).toHaveBeenCalledWith({
      name: 'Sentiment',
      query: { symbol: 'HDFCBANK' },
    })
  })

  it('has card-panel styling class', () => {
    const wrapper = mount(SignalCard, { props: { signal: sampleSignal } })
    expect(wrapper.classes()).toContain('card-panel')
  })

  it('has transition and hover classes', () => {
    const wrapper = mount(SignalCard, { props: { signal: sampleSignal } })
    expect(wrapper.classes()).toContain('transition-all')
    expect(wrapper.classes()).toContain('hover:border-border-default')
  })

  it('renders all price labels', () => {
    const wrapper = mount(SignalCard, { props: { signal: sampleSignal } })
    expect(wrapper.text()).toContain('Entry')
    expect(wrapper.text()).toContain('Stop Loss')
    expect(wrapper.text()).toContain('Target')
  })

  it('renders risk:reward label', () => {
    const wrapper = mount(SignalCard, { props: { signal: sampleSignal } })
    expect(wrapper.text()).toContain('Risk:Reward')
  })

  it('click hint is present', () => {
    const wrapper = mount(SignalCard, { props: { signal: sampleSignal } })
    expect(wrapper.text()).toContain('Click for full sentiment analysis')
  })
})
