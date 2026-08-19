<template>
  <div class="p-6 animate-fade-in">
    <!-- Page Header -->
    <div class="mb-6 flex items-center justify-between">
      <div>
        <h1 class="font-display text-2xl font-semibold text-text-primary">Positions</h1>
        <p class="mt-1 text-sm text-text-muted">Active and closed paper trading positions</p>
      </div>
      <div class="flex items-center gap-3">
        <button
          class="flex items-center gap-2 rounded-md border border-border-subtle bg-bg-surface px-3 py-2 text-sm font-medium text-text-muted transition-colors hover:border-border-default hover:text-text-primary"
          @click="refreshPositions"
        >
          <svg
            class="h-4 w-4 transition-transform duration-300 hover:rotate-180"
            fill="none"
            stroke="currentColor"
            viewBox="0 0 24 24"
          >
            <path
              stroke-linecap="round"
              stroke-linejoin="round"
              stroke-width="2"
              d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15"
            />
          </svg>
          Refresh
        </button>
        <button
          class="flex items-center gap-2 rounded-md bg-brand px-3 py-2 text-sm font-medium text-white transition-colors hover:bg-brand/90"
          @click="showNewPositionModal = true"
        >
          <svg class="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path
              stroke-linecap="round"
              stroke-linejoin="round"
              stroke-width="2"
              d="M12 4v16m8-8H4"
            />
          </svg>
          New Position
        </button>
      </div>
    </div>

    <ErrorBoundary>
      <template #error>
        <div class="flex flex-col items-center justify-center py-20">
          <p class="text-sm text-danger">{{ errorMessage }}</p>
          <button
            class="mt-2 rounded-md bg-brand px-3 py-1.5 text-xs font-medium text-white"
            @click="refreshPositions"
          >
            Retry
          </button>
        </div>
      </template>
      <div v-if="loading" class="flex items-center justify-center py-20">
        <LoadingSpinner message="Loading positions..." />
      </div>

      <template v-else>
        <!-- Filters -->
        <div class="mb-4 flex items-center gap-3">
          <input
            v-model="searchQuery"
            placeholder="Search symbol..."
            class="w-56 rounded-md border border-border-subtle bg-bg-surface px-3 py-2 text-sm text-text-primary placeholder:text-text-muted/60 transition-colors focus:border-brand/50 focus:outline-none"
          />
          <div class="flex rounded-md border border-border-subtle">
            <button
              v-for="filter in ['ALL', 'OPEN', 'CLOSED']"
              :key="filter"
              class="px-3 py-1.5 text-xs font-medium transition-colors first:rounded-l-md last:rounded-r-md"
              :class="
                statusFilter === filter
                  ? 'bg-brand-subtle text-brand'
                  : 'text-text-muted hover:bg-bg-hover'
              "
              @click="statusFilter = filter"
            >
              {{ filter }}
            </button>
          </div>
        </div>

        <!-- Table -->
        <div class="card-panel">
          <div class="w-full overflow-x-auto">
            <table class="min-w-full">
              <thead>
                <tr class="border-b border-border-subtle bg-bg-primary/50">
                  <th
                    class="px-5 py-3 text-left text-xs font-semibold uppercase tracking-wider text-text-muted"
                  >
                    Symbol
                  </th>
                  <th
                    class="px-5 py-3 text-left text-xs font-semibold uppercase tracking-wider text-text-muted"
                  >
                    Entry
                  </th>
                  <th
                    class="px-5 py-3 text-right text-xs font-semibold uppercase tracking-wider text-text-muted"
                  >
                    Qty
                  </th>
                  <th
                    class="px-5 py-3 text-right text-xs font-semibold uppercase tracking-wider text-text-muted"
                  >
                    Current
                  </th>
                  <th
                    class="px-5 py-3 text-right text-xs font-semibold uppercase tracking-wider text-text-muted"
                  >
                    Stop Loss
                  </th>
                  <th
                    class="px-5 py-3 text-right text-xs font-semibold uppercase tracking-wider text-text-muted"
                  >
                    Target
                  </th>
                  <th
                    class="px-5 py-3 text-left text-xs font-semibold uppercase tracking-wider text-text-muted"
                  >
                    Status
                  </th>
                  <th
                    class="px-5 py-3 text-right text-xs font-semibold uppercase tracking-wider text-text-muted"
                  >
                    P&L
                  </th>
                  <th
                    class="px-5 py-3 text-center text-xs font-semibold uppercase tracking-wider text-text-muted"
                  >
                    Action
                  </th>
                </tr>
              </thead>
              <tbody class="divide-y divide-border-subtle/50">
                <tr
                  v-for="pos in filteredPositions"
                  :key="pos.id"
                  class="transition-colors hover:bg-bg-hover"
                >
                  <td class="px-5 py-4 text-sm font-semibold text-text-primary">
                    {{ pos.symbol }}
                  </td>
                  <td class="px-5 py-4 text-sm text-text-secondary">₹{{ pos.entryPrice }}</td>
                  <td class="px-5 py-4 text-right text-sm text-text-secondary">
                    {{ pos.quantity }}
                  </td>
                  <td class="px-5 py-4 text-right text-sm text-text-secondary">
                    ₹{{ pos.currentPrice }}
                  </td>
                  <td class="px-5 py-4 text-right text-sm text-danger">
                    {{ pos.stopLoss ?? '—' }}
                  </td>
                  <td class="px-5 py-4 text-right text-sm text-success">
                    {{ pos.target ?? '—' }}
                  </td>
                  <td class="px-5 py-4">
                    <span
                      class="inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium"
                      :class="
                        pos.status === 'OPEN'
                          ? 'bg-success-bg text-success'
                          : 'bg-danger-bg text-danger'
                      "
                      >{{ pos.status }}</span
                    >
                  </td>
                  <td
                    class="px-5 py-4 text-right text-sm font-semibold"
                    :class="pos.pnl >= 0 ? 'text-success' : 'text-danger'"
                  >
                    {{ pos.pnl >= 0 ? '+' : '' }}₹{{ pos.pnl }}
                    <span class="ml-1 text-xs font-normal opacity-70"
                      >({{ pos.pnlPercent >= 0 ? '+' : '' }}{{ pos.pnlPercent.toFixed(2) }}%)</span
                    >
                  </td>
                  <td class="px-5 py-4 text-center">
                    <button
                      v-if="pos.status === 'OPEN'"
                      class="rounded-md border border-danger/30 px-2.5 py-1 text-xs font-medium text-danger transition-colors hover:bg-danger/10"
                      @click="showCloseModal(pos)"
                    >
                      Close
                    </button>
                    <span v-else class="text-xs text-text-muted">—</span>
                  </td>
                </tr>
                <tr v-if="filteredPositions.length === 0">
                  <td colspan="9" class="px-5 py-12 text-center text-sm text-text-muted">
                    No positions found
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </template>
    </ErrorBoundary>
  </div>

  <!-- New Position Modal -->
  <Teleport to="body">
    <div
      v-if="showNewPositionModal"
      class="fixed inset-0 z-50 flex items-center justify-center"
      @click.self="showNewPositionModal = false"
    >
      <div class="absolute inset-0 bg-black/50" />
      <div class="relative w-full max-w-md rounded-xl bg-bg-primary p-6 shadow-xl">
        <div class="mb-4 flex items-center justify-between">
          <h2 class="text-lg font-semibold text-text-primary">New Position</h2>
          <button
            class="text-text-muted hover:text-text-primary"
            @click="showNewPositionModal = false"
          >
            <svg class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path
                stroke-linecap="round"
                stroke-linejoin="round"
                stroke-width="2"
                d="M6 18L18 6M6 6l12 12"
              />
            </svg>
          </button>
        </div>

        <form class="space-y-4" @submit.prevent="submitNewPosition">
          <div>
            <label class="mb-1 block text-xs font-medium text-text-muted">Symbol</label>
            <input
              v-model="newPos.symbol"
              required
              placeholder="e.g. RELIANCE"
              class="w-full rounded-md border border-border-subtle bg-bg-surface px-3 py-2 text-sm text-text-primary placeholder:text-text-muted/60 focus:border-brand/50 focus:outline-none"
            />
          </div>

          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="mb-1 block text-xs font-medium text-text-muted">Direction</label>
              <select
                v-model="newPos.direction"
                class="w-full rounded-md border border-border-subtle bg-bg-surface px-3 py-2 text-sm text-text-primary focus:border-brand/50 focus:outline-none"
              >
                <option value="LONG">Long</option>
                <option value="SHORT">Short</option>
              </select>
            </div>
            <div>
              <label class="mb-1 block text-xs font-medium text-text-muted">Order Type</label>
              <select
                v-model="newPos.orderType"
                class="w-full rounded-md border border-border-subtle bg-bg-surface px-3 py-2 text-sm text-text-primary focus:border-brand/50 focus:outline-none"
              >
                <option value="MARKET">Market</option>
                <option value="LIMIT">Limit</option>
              </select>
            </div>
          </div>

          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="mb-1 block text-xs font-medium text-text-muted">Quantity</label>
              <input
                v-model.number="newPos.quantity"
                type="number"
                min="1"
                required
                class="w-full rounded-md border border-border-subtle bg-bg-surface px-3 py-2 text-sm text-text-primary placeholder:text-text-muted/60 focus:border-brand/50 focus:outline-none"
              />
            </div>
            <div>
              <label class="mb-1 block text-xs font-medium text-text-muted">Entry Price</label>
              <input
                v-model.number="newPos.price"
                type="number"
                step="0.01"
                min="0.01"
                required
                class="w-full rounded-md border border-border-subtle bg-bg-surface px-3 py-2 text-sm text-text-primary placeholder:text-text-muted/60 focus:border-brand/50 focus:outline-none"
              />
            </div>
          </div>

          <div v-if="newPos.orderType === 'LIMIT'" class="grid grid-cols-2 gap-3">
            <div>
              <label class="mb-1 block text-xs font-medium text-text-muted">Limit Price</label>
              <input
                v-model.number="newPos.limitPrice"
                type="number"
                step="0.01"
                min="0.01"
                required
                class="w-full rounded-md border border-border-subtle bg-bg-surface px-3 py-2 text-sm text-text-primary placeholder:text-text-muted/60 focus:border-brand/50 focus:outline-none"
              />
            </div>
          </div>

          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="mb-1 block text-xs font-medium text-text-muted">Stop Loss</label>
              <input
                v-model.number="newPos.stopLoss"
                type="number"
                step="0.01"
                min="0.01"
                placeholder="Optional"
                class="w-full rounded-md border border-border-subtle bg-bg-surface px-3 py-2 text-sm text-text-primary placeholder:text-text-muted/60 focus:border-brand/50 focus:outline-none"
              />
            </div>
            <div>
              <label class="mb-1 block text-xs font-medium text-text-muted">Target</label>
              <input
                v-model.number="newPos.target"
                type="number"
                step="0.01"
                min="0.01"
                placeholder="Optional"
                class="w-full rounded-md border border-border-subtle bg-bg-surface px-3 py-2 text-sm text-text-primary placeholder:text-text-muted/60 focus:border-brand/50 focus:outline-none"
              />
            </div>
          </div>

          <div>
            <label class="mb-1 block text-xs font-medium text-text-muted"
              >Entry Reason (Optional)</label
            >
            <textarea
              v-model="newPos.entryReason"
              rows="2"
              placeholder="Why are you entering this trade?"
              class="w-full rounded-md border border-border-subtle bg-bg-surface px-3 py-2 text-sm text-text-primary placeholder:text-text-muted/60 focus:border-brand/50 focus:outline-none"
            />
          </div>

          <div class="flex gap-3">
            <button
              type="button"
              class="flex-1 rounded-md border border-border-subtle px-4 py-2 text-sm font-medium text-text-muted transition-colors hover:bg-bg-hover"
              @click="showNewPositionModal = false"
            >
              Cancel
            </button>
            <button
              type="submit"
              :disabled="submitting"
              class="flex-1 rounded-md bg-brand px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-brand/90 disabled:opacity-50"
            >
              {{ submitting ? 'Creating...' : 'Create Position' }}
            </button>
          </div>
        </form>
      </div>
    </div>
  </Teleport>

  <!-- Close Position Modal -->
  <Teleport to="body">
    <div
      v-if="showClosePositionModal && closeTarget"
      class="fixed inset-0 z-50 flex items-center justify-center"
      @click.self="showClosePositionModal = false"
    >
      <div class="absolute inset-0 bg-black/50" />
      <div class="relative w-full max-w-sm rounded-xl bg-bg-primary p-6 shadow-xl">
        <div class="mb-4 flex items-center justify-between">
          <h2 class="text-lg font-semibold text-text-primary">Close Position</h2>
          <button
            class="text-text-muted hover:text-text-primary"
            @click="showClosePositionModal = false"
          >
            <svg class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path
                stroke-linecap="round"
                stroke-linejoin="round"
                stroke-width="2"
                d="M6 18L18 6M6 6l12 12"
              />
            </svg>
          </button>
        </div>

        <div class="mb-4 rounded-lg bg-bg-surface p-4">
          <div class="flex items-center justify-between">
            <span class="text-sm font-semibold text-text-primary">{{ closeTarget.symbol }}</span>
            <span class="text-xs text-text-muted"
              >{{ closeTarget.quantity }} shares @ ₹{{ closeTarget.entryPrice }}</span
            >
          </div>
          <div class="mt-2 text-right">
            <span
              class="text-sm font-semibold"
              :class="closeTarget.pnl >= 0 ? 'text-success' : 'text-danger'"
            >
              {{ closeTarget.pnl >= 0 ? '+' : '' }}₹{{ closeTarget.pnl }} ({{
                closeTarget.pnlPercent >= 0 ? '+' : ''
              }}{{ closeTarget.pnlPercent.toFixed(2) }}%)
            </span>
          </div>
        </div>

        <form class="space-y-4" @submit.prevent="submitClosePosition">
          <div>
            <label class="mb-1 block text-xs font-medium text-text-muted"
              >Exit Reason (Optional)</label
            >
            <input
              v-model="closeReason"
              placeholder="e.g. Stop loss hit, target reached"
              class="w-full rounded-md border border-border-subtle bg-bg-surface px-3 py-2 text-sm text-text-primary placeholder:text-text-muted/60 focus:border-brand/50 focus:outline-none"
            />
          </div>

          <div class="flex gap-3">
            <button
              type="button"
              class="flex-1 rounded-md border border-border-subtle px-4 py-2 text-sm font-medium text-text-muted transition-colors hover:bg-bg-hover"
              @click="showClosePositionModal = false"
            >
              Cancel
            </button>
            <button
              type="submit"
              :disabled="closing"
              class="flex-1 rounded-md bg-danger px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-danger/90 disabled:opacity-50"
            >
              {{ closing ? 'Closing...' : 'Confirm Close' }}
            </button>
          </div>
        </form>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { getPositions, getClosedPositions, closePosition, executeTrade } from '../api/client'
