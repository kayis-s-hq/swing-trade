<template>
  <div class="p-6 animate-fade-in">
    <!-- Page Header -->
    <div class="mb-6 flex items-center justify-between">
      <div>
        <h1 class="font-display text-2xl font-semibold text-text-primary">Sentiment</h1>
        <p class="mt-1 text-sm text-text-muted">LLM-powered sentiment analysis on news and announcements</p>
      </div>
    </div>

    <!-- Tabs -->
    <div class="mb-6 flex gap-1 rounded-lg bg-bg-primary p-1">
      <button
        v-for="tab in tabs"
        :key="tab.key"
        @click="activeTab = tab.key"
        class="rounded-md px-4 py-1.5 text-sm font-medium transition-colors"
        :class="activeTab === tab.key ? 'bg-brand/10 text-brand' : 'text-text-muted hover:text-text-primary'"
      >
        {{ tab.label }}
      </button>
    </div>

    <!-- Overview Tab -->
    <div v-if="activeTab === 'overview'">
      <!-- Symbol Selector + Analyze -->
      <div class="mb-6 card-panel p-5">
        <h3 class="mb-3 text-sm font-semibold text-text-primary">Select Symbol</h3>
        <form @submit.prevent="analyzeComposite" class="flex flex-col sm:flex-row gap-3">
          <div class="flex-1">
            <input
              v-model="symbolInput"
              type="text"
              placeholder="e.g. RELIANCE"
              list="watchlistSymbols"
              required
              class="w-full rounded-md border border-border-subtle bg-bg-primary px-3 py-2 text-sm text-text-primary placeholder:text-text-muted/50 focus:outline-none focus:ring-2 focus:ring-brand/30"
            />
            <datalist id="watchlistSymbols">
              <option v-for="w in watchlistSymbols" :key="w.symbol" :value="w.symbol" />
            </datalist>
          </div>
          <button
            type="submit"
            :disabled="orchestrating"
            class="rounded-md bg-brand px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-brand/90 disabled:opacity-50"
          >
            {{ orchestrating ? 'Analyzing...' : 'Analyze' }}
          </button>
          <button
            type="button"
            :disabled="loading"
            @click="loadSentiment"
            class="rounded-md border border-border-subtle px-4 py-2 text-sm text-text-muted transition-colors hover:bg-bg-hover disabled:opacity-50"
          >
            {{ loading ? 'Loading...' : 'View Sentiment' }}
          </button>
        </form>
        <p v-if="error" class="mt-3 text-xs text-danger">{{ error }}</p>
      </div>

      <!-- Full analysis orchestration progress -->
      <div v-if="orchestrating" class="animate-fade-in">
        <AnalysisProgressComp
          :stages="analysisStages"
          :current-stage="analysisCurrentStage"
          :is-complete="analysisComplete"
          :duration-ms="analysisDurationMs"
          :error="analysisError"
        />
      </div>

      <!-- Legacy composite loading state -->
      <div v-else-if="compositeLoading" class="flex justify-center py-12">
        <div class="h-6 w-6 animate-spin rounded-full border-2 border-brand border-t-transparent" />
      </div>

      <!-- Composite analysis results -->
      <div v-if="(!orchestrating && !compositeLoading) && composite" class="flex flex-col gap-6">
        <!-- Score Card -->
        <ScoreCard
          :score="composite.compositeScore"
          :signal="composite.compositeSignal"
          :confidence="composite.compositeConfidence"
          :reasoning="composite.reasoning"
        />

        <!-- Source Breakdown -->
        <SourceBreakdown :sources="composite.sources" />

        <!-- Technical Indicators -->
        <TechnicalIndicatorsComp
          :score="composite.technical.score"
          :signal="composite.technical.signal as 'BUY' | 'SELL' | 'HOLD'"
          :confidence="composite.technical.confidence"
          :indicators="composite.technical.indicators"
        />

        <!-- Fundamentals -->
        <FundamentalsPanel
          :score="composite.fundamentals.score"
          :factors="composite.fundamentals.factors"
        />

        <!-- Backtest -->
        <BacktestPanel
          :totalTrades="composite.backtest.totalTrades"
          :winRate="composite.backtest.winRate"
          :profitFactor="composite.backtest.profitFactor"
          :maxDrawdown="composite.backtest.maxDrawdown"
          :totalReturn="composite.backtest.totalReturn"
          :expectancy="composite.backtest.expectancy"
          :hasEnoughData="composite.backtest.hasEnoughData"
        />
      </div>

      <!-- Old sentiment result (coexists with composite) -->
      <div v-if="!compositeLoading && sentiment" class="mt-6">
        <div class="mb-6 card-panel p-5">
          <h3 class="mb-4 text-sm font-semibold text-text-primary">LLM Analysis</h3>
          <div class="mb-4 flex items-center justify-between">
            <h4 class="text-sm font-semibold text-text-primary">{{ sentiment.symbol }}</h4>
            <span class="text-xs text-text-muted">{{ sentiment.date }}</span>
          </div>
          <div class="mb-4 flex items-center gap-4">
            <SentimentBadge :score="sentiment.score" :confidence="sentiment.confidence" />
          </div>
          <div class="mb-4">
            <h4 class="mb-1 text-xs font-semibold uppercase tracking-wider text-text-muted">Summary</h4>
            <p class="text-sm text-text-secondary">{{ sentiment.summary }}</p>
          </div>
          <div v-if="sentiment.redFlags.length" class="mb-4">
            <h4 class="mb-1 text-xs font-semibold uppercase tracking-wider text-danger">Red Flags</h4>
            <ul class="list-disc pl-4 text-sm text-text-secondary">
              <li v-for="rf in sentiment.redFlags" :key="rf">{{ rf }}</li>
            </ul>
          </div>
          <div v-if="sentiment.catalysts.length">
            <h4 class="mb-1 text-xs font-semibold uppercase tracking-wider text-success">Catalysts</h4>
            <ul class="list-disc pl-4 text-sm text-text-secondary">
              <li v-for="c in sentiment.catalysts" :key="c">{{ c }}</li>
            </ul>
          </div>
        </div>

        <!-- Context Panel -->
        <ContextPanel
          :signal="latestSignal"
          :sentiment="sentiment"
          :article-count="newsArticles.length"
          :trend="history.length > 0 ? history : [sentiment]"
        />
      </div>

      <!-- Empty state -->
      <div v-if="!composite && !sentiment && !compositeLoading && !loading" class="card-panel p-5">
        <p class="text-sm text-text-muted">No data available. Enter a symbol and click Analyze or View Sentiment.</p>
      </div>
    </div>

    <!-- Details Tab -->
    <div v-if="activeTab === 'details'">
      <!-- Symbol Selector -->
      <div class="mb-6 card-panel p-5">
        <h3 class="mb-3 text-sm font-semibold text-text-primary">Select Symbol</h3>
        <form @submit.prevent="loadSentiment" class="flex flex-col sm:flex-row gap-3">
          <div class="flex-1">
            <input
              v-model="symbolInput"
              type="text"
              placeholder="e.g. RELIANCE"
              list="watchlistSymbols"
              required
              class="w-full rounded-md border border-border-subtle bg-bg-primary px-3 py-2 text-sm text-text-primary placeholder:text-text-muted/50 focus:outline-none focus:ring-2 focus:ring-brand/30"
            />
            <datalist id="watchlistSymbols">
              <option v-for="w in watchlistSymbols" :key="w.symbol" :value="w.symbol" />
            </datalist>
          </div>
          <button
            type="submit"
            :disabled="loading"
            class="rounded-md bg-brand px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-brand/90 disabled:opacity-50"
          >
            {{ loading ? 'Loading...' : 'View' }}
          </button>
          <button
            type="button"
            :disabled="analyzing"
            @click="handleAnalyse"
            class="rounded-md border border-border-subtle px-4 py-2 text-sm text-text-muted transition-colors hover:bg-bg-hover disabled:opacity-50"
          >
            {{ analyzing ? 'Analyzing...' : 'Refresh' }}
          </button>
        </form>
        <p v-if="error" class="mt-3 text-xs text-danger">{{ error }}</p>
      </div>

      <!-- Loading state -->
      <div v-if="loading || analyzing" class="flex justify-center py-12">
        <div class="h-6 w-6 animate-spin rounded-full border-2 border-brand border-t-transparent" />
      </div>

      <!-- Composite analysis (if available) -->
      <div v-if="!loading && !analyzing && composite" class="flex flex-col gap-6 mb-6">
        <ScoreCard
          :score="composite.compositeScore"
          :signal="composite.compositeSignal"
          :confidence="composite.compositeConfidence"
          :reasoning="composite.reasoning"
        />
        <SourceBreakdown :sources="composite.sources" />
        <TechnicalIndicatorsComp
          :score="composite.technical.score"
          :signal="composite.technical.signal as 'BUY' | 'SELL' | 'HOLD'"
          :confidence="composite.technical.confidence"
          :indicators="composite.technical.indicators"
        />
        <FundamentalsPanel
          :score="composite.fundamentals.score"
          :factors="composite.fundamentals.factors"
        />
        <BacktestPanel
          :totalTrades="composite.backtest.totalTrades"
          :winRate="composite.backtest.winRate"
          :profitFactor="composite.backtest.profitFactor"
          :maxDrawdown="composite.backtest.maxDrawdown"
          :totalReturn="composite.backtest.totalReturn"
          :expectancy="composite.backtest.expectancy"
          :hasEnoughData="composite.backtest.hasEnoughData"
        />
      </div>

      <!-- News Sources Section -->
      <div v-if="!loading && !analyzing && newsArticles.length" class="mb-6">
        <h3 class="mb-3 text-sm font-semibold text-text-primary">News Sources</h3>
        <div class="space-y-2">
          <NewsSourceCard v-for="article in newsArticles" :key="article.link" :article="article" />
        </div>
      </div>

      <!-- LLM Analysis -->
      <div v-if="!loading && !analyzing && sentiment">
        <div class="mb-6 card-panel p-5">
          <h3 class="mb-4 text-sm font-semibold text-text-primary">LLM Analysis</h3>
          <div class="mb-4 flex items-center justify-between">
            <h4 class="text-sm font-semibold text-text-primary">{{ sentiment.symbol }}</h4>
            <span class="text-xs text-text-muted">{{ sentiment.date }}</span>
          </div>
          <div class="mb-4 flex items-center gap-4">
            <SentimentBadge :score="sentiment.score" :confidence="sentiment.confidence" />
          </div>
          <div class="mb-4">
            <h4 class="mb-1 text-xs font-semibold uppercase tracking-wider text-text-muted">Summary</h4>
            <p class="text-sm text-text-secondary">{{ sentiment.summary }}</p>
          </div>
          <div v-if="sentiment.redFlags.length" class="mb-4">
            <h4 class="mb-1 text-xs font-semibold uppercase tracking-wider text-danger">Red Flags</h4>
            <ul class="list-disc pl-4 text-sm text-text-secondary">
              <li v-for="rf in sentiment.redFlags" :key="rf">{{ rf }}</li>
            </ul>
          </div>
          <div v-if="sentiment.catalysts.length">
            <h4 class="mb-1 text-xs font-semibold uppercase tracking-wider text-success">Catalysts</h4>
            <ul class="list-disc pl-4 text-sm text-text-secondary">
              <li v-for="c in sentiment.catalysts" :key="c">{{ c }}</li>
            </ul>
          </div>
        </div>

        <!-- Context Panel -->
        <ContextPanel
          :signal="latestSignal"
          :sentiment="sentiment"
          :article-count="newsArticles.length"
          :trend="history.length > 0 ? history : [sentiment]"
        />
      </div>

      <!-- Empty state -->
      <div v-if="!sentiment && !composite && !loading && !analyzing" class="card-panel p-5">
        <p class="text-sm text-text-muted">No data available. Enter a symbol to view sentiment.</p>
      </div>
    </div>

    <!-- History Tab -->
    <div v-if="activeTab === 'history'">
      <div v-if="historyLoading" class="flex justify-center py-12">
        <div class="h-6 w-6 animate-spin rounded-full border-2 border-brand border-t-transparent" />
      </div>
      <SentimentTimeline v-else-if="history.length" :items="history" />
      <div v-else class="card-panel p-5">
        <p class="text-sm text-text-muted">No sentiment history available.</p>
      </div>

      <!-- Pagination -->
      <div v-if="history.length > 0" class="mt-4 flex items-center justify-center gap-2">
        <button
          @click="historyPage = Math.max(0, historyPage - 1)"
          :disabled="historyPage === 0"
          class="rounded-md border border-border-subtle px-3 py-1.5 text-sm text-text-muted transition-colors hover:bg-bg-hover disabled:opacity-50"
        >
          Previous
        </button>
        <span class="text-sm text-text-muted">Page {{ historyPage + 1 }}</span>
        <button
          @click="historyPage++"
          :disabled="!hasMore"
          class="rounded-md border border-border-subtle px-3 py-1.5 text-sm text-text-muted transition-colors hover:bg-bg-hover disabled:opacity-50"
        >
          Next
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import {
  getSentimentLatest,
  getSentimentHistory,
  triggerSentimentAnalysis,
  getLatestNews,
  getLatestSignalForSymbol,
  getWatchlist,
  runFullAnalysis,
} from '../api/client'
import type { SentimentResult, WatchlistEntry, NewsArticle, Signal, CompositeAnalysis, AnalysisProgress, FullAnalysisResult } from '../api/types'
import SentimentBadge from '../components/SentimentBadge.vue'
import SentimentTimeline from '../components/SentimentTimeline.vue'
import NewsSourceCard from '../components/NewsSourceCard.vue'
import ContextPanel from '../components/ContextPanel.vue'
import ScoreCard from '../components/ScoreCard.vue'
import SourceBreakdown from '../components/SourceBreakdown.vue'
import TechnicalIndicatorsComp from '../components/TechnicalIndicators.vue'
import FundamentalsPanel from '../components/FundamentalsPanel.vue'
import BacktestPanel from '../components/BacktestPanel.vue'
import AnalysisProgressComp from '../components/AnalysisProgress.vue'

