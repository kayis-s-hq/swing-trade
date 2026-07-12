# Sentiment Analysis Dashboard Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a story-flow detail view to SentimentView showing news sources, LLM analysis, and context panel (technical signal agreement, confidence breakdown, historical trend).

**Architecture:** Extend the existing SentimentView.vue with three new sections (News Sources, expanded LLM Analysis, Context Panel) and two new Vue components. No backend changes needed — all data already available through existing APIs.

**Tech Stack:** Vue 3 + TypeScript + Composition API, Tailwind CSS v4, existing API client patterns.

## Global Constraints

- Follow existing component patterns: inline `<script setup lang="ts">`, Tailwind classes, card-panel utility class for panels
- No new backend endpoints — use `GET /api/news/{symbol}/latest`, `GET /api/sentiment/{symbol}/latest`, `GET /api/signals/symbol/{symbol}`, `GET /api/sentiment/{symbol}/history`
- TDD where applicable, but UI components are primarily integration-tested via the running app
- Keep changes minimal — extend SentimentView, don't refactor existing working code

---

### Task 1: Add `getLatestSignalForSymbol` API function

**Files:**
- Modify: `dashboard/src/api/client.ts:263-268`

**Interfaces:**
- Consumes: existing `rawFetch` and `BackendSignal` type
- Produces: `getLatestSignalForSymbol(symbol: string): Promise<ApiResponse<Signal>>`

**Step 1: Add the API function**

After line 268 in `client.ts`, add:

```typescript
export async function getLatestSignalForSymbol(symbol: string): Promise<ApiResponse<Signal>> {
  const raw = await rawFetch(`/signals/symbol/${symbol}`)
  if (!raw.ok) return errResponse(raw.error!)

  const data = (raw.data as BackendSignal[])
  const latest = data.length > 0 ? data[data.length - 1] : null
  if (!latest) return { success: true, data: null }

  return { success: true, data: mapSignal(latest) }
}
```

`mapSignal` is already defined at line ~100 of client.ts and handles the `BackendSignal → Signal` conversion.

**Step 2: Verify TypeScript compiles**

Run: `cd dashboard && npm run typecheck`
Expected: PASS, no errors

**Step 3: Commit**

```bash
git add dashboard/src/api/client.ts
git commit -m "feat(api): add getLatestSignalForSymbol for sentiment context panel"
```

---

### Task 2: Create `NewsSourceCard.vue` component

**Files:**
- Create: `dashboard/src/components/NewsSourceCard.vue`

**Interfaces:**
- Consumes: `NewsArticle` type from `../api/types`
- Produces: component with `article: NewsArticle` prop

**Step 1: Write the component**

Create `dashboard/src/components/NewsSourceCard.vue`:

```vue
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
```

**Step 2: Verify it renders**

Start dev server: `cd dashboard && npm run dev`
Navigate to SentimentView, enter a symbol, click "View". The news section should show article cards.

**Step 3: Commit**

```bash
git add dashboard/src/components/NewsSourceCard.vue
git commit -m "feat(ui): add NewsSourceCard component for sentiment analysis dashboard"
```

---

### Task 3: Create `ContextPanel.vue` component

**Files:**
- Create: `dashboard/src/components/ContextPanel.vue`

**Interfaces:**
- Consumes: `Signal` type (technical signal), `SentimentResult` (sentiment data), `number` (article count)
- Produces: component with three sub-sections: technical agreement, confidence breakdown, historical trend

**Step 1: Write the component**

Create `dashboard/src/components/ContextPanel.vue`:

```vue
<template>
  <div class="card-panel p-5">
    <h3 class="mb-4 text-sm font-semibold text-text-primary">Analysis Context</h3>

    <!-- Technical Signal Agreement -->
    <div class="mb-4">
      <h4 class="mb-2 text-xs font-semibold uppercase tracking-wider text-text-muted">Technical Signal</h4>
      <div v-if="signal" class="flex items-center gap-3">
        <span
          class="inline-flex items-center gap-1 rounded-full px-3 py-1 text-xs font-semibold"
          :class="signalBadgeClass"
        >
          {{ signal.signalType }}
        </span>
        <span
          class="text-xs font-medium"
          :class="agreementClass"
        >
          {{ agreementText }}
        </span>
      </div>
      <p v-else class="text-xs text-text-muted">No technical signal available</p>
    </div>

    <!-- Confidence Breakdown -->
    <div class="mb-4">
      <h4 class="mb-2 text-xs font-semibold uppercase tracking-wider text-text-muted">Confidence Breakdown</h4>
      <div class="grid grid-cols-3 gap-3 text-center">
        <div class="rounded-lg bg-bg-primary p-2">
          <div class="text-lg font-semibold text-text-primary">{{ confidencePct }}%</div>
          <div class="text-[10px] text-text-muted">Confidence</div>
        </div>
        <div class="rounded-lg bg-bg-primary p-2">
          <div class="text-lg font-semibold text-text-primary">{{ articleCount }}</div>
          <div class="text-[10px] text-text-muted">Articles</div>
        </div>
        <div class="rounded-lg bg-bg-primary p-2">
          <div class="text-lg font-semibold text-text-primary">{{ summary.length }} chars</div>
          <div class="text-[10px] text-text-muted">Summary</div>
        </div>
      </div>
    </div>

    <!-- Historical Trend -->
    <div>
      <h4 class="mb-2 text-xs font-semibold uppercase tracking-wider text-text-muted">Recent Trend</h4>
      <div v-if="trend.length" class="flex items-center gap-1">
        <div
          v-for="(item, i) in trend"
          :key="i"
          class="h-6 w-4 rounded-t-sm transition-colors"
          :class="trendColor(item.score)"
          :title="`${item.date}: ${item.score}`"
        />
      </div>
      <p v-else class="text-xs text-text-muted">No historical data</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { Signal } from '../api/types'
import type { SentimentResult } from '../api/types'

const props = defineProps<{
  signal: Signal | null
  sentiment: SentimentResult
  articleCount: number
  trend: SentimentResult[]
}>()

const confidencePct = computed(() => Math.round((props.sentiment.confidence ?? 0) * 100))

const agreementText = computed(() => {
  if (!props.signal) return 'No signal to compare'
  const sentimentMatchesBuy = props.sentiment.score === 'POSITIVE'
  const sentimentMatchesSell = props.sentiment.score === 'NEGATIVE'
  const sentimentNeutral = props.sentiment.score === 'NEUTRAL'

  if (props.signal.signalType === 'BUY' && sentimentMatchesBuy) return 'Agrees — bullish sentiment'
  if (props.signal.signalType === 'SELL' && sentimentMatchesSell) return 'Agrees — bearish sentiment'
  if (props.signal.signalType === 'BUY' && sentimentMatchesSell) return 'Conflicts — bearish sentiment vs buy signal'
  if (props.signal.signalType === 'SELL' && sentimentMatchesBuy) return 'Conflicts — bullish sentiment vs sell signal'
  return 'Neutral — no strong sentiment'
})

const agreementClass = computed(() => {
  if (!props.signal) return 'text-text-muted'
  const sentimentMatchesBuy = props.sentiment.score === 'POSITIVE'
  const sentimentMatchesSell = props.sentiment.score === 'NEGATIVE'

  if (props.signal.signalType === 'BUY' && sentimentMatchesBuy) return 'text-success'
  if (props.signal.signalType === 'SELL' && sentimentMatchesSell) return 'text-success'
  if (props.signal.signalType === 'BUY' && sentimentMatchesSell) return 'text-danger'
  if (props.signal.signalType === 'SELL' && sentimentMatchesBuy) return 'text-danger'
  return 'text-text-muted'
})

const signalBadgeClass = computed(() => ({
  BUY: 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-400',
  SELL: 'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-400',
}[props.signal?.signalType ?? 'BUY']))

const trendColor = (score: string) => ({
  POSITIVE: 'bg-green-500',
  NEUTRAL: 'bg-amber-500',
  NEGATIVE: 'bg-red-500',
}[score] as string)
</script>
```

**Step 2: Verify TypeScript compiles**

Run: `cd dashboard && npm run typecheck`
Expected: PASS, no errors

**Step 3: Commit**

```bash
git add dashboard/src/components/ContextPanel.vue
git commit -m "feat(ui): add ContextPanel with technical agreement, confidence breakdown, historical trend"
```

---

### Task 4: Wire up SentimentView with all four sections

**Files:**
- Modify: `dashboard/src/views/SentimentView.vue`

**Interfaces:**
- Consumes: `getLatestSignalForSymbol` from client, `NewsSourceCard` component, `ContextPanel` component
- Produces: updated view with News Sources, LLM Analysis, and Context Panel sections

**Step 1: Add imports and state variables**

In the `<script setup>` section, add:

```typescript
import NewsSourceCard from '../components/NewsSourceCard.vue'
import ContextPanel from '../components/ContextPanel.vue'
import { getLatestSignalForSymbol } from '../api/client'

const newsArticles = ref<NewsArticle[]>([])
const latestSignal = ref<Signal | null>(null)
```

**Step 2: Update `loadSentiment` to fetch news and signal**

Replace the existing `loadSentiment` function:

```typescript
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
```

**Step 3: Update the template**

Replace the entire template section with the four-section layout:

