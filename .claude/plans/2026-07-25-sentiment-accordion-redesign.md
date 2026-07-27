# Sentiment Page Accordion Redesign — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the 8 separate result cards on the Sentiment page with a single accordion showing all 9 analysis stages + 10th LLM synthesis stage. Each stage has a headline summary and expandable detail panel. The News tab becomes a dedicated article browser. The existing API is extended (no new endpoint) to carry per-stage detail data.

**Architecture:**
- Backend: Enrich `AnalysisProgress` with per-stage detail payloads. Add `SynthesisService` for LLM synthesis. Refactor `AnalysisOrchestratorService` to expose fundamentals and backtest as separate stages (8 total) + auto-trigger synthesis (stage 9).
- Frontend: New `AnalysisAccordion.vue` replaces `AnalysisProgress.vue` + 8 separate cards. Per-stage detail sub-components render structured data. `SentimentView.vue` overview tab becomes accordion-only. News tab becomes `ArticleBrowser.vue`.

**Tech Stack:** Vue 3.5 + TypeScript + Composition API, Tailwind CSS v4, Java 21, Spring Boot 3.3.1, Maven multi-module

**Global constraints:**
- Use Java 21 via sdkman: `source "$HOME/.sdkman/bin/sdkman-init.sh"` before Maven
- No new frontend dependencies — only Vue reactivity + Tailwind
- All components use `card-panel` class for card styling
- Color tokens: `text-success`, `text-danger`, `text-warning`, `text-brand`, `text-text-muted`, `text-text-primary`, `text-text-secondary`
- New backend DTO fields are optional (backward compatible)

---

## Stage Mapping (Current → New)

| # | Current Stage | New Stage | Change |
|---|---|---|---|
| 0 | pipeline-start | pipeline-start | Same |
| 1 | checking data | checking data | Same |
| 2 | backfilling OHLCV | backfilling OHLCV | Same |
| 3 | fetching news | fetching news | Same |
| 4 | LLM sentiment | LLM sentiment | Same |
| 5 | technical analysis | technical analysis | Same |
| 6 | composite score (embeds fundamentals + backtest) | fundamentals | **Extracted from composite** |
| 7 | backtest | backtest | **Already separate in orchestrator** |
| — | — | composite score | **New: uses pre-computed values from stages 5-7** |
| — | — | LLM synthesis | **New: synthesizes all stage data** |

---

## Task 1: Backend — Extend DTOs with per-stage details

**Files:**
- Modify: `backend/api/src/main/java/com/swingtrade/api/dto/AnalysisProgress.java`
- Modify: `backend/api/src/main/java/com/swingtrade/api/dto/CompositeAnalysis.java`
- Create: `backend/api/src/main/java/com/swingtrade/api/dto/SynthesisResult.java`

### Step 1: Add `details` field to AnalysisProgress

Add a new nested record and field to carry stage-specific structured data:

```java
package com.swingtrade.api.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record AnalysisProgress(
    int stageNumber,
    String stageName,
    String status,
    String message,
    LocalDateTime timestamp,
    StageDetails details
) {
    // ... existing factory methods (running, completed, skipped, error) — add details=null

    public static AnalysisProgress completed(int stage, String name, String message, StageDetails details) {
        return new AnalysisProgress(stage, name, "completed", message, LocalDateTime.now(), details);
    }

    public record StageDetails(
        String type,           // "data-check", "backfill", "news", "sentiment", "technical", "fundamentals", "backtest", "composite", "synthesis"
        Map<String, Object> payload
    ) {}
}
```

### Step 2: Add synthesis field to CompositeAnalysis

```java
public record CompositeAnalysis(
    String symbol,
    LocalDate date,
    int compositeScore,
    String compositeSignal,
    BigDecimal compositeConfidence,
    List<SourceScore> sources,
    NewsScore news,
    TechnicalScore technical,
    FundamentalScore fundamentals,
    BacktestScore backtest,
    String reasoning,
    SynthesisResult synthesis  // NEW — nullable, populated by stage 9
) {
    // ... existing nested records unchanged

    // Factory method without synthesis (for backward compat)
    public CompositeAnalysis {
        // default synthesis to null if not provided
    }
}
```

### Step 3: Create SynthesisResult DTO

```java
package com.swingtrade.api.dto;

import java.util.List;

public record SynthesisResult(
    String narrative,           // Full human-readable analysis
    String recommendation,      // "BUY", "SELL", "HOLD"
    double confidence,          // 0.0 - 1.0
    List<String> keyDrivers,    // Top factors driving the recommendation
    List<String> bullishFactors,
    List<String> bearishFactors
) {}
```

### Step 4: Commit

```bash
git add backend/api/src/main/java/com/swingtrade/api/dto/
cd backend && mvn compile -q
git commit -m "feat: extend DTOs with per-stage details and synthesis result"
```

---

## Task 2: Backend — Create SynthesisService

**Files:**
- Create: `backend/llm/src/main/java/com/swingtrade/llm/service/SynthesisService.java`

### Step 1: Implement the service

