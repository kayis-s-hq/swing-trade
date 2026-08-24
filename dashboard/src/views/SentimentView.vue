<template>
  <div class="p-6 animate-fade-in">
    <!-- Page Header -->
    <div class="mb-6 flex items-center justify-between">
      <div>
        <h1 class="font-display text-2xl font-semibold text-text-primary">Sentiment</h1>
        <p class="mt-1 text-sm text-text-muted">LLM-powered analysis across 9 stages</p>
      </div>
    </div>

    <!-- Tabs -->
    <div class="mb-6 flex gap-1 rounded-lg bg-bg-primary p-1">
      <button
        v-for="tab in tabs"
        :key="tab.key"
        class="rounded-md px-4 py-1.5 text-sm font-medium transition-colors"
        :class="
          activeTab === tab.key
            ? 'bg-brand/10 text-brand'
            : 'text-text-muted hover:text-text-primary'
        "
        @click="activeTab = tab.key"
      >
        {{ tab.label }}
      </button>
    </div>

    <!-- Overview Tab -->
    <div v-if="activeTab === 'overview'">
      <!-- Symbol Selector + Analyze -->
      <div class="mb-6 card-panel p-5">
        <h3 class="mb-3 text-sm font-semibold text-text-primary">Select Symbol</h3>
        <form class="flex flex-col sm:flex-row gap-3" @submit.prevent="runAnalysis">
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
            class="rounded-md border border-border-subtle px-4 py-2 text-sm text-text-muted transition-colors hover:bg-bg-hover disabled:opacity-50"
            @click="loadQuick"
          >
            {{ loading ? 'Loading...' : 'View Sentiment' }}
          </button>
        </form>
        <p v-if="error" class="mt-3 text-xs text-danger">
          {{ error }}
        </p>
      </div>

      <!-- Pipeline accordion -->
      <div v-if="orchestrating || analysisProgress.length" class="animate-fade-in">
        <AnalysisAccordion :stages="analysisProgress" :composite="composite" />
      </div>

      <!-- Legacy composite loading -->
      <div v-else-if="compositeLoading" class="flex justify-center py-12">
        <div class="h-6 w-6 animate-spin rounded-full border-2 border-brand border-t-transparent" />
      </div>

      <!-- Historical Sentiment -->
      <div v-if="sentiment" class="mt-6">
        <button
          class="mb-3 flex items-center gap-2 text-sm font-medium text-text-muted transition-colors hover:text-text-primary"
          @click="showHistoricalSentiment = !showHistoricalSentiment"
        >
          <svg
            class="h-4 w-4 transition-transform"
            :class="{ 'rotate-180': showHistoricalSentiment }"
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
          >
            <path
              stroke-linecap="round"
              stroke-linejoin="round"
              stroke-width="2"
              d="M19 9l-7 7-7-7"
            />
          </svg>
          Historical Sentiment
        </button>

        <div v-if="showHistoricalSentiment" class="animate-fade-in">
          <div class="card-panel p-5">
            <div class="mb-4 flex items-center justify-between">
              <h4 class="text-sm font-semibold text-text-primary">
                {{ sentiment.symbol }}
              </h4>
              <span class="text-xs text-text-muted">{{ sentiment.date }}</span>
            </div>
            <div class="mb-4 flex items-center gap-4">
              <SentimentBadge :score="sentiment.score" :confidence="sentiment.confidence" />
            </div>
            <div class="mb-4">
              <h4 class="mb-1 text-xs font-semibold uppercase tracking-wider text-text-muted">
                Summary
              </h4>
              <p class="text-sm text-text-secondary">
                {{ sentiment.summary }}
              </p>
            </div>
            <div v-if="sentiment.redFlags.length" class="mb-4">
              <h4 class="mb-1 text-xs font-semibold uppercase tracking-wider text-danger">
                Red Flags
              </h4>
              <ul class="list-disc pl-4 text-sm text-text-secondary">
                <li v-for="rf in sentiment.redFlags" :key="rf">
                  {{ rf }}
                </li>
              </ul>
            </div>
            <div v-if="sentiment.catalysts.length">
              <h4 class="mb-1 text-xs font-semibold uppercase tracking-wider text-success">
                Catalysts
              </h4>
              <ul class="list-disc pl-4 text-sm text-text-secondary">
                <li v-for="c in sentiment.catalysts" :key="c">
                  {{ c }}
                </li>
              </ul>
            </div>
          </div>
        </div>
      </div>

      <!-- Empty state -->
      <div
        v-if="
          !composite &&
          !sentiment &&
          !orchestrating &&
          !compositeLoading &&
          analysisProgress.length === 0
        "
        class="card-panel p-5"
      >
        <p class="text-sm text-text-muted">
          No data available. Enter a symbol and click Analyze or View Sentiment.
        </p>
      </div>
    </div>

    <!-- News Tab -->
    <div v-if="activeTab === 'news'">
      <div class="mb-6 card-panel p-5">
        <h3 class="mb-3 text-sm font-semibold text-text-primary">Select Symbol</h3>
        <form class="flex flex-col sm:flex-row gap-3" @submit.prevent="loadNews">
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
            :disabled="newsLoading"
            class="rounded-md bg-brand px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-brand/90 disabled:opacity-50"
          >
            {{ newsLoading ? 'Loading...' : 'Fetch News' }}
          </button>
        </form>
        <p v-if="error" class="mt-3 text-xs text-danger">
          {{ error }}
        </p>
      </div>

      <ArticleBrowser :articles="newsArticles" :loading="newsLoading" />
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
          :disabled="historyPage === 0"
          class="rounded-md border border-border-subtle px-3 py-1.5 text-sm text-text-muted transition-colors hover:bg-bg-hover disabled:opacity-50"
          @click="historyPage = Math.max(0, historyPage - 1)"
        >
          Previous
        </button>
        <span class="text-sm text-text-muted">Page {{ historyPage + 1 }}</span>
        <button
          :disabled="!hasMore"
          class="rounded-md border border-border-subtle px-3 py-1.5 text-sm text-text-muted transition-colors hover:bg-bg-hover disabled:opacity-50"
          @click="historyPage++"
        >
          Next
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { getSentimentLatest, getSentimentHistory, getLatestNews } from '../api/sentiment'
import { getWatchlist } from '../api/watchlist'
import { runFullAnalysis } from '../api/analysis'
import type {
  SentimentResult,
  WatchlistEntry,
  NewsArticle,
  CompositeAnalysis,
  AnalysisProgress,
  FullAnalysisResult,
} from '../api/types'
import SentimentBadge from '../components/SentimentBadge.vue'
import SentimentTimeline from '../components/SentimentTimeline.vue'
import ArticleBrowser from '../components/ArticleBrowser.vue'
import AnalysisAccordion from '../components/AnalysisAccordion.vue'

