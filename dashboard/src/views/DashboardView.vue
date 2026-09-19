<template>
  <div class="view-shell dashboard-view p-4 sm:p-6 animate-fade-in">
    <div class="mb-6 flex items-center justify-between gap-4">
      <div>
        <p class="dashboard-kicker">Trading desk / live snapshot</p>
        <h1
          class="mt-2 font-display text-2xl font-semibold tracking-tight text-text-primary sm:text-3xl"
        >
          Portfolio at a glance
        </h1>
        <p class="mt-1 text-sm text-text-muted">
          Capital, risk, performance, and execution readiness in one place.
        </p>
      </div>
      <button
        class="dashboard-refresh flex items-center gap-2 rounded-lg border border-border-subtle bg-bg-surface px-3 py-2 text-sm font-medium text-text-muted"
        :disabled="loading"
        @click="refreshDashboard"
      >
        <svg class="h-4 w-4" :class="loading ? 'animate-spin' : ''" fill="none" viewBox="0 0 24 24">
          <path
            stroke="currentColor"
            stroke-linecap="round"
            stroke-linejoin="round"
            stroke-width="2"
            d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15"
          />
        </svg>
        {{ loading ? 'Refreshing...' : 'Refresh' }}
      </button>
    </div>

    <ErrorBoundary :error="false">
      <div v-if="loading" class="flex items-center justify-center py-20">
        <LoadingSpinner message="Loading market data..." />
      </div>

      <template v-else>
        <section class="dashboard-hero mb-6 rounded-2xl border border-border-subtle p-5 sm:p-6">
          <div class="flex flex-col justify-between gap-5 lg:flex-row lg:items-end">
            <div>
              <p class="text-xs font-medium uppercase tracking-[0.16em] text-text-muted">
                Today’s performance
              </p>
              <div class="mt-2 flex flex-wrap items-baseline gap-x-3 gap-y-1">
                <strong
                  class="text-4xl font-semibold tracking-tight"
                  :class="pnlClass(marketOverview?.todayPnl)"
                >
                  {{ signedCurrency(marketOverview?.todayPnl) }}
                </strong>
                <span class="text-sm font-medium" :class="pnlClass(marketOverview?.todayPnl)">
                  {{
                    marketOverview?.todayPnlPercent
                      ? `${signedPercent(marketOverview.todayPnlPercent)} today`
                      : 'Intraday snapshot'
                  }}
                </span>
              </div>
              <p class="mt-2 text-sm text-text-muted">
                {{ marketOverview?.openPositions ?? 0 }} open positions contributing to the snapshot
              </p>
            </div>
            <div
              class="flex items-center gap-3 rounded-xl border border-border-subtle/70 bg-bg-primary/35 px-3 py-2"
            >
              <HealthStatus v-if="healthData" :health="healthData" />
              <span class="dashboard-updated">Updated {{ lastUpdated }}</span>
            </div>
          </div>
          <div class="mt-6 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
            <div class="dashboard-primary-stat">
              <span>Portfolio value</span>
              <strong>{{ currency(portfolioSummary?.totalValue) }}</strong>
              <small>Capital currently tracked</small>
            </div>
            <div class="dashboard-primary-stat">
              <span>Total return</span>
              <strong :class="pnlClass(portfolioSummary?.totalPnl)">{{
                signedCurrency(portfolioSummary?.totalPnl)
              }}</strong>
              <small>{{ signedPercent(portfolioSummary?.totalPnlPercent) }} overall</small>
            </div>
            <div class="dashboard-primary-stat">
              <span>Open exposure</span>
              <strong>{{ marketOverview?.openPositions ?? 0 }} <em>positions</em></strong>
              <small>{{ marketOverview?.totalPositions ?? 0 }} total positions</small>
            </div>
            <div class="dashboard-primary-stat">
              <span>Win rate</span>
              <strong>{{ portfolioSummary?.winRate ?? 0 }}%</strong>
              <small>{{ portfolioSummary?.totalTrades ?? 0 }} trades recorded</small>
            </div>
          </div>
        </section>

        <div class="mb-6 grid gap-6 xl:grid-cols-[minmax(0,1.6fr)_minmax(280px,0.8fr)]">
          <section class="card-panel overflow-hidden">
            <div class="flex items-center justify-between border-b border-border-subtle px-5 py-4">
              <div>
                <p class="dashboard-kicker">Capital at work</p>
                <h2 class="mt-1 text-base font-semibold text-text-primary">Active Positions</h2>
                <p v-if="!positionsError" class="mt-1 text-xs text-text-muted">
                  {{ positions.length }} positions currently tracked
                </p>
              </div>
              <router-link to="/positions" class="text-sm font-medium text-brand hover:underline"
                >View all →</router-link
              >
            </div>
            <ErrorMessage
              v-if="positionsError"
              title="Couldn’t load positions"
              message="Active positions are temporarily unavailable."
              action-label="Retry"
              @action="loadPositions"
            />
            <div v-else-if="positions.length === 0" class="px-5 py-12 text-center">
              <p class="text-sm font-medium text-text-primary">No active positions</p>
              <p class="mt-1 text-xs text-text-muted">
                Execute a paper trade to see exposure here.
              </p>
              <router-link
                to="/positions"
                class="mt-4 inline-block text-sm font-medium text-brand hover:underline"
                >Open positions →</router-link
              >
            </div>
            <div v-else class="w-full overflow-x-auto">
              <table class="min-w-full">
                <thead>
                  <tr class="border-b border-border-subtle bg-bg-primary/50">
                    <th
                      v-for="heading in ['Symbol', 'Entry', 'Qty', 'Current', 'Status', 'P&L']"
                      :key="heading"
                      class="px-5 py-3 text-left text-xs font-semibold uppercase tracking-wider text-text-muted"
                      :class="
                        heading === 'Qty' || heading === 'Current' || heading === 'P&L'
                          ? 'text-right'
                          : ''
                      "
                    >
                      {{ heading }}
                    </th>
                  </tr>
                </thead>
                <tbody class="divide-y divide-border-subtle/50">
                  <tr
                    v-for="pos in positions.slice(0, 5)"
                    :key="pos.id"
                    class="transition-colors hover:bg-bg-hover"
                  >
                    <td class="px-5 py-4 text-sm font-semibold text-text-primary">
                      {{ pos.symbol }}
                    </td>
                    <td class="px-5 py-4 text-sm text-text-secondary">
                      {{ currency(pos.entryPrice) }}
                    </td>
                    <td class="px-5 py-4 text-right text-sm text-text-secondary">
                      {{ pos.quantity }}
                    </td>
                    <td class="px-5 py-4 text-right text-sm text-text-secondary">
                      {{ currency(pos.currentPrice) }}
                    </td>
                    <td class="px-5 py-4">
                      <span
                        class="inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium"
                        :class="
                          pos.status === 'OPEN'
                            ? 'bg-success-bg text-success'
                            : 'bg-danger-bg text-danger'
                        "
                      >
                        {{ pos.status.replace('_', ' ') }}
                      </span>
                    </td>
                    <td
                      class="px-5 py-4 text-right text-sm font-semibold"
                      :class="pnlClass(pos.pnl)"
                    >
                      {{ signedCurrency(pos.pnl) }}
                      <span class="ml-1 text-xs font-normal opacity-70"
                        >({{ signedPercent(pos.pnlPercent) }})</span
                      >
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </section>

          <aside class="dashboard-insight card-panel p-5">
            <div class="flex items-start justify-between gap-3">
              <div>
                <p class="dashboard-kicker">Performance readout</p>
                <h2 class="mt-1 text-base font-semibold text-text-primary">Portfolio quality</h2>
              </div>
              <router-link to="/portfolio" class="text-xs font-medium text-brand hover:underline"
                >Details →</router-link
              >
            </div>
            <div class="dashboard-score mt-5">
              <div class="flex items-end justify-between gap-3">
                <span>Win rate</span>
                <strong>{{ portfolioSummary?.winRate ?? 0 }}%</strong>
              </div>
              <div class="dashboard-progress mt-3">
                <span :style="{ width: `${Math.min(portfolioSummary?.winRate ?? 0, 100)}%` }" />
              </div>
              <small class="mt-2 block"
                >Based on {{ portfolioSummary?.totalTrades ?? 0 }} recorded trades</small
              >
            </div>
            <dl class="dashboard-facts mt-5">
              <div>
                <dt>Profit factor</dt>
                <dd>{{ number(portfolioSummary?.profitFactor) }}</dd>
              </div>
              <div>
                <dt>Average win</dt>
                <dd class="text-success">{{ currency(portfolioSummary?.averageWin) }}</dd>
              </div>
              <div>
                <dt>Average loss</dt>
                <dd class="text-danger">{{ currency(portfolioSummary?.averageLoss) }}</dd>
              </div>
              <div>
                <dt>Max drawdown</dt>
                <dd class="text-danger">{{ percent(portfolioSummary?.maxDrawdown) }}</dd>
              </div>
              <div>
                <dt>Sharpe ratio</dt>
                <dd>{{ number(portfolioSummary?.sharpeRatio) }}</dd>
              </div>
            </dl>
            <router-link to="/monitoring" class="dashboard-insight-link mt-5"
              >Review signal accuracy <span>↗</span></router-link
            >
          </aside>
        </div>

        <StrategyBoard :signals="signals" class="mb-6" />

        <div class="grid gap-6 xl:grid-cols-2">
          <section class="card-panel overflow-hidden">
            <div class="flex items-center justify-between border-b border-border-subtle px-5 py-4">
              <div>
                <p class="dashboard-kicker">Decision queue</p>
                <h2 class="mt-1 text-base font-semibold text-text-primary">Latest Signals</h2>
              </div>
              <router-link to="/signals" class="text-xs font-medium text-brand hover:underline"
                >Open signals →</router-link
              >
            </div>
            <div v-if="signals.length" class="divide-y divide-border-subtle/50">
              <div
                v-for="signal in signals.slice(0, 4)"
                :key="signal.id"
                class="flex items-center justify-between gap-4 px-5 py-3"
              >
                <div class="flex min-w-0 items-center gap-3">
                  <span
                    class="signal-direction"
                    :class="
                      signal.direction === 'BUY'
                        ? 'signal-buy'
                        : signal.direction === 'SELL'
                          ? 'signal-sell'
                          : 'signal-hold'
                    "
                    >{{ signal.direction }}</span
                  >
                  <div class="min-w-0">
                    <p class="truncate text-sm font-semibold text-text-primary">
                      {{ signal.symbol }}
                    </p>
                    <p class="truncate text-xs text-text-muted">{{ signal.reason }}</p>
                  </div>
                </div>
                <div class="shrink-0 text-right">
                  <p class="text-sm font-semibold text-text-primary">{{ signal.confidence }}%</p>
                  <p class="text-xs text-text-muted">confidence</p>
                </div>
              </div>
            </div>
            <div v-else class="px-5 py-8 text-center text-sm text-text-muted">
              No recent signals available.
            </div>
          </section>

          <section class="card-panel overflow-hidden">
            <div class="flex items-center justify-between border-b border-border-subtle px-5 py-4">
              <div>
                <p class="dashboard-kicker">Market coverage</p>
                <h2 class="mt-1 text-base font-semibold text-text-primary">Watchlist &amp; Risk</h2>
              </div>
              <router-link to="/watchlist" class="text-xs font-medium text-brand hover:underline"
                >Manage →</router-link
              >
            </div>
            <div class="grid grid-cols-2 gap-3 p-5">
              <div class="dashboard-mini-stat">
                <span>Watchlist</span><strong>{{ watchlist.length }}</strong
                ><small>active symbols</small>
              </div>
              <div class="dashboard-mini-stat">
                <span>Used capital</span><strong>{{ currency(riskSummary?.usedCapital) }}</strong
                ><small>current exposure</small>
              </div>
              <div class="dashboard-mini-stat">
                <span>Available</span><strong>{{ currency(riskSummary?.availableCapital) }}</strong
                ><small>cash capacity</small>
              </div>
              <div class="dashboard-mini-stat">
                <span>Stop-loss risk</span
                ><strong class="text-warning">{{ currency(riskSummary?.stopLossExposure) }}</strong
                ><small>estimated downside</small>
              </div>
            </div>
            <div class="border-t border-border-subtle px-5 py-3">
              <div class="flex items-center justify-between text-xs">
                <span class="text-text-muted">Equity curve · 1M</span>
                <router-link to="/portfolio" class="font-medium text-brand hover:underline"
                  >View performance →</router-link
                >
              </div>
              <svg
                v-if="equityPath && !equityError"
                class="dashboard-sparkline mt-3"
                viewBox="0 0 320 48"
                preserveAspectRatio="none"
                aria-label="One month equity curve"
              >
                <path
                  :d="equityPath"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2"
                  stroke-linecap="round"
                />
              </svg>
              <div v-else-if="equityError" class="mt-3 flex items-center justify-between gap-3">
                <p class="text-xs text-text-muted">Equity curve is temporarily unavailable.</p>
                <button
                  class="text-xs font-medium text-brand hover:underline"
                  type="button"
                  @click="refreshEquityCurve"
                >
                  Retry
                </button>
              </div>
              <p v-else class="mt-3 text-xs text-text-muted">
                Not enough closed-trade data for a curve yet.
              </p>
            </div>
          </section>
        </div>
      </template>
    </ErrorBoundary>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import {
  getEquityCurve,
  getHealthStatus,
  getMarketOverview,
  getPortfolioSummary,
  getPositions,
  getRiskSummary,
  getSignals,
  getWatchlist,
} from '../api/client'
import type {
  HealthStatus as HealthStatusType,
  EquityPoint,
  MarketOverview,
  PortfolioSummary,
  Position,
  RiskSummary,
  Signal,
  WatchlistEntry,
} from '../api/types'
import ErrorBoundary from '../components/ErrorBoundary.vue'
import ErrorMessage from '../components/ErrorMessage.vue'
import HealthStatus from '../components/HealthStatus.vue'
import LoadingSpinner from '../components/LoadingSpinner.vue'
import StrategyBoard from '../components/StrategyBoard.vue'
import { asAppError, type AppError } from '../errors/appError'
import {
  formatCurrency,
  formatSignedCurrency,
  formatPercent,
  formatSignedPercent,
  formatNumber,
} from '../utils/format'

