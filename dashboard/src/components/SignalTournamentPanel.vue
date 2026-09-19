<template>
  <div class="card-panel p-5" aria-label="Signal tournament">
    <div class="mb-3 flex items-center justify-between">
      <div>
        <h3 class="text-sm font-semibold text-text-primary">Signal tournament</h3>
        <p class="mt-0.5 text-xs text-text-muted">
          Highest-confidence BUY across variants drives the selected paper book
          <span v-if="tournamentDate">· signals dated {{ tournamentDate }}</span>
        </p>
      </div>
      <span class="rounded-full bg-brand/10 px-2.5 py-1 text-[11px] font-semibold text-brand">
        {{ selections.length }} winner{{ selections.length === 1 ? '' : 's' }}
      </span>
    </div>

    <p v-if="selections.length === 0" class="text-sm text-text-muted">
      No variant produced a BUY, so no tournament winner was selected.
    </p>

    <div v-else class="space-y-3">
      <div
        v-for="row in selections"
        :key="row.id"
        class="rounded-lg border border-border-subtle p-3"
      >
        <div class="flex flex-wrap items-center justify-between gap-2">
          <div class="flex items-center gap-2">
            <router-link
              :to="`/symbols/${row.symbol}`"
              class="font-semibold text-text-primary hover:underline"
              >{{ row.symbol }}</router-link
            >
            <span class="text-xs text-text-muted">
              winner
              <span class="font-medium text-success">{{ row.winnerVariantId }}</span>
              ({{ formatConfidence(row.winnerConfidence) }})
            </span>
          </div>
          <StatusBadge :status="badgeStatus(row.status)" :label="row.status" />
        </div>

        <div class="mt-2 flex flex-wrap gap-1.5">
          <span
            v-for="candidate in row.candidates"
            :key="candidate.variantId"
            class="inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[11px] font-medium"
            :class="chipClass(candidate)"
            :title="`${candidate.variantId} v${candidate.version}`"
          >
            <span v-if="candidate.selected" aria-label="selected">★</span>
            {{ candidate.variantId }} {{ candidate.signal }}
            <span v-if="candidate.confidence != null">{{
              formatConfidence(candidate.confidence)
            }}</span>
          </span>
        </div>

        <p v-if="row.statusDetail || row.reason" class="mt-2 text-xs text-text-muted">
          {{ row.statusDetail || row.reason }}
        </p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import StatusBadge from './StatusBadge.vue'
import type { SelectionCandidate, SignalSelection } from '../api/selections'

const props = defineProps<{ selections: SignalSelection[] }>()

const tournamentDate = computed(() => props.selections[0]?.selectionDate ?? '')

function formatConfidence(value: number | string | null): string {
  const n = Number(value)
  return Number.isFinite(n) ? `${Math.round(n * 100)}%` : '-'
}

function badgeStatus(status: SignalSelection['status']): string {
  if (status === 'EXECUTED') return 'COMPLETED'
  if (status === 'BLOCKED') return 'CANCELLED'
  return 'PENDING'
}

function chipClass(candidate: SelectionCandidate): string {
  const ring = candidate.selected ? ' ring-1 ring-success' : ''
  if (candidate.signal === 'BUY') return `bg-success-subtle text-success${ring}`
  if (candidate.signal === 'SELL') return `bg-danger-subtle text-danger${ring}`
  return `bg-bg-primary text-text-muted${ring}`
}
</script>
