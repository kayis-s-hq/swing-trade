# Sentiment Pipeline Accordion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make each pipeline stage in the Sentiment page expandable with structured summary cards and a formatted details view, and merge the old separate LLM analysis block into the LLM sentiment pipeline stage.

**Architecture:** Add accordion behavior to the existing `AnalysisProgress` component. Each stage row becomes clickable, opening a detail panel with Summary and Details tabs. The `SentimentView` wiring maps pipeline progress events to structured per-stage data. The old LLM block is moved to the bottom of the Overview tab.

**Tech Stack:** Vue 3.5 + TypeScript, Tailwind CSS v4, Composition API

## Global Constraints

- Use Java 21 via sdkman for Maven builds: `source "$HOME/.sdkman/bin/sdkman-init.sh"`
- Frontend uses Vue 3.5 + TypeScript + Composition API with `defineProps`
- All components use `card-panel` class for card styling
- Color tokens: `text-success`, `text-danger`, `text-warning`, `text-brand`, `text-text-muted`, `text-text-primary`, `text-text-secondary`
- No new dependencies — use only existing Vue reactivity and Tailwind classes
- Mobile: identical behavior, no special handling

---

### Task 1: Add accordion state and click handler to AnalysisProgress

**Files:**
- Modify: `dashboard/src/components/AnalysisProgress.vue`

**Interfaces:**
- Consumes: existing `Props` (stages, currentStage, isComplete, durationMs, error)
- Produces: `expandedStage: number | null`, `detailView: Record<number, 'summary' | 'details'>`, `handleToggleStage(stageNumber: number)`

- [ ] **Step 1: Add accordion state and click handler**

Add to the `<script setup lang="ts">` section, after `const props = defineProps<Props>()`:

```typescript
const expandedStage = ref<number | null>(null)
const detailView = ref<Record<number, 'summary' | 'details'>>({})

function handleToggleStage(stageNumber: number) {
  if (expandedStage.value === stageNumber) {
    expandedStage.value = null
  } else {
    expandedStage.value = stageNumber
    if (!(stageNumber in detailView.value)) {
      detailView.value[stageNumber] = 'summary'
    }
  }
}

function toggleDetailView(stageNumber: number, view: 'summary' | 'details') {
  detailView.value[stageNumber] = view
}
```

- [ ] **Step 2: Make stage rows clickable and show chevron**

Replace the existing stage row template (lines 67-97 in the `<template>` section). The outer `<div>` for each stage needs:
- `@click="handleToggleStage(stage.stageNumber)"`
- `cursor-pointer` class
- A chevron icon that rotates 180° when expanded

The new stage row template:

```vue
<div
  v-for="stage in filteredStages"
  :key="stage.stageNumber"
  class="cursor-pointer rounded-md transition-colors"
  :class="[
    stage.stageNumber === currentStage && !isComplete ? 'bg-bg-hover' : '',
    expandedStage === stage.stageNumber ? 'bg-bg-hover' : 'hover:bg-bg-hover/50'
  ]"
  @click="handleToggleStage(stage.stageNumber)"
>
  <!-- Row header -->
  <div class="flex items-center gap-3 px-3 py-2">
    <!-- Status icon -->
    <span
      class="flex h-6 w-6 shrink-0 items-center justify-center rounded-full text-xs"
      :class="stageColors(stage.status, stage.stageNumber === currentStage && !isComplete)"
    >
      <span v-if="stage.status === 'running' && !isComplete" class="animate-spin">⟳</span>
      <span v-else>{{ stageIcons(stage.status) }}</span>
    </span>

    <!-- Stage info -->
    <div class="min-w-0 flex-1">
      <div class="flex items-center gap-2">
        <span
          class="text-sm font-medium"
          :class="stageColors(stage.status, stage.stageNumber === currentStage && !isComplete)"
        >
          {{ stage.stageName }}
        </span>
        <span v-if="stage.status === 'skipped'" class="text-xs italic text-text-muted">skipped</span>
      </div>
      <p v-if="stage.message" class="text-xs text-text-muted">
        {{ stage.message }}
      </p>
    </div>

    <!-- Chevron -->
    <svg
      class="h-4 w-4 shrink-0 text-text-muted transition-transform"
      :class="{ 'rotate-180': expandedStage === stage.stageNumber }"
      fill="none"
      viewBox="0 0 24 24"
      stroke="currentColor"
    >
      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7" />
    </svg>
  </div>

  <!-- Expandable detail panel -->
  <div
    v-if="expandedStage === stage.stageNumber"
    class="animate-fade-in border-t border-border-subtle/50 px-3 py-3"
  >
    <!-- Tab toggle -->
    <div class="mb-3 flex gap-1 rounded bg-bg-primary p-1">
      <button
        @click.stop="toggleDetailView(stage.stageNumber, 'summary')"
        class="rounded px-3 py-1 text-xs font-medium transition-colors"
        :class="detailView[stage.stageNumber] === 'summary' ? 'bg-brand/10 text-brand' : 'text-text-muted hover:text-text-primary'"
      >
        Summary
      </button>
      <button
        @click.stop="toggleDetailView(stage.stageNumber, 'details')"
        class="rounded px-3 py-1 text-xs font-medium transition-colors"
        :class="detailView[stage.stageNumber] === 'details' ? 'bg-brand/10 text-brand' : 'text-text-muted hover:text-text-primary'"
      >
        Details
      </button>
    </div>

    <!-- Summary tab -->
    <div v-if="detailView[stage.stageNumber] === 'summary'" class="space-y-2">
      <StageSummaryCard :stage="stage" :composite="null" />
    </div>

    <!-- Details tab -->
    <div v-else class="max-h-60 overflow-y-auto rounded bg-bg-primary p-3 text-xs font-mono text-text-secondary">
      <pre class="whitespace-pre-wrap break-words">{{ formatStageDetails(stage) }}</pre>
    </div>
  </div>
</div>
```

- [ ] **Step 3: Add `formatStageDetails` helper function**

Add to `<script setup lang="ts">`:

```typescript
function formatStageDetails(stage: AnalysisProgress): string {
  return JSON.stringify({
    stage: stage.stageName,
    number: stage.stageNumber,
    status: stage.status,
    message: stage.message,
    timestamp: stage.timestamp,
  }, null, 2)
}
```

- [ ] **Step 4: Commit**

```bash
git add dashboard/src/components/AnalysisProgress.vue
git commit -m "feat: add accordion behavior to AnalysisProgress with summary/details tabs"
```

---

### Task 2: Create StageSummaryCard component

**Files:**
- Create: `dashboard/src/components/StageSummaryCard.vue`

**Interfaces:**
- Consumes: `stage: AnalysisProgress`, `composite: CompositeAnalysis | null`
- Produces: structured summary cards per stage type

- [ ] **Step 1: Write the StageSummaryCard component**

Create `dashboard/src/components/StageSummaryCard.vue`:

```vue
<script setup lang="ts">
import { computed } from 'vue'
import type { AnalysisProgress } from '../api/types'
import type { CompositeAnalysis } from '../api/types'
import SentimentBadge from './SentimentBadge.vue'

interface Props {
  stage: AnalysisProgress
  composite: CompositeAnalysis | null
}

const props = defineProps<Props>()

const stage = computed(() => props.stage)
const composite = computed(() => props.composite)

// Parse message for structured data
const parsedData = computed(() => {
  const msg = props.stage.message || ''
  const name = props.stage.stageName.toLowerCase()

  // "Found 739 candles"
  const foundMatch = msg.match(/Found (\d+) candles?/)
  if (foundMatch) {
    return { type: 'data-check', candleCount: parseInt(foundMatch[1], 10) }
  }

  // "Backfilled: 739 → 739 candles"
  const backfillMatch = msg.match(/Backfilled:\s*(\d+)\s*→\s*(\d+)\s*candles?/)
  if (backfillMatch) {
    return {
      type: 'backfill',
      before: parseInt(backfillMatch[1], 10),
      after: parseInt(backfillMatch[2], 10),
    }
  }

  // "News articles fetched" or "Fetched N news articles"
  const newsMatch = msg.match(/Fetched\s+(\d+)\s+news?\s+articles?/i)
  if (newsMatch) {
    return { type: 'news', count: parseInt(newsMatch[1], 10) }
  }

  // "News articles fetched" (generic)
  if (name.includes('news') || name.includes('fetch')) {
    return { type: 'news', count: -1 }
  }

  // "Score: POSITIVE, confidence: 85%"
  const llmMatch = msg.match(/Score:\s*(\w+),\s*confidence:\s*(\d+)%/)
  if (llmMatch) {
    return {
      type: 'llm',
      score: llmMatch[1] as 'POSITIVE' | 'NEUTRAL' | 'NEGATIVE',
      confidence: parseInt(llmMatch[2], 10) / 100,
    }
  }

  // "Signal: SELL, score: -100"
  const techMatch = msg.match(/Signal:\s*(\w+),\s*score:\s*(-?\d+)/)
  if (techMatch) {
    return {
      type: 'technical',
      signal: techMatch[1] as 'BUY' | 'SELL' | 'HOLD',
      score: parseInt(techMatch[2], 10),
    }
  }

  // "Score: -17, signal: HOLD"
  const compositeMatch = msg.match(/Score:\s*(-?\d+),\s*signal:\s*(\w+)/)
  if (compositeMatch) {
    return {
      type: 'composite',
      score: parseInt(compositeMatch[1], 10),
      signal: compositeMatch[2] as 'BUY' | 'SELL' | 'HOLD',
    }
  }

  // "16 trades, win rate: 37.5%, return: -0.9%"
  const backtestMatch = msg.match(/(\d+)\s+trades?,\s*win rate:\s*([\d.]+)%,\s*return:\s*([-\d.]+)%/)
  if (backtestMatch) {
    return {
      type: 'backtest',
      trades: parseInt(backtestMatch[1], 10),
      winRate: parseFloat(backtestMatch[2]),
      return: parseFloat(backtestMatch[3]),
    }
  }

  // "Analysis finished in 9314ms"
  const doneMatch = msg.match(/finished in (\d+)ms/)
  if (doneMatch) {
    return { type: 'complete', durationMs: parseInt(doneMatch[1], 10) }
  }

  // "Full analysis started for RELIANCE"
  const startMatch = msg.match(/started for (\S+)/)
  if (startMatch) {
    return { type: 'start', symbol: startMatch[1] }
  }

  return { type: 'generic' as const }
})

// Get composite LLM data from composite object
const compositeLlm = computed(() => {
  if (!composite.value) return null
  return {
    score: composite.value.news.score,
    summary: composite.value.news.summary,
    catalysts: composite.value.news.catalysts,
    redFlags: composite.value.news.redFlags,
  }
})

// Get composite technical data
const compositeTech = computed(() => {
  if (!composite.value) return null
  return {
    score: composite.value.technical.score,
    signal: composite.value.technical.signal,
    confidence: composite.value.technical.confidence,
    indicators: composite.value.technical.indicators,
  }
})

// Get composite score data
const compositeScore = computed(() => {
  if (!composite.value) return null
  return {
    score: composite.value.compositeScore,
    signal: composite.value.compositeSignal,
    confidence: composite.value.compositeConfidence,
    reasoning: composite.value.reasoning,
  }
})

// Get composite backtest data
const compositeBacktest = computed(() => {
  if (!composite.value) return null
  return {
    totalTrades: composite.value.backtest.totalTrades,
    winRate: composite.value.backtest.winRate,
    profitFactor: composite.value.backtest.profitFactor,
    maxDrawdown: composite.value.backtest.maxDrawdown,
    totalReturn: composite.value.backtest.totalReturn,
    expectancy: composite.value.backtest.expectancy,
  }
})
</script>

<template>
  <!-- pipeline-start -->
  <div v-if="parsedData.type === 'start'" class="space-y-1">
    <div class="flex items-center gap-2">
      <span class="text-xs text-text-muted">Symbol:</span>
      <span class="text-sm font-semibold text-text-primary">{{ parsedData.symbol }}</span>
    </div>
    <div class="flex items-center gap-2">
      <span class="text-xs text-text-muted">Started:</span>
      <span class="text-xs text-text-secondary">{{ stage.timestamp }}</span>
    </div>
  </div>

  <!-- checking data -->
  <div v-else-if="parsedData.type === 'data-check'" class="space-y-1">
    <div class="flex items-center gap-2">
      <span class="text-xs text-text-muted">Candles found:</span>
      <span class="text-sm font-semibold text-text-primary">{{ parsedData.candleCount }}</span>
    </div>
  </div>

  <!-- backfilling OHLCV -->
  <div v-else-if="parsedData.type === 'backfill'" class="space-y-1">
    <div class="flex items-center gap-2">
      <span class="text-xs text-text-muted">Candles:</span>
      <span class="text-sm font-semibold text-text-primary">
        {{ parsedData.before }} → {{ parsedData.after }}
      </span>
    </div>
  </div>

  <!-- fetching news -->
  <div v-else-if="parsedData.type === 'news'" class="space-y-1">
    <div class="flex items-center gap-2">
      <span class="text-xs text-text-muted">Articles:</span>
      <span class="text-sm font-semibold text-text-primary">
        {{ parsedData.count > 0 ? parsedData.count : 'fetched' }}
      </span>
    </div>
  </div>

  <!-- LLM sentiment -->
  <div v-else-if="parsedData.type === 'llm'" class="space-y-2">
    <!-- Badge from SentimentResult data -->
    <div v-if="compositeLlm" class="flex items-center gap-2">
      <SentimentBadge :score="compositeLlm.score" :confidence="compositeLlm.score === 'NEUTRAL' ? 0.5 : 0.85" />
    </div>
    <p v-if="compositeLlm?.summary" class="text-xs text-text-secondary leading-relaxed">
      {{ compositeLlm.summary }}
    </p>
    <div v-if="compositeLlm?.catalysts.length" class="space-y-0.5">
      <p class="text-[10px] font-semibold uppercase tracking-wider text-success">Catalysts</p>
      <div v-for="c in compositeLlm.catalysts" :key="c" class="flex items-start gap-1.5 text-xs text-text-secondary">
        <span class="mt-1.5 h-1.5 w-1.5 rounded-full bg-success flex-shrink-0" />
        <span>{{ c }}</span>
      </div>
    </div>
    <div v-if="compositeLlm?.redFlags.length" class="space-y-0.5">
      <p class="text-[10px] font-semibold uppercase tracking-wider text-danger">Red Flags</p>
      <div v-for="r in compositeLlm.redFlags" :key="r" class="flex items-start gap-1.5 text-xs text-text-secondary">
        <span class="mt-1.5 h-1.5 w-1.5 rounded-full bg-danger flex-shrink-0" />
        <span>{{ r }}</span>
      </div>
    </div>
  </div>

  <!-- technical analysis -->
  <div v-else-if="parsedData.type === 'technical'" class="space-y-2">
    <div class="flex items-center gap-2">
      <span
        class="inline-flex items-center rounded px-2 py-0.5 text-xs font-bold"
        :class="{
          'bg-success/15 text-success': parsedData.signal === 'BUY',
          'bg-danger/15 text-danger': parsedData.signal === 'SELL',
          'bg-warning/15 text-warning': parsedData.signal === 'HOLD',
        }"
      >
        {{ parsedData.signal }}
      </span>
      <span class="text-sm font-mono font-semibold" :class="parsedData.score > 0 ? 'text-success' : parsedData.score < 0 ? 'text-danger' : 'text-warning'">
        {{ parsedData.score > 0 ? '+' : '' }}{{ parsedData.score }}
      </span>
    </div>
    <div v-if="compositeTech">
      <div class="mb-1">
        <div class="flex justify-between text-[10px] text-text-muted">
          <span>Confidence</span>
          <span>{{ Math.round(compositeTech.confidence * 100) }}%</span>
        </div>
        <div class="h-1.5 rounded-full bg-bg-primary overflow-hidden">
          <div
            class="h-full rounded-full"
            :class="compositeTech.confidence >= 0.7 ? 'bg-success' : compositeTech.confidence >= 0.4 ? 'bg-warning' : 'bg-danger'"
            :style="{ width: `${compositeTech.confidence * 100}%` }"
          />
        </div>
      </div>
      <div v-if="compositeTech.indicators.length" class="mt-2 space-y-1">
        <div
          v-for="indicator in compositeTech.indicators"
          :key="indicator"
          class="flex items-center gap-1.5 text-[11px]"
        >
          <span
            class="h-1.5 w-1.5 rounded-full flex-shrink-0"
            :class="{
              'bg-success': indicator.toLowerCase().includes('bullish') || indicator.toLowerCase().includes('above') || indicator.toLowerCase().includes('positive'),
              'bg-danger': indicator.toLowerCase().includes('bearish') || indicator.toLowerCase().includes('below') || indicator.toLowerCase().includes('negative'),
              'bg-text-muted': true,
            }"
          />
          <template v-if="indicator.includes(': ')">
            <span class="text-text-muted w-20 truncate flex-shrink-0">{{ indicator.split(': ')[0] }}</span>
            <span class="text-text-secondary">{{ indicator.split(': ')[1] }}</span>
          </template>
          <template v-else>
            <span class="text-text-secondary">{{ indicator }}</span>
          </template>
        </div>
      </div>
    </div>
  </div>

  <!-- composite score -->
  <div v-else-if="parsedData.type === 'composite'" class="space-y-2">
    <div class="flex items-center gap-2">
      <span class="text-lg font-bold font-mono" :class="compositeScore ? (compositeScore.score > 20 ? 'text-success' : compositeScore.score < -20 ? 'text-danger' : 'text-warning') : 'text-text-primary'">
        {{ compositeScore ? (compositeScore.score >= 0 ? '+' : '') + compositeScore.score : parsedData.score }}
      </span>
      <span
        class="inline-flex items-center rounded px-2 py-0.5 text-xs font-bold"
        :class="{
          'bg-success/15 text-success': parsedData.signal === 'BUY',
          'bg-danger/15 text-danger': parsedData.signal === 'SELL',
          'bg-warning/15 text-warning': parsedData.signal === 'HOLD',
        }"
      >
        {{ parsedData.signal }}
      </span>
    </div>
    <div v-if="compositeScore">
      <div class="flex justify-between text-[10px] text-text-muted">
        <span>Confidence</span>
        <span>{{ Math.round(compositeScore.confidence * 100) }}%</span>
      </div>
      <div class="h-1.5 rounded-full bg-bg-primary overflow-hidden">
        <div
          class="h-full rounded-full"
          :class="compositeScore.confidence >= 0.7 ? 'bg-success' : compositeScore.confidence >= 0.4 ? 'bg-warning' : 'bg-danger'"
          :style="{ width: `${compositeScore.confidence * 100}%` }"
        />
      </div>
    </div>
    <p v-if="compositeScore?.reasoning" class="text-xs text-text-secondary leading-relaxed">
      {{ compositeScore.reasoning }}
    </p>
  </div>

  <!-- backtest -->
  <div v-else-if="parsedData.type === 'backtest'" class="space-y-2">
    <div class="grid grid-cols-2 gap-2">
      <div class="rounded bg-bg-elevated/50 p-2">
        <p class="text-[10px] uppercase tracking-wider text-text-muted/50">Trades</p>
        <p class="text-base font-bold text-text-primary">{{ parsedData.trades }}</p>
      </div>
      <div class="rounded bg-bg-elevated/50 p-2">
        <p class="text-[10px] uppercase tracking-wider text-text-muted/50">Win Rate</p>
        <p class="text-base font-bold" :class="parsedData.winRate > 50 ? 'text-success' : parsedData.winRate < 40 ? 'text-danger' : 'text-text-primary'">
          {{ parsedData.winRate.toFixed(1) }}%
        </p>
      </div>
      <div class="rounded bg-bg-elevated/50 p-2">
        <p class="text-[10px] uppercase tracking-wider text-text-muted/50">Return</p>
        <p class="text-base font-bold" :class="parsedData.return > 0 ? 'text-success' : 'text-danger'">
          {{ parsedData.return >= 0 ? '+' : '' }}{{ parsedData.return.toFixed(1) }}%
        </p>
      </div>
      <div v-if="compositeBacktest" class="rounded bg-bg-elevated/50 p-2">
        <p class="text-[10px] uppercase tracking-wider text-text-muted/50">Profit Factor</p>
        <p class="text-base font-bold" :class="compositeBacktest.profitFactor > 1.5 ? 'text-success' : compositeBacktest.profitFactor < 0.8 ? 'text-danger' : 'text-text-primary'">
          {{ compositeBacktest.profitFactor.toFixed(2) }}
        </p>
      </div>
    </div>
  </div>

  <!-- complete -->
  <div v-else-if="parsedData.type === 'complete'" class="space-y-1">
    <div class="flex items-center gap-2">
      <span class="text-sm font-semibold text-success">Complete</span>
      <span class="text-xs text-text-muted">
        {{ parsedData.durationMs >= 1000 ? `${(parsedData.durationMs / 1000).toFixed(1)}s` : `${parsedData.durationMs}ms` }}
      </span>
    </div>
  </div>

  <!-- generic / fallback -->
  <div v-else class="text-xs text-text-secondary">
    {{ stage.message }}
  </div>
</template>
```