const loading = ref(true)
const positionsError = ref<AppError | null>(null)
const marketOverview = ref<MarketOverview | null>(null)
const positions = ref<Position[]>([])
const portfolioSummary = ref<PortfolioSummary | null>(null)
const healthData = ref<HealthStatusType | null>(null)
const riskSummary = ref<RiskSummary | null>(null)
const signals = ref<Signal[]>([])
const watchlist = ref<WatchlistEntry[]>([])
const equityPoints = ref<EquityPoint[]>([])
const equityError = ref<AppError | null>(null)
const lastUpdated = ref('--:--')

const pnlClass = (value?: number | null) =>
  Number.isFinite(value) && (value as number) >= 0 ? 'text-success' : 'text-danger'
const currency = (value?: number | null) => formatCurrency(value)
const signedCurrency = (value?: number | null) => formatSignedCurrency(value)
const percent = (value?: number | null) => formatPercent(value)
const signedPercent = (value?: number | null) => formatSignedPercent(value)
const number = (value?: number | null) => formatNumber(value)
const equityPath = computed(() => {
  const values = equityPoints.value.map((point) => point.value)
  if (values.length < 2) return ''
  const min = Math.min(...values)
  const range = Math.max(...values) - min || 1
  return values
    .map((value, index) => {
      const x = (index / (values.length - 1)) * 320
      const y = 44 - ((value - min) / range) * 38
      return `${index === 0 ? 'M' : 'L'} ${x.toFixed(1)} ${y.toFixed(1)}`
    })
    .join(' ')
})