import type { Position } from '../api/types'
import LoadingSpinner from '../components/LoadingSpinner.vue'
import ErrorBoundary from '../components/ErrorBoundary.vue'
import { useAsyncData } from '../composables/useAsyncData'

const { loading, error, errorMessage, execute } = useAsyncData()
const positions = ref<Position[]>([])
const searchQuery = ref('')
const statusFilter = ref('ALL')

const filteredPositions = computed(() => {
  return positions.value.filter((p) => {
    const matchesSearch = p.symbol.toLowerCase().includes(searchQuery.value.toLowerCase())
    const matchesStatus = statusFilter.value === 'ALL' || p.status === statusFilter.value
    return matchesSearch && matchesStatus
  })
})

const refreshPositions = () => {
  execute(async () => {
    const openRes = await getPositions()
    const openPositions: Position[] = openRes.success && openRes.data ? openRes.data : []
    const closedRes = await getClosedPositions()
    const closedPositions: Position[] = closedRes.success && closedRes.data ? closedRes.data : []
    positions.value = [...openPositions, ...closedPositions]
    if (openRes.error || closedRes.error) throw new Error(openRes.error || closedRes.error)
  })
}

// New Position Modal
const showNewPositionModal = ref(false)
const submitting = ref(false)
const newPos = ref({
  symbol: '',
  direction: 'LONG' as 'LONG' | 'SHORT',
  orderType: 'MARKET' as 'MARKET' | 'LIMIT',
  quantity: 1,
  price: undefined as number | undefined,
  limitPrice: undefined as number | undefined,
  stopLoss: undefined as number | undefined,
  target: undefined as number | undefined,
  entryReason: '',
})