const tabs = [
  { key: 'overview', label: 'Overview' },
  { key: 'details', label: 'Details' },
  { key: 'history', label: 'History' },
]
const activeTab = ref('overview')

const symbolInput = ref('RELIANCE')
const loading = ref(false)
const analyzing = ref(false)
const error = ref('')
const sentiment = ref<SentimentResult | null>(null)
const newsArticles = ref<NewsArticle[]>([])
const latestSignal = ref<Signal | null>(null)

// Composite analysis state
let composite: CompositeAnalysis | null = null
const compositeLoading = ref(false)

// Full analysis orchestration state
const analysisStages = ref<AnalysisProgress[]>([])
const analysisCurrentStage = ref(0)
const analysisComplete = ref(false)
const analysisDurationMs = ref(0)
const analysisError = ref<string | null>(null)
const orchestrating = ref(false)

const historyLoading = ref(false)
const history = ref<SentimentResult[]>([])
const historyPage = ref(0)
const hasMore = ref(true)

const watchlistSymbols = ref<WatchlistEntry[]>([])

const loadSentiment = async () => {
  if (!symbolInput.value.trim()) return
  loading.value = true
  error.value = ''
  sentiment.value = null
  newsArticles.value = []
  latestSignal.value = null
  try {
    const [sentRes, newsRes, signalRes] = await Promise.all([
      getSentimentLatest(symbolInput.value),
      getLatestNews(symbolInput.value),
      getLatestSignalForSymbol(symbolInput.value),
    ])

    if (sentRes.success && sentRes.data) {
      sentiment.value = sentRes.data
    } else {
      error.value = sentRes.error || 'Failed to load sentiment'
    }

    if (newsRes.success && newsRes.data) {
      newsArticles.value = newsRes.data
    }

    if (signalRes.success && signalRes.data) {
      latestSignal.value = signalRes.data
    }
  } finally {
    loading.value = false
  }
}