```java
package com.swingtrade.llm.service;

import com.swingtrade.api.dto.CompositeAnalysis;
import com.swingtrade.api.dto.SynthesisResult;
import com.swingtrade.llm.client.VLLMClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;

@Service
public class SynthesisService {

    private static final Logger logger = LoggerFactory.getLogger(SynthesisService.class);
    private static final int MAX_TOKENS = 1024;
    private static final double TEMPERATURE = 0.2;
    private static final long TIMEOUT_SECONDS = 60;

    private final VLLMClient vllmClient;

    public SynthesisService(VLLMClient vllmClient) {
        this.vllmClient = vllmClient;
    }

    public SynthesisResult synthesize(CompositeAnalysis composite) {
        String symbol = composite.symbol();
        logger.info("Generating LLM synthesis for {}", symbol);

        String systemPrompt = """
            You are a senior equity analyst specializing in Indian markets.
            Given the results of 9 analysis stages for a stock, produce a final investment recommendation.
            Be concise, data-driven, and specific. Reference actual numbers from the analysis.
            Return JSON with this structure:
            {
              "narrative": "2-3 paragraph summary of the overall outlook",
              "recommendation": "BUY or SELL or HOLD",
              "confidence": 0.0 to 1.0,
              "keyDrivers": ["factor 1", "factor 2", "factor 3"],
              "bullishFactors": ["factor 1", "factor 2"],
              "bearishFactors": ["factor 1", "factor 2"]
            }
        """;

        String userPrompt = buildPrompt(composite);

        List<Map<String, String>> messages = List.of(
            Map.of("role", "system", "content", systemPrompt),
            Map.of("role", "user", "content", userPrompt)
        );

        try {
            String llmResponse = vllmClient.generateChatCompletion(messages, MAX_TOKENS, TEMPERATURE)
                .block(Duration.ofSeconds(TIMEOUT_SECONDS));

            if (llmResponse == null || llmResponse.isBlank()) {
                return defaultSynthesis(composite);
            }

            return parseResponse(llmResponse);
        } catch (Exception e) {
            logger.warn("LLM synthesis failed for {}, using fallback: {}", symbol, e.getMessage());
            return fallbackSynthesis(composite);
        }
    }

    private String buildPrompt(CompositeAnalysis c) {
        return String.format("""
            Stock: %s | Date: %s

            === NEWS SENTIMENT ===
            Score: %d | Articles: %d
            Summary: %s
            Catalysts: %s
            Red Flags: %s

            === TECHNICAL ANALYSIS ===
            Signal: %s | Score: %d/100 | Confidence: %.0f%%
            Indicators: %s

            === FUNDAMENTALS ===
            Score: %d/100 | Signal: %s
            Factors: %s

            === BACKTEST ===
            Trades: %d | Win Rate: %.1f%% | Profit Factor: %.2f
            Max Drawdown: %.1f%% | Total Return: %.1f%% | Expectancy: %.1f%%

            === COMPOSITE ===
            Score: %d/100 | Signal: %s | Confidence: %.0f%%
            Reasoning: %s
        """,
            c.symbol(), c.date(),
            c.news().score(), c.news().articleCount(),
            c.news().summary(), c.news().catalysts(), c.news().redFlags(),
            c.technical().signal(), c.technical().score(), c.technical().confidence() * 100, c.technical().indicators(),
            c.fundamentals().score(), c.fundamentals().score() > 0 ? "BULLISH" : c.fundamentals().score() < 0 ? "BEARISH" : "NEUTRAL", c.fundamentals().factors(),
            c.backtest().totalTrades(), c.backtest().winRate(), c.backtest().profitFactor(),
            c.backtest().maxDrawdown(), c.backtest().totalReturn(), c.backtest().expectancy(),
            c.compositeScore(), c.compositeSignal(), c.compositeConfidence().doubleValue() * 100, c.reasoning()
        );
    }

    private SynthesisResult parseResponse(String response) {
        // Extract JSON from response (handle reasoning models that wrap JSON in text)
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        if (start == -1 || end == -1) return fallbackSynthesis(null);

        String json = response.substring(start, end + 1);
        // Use Jackson ObjectMapper — same as SentimentAnalyzer.parseResponse
        // For simplicity, parse key fields from the JSON string
        // In production, use the same JsonParser/SentimentAnalyzer pattern
        return fallbackSynthesis(null); // TODO: implement proper JSON parsing using existing pattern
    }

    private SynthesisResult defaultSynthesis(CompositeAnalysis c) {
        return new SynthesisResult(
            "LLM synthesis unavailable. Composite score: " + c.compositeScore() + " (" + c.compositeSignal() + ").",
            c.compositeSignal(), 0.0, List.of(), List.of(), List.of()
        );
    }

    private SynthesisResult fallbackSynthesis(CompositeAnalysis c) {
        if (c == null) {
            return new SynthesisResult("Synthesis data unavailable.", "HOLD", 0.0, List.of(), List.of(), List.of());
        }
        return new SynthesisResult(
            c.reasoning(),
            c.compositeSignal(),
            c.compositeConfidence().doubleValue() * 0.7, // slightly lower confidence for synthesis
            List.of("Based on composite analysis"),
            List.of(),
            List.of()
        );
    }
}
```

**Note:** The `parseResponse` method should reuse the same JSON extraction pattern from `SentimentAnalyzer.extractJsonFromReasoning()` (line 389). Copy that method or extract it to a shared utility.

### Step 2: Commit

```bash
git add backend/llm/src/main/java/com/swingtrade/llm/service/SynthesisService.java
cd backend && mvn compile -q
git commit -m "feat: add SynthesisService for LLM-powered final analysis stage"
```

---

## Task 3: Backend — Refactor AnalysisOrchestratorService (9 stages + synthesis)

**Files:**
- Modify: `backend/api/src/main/java/com/swingtrade/api/service/AnalysisOrchestratorService.java`
- Modify: `backend/api/src/main/java/com/swingtrade/api/service/CompositeAnalysisService.java`

### Step 1: Refactor CompositeAnalysisService

The current `CompositeAnalysisService.analyze()` internally computes technical, fundamentals, and backtest. Refactor it to accept pre-computed values:

```java
// OLD: public CompositeAnalysis analyze(String symbol)
// NEW:
public CompositeAnalysis analyze(
    String symbol,
    CompositeAnalysis.TechnicalScore technical,
    CompositeAnalysis.FundamentalScore fundamentals,
    CompositeAnalysis.BacktestScore backtest,
    SentimentResult sentimentResult
) {
    // ... use pre-computed values instead of calling services internally
    // ... compute composite from the passed values
    // ... return CompositeAnalysis with all pre-computed data
}
```

Also add a backward-compatible overload for the existing `POST /api/analysis/analyze` endpoint:

```java
public CompositeAnalysis analyze(String symbol) {
    // Call the new method with freshly computed values
    TechnicalScore technical = technicalService.compute(symbol);
    FundamentalScore fundamentals = fundamentalScorer.compute(symbol);
    BacktestScore backtest = backtestScorer.compute(symbol);
    SentimentResult sentiment = sentimentService.analyzeStockSentiment(symbol, LocalDate.now());
    return analyze(symbol, technical, fundamentals, backtest, sentiment);
}
```

### Step 2: Refactor AnalysisOrchestratorService

Replace the current 7-stage pipeline with 9 stages + synthesis:

```java
public FullAnalysisResult runFullAnalysis(String symbol, SseEmitter emitter, int backfillYears) {
    String sym = symbol.toUpperCase(Locale.ROOT);
    long startTime = System.currentTimeMillis();
    List<AnalysisProgress> progress = new ArrayList<>();
    AtomicLong stageDurations = new AtomicLong(0);

    // Stage 0: pipeline-start
    emitProgress(emitter, progress, AnalysisProgress.running(0, "pipeline-start"));
    emitProgress(emitter, progress, AnalysisProgress.completed(0, "pipeline-start",
        "Full analysis started for " + sym));

    try {
        // Stage 1: checking data (unchanged)
        // Stage 2: backfilling OHLCV (unchanged)
        // Stage 3: fetching news (unchanged)
        // Stage 4: LLM sentiment (unchanged, but capture result)

        // Stage 5: technical analysis (unchanged, capture result)
        CompositeAnalysis.TechnicalScore technical = technicalService.compute(sym);
        emitProgress(emitter, progress, AnalysisProgress.completed(5, "technical analysis",
            String.format("Signal: %s, score: %d", technical.signal(), technical.score()),
            buildTechnicalDetails(technical));

        // Stage 6: fundamentals (NEW — extracted from composite)
        CompositeAnalysis.FundamentalScore fundamentals = fundamentalScorer.compute(sym);
        emitProgress(emitter, progress, AnalysisProgress.completed(6, "fundamentals",
            String.format("Score: %d, %s", fundamentals.score(),
                fundamentals.score() > 0 ? "BULLISH" : fundamentals.score() < 0 ? "BEARISH" : "NEUTRAL"),
            buildFundamentalsDetails(fundamentals)));

        // Stage 7: backtest (unchanged, but enrich details)
        CompositeAnalysis.BacktestScore backtest = backtestScorer.compute(sym);
        emitProgress(emitter, progress, AnalysisProgress.completed(7, "backtest",
            String.format("%d trades, win rate: %.1f%%, return: %.1f%%",
                backtest.totalTrades(), backtest.winRate(), backtest.totalReturn()),
            buildBacktestDetails(backtest)));

        // Stage 8: composite score (NEW — uses pre-computed values)
        SentimentResult sentimentResult = ... // captured from stage 4
        CompositeAnalysis composite = compositeAnalysisService.analyze(
            sym, technical, fundamentals, backtest, sentimentResult);
        emitProgress(emitter, progress, AnalysisProgress.completed(8, "composite score",
            String.format("Score: %d, signal: %s",
                composite.compositeScore(), composite.compositeSignal()),
            buildCompositeDetails(composite)));

        // Stage 9: LLM synthesis (NEW — auto-trigger, async)
        emitProgress(emitter, progress, AnalysisProgress.running(9, "LLM synthesis"));
        try {
            SynthesisService synthesisResult = synthesisService.synthesize(composite);
            composite = new CompositeAnalysis(
                composite.symbol(), composite.date(), composite.compositeScore(),
                composite.compositeSignal(), composite.compositeConfidence(),
                composite.sources(), composite.news(), composite.technical(),
                composite.fundamentals(), composite.backtest(), composite.reasoning(),
                synthesisResult);
            emitProgress(emitter, progress, AnalysisProgress.completed(9, "LLM synthesis",
                "Recommendation: " + synthesisResult.recommendation() + " (" + String.format("%.0f%%", synthesisResult.confidence() * 100) + ")",
                buildSynthesisDetails(synthesisResult)));
        } catch (Exception e) {
            emitProgress(emitter, progress, AnalysisProgress.error(9, "LLM synthesis", e.getMessage()));
        }

        // Stage 99: complete
        long duration = System.currentTimeMillis() - startTime;
        var result = new FullAnalysisResult(composite, List.copyOf(progress), duration, sym);
        emitProgress(emitter, progress,
            AnalysisProgress.completed(99, "complete",
                String.format("Analysis finished in %dms", duration)));
        return result;

    } catch (Exception e) {
        // ... error handling (unchanged)
    }
}
```