const refreshEquityCurve = async () => {
  equityError.value = null
  try {
    const value = await getEquityCurve('1M')
    equityPoints.value = value.data
  } catch (cause) {
    equityPoints.value = []
    equityError.value = asAppError(cause)
  }
}

const loadPositions = async () => {
  positionsError.value = null
  try {
    positions.value = await getPositions()
  } catch (cause) {
    positionsError.value = asAppError(cause)
  }
}

const refreshDashboard = async () => {
  loading.value = true
  await Promise.allSettled([
    getMarketOverview().then((value) => (marketOverview.value = value)),
    loadPositions(),
    getPortfolioSummary().then((value) => (portfolioSummary.value = value)),
    getHealthStatus().then((value) => (healthData.value = value)),
    getRiskSummary().then((value) => (riskSummary.value = value)),
    getSignals().then((value) => (signals.value = value)),
    getWatchlist().then((value) => (watchlist.value = value)),
    refreshEquityCurve(),
  ])
  lastUpdated.value = new Date().toLocaleTimeString('en-IN', {
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  })
  loading.value = false
}

let refreshTimer: ReturnType<typeof setInterval> | undefined

onMounted(() => {
  refreshDashboard()
  refreshTimer = setInterval(() => void refreshDashboard(), 60_000)
})

