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

type ParsedStageData =
  | { type: 'start'; symbol: string }
  | { type: 'data-check'; candleCount: number }
  | { type: 'backfill'; before: number; after: number }
  | { type: 'news'; count: number }
  | { type: 'llm'; score: 'POSITIVE' | 'NEUTRAL' | 'NEGATIVE'; confidence: number }
  | { type: 'technical'; signal: 'BUY' | 'SELL' | 'HOLD'; score: number }
  | { type: 'composite'; score: number; signal: 'BUY' | 'SELL' | 'HOLD' }
  | { type: 'backtest'; trades: number; winRate: number; return: number }
  | { type: 'complete'; durationMs: number }
  | { type: 'generic' }

// Get composite LLM data from composite object
const compositeLlm = computed(() => {
  if (!props.composite) return null
  const numScore = props.composite.news.score
  const sentimentScore: 'POSITIVE' | 'NEUTRAL' | 'NEGATIVE' = numScore > 0 ? 'POSITIVE' : numScore < 0 ? 'NEGATIVE' : 'NEUTRAL'
  return {
    score: sentimentScore,
    summary: props.composite.news?.summary,
    catalysts: props.composite.news?.catalysts,
    redFlags: props.composite.news?.redFlags,
  }
})

// Guard: only render LLM blocks when there is actual content
const hasLlmContent = computed(() => {
  if (!props.composite) return false
  const news = props.composite.news
  return !!(news?.summary || news?.catalysts?.length || news?.redFlags?.length)
})

// Get composite technical data
const compositeTech = computed(() => {
  if (!props.composite) return null
  return {
    score: props.composite.technical.score,
    signal: props.composite.technical.signal,
    confidence: props.composite.technical.confidence,
    indicators: props.composite.technical.indicators,
  }
})

// Get composite score data
const compositeScore = computed(() => {
  if (!props.composite) return null
  return {
    score: props.composite.compositeScore,
    signal: props.composite.compositeSignal,
    confidence: props.composite.compositeConfidence,
    reasoning: props.composite.reasoning,
  }
})

// Get composite backtest data
const compositeBacktest = computed(() => {
  if (!props.composite) return null
  return {
    totalTrades: props.composite.backtest.totalTrades,
    winRate: props.composite.backtest.winRate,
    profitFactor: props.composite.backtest.profitFactor,
    maxDrawdown: props.composite.backtest.maxDrawdown,
    totalReturn: props.composite.backtest.totalReturn,
    expectancy: props.composite.backtest.expectancy,
  }
})

// Indicator dot color class
const indicatorDotClass = (indicator: string) => {
  const lower = indicator.toLowerCase()
  if (lower.includes('bullish') || lower.includes('above') || lower.includes('positive')) return 'bg-success'
  if (lower.includes('bearish') || lower.includes('below') || lower.includes('negative')) return 'bg-danger'
  return 'bg-text-muted'
}

const parsedData = computed<ParsedStageData>(() => {
  const msg = props.stage.message || ''
  const name = props.stage.stageName.toLowerCase()

  // "Found 739 candles"
  const foundMatch = msg.match(/Found (\d+) candles?/)
  if (foundMatch) {
    return { type: 'data-check', candleCount: parseInt(foundMatch[1], 10) }
  }

  // "Backfilled: 739 -> 739 candles"
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

  return { type: 'generic' }
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
        {{ parsedData.before }} &rarr; {{ parsedData.after }}
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
    <div v-if="hasLlmContent" class="flex items-center gap-2">
      <SentimentBadge :score="compositeLlm!.score" :confidence="composite?.compositeConfidence ?? 0.5" />
    </div>
    <p v-if="compositeLlm?.summary" class="text-xs text-text-secondary leading-relaxed">
      {{ compositeLlm.summary }}
    </p>
    <div v-if="compositeLlm?.catalysts?.length" class="space-y-0.5">
      <p class="text-[10px] font-semibold uppercase tracking-wider text-success">Catalysts</p>
      <div v-for="c in compositeLlm!.catalysts" :key="c" class="flex items-start gap-1.5 text-xs text-text-secondary">
        <span class="mt-1.5 h-1.5 w-1.5 rounded-full bg-success flex-shrink-0" />
        <span>{{ c }}</span>
      </div>
    </div>
    <div v-if="compositeLlm?.redFlags?.length" class="space-y-0.5">
      <p class="text-[10px] font-semibold uppercase tracking-wider text-danger">Red Flags</p>
      <div v-for="r in compositeLlm!.redFlags" :key="r" class="flex items-start gap-1.5 text-xs text-text-secondary">
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
            :class="indicatorDotClass(indicator)"
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