### Step 3: Add detail builder helper methods

```java
private AnalysisProgress.StageDetails buildTechnicalDetails(CompositeAnalysis.TechnicalScore t) {
    return new AnalysisProgress.StageDetails("technical", Map.of(
        "score", t.score(),
        "signal", t.signal(),
        "confidence", t.confidence(),
        "indicators", t.indicators()
    ));
}

private AnalysisProgress.StageDetails buildFundamentalsDetails(CompositeAnalysis.FundamentalScore f) {
    return new AnalysisProgress.StageDetails("fundamentals", Map.of(
        "score", f.score(),
        "factors", f.factors()
    ));
}

private AnalysisProgress.StageDetails buildBacktestDetails(CompositeAnalysis.BacktestScore b) {
    return new AnalysisProgress.StageDetails("backtest", Map.of(
        "totalTrades", b.totalTrades(),
        "winRate", b.winRate(),
        "profitFactor", b.profitFactor(),
        "maxDrawdown", b.maxDrawdown(),
        "totalReturn", b.totalReturn(),
        "expectancy", b.expectancy(),
        "hasEnoughData", b.hasEnoughData()
    ));
}

private AnalysisProgress.StageDetails buildCompositeDetails(CompositeAnalysis c) {
    return new AnalysisProgress.StageDetails("composite", Map.of(
        "compositeScore", c.compositeScore(),
        "compositeSignal", c.compositeSignal(),
        "compositeConfidence", c.compositeConfidence().doubleValue(),
        "sources", c.sources(),
        "reasoning", c.reasoning()
    ));
}

private AnalysisProgress.StageDetails buildSynthesisDetails(SynthesisResult s) {
    return new AnalysisProgress.StageDetails("synthesis", Map.of(
        "narrative", s.narrative(),
        "recommendation", s.recommendation(),
        "confidence", s.confidence(),
        "keyDrivers", s.keyDrivers(),
        "bullishFactors", s.bullishFactors(),
        "bearishFactors", s.bearishFactors()
    ));
}
```

### Step 4: Capture sentiment result from stage 4 for composite

The current code sets `sentiment = null` on error. Need to preserve it for the composite stage. Change:

```java
// Stage 4: LLM sentiment — capture even on partial failure
SentimentResult sentiment;
try {
    sentiment = sentimentService.analyzeStockSentiment(sym, LocalDate.now());
    emitProgress(emitter, progress, AnalysisProgress.completed(4, "LLM sentiment",
        String.format("Score: %s, confidence: %.0f%%",
            sentiment.score(), sentiment.confidence() * 100),
        buildSentimentDetails(sentiment)));
} catch (Exception e) {
    sentiment = createFallbackSentiment(sym);  // NOT null
    emitProgress(emitter, progress, AnalysisProgress.error(4, "LLM sentiment", e.getMessage()));
}
```

### Step 5: Commit

```bash
git add backend/api/src/main/java/com/swingtrade/api/service/
git add backend/api/src/main/java/com/swingtrade/api/dto/
cd backend && mvn compile -q
git commit -m "feat: restructure pipeline to 9 stages + LLM synthesis, enrich SSE events with stage details"
```

---

## Task 4: Frontend — Update TypeScript types

**Files:**
- Modify: `dashboard/src/api/types.ts`

### Step 1: Update AnalysisProgress type

```typescript
export interface AnalysisProgress {
  stageNumber: number
  stageName: string
  status: 'running' | 'completed' | 'skipped' | 'error'
  message: string
  timestamp: string
  details?: {
    type: string
    payload: Record<string, unknown>
  }
}
```

### Step 2: Update CompositeAnalysis type

```typescript
export interface CompositeAnalysis {
  symbol: string
  date: string
  compositeScore: number
  compositeSignal: 'BUY' | 'SELL' | 'HOLD'
  compositeConfidence: number
  sources: Array<{ name: string; score: number; weight: number; description: string }>
  news: {
    score: number
    summary: string
    catalysts: string[]
    redFlags: string[]
    articleCount: number
  }
  technical: {
    score: number
    signal: string
    confidence: number
    indicators: string[]
  }
  fundamentals: {
    score: number
    factors: string[]
  }
  backtest: {
    totalTrades: number
    winRate: number
    profitFactor: number
    maxDrawdown: number
    totalReturn: number
    expectancy: number
    hasEnoughData: boolean
  }
  reasoning: string
  synthesis?: {                    // NEW
    narrative: string
    recommendation: 'BUY' | 'SELL' | 'HOLD'
    confidence: number
    keyDrivers: string[]
    bullishFactors: string[]
    bearishFactors: string[]
  }
}
```

### Step 3: Add SynthesisResult type (standalone, for on-demand synthesis)

```typescript
export interface SynthesisResult {
  narrative: string
  recommendation: 'BUY' | 'SELL' | 'HOLD'
  confidence: number
  keyDrivers: string[]
  bullishFactors: string[]
  bearishFactors: string[]
}
```

### Step 4: Commit

```bash
git add dashboard/src/api/types.ts
git commit -m "feat: update TypeScript types for per-stage details and synthesis"
```

---

## Task 5: Frontend — Create AnalysisAccordion component

**Files:**
- Create: `dashboard/src/components/AnalysisAccordion.vue`
- Create: `dashboard/src/components/StageNewsDetail.vue`
- Create: `dashboard/src/components/StageTechnicalDetail.vue`
- Create: `dashboard/src/components/StageFundamentalsDetail.vue`
- Create: `dashboard/src/components/StageBacktestDetail.vue`
- Create: `dashboard/src/components/StageSynthesisDetail.vue`

