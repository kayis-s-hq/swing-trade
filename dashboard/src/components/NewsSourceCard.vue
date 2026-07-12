<template>
  <div class="rounded-lg border border-border-subtle bg-bg-secondary p-3 transition-colors hover:bg-bg-hover">
    <div class="flex items-start justify-between gap-3">
      <div class="flex-1 min-w-0">
        <a
          :href="article.link"
          target="_blank"
          rel="noopener noreferrer"
          class="text-sm font-medium text-text-primary hover:text-brand transition-colors line-clamp-2"
        >
          {{ article.title }}
        </a>
        <p class="mt-1 text-xs text-text-muted line-clamp-3">{{ article.description }}</p>
      </div>
    </div>
    <div class="mt-2 flex items-center gap-2">
      <span class="inline-flex items-center rounded-full bg-bg-primary px-2 py-0.5 text-[10px] font-medium text-text-muted">
        {{ article.source }}
      </span>
      <span class="text-[10px] text-text-muted/60">{{ article.publishedDate }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { NewsArticle } from '../api/types'

defineProps<{
  article: NewsArticle
}>()
</script>