const route = useRoute()

const tabs = [
  { key: 'overview', label: 'Overview' },
  { key: 'news', label: 'News' },
  { key: 'history', label: 'History' },
]
const activeTab = ref('overview')

const symbolInput = ref('RELIANCE')

// Pre-fill symbol from query param (e.g. when navigating from SignalCard)
onMounted(async () => {
  const symbol = route.query.symbol as string | undefined
  if (symbol) {
    await nextTick()
    symbolInput.value = symbol.toUpperCase()
  }
  const wr = await getWatchlist()
  if (wr.success && wr.data) watchlistSymbols.value = wr.data
})
const loading = ref(false)
const error = ref('')
const sentiment = ref<SentimentResult | null>(null)
const newsArticles = ref<NewsArticle[]>([])
const newsLoading = ref(false)

// Composite analysis state
const composite = ref<CompositeAnalysis | null>(null)
const compositeLoading = ref(false)

// Full analysis orchestration state
const analysisProgress = ref<AnalysisProgress[]>([])
const analysisComplete = ref(false)
const analysisDurationMs = ref(0)
const analysisError = ref<string | null>(null)
const orchestrating = ref(false)

const historyLoading = ref(false)
const history = ref<SentimentResult[]>([])
const historyPage = ref(0)
const hasMore = ref(true)

const showHistoricalSentiment = ref(false)
const watchlistSymbols = ref<WatchlistEntry[]>([])

const loadQuick = async () => {
  if (!symbolInput.value.trim()) return
  loading.value = true
  error.value = ''
  sentiment.value = null
  newsArticles.value = []
  try {
    const [sentRes, newsRes] = await Promise.all([
      getSentimentLatest(symbolInput.value),
      getLatestNews(symbolInput.value),
    ])

    if (sentRes.success && sentRes.data) {
      sentiment.value = sentRes.data
    } else {
      error.value = sentRes.error || 'Failed to load sentiment'
    }

    if (newsRes.success && newsRes.data) {
      newsArticles.value = newsRes.data
    }
  } finally {
    loading.value = false
  }
}

const runAnalysis = async () => {
  if (!symbolInput.value.trim()) return
  orchestrating.value = true
  error.value = ''
  composite.value = null
  analysisProgress.value = []
  analysisComplete.value = false
  analysisDurationMs.value = 0
  analysisError.value = null
  try {
    for await (const data of runFullAnalysis(symbolInput.value)) {
      const evt = (data as any)._eventType
      if (evt === 'complete') {
        const result = data as FullAnalysisResult
        composite.value = result.composite
        analysisDurationMs.value = result.durationMs
        analysisComplete.value = true
      } else if (evt === 'progress') {
        const stage = data as AnalysisProgress
        const idx = analysisProgress.value.findIndex((s) => s.stageNumber === stage.stageNumber)
        if (idx >= 0) {
          // Update existing stage entry (running → completed)
          const next = [...analysisProgress.value]
          next[idx] = stage
          analysisProgress.value = next
        } else {
          analysisProgress.value = [...analysisProgress.value, stage]
        }
        await new Promise((r) => setTimeout(r, 100))
      }
    }
  } catch (e) {
    analysisError.value = e instanceof Error ? e.message : 'Analysis failed'
    error.value = analysisError.value
    analysisComplete.value = true
  } finally {
    orchestrating.value = false
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

watch(activeTab, (tab) => {
  if (tab === 'history') loadHistory()
})

const loadNews = async () => {
  if (!symbolInput.value.trim()) return
  newsLoading.value = true
  error.value = ''
  try {
    const res = await getLatestNews(symbolInput.value)
    if (res.success && res.data) {
      newsArticles.value = res.data
    } else {
      error.value = res.error || 'Failed to fetch news'
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'Failed to fetch news'
  } finally {
    newsLoading.value = false
  }
}
</script>