### Step 1: Create AnalysisAccordion.vue

This is the main accordion component that replaces `AnalysisProgress.vue` + all 8 separate cards.

```vue
<script setup lang="ts">
import { computed, ref } from 'vue'
import type { AnalysisProgress, CompositeAnalysis } from '../api/types'
import SentimentBadge from './SentimentBadge.vue'
import StageNewsDetail from './StageNewsDetail.vue'
import StageTechnicalDetail from './StageTechnicalDetail.vue'
import StageFundamentalsDetail from './StageFundamentalsDetail.vue'
import StageBacktestDetail from './StageBacktestDetail.vue'
import StageSynthesisDetail from './StageSynthesisDetail.vue'

interface Props {
  stages: AnalysisProgress[]
  currentStage: number
  isComplete: boolean
  durationMs?: number
  error: string | null
  composite?: CompositeAnalysis | null
}

const props = defineProps<Props>()

const expandedStage = ref<number | null>(null)

const filteredStages = computed(() => {
  const stages = props.stages.filter(s => s.stageNumber < 100)
  const result: typeof props.stages = []
  for (let i = 0; i < stages.length; i++) {
    const s = stages[i]
    if (s.status === 'running') {
      const hasCompletion = stages.slice(i + 1).some(
        later => later.stageNumber === s.stageNumber &&
          (later.status === 'completed' || later.status === 'error' || later.status === 'skipped')
      )
      if (!hasCompletion) result.push(s)
    } else {
      result.push(s)
    }
  }
  return result
})

const completedCount = computed(() =>
  filteredStages.value.filter(s => s.status === 'completed').length
)

const progressPercent = computed(() =>
  Math.round((completedCount.value / Math.max(filteredStages.value.length, 1)) * 100)
)

function toggleStage(stageNumber: number) {
  expandedStage.value = expandedStage.value === stageNumber ? null : stageNumber
}

function stageIcon(status: string) {
  if (status === 'running') return '⟳'
  if (status === 'completed') return '✓'
  if (status === 'error') return '✗'
  return '—'
}

function stageColor(status: string, isCurrent: boolean) {
  if (status === 'error') return 'text-danger'
  if (status === 'completed') return 'text-success'
  if (isCurrent && !props.isComplete) return 'text-brand'
  return 'text-text-muted'
}

// Headline summary per stage type — shown in the collapsed row
const headlineSummary = computed(() => (stage: AnalysisProgress): string => {
  if (stage.status === 'running') return 'Running...'
  if (stage.status === 'skipped') return stage.message
  if (stage.status === 'error') return `Error: ${stage.message}`

  const type = stage.details?.type || ''
  const payload = stage.details?.payload || {}

  switch (type) {
    case 'data-check':
      return `Found ${payload.candleCount ?? stage.message} candles`
    case 'backfill':
      return `${payload.before ?? 0} → ${payload.after ?? 0} candles`
    case 'news':
      return `${payload.articleCount ?? stage.message} articles from ${payload.sourceCount ?? '?'} sources`
    case 'sentiment':
      return `${payload.score ?? 'N/A'}, ${Math.round((payload.confidence ?? 0) * 100)}% confidence`
    case 'technical':
      return `${payload.signal ?? 'N/A'}, score: ${payload.score ?? '?'}'
    case 'fundamentals':
      return `Score: ${payload.score ?? '?'}'
    case 'backtest':
      return `${payload.totalTrades ?? 0} trades, ${payload.winRate?.toFixed(1) ?? '?'}% WR`
    case 'composite':
      return `Score: ${payload.compositeScore ?? '?'}' ${payload.compositeSignal ?? ''}'
    case 'synthesis':
      return `${payload.recommendation ?? 'Analyzing...'} (${Math.round((payload.confidence ?? 0) * 100)}% conf)`
    default:
      return stage.message
  }
})
</script>