```vue
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

    <!-- Detail Tab -->
    <div v-if="activeTab === 'detail'">
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

      <!-- News Sources Section -->
      <div v-if="!loading && !analyzing && newsArticles.length" class="mb-6">
        <h3 class="mb-3 text-sm font-semibold text-text-primary">News Sources</h3>
        <div class="space-y-2">
          <NewsSourceCard v-for="article in newsArticles" :key="article.link" :article="article" />
        </div>
      </div>

      <!-- Sentiment Result -->
      <div v-if="!loading && !analyzing && sentiment">
        <!-- LLM Analysis -->
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
      <div v-if="!sentiment && !loading && !analyzing" class="card-panel p-5">
        <p class="text-sm text-text-muted">No data available. Enter a symbol to view sentiment.</p>
      </div>
    </div>

    <!-- History Tab (unchanged) -->
    <div v-if="activeTab === 'history'">
      <div v-if="historyLoading" class="flex justify-center py-12">
        <div class="h-6 w-6 animate-spin rounded-full border-2 border-brand border-t-transparent" />
      </div>
      <SentimentTimeline v-else-if="history.length" :items="history" />
      <div v-else class="card-panel p-5">
        <p class="text-sm text-text-muted">No sentiment history available.</p>
      </div>
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
```

**Step 4: Add missing type imports**

In the script imports, add:

```typescript
import type { NewsArticle, Signal } from '../api/types'
```

**Step 5: Verify TypeScript compiles and app runs**

Run: `cd dashboard && npm run typecheck`
Expected: PASS, no errors

Start dev server: `cd dashboard && npm run dev`
Expected: No errors, SentimentView renders with all sections

**Step 6: Manual verification**

1. Open http://localhost:3003 in browser
2. Navigate to Sentiment page
3. Enter a symbol (e.g. RELIANCE) and click "View"
4. Verify: News Sources section shows articles with clickable links
5. Verify: LLM Analysis shows score, summary, red flags, catalysts
6. Verify: Context Panel shows technical signal agreement, confidence breakdown, historical trend
7. Click "Refresh" to trigger live analysis and verify all sections update

**Step 7: Commit**

```bash
git add dashboard/src/views/SentimentView.vue
git commit -m "feat(ui): wire SentimentView with news sources, context panel, and full analysis flow"
```

---

### Task 5: Fix 500 error on sentiment latest endpoint

**Files:**
- Modify: `backend/api/src/main/java/com/swingtrade/api/controller/SentimentApiController.java:55-64`

**Interfaces:**
- Consumes: `SentimentResultRepository.findLatestBySymbol(symbol)`
- Produces: proper 200 response with default sentiment when no data exists

**Step 1: Identify the issue**

The `findLatestBySymbol` query uses `ORDER BY s.date DESC LIMIT 1` but the database may have no `sentiment_results` rows, or the symbol parameter may be case-sensitive. The 500 error is likely from `SentimentResult.SentimentScore.valueOf(sentimentScore)` receiving an unexpected value, or the query itself failing.

**Step 2: Add defensive handling**

In `SentimentApiController.java`, replace the `getLatestSentiment` method (lines 55-64):

```java
@GetMapping("/sentiment/{symbol}/latest")
public ResponseEntity<ApiResponse<SentimentResult>> getLatestSentiment(
        @PathVariable String symbol) {
    return sentimentRepo.findLatestBySymbol(symbol.toUpperCase())
        .map(e -> ResponseEntity.ok(ApiResponse.ok(e.toDomain())))
        .orElseGet(() -> ResponseEntity.ok(ApiResponse.ok(SentimentResult.create(
            symbol.toUpperCase(), java.time.LocalDate.now(),
            SentimentResult.SentimentScore.NEUTRAL, "No data", "", 0.0,
            List.of(), List.of()))));
}
```

Also fix `getSentimentHistory` similarly:

```java
@GetMapping("/sentiment/{symbol}/history")
public ResponseEntity<ApiResponse<List<SentimentResult>>> getSentimentHistory(
        @PathVariable String symbol,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) {
    PageRequest pageable = PageRequest.of(page, size, Sort.by("date").descending());
    List<SentimentResult> results = sentimentRepo.findAllBySymbol(symbol.toUpperCase(), pageable)
        .stream().map(SentimentResultEntity::toDomain).toList();
    return ResponseEntity.ok(ApiResponse.ok(results));
}
```

**Step 3: Build and test**

Run: `cd backend && mvn clean install -DskipTests`
Expected: BUILD SUCCESS

**Step 4: Commit**

```bash
git add backend/api/src/main/java/com/swingtrade/api/controller/SentimentApiController.java
git commit -m "fix(api): normalize symbol case in sentiment endpoints to prevent 500 errors"
```