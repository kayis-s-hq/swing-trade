<template>
  <div
    class="group relative overflow-hidden border border-border-subtle bg-bg-surface transition-all duration-200 hover:border-brand/30"
  >
    <!-- Top accent bar colored by status -->
    <div class="h-[2px] w-full" :class="statusBarColor" />

    <!-- Header: Symbol + Status -->
    <div class="flex items-start justify-between border-b border-border-subtle/50 px-4 py-3">
      <div>
        <h3 class="text-sm font-bold tracking-tight text-text-primary">
          {{ position.symbol }}
        </h3>
        <p class="mt-0.5 text-[10px] text-text-muted">
          {{ position.reason }}
        </p>
      </div>
      <span
        class="inline-flex items-center gap-1.5 rounded-none border px-2 py-0.5 text-[10px] font-medium uppercase tracking-wider"
        :class="statusBadgeClass"
      >
        <span class="h-1 w-1 rounded-none" :class="statusDotColor" />
        {{ position.status }}
      </span>
    </div>

    <!-- Data Grid -->
    <div class="grid grid-cols-2 gap-x-4 gap-y-2.5 px-4 py-3">
      <!-- Entry Price -->
      <div>
        <span class="text-[9px] uppercase tracking-[0.15em] text-text-muted">Entry</span>
        <p class="text-sm font-medium text-text-primary">₹{{ position.entryPrice.toFixed(2) }}</p>
      </div>

      <!-- Current Price -->
      <div>
        <span class="text-[9px] uppercase tracking-[0.15em] text-text-muted">Current</span>
        <p class="text-sm font-medium text-text-primary">₹{{ position.currentPrice.toFixed(2) }}</p>
      </div>

      <!-- Stop Loss -->
      <div>
        <span class="text-[9px] uppercase tracking-[0.15em] text-text-muted">Stop Loss</span>
        <p class="text-sm font-medium text-danger">₹{{ position.stopLoss.toFixed(2) }}</p>
      </div>

      <!-- Target -->
      <div>
        <span class="text-[9px] uppercase tracking-[0.15em] text-text-muted">Target</span>
        <p class="text-sm font-medium text-success">₹{{ position.target.toFixed(2) }}</p>
      </div>
    </div>

    <!-- P&L Footer -->
    <div class="flex items-center justify-between border-t border-border-subtle/50 px-4 py-3">
      <div>
        <span class="text-[9px] uppercase tracking-[0.15em] text-text-muted">Quantity</span>
        <p class="text-sm font-medium text-text-primary">
          {{ position.quantity }}
        </p>
      </div>
      <div class="text-right">
        <span class="text-[9px] uppercase tracking-[0.15em] text-text-muted">P&L</span>
        <p class="text-sm font-bold" :class="position.pnl >= 0 ? 'text-success' : 'text-danger'">
          {{ position.pnl >= 0 ? '+' : '' }}₹{{ position.pnl.toFixed(2) }}
          <span class="text-[10px] font-normal opacity-70">
            ({{ position.pnlPercent >= 0 ? '+' : '' }}{{ position.pnlPercent.toFixed(2) }}%)
          </span>
        </p>
      </div>
    </div>

    <!-- Corner accent -->
    <div class="pointer-events-none absolute -right-[1px] -top-[1px] h-3 w-3">
      <div
        class="absolute right-0 top-0 h-[1px] w-3 bg-brand/20 transition-all group-hover:bg-brand/50"
      />
      <div
        class="absolute right-0 top-0 h-3 w-[1px] bg-brand/20 transition-all group-hover:bg-brand/50"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

export interface PositionCardProps {
  position: {
    symbol: string
    entryPrice: number
    currentPrice: number
    stopLoss: number
    target: number
    quantity: number
    status: string
    pnl: number
    pnlPercent: number
    reason: string
  }
}

const props = defineProps<PositionCardProps>()

const statusBarColor = computed(() => {
  switch (props.position.status) {
    case 'OPEN':
      return 'bg-success'
    case 'STOPPED':
      return 'bg-warning'
    case 'TARGET_HIT':
      return 'bg-brand'
    default:
      return 'bg-text-muted'
  }
})

const statusBadgeClass = computed(() => {
  switch (props.position.status) {
    case 'OPEN':
      return 'border-success/30 bg-success-bg text-success'
    case 'STOPPED':
      return 'border-warning/30 bg-warning-bg text-warning'
    case 'TARGET_HIT':
      return 'border-brand/30 bg-brand-subtle text-brand'
    default:
      return 'border-border-default bg-bg-elevated text-text-muted'
  }
})

const statusDotColor = computed(() => {
  switch (props.position.status) {
    case 'OPEN':
      return 'bg-success'
    case 'STOPPED':
      return 'bg-warning'
    case 'TARGET_HIT':
      return 'bg-brand'
    default:
      return 'bg-text-muted'
  }
})
</script>