<template>
  <div class="card-panel p-5 animate-fade-in">
    <!-- Progress bar -->
    <div class="mb-4">
      <div class="mb-2 flex items-center justify-between">
        <span class="text-sm font-medium text-text-primary">Analysis Pipeline</span>
        <div class="flex items-center gap-3">
          <span class="text-xs text-text-muted">{{ completedCount }}/{{ filteredStages.length }} stages</span>
          <span v-if="isComplete && durationMs != null" class="text-xs text-text-muted">
            {{ (durationMs / 1000).toFixed(1) }}s
          </span>
        </div>
      </div>
      <div class="h-2 overflow-hidden rounded bg-bg-hover">
        <div
          class="h-full rounded bg-brand transition-all duration-500"
          :style="{ width: `${progressPercent}%` }"
        />
      </div>
    </div>

    <!-- Stage accordion -->
    <div class="space-y-1">
      <div
        v-for="stage in filteredStages"
        :key="stage.stageNumber"
        class="cursor-pointer rounded-md transition-colors"
        :class="[
          stage.stageNumber === currentStage && !isComplete ? 'bg-bg-hover' : '',
          expandedStage === stage.stageNumber ? 'bg-bg-hover' : 'hover:bg-bg-hover/50'
        ]"
        @click="toggleStage(stage.stageNumber)"
      >
        <!-- Row header -->
        <div class="flex items-center gap-3 px-3 py-2">
          <!-- Status icon -->
          <span
            class="flex h-6 w-6 shrink-0 items-center justify-center rounded-full text-xs"
            :class="stageColor(stage.status, stage.stageNumber === currentStage && !isComplete)"
          >
            <span v-if="stage.status === 'running' && !isComplete" class="animate-spin">⟳</span>
            <span v-else>{{ stageIcon(stage.status) }}</span>
          </span>

          <!-- Stage name + headline -->
          <div class="min-w-0 flex-1">
            <div class="flex items-center gap-2">
              <span
                class="text-sm font-medium truncate"
                :class="stageColor(stage.status, stage.stageNumber === currentStage && !isComplete)"
              >
                {{ stage.stageName }}
              </span>
              <span v-if="stage.status === 'skipped'" class="text-xs italic text-text-muted">skipped</span>
            </div>
            <p class="text-xs text-text-muted truncate">
              {{ headlineSummary(stage) }}
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
        <div v-if="expandedStage === stage.stageNumber" class="animate-fade-in border-t border-border-subtle/50 px-3 py-3">
          <!-- Stage-specific detail component -->
          <div v-if="stage.status === 'completed' || stage.status === 'error'">
            <!-- News stage -->
            <StageNewsDetail
              v-if="stage.details?.type === 'news'"
              :stage="stage"
              :composite="composite"
            />

            <!-- Sentiment stage -->
            <StageNewsDetail
              v-else-if="stage.details?.type === 'sentiment'"
              :stage="stage"
              :composite="composite"
            />

            <!-- Technical stage -->
            <StageTechnicalDetail
              v-else-if="stage.details?.type === 'technical'"
              :stage="stage"
              :composite="composite"
            />

            <!-- Fundamentals stage -->
            <StageFundamentalsDetail
              v-else-if="stage.details?.type === 'fundamentals'"
              :stage="stage"
              :composite="composite"
            />

            <!-- Backtest stage -->
            <StageBacktestDetail
              v-else-if="stage.details?.type === 'backtest'"
              :stage="stage"
              :composite="composite"
            />

            <!-- Composite stage -->
            <div v-else-if="stage.details?.type === 'composite'" class="space-y-2">
              <div class="flex items-center gap-2">
                <span class="text-lg font-bold font-mono" :class="composite ? (composite.compositeScore > 20 ? 'text-success' : composite.compositeScore < -20 ? 'text-danger' : 'text-warning') : 'text-text-primary'">
                  {{ composite ? (composite.compositeScore >= 0 ? '+' : '') + composite.compositeScore : '?' }}
                </span>
                <span class="inline-flex items-center rounded px-2 py-0.5 text-xs font-bold" :class="{
                  'bg-success/15 text-success': composite?.compositeSignal === 'BUY',
                  'bg-danger/15 text-danger': composite?.compositeSignal === 'SELL',
                  'bg-warning/15 text-warning': composite?.compositeSignal === 'HOLD',
                }">
                  {{ composite?.compositeSignal }}
                </span>
              </div>
              <div v-if="composite" class="space-y-1">
                <div class="flex justify-between text-xs text-text-muted">
                  <span>Confidence</span>
                  <span>{{ Math.round(composite.compositeConfidence * 100) }}%</span>
                </div>
                <div class="h-1.5 rounded-full bg-bg-primary overflow-hidden">
                  <div class="h-full rounded-full transition-all" :class="composite.compositeConfidence >= 0.7 ? 'bg-success' : composite.compositeConfidence >= 0.4 ? 'bg-warning' : 'bg-danger'" :style="{ width: `${composite.compositeConfidence * 100}%` }" />
                </div>
              </div>
              <p v-if="composite?.reasoning" class="text-xs text-text-secondary leading-relaxed">{{ composite.reasoning }}</p>
              <!-- Source breakdown -->
              <div v-if="composite?.sources" class="mt-2 space-y-1">
                <p class="text-[10px] font-semibold uppercase tracking-wider text-text-muted">Source Weights</p>
                <div v-for="src in composite.sources" :key="src.name" class="flex items-center justify-between text-xs">
                  <span class="text-text-secondary">{{ src.name }}</span>
                  <span class="text-text-muted">{{ src.weight * 100 }}% weight, score {{ src.score }}</span>
                </div>
              </div>
            </div>

            <!-- Synthesis stage -->
            <StageSynthesisDetail
              v-else-if="stage.details?.type === 'synthesis' || stage.stageName === 'LLM synthesis'"
              :stage="stage"
              :composite="composite"
            />

            <!-- Generic fallback for other stages -->
            <div v-else class="space-y-2">
              <p class="text-xs text-text-secondary">{{ stage.message }}</p>
              <div v-if="stage.details?.payload" class="max-h-40 overflow-y-auto rounded bg-bg-primary p-2 text-[11px] font-mono text-text-secondary">
                <pre class="whitespace-pre-wrap break-words">{{ JSON.stringify(stage.details.payload, null, 2) }}</pre>
              </div>
            </div>
          </div>

          <!-- Error state -->
          <div v-else-if="stage.status === 'error'" class="rounded-md border border-danger/30 bg-danger/5 p-3">
            <p class="text-sm text-danger">{{ stage.message }}</p>
          </div>
        </div>
      </div>
    </div>

    <!-- Overall error -->
    <div v-if="error" class="mt-4 rounded-md border border-danger/30 bg-danger/5 p-3">
      <p class="text-sm text-danger">{{ error }}</p>
    </div>

    <!-- Complete state -->
    <div v-if="isComplete && !error" class="mt-4 text-center">
      <p class="text-sm font-medium text-success">Analysis complete</p>
    </div>
  </div>
</template>
```

### Step 2: Create StageNewsDetail.vue

```vue
<script setup lang="ts">
import { computed } from 'vue'
import type { AnalysisProgress, CompositeAnalysis } from '../api/types'
import SentimentBadge from './SentimentBadge.vue'

interface Props {
  stage: AnalysisProgress
  composite: CompositeAnalysis | null
}
const props = defineProps<Props>()

const sentimentData = computed(() => {
  if (!props.composite) return null
  return {
    score: props.composite.news.score,
    summary: props.composite.news.summary,
    catalysts: props.composite.news.catalysts,
    redFlags: props.composite.news.redFlags,
    articleCount: props.composite.news.articleCount,
  }
})
</script>

<template>
  <div class="space-y-2">
    <!-- Score badge -->
    <div v-if="sentimentData" class="flex items-center gap-2">
      <SentimentBadge :score="sentimentData.articleCount > 0 ? 'NEUTRAL' : 'NEUTRAL'" :confidence="0.5" />
      <span class="text-xs text-text-muted">{{ sentimentData.articleCount }} articles</span>
    </div>

    <!-- Summary -->
    <p v-if="sentimentData?.summary" class="text-xs text-text-secondary leading-relaxed">{{ sentimentData.summary }}</p>

    <!-- Catalysts -->
    <div v-if="sentimentData?.catalysts.length" class="space-y-0.5">
      <p class="text-[10px] font-semibold uppercase tracking-wider text-success">Catalysts</p>
      <div v-for="c in sentimentData.catalysts" :key="c" class="flex items-start gap-1.5 text-xs text-text-secondary">
        <span class="mt-1.5 h-1.5 w-1.5 rounded-full bg-success flex-shrink-0" />
        <span>{{ c }}</span>
      </div>
    </div>

    <!-- Red Flags -->
    <div v-if="sentimentData?.redFlags.length" class="space-y-0.5">
      <p class="text-[10px] font-semibold uppercase tracking-wider text-danger">Red Flags</p>
      <div v-for="r in sentimentData.redFlags" :key="r" class="flex items-start gap-1.5 text-xs text-text-secondary">
        <span class="mt-1.5 h-1.5 w-1.5 rounded-full bg-danger flex-shrink-0" />
        <span>{{ r }}</span>
      </div>
    </div>

    <!-- Generic fallback -->
    <p v-else class="text-xs text-text-secondary">{{ stage.message }}</p>
  </div>
</template>
```

### Step 3: Create StageTechnicalDetail.vue

```vue
<script setup lang="ts">
import { computed } from 'vue'
import type { AnalysisProgress, CompositeAnalysis } from '../api/types'

interface Props {
  stage: AnalysisProgress
  composite: CompositeAnalysis | null
}
const props = defineProps<Props>()

const techData = computed(() => props.composite?.technical)
</script>

