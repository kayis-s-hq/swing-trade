<template>
  <div class="p-4">
    <!-- Header -->
    <div class="mb-6 flex items-center justify-between">
      <h1 class="text-2xl font-bold text-gray-800 dark:text-white">Positions</h1>
      <button
        @click="refreshPositions"
        class="rounded-lg bg-gray-100 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-200 dark:bg-gray-800 dark:text-gray-300 dark:hover:bg-gray-700"
      >
        Refresh
      </button>
    </div>

    <!-- Loading State -->
    <div v-if="loading" class="flex items-center justify-center py-12">
      <div class="text-center">
        <svg class="mx-auto h-12 w-12 animate-spin text-indigo-600" fill="none" viewBox="0 0 24 24">
          <circle
            class="opacity-25"
            cx="12"
            cy="12"
            r="10"
            stroke="currentColor"
            stroke-width="4"
          />
          <path
            class="opacity-75"
            fill="currentColor"
            d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"
          />
        </svg>
        <p class="mt-2 text-sm text-gray-500 dark:text-gray-400">Loading positions...</p>
      </div>
    </div>

    <!-- Error State -->
    <div v-else-if="error" class="rounded-2xl border border-error-200 bg-error-50 p-6 dark:border-error-900 dark:bg-error-900/20">
      <div class="flex items-center gap-3">
        <svg class="h-8 w-8 text-error-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
        </svg>
        <div>
          <h3 class="text-lg font-medium text-error-800 dark:text-error-200">Error Loading Positions</h3>
          <p class="text-sm text-error-600 dark:text-error-300">{{ errorMessage }}</p>
          <button
            @click="refreshPositions"
            class="mt-2 rounded bg-error-600 px-3 py-1 text-sm font-medium text-white hover:bg-error-700"
          >
            Retry
          </button>
        </div>
      </div>
    </div>

    <!-- Positions Content -->
    <div v-else>
      <!-- Filters -->
      <div class="mb-6 flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div class="flex gap-2">
          <input
            type="text"
            v-model="searchTerm"
            placeholder="Search by symbol..."
            class="rounded-lg border border-gray-200 bg-white px-4 py-2 text-sm focus:border-indigo-500 focus:outline-none dark:border-gray-700 dark:bg-gray-700 dark:text-white"
          />
          <select
            v-model="statusFilter"
            class="rounded-lg border border-gray-200 bg-white px-4 py-2 text-sm focus:border-indigo-500 focus:outline-none dark:border-gray-700 dark:bg-gray-700 dark:text-white"
          >
            <option value="">All Status</option>
            <option value="OPEN">Open</option>
            <option value="CLOSED">Closed</option>
            <option value="STOPPED">Stopped</option>
            <option value="TARGET_HIT">Target Hit</option>
          </select>
        </div>
      </div>

      <!-- Positions Table -->
      <div class="overflow-hidden rounded-2xl border border-gray-200 bg-white dark:border-gray-800 dark:bg-white/[0.03]">
        <div class="w-full overflow-x-auto">
          <table class="min-w-full">
            <thead class="border-gray-100 border-y dark:border-gray-800">
              <tr>
                <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Symbol</th>
                <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Entry Date</th>
                <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Entry Price</th>
                <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Quantity</th>
                <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Current Price</th>
                <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">P&L %</th>
                <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Status</th>
                <th class="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Actions</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-gray-100 dark:divide-gray-800">
              <tr
                v-for="position in filteredPositions"
                :key="position.id"
                class="hover:bg-gray-50 dark:hover:bg-white/[0.02]"
              >
                <td class="px-4 py-3 text-sm font-medium text-gray-700 dark:text-gray-300">{{ position.symbol }}</td>
                <td class="px-4 py-3 text-sm text-gray-700 dark:text-gray-300">{{ position.entryDate }}</td>
                <td class="px-4 py-3 text-sm text-gray-700 dark:text-gray-300">${{ position.entryPrice }}</td>
                <td class="px-4 py-3 text-sm text-gray-700 dark:text-gray-300">{{ position.quantity }}</td>
                <td class="px-4 py-3 text-sm text-gray-700 dark:text-gray-300">${{ position.currentPrice }}</td>
                <td
                  class="px-4 py-3 text-sm font-medium"
                  :class="position.pnlPercent >= 0 ? 'text-success-600' : 'text-error-600'"
                >
                  {{ position.pnlPercent }}%
                </td>
                <td class="px-4 py-3">
                  <span
                    class="inline-flex items-center rounded-full bg-success-50 px-2.5 py-0.5 text-xs font-medium text-success-600 dark:bg-success-500/15 dark:text-success-500"
                    v-if="position.status === 'OPEN'"
                    >Open</span
                  >
                  <span
                    class="inline-flex items-center rounded-full bg-warning-50 px-2.5 py-0.5 text-xs font-medium text-warning-600 dark:bg-warning-500/15 dark:text-orange-400"
                    v-else-if="position.status === 'STOPPED'"
                    >Stopped</span
                  >
                  <span
                    class="inline-flex items-center rounded-full bg-error-50 px-2.5 py-0.5 text-xs font-medium text-error-600 dark:bg-error-500/15 dark:text-error-500"
                    v-else
                    >Closed</span
                  >
                </td>
                <td class="px-4 py-3">
                  <button
                    v-if="position.status === 'OPEN'"
                    @click="openCloseModal(position)"
                    class="rounded-lg bg-indigo-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-indigo-700"
                  >
                    Close
                  </button>
                </td>
              </tr>
              <tr v-if="filteredPositions.length === 0">
                <td colspan="8" class="px-4 py-8 text-center text-gray-500 dark:text-gray-400">
                  No positions found
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <!-- Close Position Modal -->
    <div
      v-if="positionToClose"
      class="fixed inset-0 z-9999 flex items-center justify-center bg-black bg-opacity-50"
    >
      <div class="rounded-xl bg-white p-6 shadow-lg dark:bg-gray-800">
        <h3 class="text-lg font-bold text-gray-800 dark:text-white mb-4">Close Position</h3>
        <p class="text-gray-600 dark:text-gray-300 mb-6">
          Are you sure you want to close position for
          <strong>{{ positionToClose.symbol }}</strong>?
        </p>
        <div class="flex justify-end gap-3">
          <button
            @click="positionToClose = null"
            class="rounded-lg px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-100 dark:text-gray-300 dark:hover:bg-white/[0.05]"
          >
            Cancel
          </button>
          <button
            @click="closePosition"
            class="rounded-lg bg-indigo-600 px-4 py-2 text-sm font-medium text-white hover:bg-indigo-700"
          >
            Confirm Close
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { getPositionList, closePosition } from '../api/client'
import type { Position } from '../api/types'

