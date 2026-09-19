import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import VariantSignalChips from './VariantSignalChips.vue'

describe('VariantSignalChips', () => {
  it('shows confidence as an already-percent value and stars the winner', () => {
    const wrapper = mount(VariantSignalChips, {
      props: {
        chips: [
          { variantId: 'pullback-v1', direction: 'BUY', confidence: 75, selected: true },
          { variantId: 'legacy', direction: 'SELL', confidence: 100, selected: false },
        ],
      },
    })
    expect(wrapper.text()).toContain('pullback-v1 BUY 75%')
    expect(wrapper.text()).toContain('legacy SELL 100%')
    expect(wrapper.findAll('[aria-label="tournament winner"]')).toHaveLength(1)
  })
})
