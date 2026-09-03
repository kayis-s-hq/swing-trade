<template>
  <div class="view-shell p-4 sm:p-6 animate-fade-in">
    <!-- Page Header -->
    <div class="mb-6 flex items-center justify-between">
      <div>
        <h1 class="font-display text-2xl font-semibold text-text-primary">News Analysis</h1>
        <p class="mt-1 text-sm text-text-muted">Stock-specific news from Google News</p>
      </div>
    </div>

    <!-- Symbol Selector -->
    <div class="mb-6 card-panel p-5">
      <h3 class="mb-3 text-sm font-semibold text-text-primary">Select Symbol</h3>
      <form class="flex flex-col sm:flex-row gap-3" @submit.prevent="fetchNews">
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
          {{ loading ? 'Loading...' : 'Fetch News' }}
        </button>
      </form>
      <p v-if="error" class="mt-3 text-xs text-danger">
        {{ error }}
      </p>
    </div>

    <!-- Loading state -->
    <div v-if="loading" class="flex justify-center py-12">
      <div class="h-6 w-6 animate-spin rounded-full border-2 border-brand border-t-transparent" />
    </div>

    <!-- Results -->
    <div v-else-if="articles.length > 0" class="flex flex-col gap-4">
      <section
        class="news-history overflow-hidden rounded-xl border border-border-subtle bg-bg-surface"
      >
        <header class="border-b border-border-subtle px-5 py-5 sm:px-6">
          <div class="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
            <div>
              <p class="news-kicker">Coverage history</p>
              <div class="mt-1 flex items-center gap-3">
                <h2 class="font-display text-lg font-semibold tracking-tight text-text-primary">
                  Latest reporting
                </h2>
                <span
                  class="rounded-full bg-brand/10 px-2.5 py-1 text-xs font-semibold text-brand"
                  >{{ displayedSymbol }}</span
                >
              </div>
              <p class="mt-1 text-sm text-text-muted">
                A focused archive of recent market coverage for this symbol.
              </p>
            </div>
            <div class="flex items-center gap-2 text-xs">
              <div class="rounded-lg border border-border-subtle bg-bg-primary/40 px-3 py-2">
                <span class="block text-text-muted">Articles</span>
                <strong class="mt-0.5 block text-sm text-text-primary">{{
                  articles.length
                }}</strong>
              </div>
              <div class="rounded-lg border border-border-subtle bg-bg-primary/40 px-3 py-2">
                <span class="block text-text-muted">Sources</span>
                <strong class="mt-0.5 block text-sm text-text-primary">{{ sourceCount }}</strong>
              </div>
            </div>
          </div>
          <div class="mt-3 flex items-center gap-2 text-xs text-text-muted">
            <span>Symbol:</span>
            <span class="font-semibold text-text-primary">{{ displayedSymbol }}</span>
            <span class="text-border-default">•</span>
            <span>Newest results first</span>
          </div>
        </header>

        <div class="divide-y divide-border-subtle/70">
          <article
            v-for="(article, index) in articles"
            :key="article.link || `${article.title}-${index}`"
            class="news-history-item group p-5 transition-colors duration-150 hover:bg-bg-hover/45 sm:p-6"
          >
            <div class="flex gap-4">
              <div class="news-timeline-marker mt-1 hidden shrink-0 sm:block" aria-hidden="true">
                <span class="block h-2.5 w-2.5 rounded-full bg-brand ring-4 ring-brand/10" />
              </div>
              <div class="min-w-0 flex-1">
                <div class="mb-2 flex flex-wrap items-center gap-2">
                  <span
                    class="rounded-md bg-brand/10 px-2 py-1 text-[11px] font-semibold uppercase tracking-[0.08em] text-brand"
                  >
                    {{ article.source }}
                  </span>
                  <span v-if="article.publishedDate" class="text-xs text-text-muted">
                    {{ formatDate(article.publishedDate) }}
                  </span>
                </div>
                <h3 class="text-base font-semibold leading-6 text-text-primary">
                  {{ article.title }}
                </h3>
                <p
                  v-if="!expandedArticles.has(index) && article.description"
                  class="mt-2 line-clamp-2 text-sm leading-6 text-text-secondary"
                >
                  {{ article.description }}
                </p>
                <div v-if="expandedArticles.has(index)" class="mt-3 animate-fade-in">
                  <p v-if="article.description" class="text-sm leading-relaxed text-text-secondary">
                    {{ article.description }}
                  </p>
                  <p
                    v-if="article.rawContent && article.rawContent !== article.description"
                    class="mt-2 text-sm leading-relaxed text-text-secondary"
                  >
                    {{ article.rawContent }}
                  </p>
                  <a
                    v-if="article.link"
                    :href="article.link"
                    target="_blank"
                    rel="noopener noreferrer"
                    class="mt-3 inline-flex items-center gap-1 text-xs font-semibold text-brand hover:underline"
                  >
                    Read full article <span aria-hidden="true">↗</span>
                  </a>
                </div>
              </div>
              <button
                class="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg text-text-muted transition-colors duration-150 hover:bg-bg-hover hover:text-text-primary active:scale-[0.97]"
                :aria-label="
                  expandedArticles.has(index)
                    ? `Collapse ${article.title}`
                    : `Expand ${article.title}`
                "
                :aria-expanded="expandedArticles.has(index)"
                @click="toggleArticle(index)"
              >
                <svg
                  class="h-4 w-4 transition-transform duration-150"
                  :class="{ 'rotate-180': expandedArticles.has(index) }"
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
              </button>
            </div>
          </article>
        </div>
      </section>
    </div>

    <!-- Empty state -->
    <div v-else class="card-panel p-5">
      <p class="text-sm text-text-muted">
        No news articles found. Enter a symbol and click Fetch News.
      </p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { getLatestNews } from '../api/client'