- [ ] **Step 2: Register the import in AnalysisProgress.vue**

Add to the imports in `AnalysisProgress.vue` `<script setup>`:

```typescript
import StageSummaryCard from './StageSummaryCard.vue'
```

- [ ] **Step 3: Commit**

```bash
git add dashboard/src/components/StageSummaryCard.vue dashboard/src/components/AnalysisProgress.vue
git commit -m "feat: add StageSummaryCard component with per-stage structured data"
```

---

### Task 3: Wire composite data to stage detail cards in AnalysisProgress

**Files:**
- Modify: `dashboard/src/components/AnalysisProgress.vue`

**Interfaces:**
- Consumes: `composite` prop (new), stage progress events
- Produces: stage detail cards populated with composite analysis data

- [ ] **Step 1: Add composite prop to AnalysisProgress props**

Add to the `Props` interface in `AnalysisProgress.vue`:

```typescript
interface Props {
  stages: AnalysisProgressType[]
  currentStage: number
  isComplete: boolean
  durationMs?: number
  error: string | null
  composite?: AnalysisProgressType // NOTE: will be replaced with CompositeAnalysis type
}
```

Wait — the prop type should be `CompositeAnalysis`. Update the import and props:

```typescript
import type { AnalysisProgress as AnalysisProgressType, CompositeAnalysis } from '../api/types'

interface Props {
  stages: AnalysisProgressType[]
  currentStage: number
  isComplete: boolean
  durationMs?: number
  error: string | null
  composite?: CompositeAnalysis
}
```