const handleAnalyse = async () => {
  if (!symbolInput.value.trim()) return
  analyzing.value = true
  error.value = ''
  newsArticles.value = []
  latestSignal.value = null
  try {
    const [res, newsRes, signalRes] = await Promise.all([
      triggerSentimentAnalysis(symbolInput.value),
      getLatestNews(symbolInput.value),
      getLatestSignalForSymbol(symbolInput.value),
    ])
    if (res.success && res.data) {
      sentiment.value = res.data
    } else {
      error.value = res.error || 'Analysis failed'
    }
    if (newsRes.success && newsRes.data) {
      newsArticles.value = newsRes.data
    }
    if (signalRes.success && signalRes.data) {
      latestSignal.value = signalRes.data
    }
  } finally {
    analyzing.value = false
  }
}

const analyzeComposite = async () => {
  if (!symbolInput.value.trim()) return
  orchestrating.value = true
  error.value = ''
  composite = null
  analysisStages.value = []
  analysisCurrentStage.value = 0
  analysisComplete.value = false
  analysisDurationMs.value = 0
  analysisError.value = null
  try {
    for await (const data of runFullAnalysis(symbolInput.value)) {
      if ('stageNumber' in data && 'stageName' in data) {
        // AnalysisProgress
        const stage = data as AnalysisProgress
        analysisStages.value = [...analysisStages.value, stage]
        if (stage.status === 'running') {
          analysisCurrentStage.value = stage.stageNumber
        }
      } else {
        // FullAnalysisResult
        const result = data as FullAnalysisResult
        composite = result.composite
        analysisDurationMs.value = result.durationMs
        analysisComplete.value = true
      }
    }
  } catch (e) {
    analysisError.value = e instanceof Error ? e.message : 'Analysis failed'
    error.value = analysisError.value
  } finally {
    orchestrating.value = false
    compositeLoading.value = false
  }
}

const loadHistory = async () => {
  if (!symbolInput.value.trim()) return
  historyLoading.value = true
  try {
    const res = await getSentimentHistory(symbolInput.value, historyPage.value)
    if (res.success && res.data) {
      history.value = res.data
      hasMore.value = history.value.length === 20
    }
  } finally {
    historyLoading.value = false
  }
}

// Load history when switching to history tab
watch(activeTab, (tab) => {
  if (tab === 'history') loadHistory()
})

onMounted(async () => {
  const wr = await getWatchlist()
  if (wr.success && wr.data) watchlistSymbols.value = wr.data
})
</script>