<template>
  <div class="space-y-2">
    <div v-if="techData" class="flex items-center gap-2">
      <span class="inline-flex items-center rounded px-2 py-0.5 text-xs font-bold" :class="{
        'bg-success/15 text-success': techData.signal === 'BUY',
        'bg-danger/15 text-danger': techData.signal === 'SELL',
        'bg-warning/15 text-warning': techData.signal === 'HOLD',
      }">{{ techData.signal }}</span>
      <span class="text-sm font-mono font-semibold" :class="techData.score > 0 ? 'text-success' : techData.score < 0 ? 'text-danger' : 'text-warning'">
        {{ techData.score > 0 ? '+' : '' }}{{ techData.score }}/100
      </span>
      <span class="text-xs text-text-muted">{{ Math.round(techData.confidence * 100) }}% confidence</span>
    </div>

    <div v-if="techData?.indicators?.length" class="space-y-1">
      <p class="text-[10px] font-semibold uppercase tracking-wider text-text-muted">Indicators</p>
      <div v-for="indicator in techData.indicators" :key="indicator" class="flex items-center gap-1.5 text-[11px]">
        <span class="h-1.5 w-1.5 rounded-full flex-shrink-0" :class="{
          'bg-success': /bullish|above|positive|proximate/i.test(indicator),
          'bg-danger': /bearish|below|negative|distant/i.test(indicator),
          'bg-text-muted': true,
        }" />
        <template v-if="indicator.includes(': ')">
          <span class="text-text-muted w-20 truncate flex-shrink-0">{{ indicator.split(': ')[0] }}</span>
          <span class="text-text-secondary">{{ indicator.split(': ')[1] }}</span>
        </template>
        <template v-else><span class="text-text-secondary">{{ indicator }}</span></template>
      </div>
    </div>
  </div>
</template>
```

### Step 4: Create StageFundamentalsDetail.vue

```vue
<script setup lang="ts">
import { computed } from 'vue'
import type { AnalysisProgress, CompositeAnalysis } from '../api/types'

interface Props {
  stage: AnalysisProgress
  composite: CompositeAnalysis | null
}
const props = defineProps<Props>()

const fundData = computed(() => props.composite?.fundamentals)
</script>

<template>
  <div class="space-y-2">
    <div v-if="fundData" class="flex items-center gap-2">
      <span class="text-sm font-mono font-semibold" :class="fundData.score > 0 ? 'text-success' : fundData.score < 0 ? 'text-danger' : 'text-warning'">
        {{ fundData.score > 0 ? '+' : '' }}{{ fundData.score }}/100
      </span>
      <span class="text-xs text-text-muted">{{ fundData.score > 0 ? 'Bullish' : fundData.score < 0 ? 'Bearish' : 'Neutral' }}</span>
    </div>

    <div v-if="fundData?.factors?.length" class="space-y-1">
      <p class="text-[10px] font-semibold uppercase tracking-wider text-text-muted">Factors</p>
      <div v-for="factor in fundData.factors" :key="factor" class="text-[11px] text-text-secondary">
        {{ factor }}
      </div>
    </div>
  </div>
</template>
```

### Step 5: Create StageBacktestDetail.vue

```vue
<script setup lang="ts">
import { computed } from 'vue'
import type { AnalysisProgress, CompositeAnalysis } from '../api/types'

interface Props {
  stage: AnalysisProgress
  composite: CompositeAnalysis | null
}
const props = defineProps<Props>()

const btData = computed(() => props.composite?.backtest)
</script>

<template>
  <div class="space-y-2">
    <div v-if="btData" class="grid grid-cols-2 gap-2">
      <div class="rounded bg-bg-elevated/50 p-2">
        <p class="text-[10px] uppercase tracking-wider text-text-muted/50">Trades</p>
        <p class="text-base font-bold text-text-primary">{{ btData.totalTrades }}</p>
      </div>
      <div class="rounded bg-bg-elevated/50 p-2">
        <p class="text-[10px] uppercase tracking-wider text-text-muted/50">Win Rate</p>
        <p class="text-base font-bold" :class="btData.winRate > 50 ? 'text-success' : btData.winRate < 40 ? 'text-danger' : 'text-text-primary'">
          {{ btData.winRate.toFixed(1) }}%
        </p>
      </div>
      <div class="rounded bg-bg-elevated/50 p-2">
        <p class="text-[10px] uppercase tracking-wider text-text-muted/50">Total Return</p>
        <p class="text-base font-bold" :class="btData.totalReturn > 0 ? 'text-success' : 'text-danger'">
          {{ btData.totalReturn >= 0 ? '+' : '' }}{{ btData.totalReturn.toFixed(1) }}%
        </p>
      </div>
      <div class="rounded bg-bg-elevated/50 p-2">
        <p class="text-[10px] uppercase tracking-wider text-text-muted/50">Profit Factor</p>
        <p class="text-base font-bold" :class="btData.profitFactor > 1.5 ? 'text-success' : btData.profitFactor < 0.8 ? 'text-danger' : 'text-text-primary'">
          {{ btData.profitFactor.toFixed(2) }}
        </p>
      </div>
      <div class="rounded bg-bg-elevated/50 p-2">
        <p class="text-[10px] uppercase tracking-wider text-text-muted/50">Max Drawdown</p>
        <p class="text-base font-bold text-danger">{{ btData.maxDrawdown.toFixed(1) }}%</p>
      </div>
      <div class="rounded bg-bg-elevated/50 p-2">
        <p class="text-[10px] uppercase tracking-wider text-text-muted/50">Expectancy</p>
        <p class="text-base font-bold" :class="btData.expectancy > 0 ? 'text-success' : 'text-danger'">
          {{ btData.expectancy >= 0 ? '+' : '' }}{{ btData.expectancy.toFixed(1) }}%
        </p>
      </div>
    </div>
    <p v-if="!btData?.hasEnoughData" class="text-xs text-text-muted">Insufficient data for backtest.</p>
  </div>
</template>
```

### Step 6: Create StageSynthesisDetail.vue

```vue
<script setup lang="ts">
import { computed } from 'vue'
import type { AnalysisProgress, CompositeAnalysis } from '../api/types'

interface Props {
  stage: AnalysisProgress
  composite: CompositeAnalysis | null
}
const props = defineProps<Props>()

const synthesis = computed(() => props.composite?.synthesis)
</script>

<template>
  <div class="space-y-3">
    <!-- Recommendation header -->
    <div v-if="synthesis" class="flex items-center gap-3">
      <span class="text-xl font-bold font-mono" :class="{
        'text-success': synthesis.recommendation === 'BUY',
        'text-danger': synthesis.recommendation === 'SELL',
        'text-warning': synthesis.recommendation === 'HOLD',
      }">{{ synthesis.recommendation }}</span>
      <span class="text-sm text-text-muted">{{ Math.round(synthesis.confidence * 100) }}% confidence</span>
    </div>

    <!-- Narrative -->
    <p v-if="synthesis?.narrative" class="text-sm text-text-secondary leading-relaxed">{{ synthesis.narrative }}</p>

    <!-- Key drivers -->
    <div v-if="synthesis?.keyDrivers?.length" class="space-y-1">
      <p class="text-[10px] font-semibold uppercase tracking-wider text-text-muted">Key Drivers</p>
      <div v-for="d in synthesis.keyDrivers" :key="d" class="flex items-start gap-1.5 text-xs text-text-secondary">
        <span class="mt-1.5 h-1.5 w-1.5 rounded-full bg-brand flex-shrink-0" />
        <span>{{ d }}</span>
      </div>
    </div>

    <!-- Bullish factors -->
    <div v-if="synthesis?.bullishFactors?.length" class="space-y-0.5">
      <p class="text-[10px] font-semibold uppercase tracking-wider text-success">Bullish Factors</p>
      <div v-for="f in synthesis.bullishFactors" :key="f" class="flex items-start gap-1.5 text-xs text-text-secondary">
        <span class="mt-1.5 h-1.5 w-1.5 rounded-full bg-success flex-shrink-0" />
        <span>{{ f }}</span>
      </div>
    </div>

    <!-- Bearish factors -->
    <div v-if="synthesis?.bearishFactors?.length" class="space-y-0.5">
      <p class="text-[10px] font-semibold uppercase tracking-wider text-danger">Bearish Factors</p>
      <div v-for="f in synthesis.bearishFactors" :key="f" class="flex items-start gap-1.5 text-xs text-text-secondary">
        <span class="mt-1.5 h-1.5 w-1.5 rounded-full bg-danger flex-shrink-0" />
        <span>{{ f }}</span>
      </div>
    </div>

    <!-- Fallback for synthesis in progress or error -->
    <p v-else class="text-xs text-text-muted">{{ stage.message || 'Generating final analysis...' }}</p>
  </div>