const loading = ref(true)
const error = ref(false)
const errorMessage = ref('')

const positions = ref<Position[]>([])
const searchTerm = ref('')
const statusFilter = ref('')
const positionToClose = ref<Position | null>(null)

const filteredPositions = computed(() => {
  return positions.value.filter((pos) => {
    const matchesSearch = pos.symbol.toLowerCase().includes(searchTerm.value.toLowerCase())
    const matchesStatus = !statusFilter.value || pos.status === statusFilter.value
    return matchesSearch && matchesStatus
  })
})

const refreshPositions = async () => {
  loading.value = true
  error.value = false
  errorMessage.value = ''

  try {
    const response = await getPositionList()
    if (response.success && response.data) {
      positions.value = response.data
    } else if (response.error) {
      throw new Error(response.error)
    }
  } catch (err: unknown) {
    const errorMsg = err instanceof Error ? err.message : 'Failed to load positions'
    errorMessage.value = errorMsg
    error.value = true
    console.error('PositionsView error:', err)
  } finally {
    loading.value = false
  }
}

const openCloseModal = (position: Position) => {
  positionToClose.value = position
}

const closePosition = async () => {
  if (!positionToClose.value) return
  try {
    await closePosition(positionToClose.value.id)
    // Refresh positions
    await refreshPositions()
    positionToClose.value = null
  } catch (err: unknown) {
    const errorMsg = err instanceof Error ? err.message : 'Failed to close position'
    errorMessage.value = errorMsg
    error.value = true
    console.error('Failed to close position:', err)
  }
}

onMounted(() => {
  refreshPositions()
})
</script>
