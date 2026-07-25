<template>
  <div class="flex flex-col gap-4">
    <!-- Filter bar -->
    <div class="flex flex-wrap items-center gap-2">
      <span class="text-xs font-medium text-text-muted">Sources:</span>
      <button
        @click="activeFilter = 'all'"
        class="rounded-full px-3 py-1 text-xs font-medium transition-colors"
        :class="activeFilter === 'all' ? 'bg-brand/10 text-brand' : 'bg-bg-elevated text-text-muted hover:bg-bg-hover'"
      >
        All ({{ articles.length }})
      </button>
      <button
        v-for="source in uniqueSources"
        :key="source"
        @click="activeFilter = source"
        class="rounded-full px-3 py-1 text-xs font-medium transition-colors"
        :class="activeFilter === source ? 'bg-brand/10 text-brand' : 'bg-bg-elevated text-text-muted hover:bg-bg-hover'"
      >
        {{ source }} ({{ sourceCounts[source] }})
      </button>
    </div>

    <!-- Loading -->
    <div v-if="loading" class="flex justify-center py-12">
      <div class="h-6 w-6 animate-spin rounded-full border-2 border-brand border-t-transparent" />
    </div>

    <!-- Article list -->
    <div v-else-if="filteredArticles.length" class="flex flex-col gap-3">
      <div
        v-for="article in filteredArticles"
        :key="article.link || article.title"
        class="card-panel p-4 transition-colors hover:bg-bg-hover/50"
      >
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
            <h3 class="text-sm font-medium text-text-primary">{{ article.title }}</h3>
          </div>
          <button
            @click="toggleExpanded(article.link || article.title)"
            class="shrink-0 text-text-muted transition-colors hover:text-text-primary"
          >
            <svg
              class="h-5 w-5 transition-transform"
              :class="{ 'rotate-180': expandedKeys.has(article.link || article.title) }"
              fill="none"
              viewBox="0 0 24 24"
              stroke="currentColor"
            >
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7" />
            </svg>
          </button>
        </div>

        <div v-if="expandedKeys.has(article.link || article.title)" class="mt-3 animate-fade-in">
          <p v-if="article.description" class="text-sm text-text-secondary leading-relaxed">
            {{ article.description }}
          </p>
          <p v-if="article.rawContent && article.rawContent !== article.description" class="mt-2 text-sm text-text-secondary leading-relaxed">
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

    <!-- Empty state -->
    <div v-else class="card-panel p-5">
      <p class="text-sm text-text-muted">No news articles found for this symbol.</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import type { NewsArticle } from '../api/types'

const props = defineProps<{
  articles: NewsArticle[]
  loading?: boolean
}>()

const activeFilter = ref('all')
const expandedKeys = ref(new Set<string>())

const uniqueSources = computed(() => {
  const sources = new Set(props.articles.map(a => a.source))
  return Array.from(sources).sort()
})

const sourceCounts = computed(() => {
  const counts: Record<string, number> = {}
  props.articles.forEach(a => {
    counts[a.source] = (counts[a.source] || 0) + 1
  })
  return counts
})

const filteredArticles = computed(() => {
  if (activeFilter.value === 'all') return props.articles
  return props.articles.filter(a => a.source === activeFilter.value)
})

const toggleExpanded = (key: string) => {
  const next = new Set(expandedKeys.value)
  if (next.has(key)) {
    next.delete(key)
  } else {
    next.add(key)
  }
  expandedKeys.value = next
}

const formatDate = (dateStr: string) => {
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