onUnmounted(() => {
  if (refreshTimer) clearInterval(refreshTimer)
})
</script>

<style scoped>
.dashboard-view {
  --dashboard-ease: cubic-bezier(0.23, 1, 0.32, 1);
}
.dashboard-kicker {
  color: var(--color-brand);
  font-size: 0.68rem;
  font-weight: 700;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}
.dashboard-hero {
  background:
    radial-gradient(
      circle at 100% 0%,
      color-mix(in srgb, var(--color-brand) 12%, transparent),
      transparent 34%
    ),
    linear-gradient(
      135deg,
      color-mix(in srgb, var(--color-bg-surface) 96%, white),
      var(--color-bg-primary)
    );
}
.dashboard-updated {
  color: var(--color-text-muted);
  font-size: 0.7rem;
  white-space: nowrap;
}
.dashboard-primary-stat {
  border-left: 1px solid color-mix(in srgb, var(--color-border-default) 65%, transparent);
  padding: 0.25rem 1rem;
}
.dashboard-primary-stat span,
.dashboard-primary-stat small {
  display: block;
  color: var(--color-text-muted);
  font-size: 0.7rem;
}
.dashboard-primary-stat strong {
  display: block;
  margin-top: 0.35rem;
  color: var(--color-text-primary);
  font-size: 1.15rem;
  font-weight: 650;
  letter-spacing: -0.02em;
}
.dashboard-primary-stat strong em {
  color: var(--color-text-muted);
  font-size: 0.7rem;
  font-style: normal;
  font-weight: 500;
}
.dashboard-primary-stat small {
  margin-top: 0.3rem;
  font-size: 0.68rem;
}
.dashboard-score {
  border-radius: 0.85rem;
  background: color-mix(in srgb, var(--color-bg-primary) 42%, transparent);
  padding: 1rem;
}
.dashboard-score span,
.dashboard-score small {
  color: var(--color-text-muted);
  font-size: 0.75rem;
}
.dashboard-score strong {
  color: var(--color-success);
  font-size: 1.5rem;
  font-weight: 650;
}
.dashboard-progress {
  height: 0.35rem;
  overflow: hidden;
  border-radius: 999px;
  background: var(--color-border-subtle);
}
.dashboard-progress span {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: var(--color-brand);
  transition: width 300ms var(--dashboard-ease);
}
.dashboard-facts {
  border-top: 1px solid var(--color-border-subtle);
}
.dashboard-facts div {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  border-bottom: 1px solid color-mix(in srgb, var(--color-border-subtle) 60%, transparent);
  padding: 0.65rem 0;
}
.dashboard-facts dt {
  color: var(--color-text-muted);
  font-size: 0.75rem;
}
.dashboard-facts dd {
  color: var(--color-text-primary);
  font-size: 0.78rem;
  font-weight: 650;
}
.dashboard-insight-link {
  display: flex;
  justify-content: space-between;
  border-top: 1px solid var(--color-border-subtle);
  padding-top: 1rem;
  color: var(--color-brand);
  font-size: 0.75rem;
  font-weight: 600;
}
.dashboard-sparkline {
  width: 100%;
  height: 3rem;
  color: var(--color-brand);
  opacity: 0.9;
}
.dashboard-mini-stat {
  border: 1px solid var(--color-border-subtle);
  border-radius: 0.75rem;
  background: color-mix(in srgb, var(--color-bg-primary) 34%, transparent);
  padding: 0.75rem;
}
.dashboard-mini-stat span,
.dashboard-mini-stat small {
  display: block;
  color: var(--color-text-muted);
  font-size: 0.68rem;
}
.dashboard-mini-stat strong {
  display: block;
  margin-top: 0.25rem;
  color: var(--color-text-primary);
  font-size: 0.95rem;
  font-weight: 650;
}
.dashboard-mini-stat small {
  margin-top: 0.2rem;
}
.signal-direction {
  display: inline-flex;
  min-width: 3.25rem;
  justify-content: center;
  border-radius: 999px;
  padding: 0.25rem 0.45rem;
  font-size: 0.62rem;
  font-weight: 700;
  letter-spacing: 0.05em;
}
.signal-buy {
  background: var(--color-success-bg);
  color: var(--color-success);
}
.signal-sell {
  background: var(--color-danger-bg);
  color: var(--color-danger);
}
.signal-hold {
  background: var(--color-warning-bg);
  color: var(--color-warning);
}
@media (max-width: 640px) {
  .dashboard-primary-stat {
    border-left: 0;
    border-top: 1px solid color-mix(in srgb, var(--color-border-default) 65%, transparent);
    padding: 0.75rem 0 0;
  }
  .dashboard-primary-stat:first-child {
    border-top: 0;
    padding-top: 0;
  }
}
</style>