- [ ] **Step 2: Pass composite to StageSummaryCard in the template**

In the expandable detail panel section of the stage row template, change the StageSummaryCard usage to pass the composite:

```vue
<StageSummaryCard :stage="stage" :composite="composite" />
```

- [ ] **Step 3: Pass composite from SentimentView**

In `SentimentView.vue`, update the `AnalysisProgressComp` usage (line 64-70) to pass the composite data:

```vue
<AnalysisProgressComp
  :stages="analysisStages"
  :current-stage="analysisCurrentStage"
  :is-complete="analysisComplete"
  :duration-ms="analysisDurationMs"
  :error="analysisError"
  :composite="composite"
/>
```

- [ ] **Step 4: Commit**

```bash
git add dashboard/src/components/AnalysisProgress.vue dashboard/src/views/SentimentView.vue
git commit -m "feat: wire composite analysis data to stage summary cards"
```

---

### Task 4: Move old LLM block to bottom of Overview tab in SentimentView

**Files:**
- Modify: `dashboard/src/views/SentimentView.vue`

**Interfaces:**
- Consumes: existing `sentiment` ref, `analysisComplete` ref
- Produces: collapsed historical sentiment section at bottom of Overview tab

- [ ] **Step 1: Remove the old LLM block from its current position**

Delete lines 128-153 in the Overview tab section (the `<div v-if="!compositeLoading && sentiment">` block that contains the old LLM Analysis section with SentimentBadge, summary, red flags, catalysts).