const submitNewPosition = async () => {
  submitting.value = true
  try {
    await executeTrade({
      symbol: newPos.value.symbol,
      quantity: newPos.value.quantity,
      direction: newPos.value.direction,
      orderType: newPos.value.orderType,
      price: newPos.value.price,
      limitPrice: newPos.value.limitPrice,
      stopPrice: newPos.value.stopLoss,
      entryReason: newPos.value.entryReason,
    })
    showNewPositionModal.value = false
    newPos.value = {
      symbol: '',
      direction: 'LONG',
      orderType: 'MARKET',
      quantity: 1,
      price: undefined,
      limitPrice: undefined,
      stopLoss: undefined,
      target: undefined,
      entryReason: '',
    }
    await refreshPositions()
  } catch (err: unknown) {
    errorMessage.value = err instanceof Error ? err.message : 'Failed to create position'
    error.value = true
  } finally {
    submitting.value = false
  }
}

// Close Position Modal
const showClosePositionModal = ref(false)
const closing = ref(false)
const closeTarget = ref<Position | null>(null)
const closeReason = ref('')

const showCloseModal = (pos: Position) => {
  closeTarget.value = pos
  closeReason.value = ''
  showClosePositionModal.value = true
}

const submitClosePosition = async () => {
  if (!closeTarget.value) return
  closing.value = true
  try {
    await closePosition(closeTarget.value.symbol, closeReason.value || 'manual_close')
    showClosePositionModal.value = false
    closeTarget.value = null
    await refreshPositions()
  } catch (err: unknown) {
    errorMessage.value = err instanceof Error ? err.message : 'Failed to close position'
    error.value = true
  } finally {
    closing.value = false
  }
}

onMounted(() => {
  refreshPositions()
})
</script>
