---
name: swing-trade-signal-suppression-analyzer
description: Analyze why trading signals are being suppressed due to sentiment filtering in the swing-trade system. Use this skill when signal count drops unexpectedly, when investigating sentiment analysis effectiveness, before optimizing signal generation parameters, or when evaluating sentiment filtering impact on trading opportunities. This skill is critical for understanding signal generation behavior and should be triggered proactively when signal counts deviate from expected ranges.
---

# SwingTrade Signal Suppression Analyzer Skill

## Overview

This skill analyzes the reasons why trading signals are being suppressed, particularly focusing on sentiment-based suppression. It helps understand the impact of sentiment filtering on trading opportunities and provides recommendations for optimizing signal generation.

## When to Use This Skill

Trigger this skill when:
- Signal generation count drops below expected range
- Investigating sentiment analysis effectiveness
- Before adjusting sentiment filtering parameters
- When evaluating trading opportunity loss
- After LLM model or prompt changes
- Quarterly signal quality review

## Signal Suppression Mechanism

### Suppression Logic

```java
public class SignalSuppressionChecker {
    // BUY signals are suppressed when sentiment is NEGATIVE
    public boolean shouldSuppress(Signal signal, SentimentResult sentiment) {
        if (signal.getSignalType() == BUY) {
            if (sentiment.getSentiment() == NEGATIVE) {
                return true; // Suppress BUY signal
            }
            if (sentiment.getSentiment() == NEUTRAL) {
                signal.setWarningFlag(WARNING_NEUTRAL_SENTIMENT);
                // Save but flag
            }
        }
        return false; // Don't suppress SELL/HOLD signals
    }
}
```

### Suppression Categories

| Category | Condition | Action |
|----------|-----------|--------|
| NEGATIVE_SENTIMENT | Sentiment = NEGATIVE | Full suppression |
| NEUTRAL_SENTIMENT | Sentiment = NEUTRAL | Flag and save |
| DATA_UNAVAILABLE | No sentiment data | Save with WARNING |
| SENTIMENT_TIMEOUT | API timeout | Save with WARNING |
| SENTIMENT_ERROR | API error | Save with WARNING |

## Analysis Workflow

```
1. Fetch signal data for period
   ↓
2. Categorize signals by type and status
   ↓
3. Analyze suppression reasons
   ↓
4. Calculate opportunity loss
   ↓
5. Identify patterns
   ↓
6. Generate recommendations
```

## Suppression Analysis Queries

```sql
-- Total signals generated vs saved
SELECT
    signal_date,
    COUNT(*) as total_generated,
    SUM(CASE WHEN warning_flag IS NOT NULL THEN 1 ELSE 0 END) as flagged,
    SUM(CASE WHEN warning_flag = 'WARNING_NEUTRAL_SENTIMENT' THEN 1 ELSE 0 END) as neutral_flagged,
    SUM(CASE WHEN warning_flag IS NULL THEN 1 ELSE 0 END) as saved_normally
FROM signals
WHERE signal_date BETWEEN '2024-01-01' AND '2024-01-31'
GROUP BY signal_date;

-- Symbols with highest suppression rates
SELECT
    symbol,
    COUNT(*) as total_signals,
    SUM(CASE WHEN warning_flag = 'WARNING_NEUTRAL_SENTIMENT' THEN 1 ELSE 0 END) as suppressed,
    ROUND(100.0 * SUM(CASE WHEN warning_flag = 'WARNING_NEUTRAL_SENTIMENT' THEN 1 ELSE 0 END) / COUNT(*), 2) as suppression_rate
FROM signals
WHERE signal_date BETWEEN '2024-01-01' AND '2024-01-31'
  AND signal_type = 'BUY'
GROUP BY symbol
ORDER BY suppression_rate DESC;

-- Sentiment distribution
SELECT
    sentiment,
    COUNT(*) as count,
    ROUND(100.0 * COUNT(*) / SUM(COUNT(*)) OVER (), 2) as percentage
FROM signals s
JOIN sentiment_results sr ON s.symbol = sr.symbol AND s.signal_date = sr.analysis_date
WHERE signal_date BETWEEN '2024-01-01' AND '2024-01-31'
GROUP BY sentiment;

-- High confidence suppressed signals
SELECT
    s.symbol,
    s.signal_date,
    s.confidence,
    sr.sentiment,
    sr.reasoning
FROM signals s
JOIN sentiment_results sr ON s.symbol = sr.symbol AND s.signal_date = sr.analysis_date
WHERE s.signal_date BETWEEN '2024-01-01' AND '2024-01-31'
  AND s.signal_type = 'BUY'
  AND s.confidence >= 0.7
  AND sr.sentiment = 'NEUTRAL'
ORDER BY s.confidence DESC;
```

## Suppression Impact Analysis

### Opportunity Loss Calculation

```java
public class OpportunityLossCalculator {
    // Calculate potential trades lost due to suppression
    public OpportunityLoss analyze(List<SuppressedSignal> suppressed) {
        double totalOpportunity = 0;
        int tradeCount = 0;

        for (SuppressedSignal s : suppressed) {
            // Calculate potential profit if signal was not suppressed
            double potentialProfit = calculatePotentialProfit(s.symbol, s.entryPrice);
            totalOpportunity += potentialProfit;
            tradeCount++;
        }

        return OpportunityLoss.builder()
            .tradeCount(tradeCount)
            .totalOpportunity(totalOpportunity)
            .averageOpportunity(totalOpportunity / tradeCount)
            .build();
    }
}
```