</template>
```

### Step 7: Commit

```bash
git add dashboard/src/components/AnalysisAccordion.vue
git add dashboard/src/components/StageNewsDetail.vue
git add dashboard/src/components/StageTechnicalDetail.vue
git add dashboard/src/components/StageFundamentalsDetail.vue
git add dashboard/src/components/StageBacktestDetail.vue
git add dashboard/src/components/StageSynthesisDetail.vue
git commit -m "feat: add AnalysisAccordion with per-stage detail components"
```

---

## Task 6: Frontend — Rewrite SentimentView.vue overview tab

**Files:**
- Modify: `dashboard/src/views/SentimentView.vue`

### Step 1: Replace all cards with AnalysisAccordion

In the Overview tab, remove:
- `ScoreCard`
- `SourceBreakdown`
- `NewsSentimentPanel`
- `TechnicalIndicatorsComp`
- `FundamentalsPanel`
- `BacktestPanel`

Replace the block from line 80 (`<!-- Composite analysis results -->`) to line 126 with:

```vue
<!-- Analysis accordion (replaces all separate cards) -->
<AnalysisAccordion
  :stages="analysisStages"
  :current-stage="analysisCurrentStage"
  :is-complete="analysisComplete"
  :duration-ms="analysisDurationMs"
  :error="analysisError"
  :composite="composite"
/>
```

Also replace the `AnalysisProgressComp` usage (line 64-71) with `AnalysisAccordion`.

### Step 2: Update imports

Replace:
```typescript
import AnalysisProgressComp from '../components/AnalysisProgress.vue'
import ScoreCard from '../components/ScoreCard.vue'
import SourceBreakdown from '../components/SourceBreakdown.vue'
import NewsSentimentPanel from '../components/NewsSentimentPanel.vue'
import TechnicalIndicatorsComp from '../components/TechnicalIndicators.vue'
import FundamentalsPanel from '../components/FundamentalsPanel.vue'
import BacktestPanel from '../components/BacktestPanel.vue'
```

With:
```typescript
import AnalysisAccordion from '../components/AnalysisAccordion.vue'
```

Remove the old imports.

### Step 3: Update Details tab

The Details tab currently shows the same 8 cards. Replace with the accordion too:

```vue
<AnalysisAccordion
  :stages="analysisStages"
  :current-stage="analysisCurrentStage"
  :is-complete="analysisComplete"
  :duration-ms="analysisDurationMs"
  :error="analysisError"
  :composite="composite"
/>
```

### Step 4: Remove unused component imports from script

Remove imports for: `ScoreCard`, `SourceBreakdown`, `NewsSentimentPanel`, `TechnicalIndicatorsComp`, `FundamentalsPanel`, `BacktestPanel`.

### Step 5: Commit

```bash
git add dashboard/src/views/SentimentView.vue
git commit -m "feat: replace all result cards with AnalysisAccordion in SentimentView"
```

---

## Task 7: Frontend — Convert News tab to Article Browser

**Files:**
- Modify: `dashboard/src/views/SentimentView.vue` (News tab section)
- Create: `dashboard/src/components/ArticleBrowser.vue`

### Step 1: Create ArticleBrowser.vue

```vue
<script setup lang="ts">
import { computed, ref } from 'vue'
import type { NewsArticle } from '../api/types'

interface Props {
  articles: NewsArticle[]
}
const props = defineProps<Props>()

const filterSource = ref('')
const filterSentiment = ref('')
const expandedArticle = ref<number | null>(null)

const filteredArticles = computed(() => {
  let result = props.articles
  if (filterSource.value) {
    result = result.filter(a => a.source === filterSource.value)
  }
  return result
})

const sources = computed(() => [...new Set(props.articles.map(a => a.source))])

const formatDate = (dateStr: string) => {
  try {
    return new Date(dateStr).toLocaleDateString('en-IN', {
      year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit',
    })
  } catch { return dateStr }
}
</script>

<template>
  <div class="space-y-4">
    <!-- Filters -->
    <div class="flex flex-wrap gap-2">
      <select v-model="filterSource" class="rounded-md border border-border-subtle bg-bg-primary px-3 py-1.5 text-xs text-text-primary">
        <option value="">All Sources</option>
        <option v-for="src in sources" :key="src" :value="src">{{ src }}</option>
      </select>
      <span class="text-xs text-text-muted">{{ filteredArticles.length }} of {{ articles.length }} articles</span>
    </div>

    <!-- Article list -->
    <div v-if="filteredArticles.length" class="space-y-2">
      <div
        v-for="(article, index) in filteredArticles"
        :key="article.link"
        class="cursor-pointer rounded-md border border-border-subtle/50 transition-colors"
        :class="expandedArticle === index ? 'bg-bg-hover' : 'hover:bg-bg-hover/50'"
        @click="expandedArticle = expandedArticle === index ? null : index"
      >
        <div class="p-3">
          <div class="flex items-start justify-between gap-2">
            <div class="min-w-0 flex-1">
              <div class="mb-1 flex items-center gap-2">
                <span class="rounded bg-brand/10 px-2 py-0.5 text-xs font-medium text-brand">{{ article.source }}</span>
                <span v-if="article.publishedDate" class="text-xs text-text-muted">{{ formatDate(article.publishedDate) }}</span>
              </div>
              <h3 class="text-sm font-medium text-text-primary">{{ article.title }}</h3>
            </div>
          </div>

          <div v-if="expandedArticle === index" class="mt-2 animate-fade-in space-y-2">
            <p v-if="article.description" class="text-xs text-text-secondary leading-relaxed">{{ article.description }}</p>
            <p v-if="article.rawContent && article.rawContent !== article.description" class="text-xs text-text-secondary leading-relaxed">{{ article.rawContent }}</p>
            <a v-if="article.link" :href="article.link" target="_blank" rel="noopener noreferrer" class="inline-block text-xs text-brand hover:underline">
              Read full article &rarr;
            </a>
          </div>
        </div>
      </div>
    </div>

    <!-- Empty state -->
    <div v-else class="card-panel p-5">
      <p class="text-sm text-text-muted">{{ articles.length === 0 ? 'No news articles found.' : 'No articles match the selected filter.' }}</p>
    </div>
  </div>
