---
name: swing-trade-llm-prompt-optimizer
description: Test and optimize LLM prompts for sentiment analysis accuracy in the swing-trade system. Use this skill when sentiment accuracy drops, when modifying prompt templates, after LLM provider changes, or when implementing new sentiment analysis features. This skill is critical for maintaining reliable signal filtering and should be triggered proactively whenever LLM-based decisions show degraded performance.
---

# SwingTrade LLM Prompt Optimizer Skill

## Overview

This skill automates the testing and optimization of LLM prompts used for sentiment analysis in the swing-trade system. It ensures prompts produce accurate, consistent sentiment classifications that effectively filter trading signals.

## When to Use This Skill

Trigger this skill when:
- Sentiment analysis accuracy appears to degrade
- Modifying sentiment prompt templates
- After LLM provider or model changes
- When signal suppression rates change unexpectedly
- Before deploying new sentiment analysis features
- Quarterly prompt performance review

## Prompt Optimization Framework

### 1. Current Prompt Analysis

```java
public class SentimentPrompt {
    private static final String SYSTEM_PROMPT = """
        You are an expert financial analyst specializing in Indian stocks.
        Analyze the provided news articles and determine the sentiment for the stock.

        Output JSON format:
        {
            "sentiment": "POSITIVE" | "NEUTRAL" | "NEGATIVE",
            "confidence": 0.0-1.0,
            "reasoning": "Brief explanation"
        }
    """;

    private static final String USER_TEMPLATE = """
        Stock: {symbol}
        Price: {price}
        Change: {change}%

        News Articles:
        {news_articles}

        Analyze sentiment based on:
        1. Company-specific news (earnings, management, products)
        2. Sector trends affecting this stock
        3. Market sentiment indicators
        4. Technical analysis mentions
    """;
}
```

### 2. Prompt Variants to Test

```java
public class PromptVariants {
    // Variant A: Original prompt
    // Variant B: Enhanced with more context
    // Variant C: Simplified for faster response
    // Variant D: Few-shot learning with examples
    // Variant E: Chain-of-thought reasoning

    Map<String, PromptConfig> variants = Map.of(
        "original", originalPrompt,
        "enhanced", enhancedPrompt,
        "simplified", simplifiedPrompt,
        "fewshot", fewShotPrompt,
        "cot", chainOfThoughtPrompt
    );
}
```

### 3. Evaluation Metrics

```java
public class EvaluationMetrics {
    // Accuracy: Correct sentiment predictions
    private double accuracy;

    // Confidence calibration: Does confidence match actual accuracy?
    private double calibrationScore;

    // Response time: Average time to generate sentiment
    private double avgResponseTime;

    // Consistency: Same input produces same output
    private double consistencyScore;

    // JSON validity: Properly formatted output
    private double jsonValidityRate;
}
```

## Testing Workflow

```
1. Collect historical sentiment predictions
   ↓
2. Compare against actual price movements
   ↓
3. Calculate accuracy metrics for each prompt variant
   ↓
4. Run A/B test with live data
   ↓
5. Evaluate response times and consistency
   ↓
6. Select best performing prompt
   ↓
7. Generate optimization recommendations
```

## Sentiment Accuracy Validation

### Backtesting Approach

```java
public class SentimentAccuracyValidator {
    // For each signal generated:
    // 1. Get sentiment prediction
    // 2. Track actual price movement over N days
    // 3. Calculate if sentiment was accurate

    public double calculateAccuracy(List<SentimentPrediction> predictions) {
        int correct = 0;
        for (Prediction p : predictions) {
            double actualReturn = getReturnAfterNPdays(p.symbol, p.date, 5);
            boolean predictedCorrectly = matchesSentiment(p.sentiment, actualReturn);
            if (predictedCorrectly) correct++;
        }
        return (double) correct / predictions.size();
    }

    private boolean matchesSentiment(Sentiment sentiment, double actualReturn) {
        return (sentiment == POSITIVE && actualReturn > 0) ||
               (sentiment == NEGATIVE && actualReturn < 0) ||
               (sentiment == NEUTRAL && Math.abs(actualReturn) < 0.02);
    }
}
```

### Expected Accuracy Benchmarks

| Metric | Target | Acceptable |
|--------|--------|------------|
| Sentiment Accuracy | > 65% | > 55% |
| Confidence Calibration | R² > 0.7 | R² > 0.5 |
| JSON Validity | > 98% | > 95% |
| Response Time | < 30s | < 60s |

## Prompt Optimization Report Format

```
# LLM Prompt Optimization Report - [Timestamp]

## Test Summary
- Prompts Tested: [X]
- Historical Samples: [X]
- Test Period: [start] to [end]

## Performance Comparison

| Prompt Variant | Accuracy | Calibration | Response Time | JSON Validity | Overall Score |
|----------------|----------|-------------|---------------|---------------|---------------|
| Original | 62% | 0.65 | 25s | 99% | 72 |
| Enhanced | 68% | 0.72 | 32s | 98% | 79 |
| Simplified | 58% | 0.58 | 18s | 97% | 64 |
| Few-Shot | 71% | 0.75 | 35s | 96% | 82 |
| Chain-of-Thought | 73% | 0.78 | 45s | 95% | 84 |

## Winner: Chain-of-Thought Prompt

### Recommended Changes
1. Switch to chain-of-thought reasoning format
2. Increase max tokens to 2048
3. Add temperature=0.3 for consistency

### Expected Improvements
- Accuracy: +11% (62% → 73%)
- Calibration: +13% (0.65 → 0.78)
- Response time: +20s (acceptable tradeoff)

## Implementation Details

### New Prompt Template
```
{optimized_prompt_template}
```

### Configuration Changes
- temperature: 0.3 (was 0.7)
- max_tokens: 2048 (was 1024)
- top_p: 0.9

## Validation Plan
1. Deploy to 10% of signals for 1 week
2. Monitor accuracy metrics daily
3. Full rollout if accuracy > 70%
```

## Example Usage

```bash
# Run full prompt optimization
/skill: swing-trade-llm-prompt-optimizer

# Test specific prompt variant
/skill: swing-trade-llm-prompt-optimizer --variant chain-of-thought

# Backtest on historical data
/skill: swing-trade-llm-prompt-optimizer --backtest --period 90-days

# Generate optimization recommendations
/skill: swing-trade-llm-prompt-optimizer --recommend

# A/B test with live traffic
/skill: swing-trade-llm-prompt-optimizer --ab-test --traffic 10-percent
```

## Dependencies

- LLM API access (vLLM/Ollama/OpenAI)
- Historical sentiment predictions
- Price movement data
- Prompt evaluation framework

## Performance Considerations

- Full optimization runs should complete within 10 minutes
- A/B tests should run for minimum 1 week
- Use cached predictions for backtesting
- Monitor API costs during testing