This is the block that starts with:
```vue
<!-- Old sentiment result (coexists with composite) -->
<div v-if="!compositeLoading && sentiment" class="mt-6">
  <div class="mb-6 card-panel p-5">
    <h3 class="mb-4 text-sm font-semibold text-text-primary">LLM Analysis</h3>
    ...
```

And ends before the ContextPanel section.

Remove the entire block including the ContextPanel that sits inside it (lines 156-162).

- [ ] **Step 2: Add collapsed historical sentiment section at bottom of Overview tab**

After the BacktestPanel closing tag (around line 124), add:

```vue
<!-- Historical Sentiment (collapsed by default) -->
<div v-if="sentiment" class="mt-6">
  <button
    @click="showHistoricalSentiment = !showHistoricalSentiment"
    class="mb-3 flex items-center gap-2 text-sm font-medium text-text-muted transition-colors hover:text-text-primary"
  >
    <svg
      class="h-4 w-4 transition-transform"
      :class="{ 'rotate-180': showHistoricalSentiment }"
      fill="none"
      viewBox="0 0 24 24"
      stroke="currentColor"
    >
      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7" />
    </svg>
    Historical Sentiment
  </button>

  <div v-if="showHistoricalSentiment" class="animate-fade-in">
    <div class="card-panel p-5">
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
  </div>
</div>
```

