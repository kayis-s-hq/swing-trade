<template>
  <div class="p-6 animate-fade-in">
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
      <!-- Summary bar -->
      <div class="card-panel p-4">
        <div class="flex items-center gap-4 text-sm">
          <span class="text-text-muted">Symbol:</span>
          <span class="font-semibold text-text-primary">{{ symbolInput }}</span>
          <span class="text-text-muted">|</span>
          <span class="text-text-muted">Articles:</span>
          <span class="font-semibold text-text-primary">{{ articles.length }}</span>
          <span class="text-text-muted">|</span>
          <span class="text-text-muted">Sources:</span>
          <span class="font-semibold text-text-primary">{{ sourceCount }}</span>
        </div>
      </div>

      <!-- Article list -->
      <div class="flex flex-col gap-3">
        <div
          v-for="(article, index) in articles"
          :key="index"
          class="card-panel p-4 transition-colors hover:bg-bg-hover/50"
        >
          <!-- Article header -->
          <div class="flex items-start justify-between gap-3">
            <div class="flex-1">
              <div class="mb-1 flex items-center gap-2">
                <span class="rounded bg-brand/10 px-2 py-0.5 text-xs font-medium text-brand">
                  {{ article.source }}
                </span>
                <span v-if="article.publishedDate" class="text-xs text-text-muted">
                  {{ formatDate(article.publishedDate) }}
                </span>
              </div>
              <h3 class="text-sm font-medium text-text-primary">
                {{ article.title }}
              </h3>
            </div>
            <button
              class="shrink-0 text-text-muted transition-colors hover:text-text-primary"
              @click="toggleArticle(index)"
            >
              <svg
                class="h-5 w-5 transition-transform"
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

          <!-- Expandable content -->
          <div v-if="expandedArticles.has(index)" class="mt-3 animate-fade-in">
            <p v-if="article.description" class="text-sm text-text-secondary leading-relaxed">
              {{ article.description }}
            </p>
            <p
              v-if="article.rawContent && article.rawContent !== article.description"
              class="mt-2 text-sm text-text-secondary leading-relaxed"
            >
              {{ article.rawContent }}
            </p>
            <a
              v-if="article.link"
              :href="article.link"
              target="_blank"
              rel="noopener noreferrer"
              class="mt-2 inline-block text-xs text-brand hover:underline"
            >
              Read full article &rarr;
            </a>
          </div>
        </div>
      </div>
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
import { getLatestNews, getWatchlist } from '../api/client'
import type { NewsArticle, WatchlistEntry } from '../api/types'

const symbolInput = ref('')
const loading = ref(false)
const error = ref('')
const articles = ref<NewsArticle[]>([])
const expandedArticles = ref(new Set<number>())
const watchlistSymbols = ref<WatchlistEntry[]>([])

const sourceCount = computed(() => {
  const sources = new Set(articles.value.map((a: NewsArticle) => a.source))
  return sources.size
})

onMounted(async () => {
  const wr = await getWatchlist()
  if (wr.success && wr.data) watchlistSymbols.value = wr.data
})

async function fetchNews() {
  if (!symbolInput.value.trim()) return
  loading.value = true
  error.value = ''
  expandedArticles.value = new Set()
  try {
    const res = await getLatestNews(symbolInput.value)
    if (res.success && res.data) {
      articles.value = res.data
    } else {
      error.value = res.error || 'Failed to fetch news'
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'Failed to fetch news'
  } finally {
    loading.value = false
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