</template>
```

### Step 2: Replace News tab in SentimentView.vue

Replace the News tab section (lines 306-413) with:

```vue
<!-- News Tab — Article Browser -->
<div v-if="activeTab === 'news'">
  <div class="mb-6 card-panel p-5">
    <h3 class="mb-3 text-sm font-semibold text-text-primary">Select Symbol</h3>
    <form @submit.prevent="loadNews" class="flex flex-col sm:flex-row gap-3">
      <div class="flex-1">
        <input v-model="symbolInput" type="text" placeholder="e.g. RELIANCE" list="watchlistSymbols" required
          class="w-full rounded-md border border-border-subtle bg-bg-primary px-3 py-2 text-sm text-text-primary placeholder:text-text-muted/50 focus:outline-none focus:ring-2 focus:ring-brand/30" />
      </div>
      <button type="submit" :disabled="newsLoading"
        class="rounded-md bg-brand px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-brand/90 disabled:opacity-50">
        {{ newsLoading ? 'Loading...' : 'Fetch News' }}
      </button>
    </form>
    <p v-if="error" class="mt-3 text-xs text-danger">{{ error }}</p>
  </div>

  <div v-if="newsLoading" class="flex justify-center py-12">
    <div class="h-6 w-6 animate-spin rounded-full border-2 border-brand border-t-transparent" />
  </div>

  <ArticleBrowser v-else :articles="newsArticles" />
</div>
```

### Step 3: Update imports

Add:
```typescript
import ArticleBrowser from '../components/ArticleBrowser.vue'
```

Remove:
```typescript
import NewsSourceCard from '../components/NewsSourceCard.vue'
```

And remove `NewsSourceCard` usage from the Details tab.

### Step 4: Commit

```bash
git add dashboard/src/components/ArticleBrowser.vue
git add dashboard/src/views/SentimentView.vue
git commit -m "feat: convert News tab to article browser with source filtering"
```

---

## Task 8: Frontend — Clean up unused components

**Files to deprecate (no longer imported anywhere):**
- `dashboard/src/components/ScoreCard.vue`
- `dashboard/src/components/SourceBreakdown.vue`
- `dashboard/src/components/NewsSentimentPanel.vue`
- `dashboard/src/components/TechnicalIndicators.vue`
- `dashboard/src/components/FundamentalsPanel.vue`
- `dashboard/src/components/BacktestPanel.vue`
- `dashboard/src/components/NewsSourceCard.vue`
- `dashboard/src/components/StageSummaryCard.vue` (replaced by per-stage detail components)
- `dashboard/src/components/AnalysisProgress.vue` (replaced by AnalysisAccordion)

**Action:** Delete these files. They are no longer referenced anywhere after Tasks 5-7.

```bash
git rm dashboard/src/components/ScoreCard.vue
git rm dashboard/src/components/SourceBreakdown.vue
git rm dashboard/src/components/NewsSentimentPanel.vue
git rm dashboard/src/components/TechnicalIndicators.vue
git rm dashboard/src/components/FundamentalsPanel.vue
git rm dashboard/src/components/BacktestPanel.vue
git rm dashboard/src/components/NewsSourceCard.vue
git rm dashboard/src/components/StageSummaryCard.vue
git rm dashboard/src/components/AnalysisProgress.vue
git commit -m "chore: remove deprecated sentiment card components replaced by accordion"
```

---

## Task 9: Backend — DB migration (optional, for synthesis persistence)

**Files:**
- Create: `backend/data/src/main/resources/db/migration/V19__add_synthesis_to_sentiment.sql`

If synthesis results should persist across restarts, add a `synthesis_json` column to `sentiment_results`:

```sql
ALTER TABLE sentiment_results ADD COLUMN synthesis_json TEXT NULL;
```

This stores the `SynthesisResult` JSON for the latest analysis date per symbol. The frontend can fetch it via the existing `/api/sentiment/{symbol}/latest` endpoint (the `SentimentResult` entity would need a new `synthesis` field).

If synthesis is ephemeral (computed per-request, not stored), skip this migration.

---

## Task 10: Verification

### Step 1: Backend build

```bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
cd backend && mvn clean install -DskipTests
```

### Step 2: Frontend typecheck

```bash
cd dashboard && npm run typecheck
```

### Step 3: Frontend build

```bash
cd dashboard && npm run build
```

### Step 4: Dev server verification

```bash
cd dashboard && npm run dev
```

Open `http://localhost:3003` and verify:

1. Navigate to Sentiment page
2. Enter a symbol and click "Analyze"
3. Pipeline runs: stages 0-8 show in accordion with headline summaries
4. After stage 8, "LLM synthesis" stage appears and runs
5. Each stage row shows: icon + name + one-line summary
6. Click any stage to expand:
   - News stage: article count, sentiment, catalysts, red flags
   - Sentiment stage: score badge, confidence, summary
   - Technical stage: signal, score, confidence, indicator values
   - Fundamentals stage: score, factor list
   - Backtest stage: trades, win rate, profit factor, drawdown, return, expectancy
   - Composite stage: score, signal, confidence, source weights, reasoning
   - Synthesis stage: recommendation, confidence, narrative, bullish/bearish factors
7. News tab: article browser with source filter
8. History tab: unchanged
9. No console errors
10. No TypeScript errors

### Step 5: Backend test

```bash
cd backend && mvn test
```

---

## File Change Summary

### Backend (7 changes)
| # | Action | File |
|---|--------|------|
| 1 | Modify | `backend/api/src/main/java/com/swingtrade/api/dto/AnalysisProgress.java` |
| 2 | Modify | `backend/api/src/main/java/com/swingtrade/api/dto/CompositeAnalysis.java` |
| 3 | Create | `backend/api/src/main/java/com/swingtrade/api/dto/SynthesisResult.java` |
| 4 | Create | `backend/llm/src/main/java/com/swingtrade/llm/service/SynthesisService.java` |
| 5 | Modify | `backend/api/src/main/java/com/swingtrade/api/service/AnalysisOrchestratorService.java` |
| 6 | Modify | `backend/api/src/main/java/com/swingtrade/api/service/CompositeAnalysisService.java` |
| 7 | Create (optional) | `backend/data/src/main/resources/db/migration/V19__add_synthesis_to_sentiment.sql` |

### Frontend (13 changes)
| # | Action | File |
|---|--------|------|
| 1 | Modify | `dashboard/src/api/types.ts` |
| 2 | Create | `dashboard/src/components/AnalysisAccordion.vue` |
| 3 | Create | `dashboard/src/components/StageNewsDetail.vue` |
| 4 | Create | `dashboard/src/components/StageTechnicalDetail.vue` |
| 5 | Create | `dashboard/src/components/StageFundamentalsDetail.vue` |
| 6 | Create | `dashboard/src/components/StageBacktestDetail.vue` |
| 7 | Create | `dashboard/src/components/StageSynthesisDetail.vue` |
| 8 | Create | `dashboard/src/components/ArticleBrowser.vue` |
| 9 | Modify | `dashboard/src/views/SentimentView.vue` |
| 10-18 | Delete | `ScoreCard.vue`, `SourceBreakdown.vue`, `NewsSentimentPanel.vue`, `TechnicalIndicators.vue`, `FundamentalsPanel.vue`, `BacktestPanel.vue`, `NewsSourceCard.vue`, `StageSummaryCard.vue`, `AnalysisProgress.vue` |