- [ ] **Step 3: Add `showHistoricalSentiment` ref**

Add to the `<script setup lang="ts">` section:

```typescript
const showHistoricalSentiment = ref(false)
```

- [ ] **Step 4: Commit**

```bash
git add dashboard/src/views/SentimentView.vue
git commit -m "feat: move old LLM block to collapsed historical sentiment section in Overview tab"
```

---

### Task 5: Run typecheck and dev server to verify

**Files:**
- No code changes
- Verify: `dashboard/` builds cleanly

- [ ] **Step 1: Run TypeScript typecheck**

```bash
cd dashboard && npm run typecheck
```

Expected: no errors.

- [ ] **Step 2: Start dev server and verify UI**

```bash
cd dashboard && npm run dev
```

Open browser to `http://localhost:3003` and verify:
1. Navigate to Sentiment page
2. Click on a pipeline stage — it expands with Summary tab showing structured cards
3. Click "Details" tab — shows formatted JSON
4. Click another chevron — previous stage collapses, new one opens
5. Click the chevron again — stage collapses
6. Verify all 9 stages (pipeline-start through complete) are expandable
7. Verify LLM sentiment stage shows score badge, summary, catalysts, red flags
8. Verify Historical Sentiment section at bottom is collapsed by default
9. Click "Historical Sentiment" to expand it — shows old sentiment data
10. Verify no console errors

- [ ] **Step 3: Commit any fixes**

If typecheck or UI verification reveals issues, fix them and commit:

```bash
git add -A
git commit -m "fix: address typecheck/UI issues from accordion implementation"
```

---

## Self-Review

**1. Spec coverage:**
- Accordion rows (spec section 1) → Task 1
- Per-stage summary cards (spec section 2) → Task 2
- LLM analysis merge (spec section 3) → Task 2 (LLM stage) + Task 4 (old block removal)
- Layout changes (spec section 4) → Task 4
- Styling (spec section 5) → Tasks 1, 2, 4 (chevron, card-panel, color tokens)
- Data flow (spec section 6) → Task 3 (composite prop wiring)

**2. Placeholder scan:**
- No TBD/TODO/placeholder text found
- All code blocks are complete with actual implementations
- All file paths are exact
- All commands are specific

**3. Type consistency:**
- `AnalysisProgress` type used consistently across all files
- `CompositeAnalysis` type used for composite prop
- `SentimentBadge` component props match: `score: 'POSITIVE' | 'NEUTRAL' | 'NEGATIVE'`, `confidence: number`
- StageSummaryCard props: `stage: AnalysisProgress`, `composite: CompositeAnalysis | null`

**4. Ambiguity check:**
- "Formatted details" = JSON.stringify with indentation and copy-ready pre block — explicit in Task 1 Step 2
- "All stages expandable" = including pipeline-start and complete — explicit in design decision