### Sentiment Accuracy Impact

```java
public class SentimentAccuracyImpact {
    // For suppressed signals, check if they would have been profitable
    public double calculateFalsePositiveRate(List<SuppressedSignal> suppressed) {
        int falsePositives = 0;

        for (SuppressedSignal s : suppressed) {
            double actualReturn = getReturnAfterNPdays(s.symbol, s.date, 5);
            if (actualReturn > 0) {
                falsePositives++; // Would have been profitable
            }
        }

        return (double) falsePositives / suppressed.size();
    }
}
```

## Suppression Analysis Report Format

```
# Signal Suppression Analysis Report - [Period]

## Executive Summary
- Total BUY Signals: [X]
- Suppressed Signals: [X] ([X]%)
- Neutral Flagged: [X] ([X]%)
- Saved Normally: [X] ([X]%)

## Suppression Breakdown
| Reason | Count | Percentage |
|--------|-------|------------|
| NEGATIVE_SENTIMENT | [X] | [X]% |
| NEUTRAL_SENTIMENT | [X] | [X]% |
| DATA_UNAVAILABLE | [X] | [X]% |
| SENTIMENT_TIMEOUT | [X] | [X]% |

## Top Suppressed Symbols
| Symbol | Total Signals | Suppressed | Suppression Rate |
|--------|---------------|------------|------------------|
| TATASTEEL | 15 | 12 | 80% |
| INFY | 10 | 7 | 70% |
| HDFCBANK | 12 | 7 | 58% |

## High Confidence Suppressed Signals
| Symbol | Date | Confidence | Sentiment | Potential Impact |
|--------|------|------------|-----------|------------------|
| RELIANCE | 2024-01-15 | 0.85 | NEUTRAL | High |
| INFY | 2024-01-18 | 0.82 | NEUTRAL | Medium |

## Sentiment Accuracy Analysis
- False Positive Rate (suppressed signals that would have won): [X]%
- False Negative Rate (non-suppressed signals that lost): [X]%
- Overall Sentiment Accuracy: [X]%

## Opportunity Loss
- Total Potential Trades Lost: [X]
- Average Opportunity per Trade: [X]%
- Estimated Capital Opportunity Lost: ₹[X]

## Pattern Analysis
### By Sector
| Sector | Signals | Suppression Rate |
|--------|---------|------------------|
| IT | 25 | 65% |
| Banking | 30 | 45% |
| Auto | 20 | 55% |

### By Time
| Week | Signals | Suppression Rate |
|------|---------|------------------|
| Week 1 | 20 | 40% |
| Week 2 | 25 | 55% |
| Week 3 | 18 | 60% |

## Recommendations
1. [Parameter adjustments]
2. [Sentiment threshold changes]
3. [LLM prompt optimizations]
4. [Sector-specific filtering]
```

## Optimization Recommendations

### Sentiment Threshold Tuning

```java
public class SentimentThresholdOptimizer {
    // Current: All NEGATIVE sentiment suppresses BUY signals
    // Proposed: Only strong NEGATIVE (confidence > 0.7) suppresses

    public OptimizationRecommendation analyzeAndRecommend() {
        // Analyze suppression rates at different confidence thresholds
        double currentSuppression = getCurrentSuppressionRate();
        double projectedSuppression = getProjectedSuppressionRate(0.7);

        return OptimizationRecommendation.builder()
            .currentThreshold("ANY_NEGATIVE")
            .recommendedThreshold("STRONG_NEGATIVE (confidence > 0.7)")
            .projectedSuppressionChange("-15%")
            .expectedOpportunityGain "+20% more trades"
            .build();
    }
}
```

### Sector-Specific Filtering

```java
public class SectorSpecificFiltering {
    // Different sectors may have different sentiment thresholds
    Map<String, SentimentThreshold> sectorThresholds = Map.of(
        "IT", SentimentThreshold.STRONG_NEGATIVE,  // More volatile
        "Banking", SentimentThreshold.ANY_NEGATIVE, // More stable
        "Auto", SentimentThreshold.MODERATE_NEGATIVE // Medium volatility
    );
}
```

## Example Usage

```bash
# Full suppression analysis
/skill: swing-trade-signal-suppression-analyzer

# Analyze specific period
/skill: swing-trade-signal-suppression-analyzer --start 2024-01-01 --end 2024-01-31

# Focus on high confidence signals
/skill: swing-trade-signal-suppression-analyzer --min-confidence 0.7

# Generate optimization recommendations
/skill: swing-trade-signal-suppression-analyzer --recommend

# Analyze by sector
/skill: swing-trade-signal-suppression-analyzer --by-sector
```

## Dependencies

- Database access (signals, sentiment_results tables)
- Historical price data (for opportunity calculation)
- LLM API access (for sentiment analysis review)

## Performance Considerations

- Analysis should complete within 1 minute for 1 month of data
- Use cached sentiment results where possible
- Consider incremental analysis for long periods
- Optimize queries with proper indexes