import { getWatchlist } from '../api/watchlist'
import type { NewsArticle, WatchlistEntry } from '../api/types'

const symbolInput = ref('')
const displayedSymbol = ref('')
let requestSequence = 0
const loading = ref(false)
const error = ref('')
const articles = ref<NewsArticle[]>([])
const expandedArticles = ref(new Set<number>())
const watchlistSymbols = ref<WatchlistEntry[]>([])
function confirmed<T>(value: T | { success: boolean; data?: T }): T | undefined {
  if (typeof value === 'object' && value !== null && 'success' in value)
    return value.success ? value.data : undefined
  return value as T
}

const sourceCount = computed(() => {
  const sources = new Set(articles.value.map((a: NewsArticle) => a.source))
  return sources.size
})

onMounted(async () => {
  watchlistSymbols.value = await getWatchlist()
})

async function fetchNews() {
  const symbol = symbolInput.value.trim().toUpperCase()
  if (!symbol) return
  const sequence = ++requestSequence
  loading.value = true
  error.value = ''
  try {
    const res = await getLatestNews(symbol)
    if (sequence !== requestSequence) return
    const data = confirmed<NewsArticle[]>(res)
    if (data) {
      articles.value = data
      displayedSymbol.value = symbol
      expandedArticles.value = new Set()
    } else {
      error.value = 'Couldn’t fetch news. Try again.'
    }
  } catch {
    if (sequence === requestSequence) error.value = 'Couldn’t fetch news. Try again.'
  } finally {
    if (sequence === requestSequence) loading.value = false
  }
}

function toggleArticle(index: number) {
  const next = new Set(expandedArticles.value)
  if (next.has(index)) {
    next.delete(index)
  } else {
    next.add(index)
  }
  expandedArticles.value = next
}

function formatDate(dateStr: string): string {
  try {
    const date = new Date(dateStr)
    return date.toLocaleDateString('en-IN', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    })
  } catch {
    return dateStr
  }
}
</script>

<style scoped>
.news-kicker {
  color: var(--color-brand);
  font-size: 0.68rem;
  font-weight: 700;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.news-history-item {
  transition-timing-function: var(--ease-out-strong);
}

.news-timeline-marker {
  position: relative;
}

.news-timeline-marker::after {
  position: absolute;
  top: 0.8rem;
  bottom: -2rem;
  left: 0.3rem;
  width: 1px;
  background: var(--color-border-subtle);
  content: '';
}

.news-history-item:last-child .news-timeline-marker::after {
  display: none;
}
</style>
