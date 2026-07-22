# LLM Accuracy Monitoring

Monitor and evaluate the accuracy of LLM-powered sentiment analysis over time. Track whether sentiment predictions correlate with actual market outcomes, detect signal drift, and enable A/B testing of prompts and models.

---

## 1. Overview

The LLM sentiment system produces scores (POSITIVE / NEUTRAL / NEGATIVE) with confidence levels. This document describes how to track whether those predictions are actually accurate, how to detect degradation, and how to use the data to improve the system.

### Why This Matters

- **Trust**: Know when the LLM is reliable vs. misleading
- **Calibration**: Ensure 80% confidence actually means ~80% accuracy
- **Drift Detection**: Alert when sentiment signal degrades (prompt changes, model updates, market regime shifts)
- **A/B Testing**: Compare prompts and models objectively
- **Strategy Tuning**: Identify which symbols have the most reliable sentiment signals

### Design Principles

- **Custom evaluation pipeline in PostgreSQL/TimescaleDB** — no external MLOps tools needed
- **5-day evaluation window** — sweet spot for swing trading (1-day is noisy, 21-day has too many confounding factors)
- **Stored in existing DB** — leverage existing infrastructure
- **Visualized via dashboard** — integrate with the SwingTrade dashboard

---

## 2. Ground Truth Design

### 2.1 Sentiment Score Mapping

| LLM Output | Numeric Score | Directional Label |
|---|---|---|
| Strongly bullish | +1.0 | UP |
| Bullish | +0.5 | UP |
| Neutral | 0.0 | FLAT |
| Bearish | -0.5 | DOWN |
| Strongly bearish | -1.0 | DOWN |

### 2.2 Return Computation

Actual return over a look-ahead window:

```
return(W, t) = (close(t + W) - close(t)) / close(t)
```

Where:
- `t` = date of sentiment analysis
- `W` = evaluation window in trading days
- `close(t + W)` = closing price at end of window

### 2.3 Evaluation Windows

| Window | Trading Days | Use Case | Noise Level |
|---|---|---|---|
| 1-day | 1 | News-driven sentiment | High |
| 5-day | 5 | **Swing trading sweet spot** | Medium |
| 21-day | 21 | Long-term signal quality | Low (but confounding factors) |

### 2.4 Ground Truth Labels

Based on return thresholds:

```
IF return >= +0.5% THEN label = 'UP'
IF return <= -0.5% THEN label = 'DOWN'
ELSE label = 'FLAT'
```

The 0.5% threshold filters out noise while capturing meaningful moves.

---

## 3. Metrics

### 3.1 Directional Accuracy

```
accuracy = (correct_UP + correct_DOWN) / (total_UP + total_DOWN)
```

Ignores FLAT predictions — measures how well sentiment predicts direction when it commits to a direction.

**Targets:**
- Warning: < 52% over 30 days
- Critical: < 48% over 30 days
- Good: > 55%

### 3.2 Per-Class Precision and Recall

| Metric | Formula | Interpretation |
|---|---|---|
| Precision (UP) | TP_UP / (TP_UP + FP_UP) | When LLM says UP, how often is it actually UP? |
| Recall (UP) | TP_UP / (TP_UP + FN_UP) | Of all actual UP days, how many did LLM catch? |
| Precision (DOWN) | TP_DOWN / (TP_DOWN + FP_DOWN) | When LLM says DOWN, how often is it actually DOWN? |
| Recall (DOWN) | TP_DOWN / (TP_DOWN + FN_DOWN) | Of all actual DOWN days, how many did LLM catch? |

### 3.3 Spearman Information Coefficient (IC)

Spearman rank correlation between sentiment scores and actual returns:

```
IC = Spearman(sentiment_score, return)
```

Computed over a rolling 30-day window. Tracks how well sentiment ranks symbols by future performance.

**Targets:**
- Warning: < 0.08 over 30 days
- Critical: < 0.03 over 30 days
- Good: > 0.10 (academic literature shows 0.15-0.25 is strong for sentiment signals)

**IC Decay**: Track when the signal loses predictive power. Alert when 30-day rolling IC drops below moving average minus 2 standard deviations.

### 3.4 Calibration Error

Bin sentiment scores into deciles. For each bin, compute the actual fraction of UP outcomes. Plot predicted confidence midpoint vs. actual frequency.

```
ECE = SUM_b (|bin_mean_confidence - bin_actual_accuracy| * bin_weight)
```

A perfectly calibrated model follows the diagonal (predicted = actual).

**Targets:**
- Warning: ECE > 15%
- Critical: ECE > 25%

Identifies if the LLM is overconfident or underconfident in certain ranges.

### 3.5 Signal Volume

Track the number of sentiment signals produced per day. Sudden drops may indicate data ingestion failures, not model degradation.

**Alerts:**
- Warning: < 50% of 7-day baseline
- Critical: < 25% of 7-day baseline

---

## 4. Database Schema

### 4.1 Enhanced `sentiment_accuracy` Table

```sql
CREATE TABLE sentiment_accuracy (
    id            BIGSERIAL PRIMARY KEY,
    symbol        VARCHAR(20) NOT NULL,
    analysis_date DATE NOT NULL,
    llm_score     VARCHAR(20) NOT NULL,           -- POSITIVE, NEUTRAL, NEGATIVE
    llm_confidence REAL NOT NULL,                 -- LLM's own confidence (0.0-1.0)
    numeric_score REAL NOT NULL,                  -- Mapped numeric score (-1.0 to +1.0)

    -- Ground truth
    actual_return_1d  DECIMAL(10,6),
    actual_return_5d  DECIMAL(10,6),
    actual_return_21d DECIMAL(10,6),
    ground_truth_label VARCHAR(10),                -- UP, DOWN, FLAT

    was_correct       BOOLEAN,
    pnl_pct           DECIMAL(10,6),              -- PnL if position was held

    -- Context
    market_regime     VARCHAR(10),                -- BULL, BEAR, NEUTRAL (from 200-day MA)
    prompt_hash       VARCHAR(64),                -- Hash of prompt used
    model_version     VARCHAR(50),                -- LLM model version
    composite_score   INT,                        -- Composite analysis score (-100 to +100)
    composite_signal  VARCHAR(10),                -- BUY, SELL, HOLD

    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    evaluated_at      TIMESTAMPTZ                 -- When ground truth was computed

    -- Composite analysis FK (optional, only when available)
    composite_id      BIGINT REFERENCES composite_analysis(id)
);

-- Hypertable for time-series queries
SELECT create_hypertable('sentiment_accuracy', 'analysis_date');

-- Indexes
CREATE INDEX idx_sentiment_accuracy_symbol_date ON sentiment_accuracy (symbol, analysis_date);
CREATE INDEX idx_sentiment_accuracy_label ON sentiment_accuracy (ground_truth_label);
CREATE INDEX idx_sentiment_accuracy_regime ON sentiment_accuracy (market_regime);
CREATE INDEX idx_sentiment_accuracy_evaluated ON sentiment_accuracy (evaluated_at) WHERE evaluated_at IS NOT NULL;
```

### 4.2 Continuous Aggregates

```sql
-- Daily accuracy summary
CREATE MATERIALIZED VIEW sentiment_accuracy_daily
WITH (timescaledb.continuous) AS
SELECT
    time_bucket('1 day', analysis_date) AS bucket,
    llm_score,
    COUNT(*) AS total_signals,
    COUNT(*) FILTER (WHERE was_correct = true) AS correct_count,
    AVG(llm_confidence) AS avg_confidence,
    AVG(actual_return_5d) AS avg_return_5d
FROM sentiment_accuracy
GROUP BY bucket, llm_score;

-- Refresh every hour
SELECT refresh_continuous_aggregate('sentiment_accuracy_daily', NULL, NULL);
```

### 4.3 Retention Policy

```sql
-- Raw evaluations: keep 1 year, then compress
SELECT add_retention_policy('sentiment_accuracy',
    INTERVAL '1 year',
    INTERVAL '7 days');

-- Continuous aggregates: retain indefinitely
```

---

## 5. Evaluation Pipeline

### 5.1 Flow

```
t=0: LLM Sentiment analysis
     -> Store in sentiment_results table
     -> Store in composite_analysis (if composite pipeline used)

t=1, 5, 21: OHLCV data arrives via normal ingestion
     -> Evaluation job picks up sentiment records
     -> Joins with OHLCV candles at t+1, t+5, t+21
     -> Computes actual_return_1d, actual_return_5d, actual_return_21d
     -> Determines ground_truth_label from return thresholds
     -> Computes market_regime from 200-day MA
     -> Inserts into sentiment_accuracy

t+21+: Metrics computed from sentiment_accuracy table
     -> Rolling accuracy, IC, calibration
     -> Dashboard displays results
```

### 5.2 Evaluation Job (Scheduled)

```java
@Component
public class SentimentEvaluationJob {

    private final OhlcvCandleRepository candleRepository;
    private final SentimentAccuracyRepository accuracyRepository;
    private final SentimentResultRepository sentimentRepository;

    @Scheduled(cron = "0 0 2 * * *") // Run nightly at 2 AM
    public void evaluatePendingSentiments() {
        List<SentimentResult> pending = sentimentRepository.findAllByEvaluatedFalse();

        for (SentimentResult sentiment : pending) {
            String symbol = sentiment.getSymbol();
            LocalDate analysisDate = sentiment.getDate();

            // Fetch OHLCV at +1, +5, +21 days
            OhlcvCandleEntity entry = candleRepository
                .findFirstBySymbolAndDateAfterOrderByDateAsc(symbol, analysisDate);
            OhlcvCandleEntity exit1 = candleRepository
                .findNthBySymbolAndDateAfterOrderByDateAsc(symbol, analysisDate, 1);
            OhlcvCandleEntity exit5 = candleRepository
                .findNthBySymbolAndDateAfterOrderByDateAsc(symbol, analysisDate, 5);
            OhlcvCandleEntity exit21 = candleRepository
                .findNthBySymbolAndDateAfterOrderByDateAsc(symbol, analysisDate, 21);

            if (entry == null || exit1 == null) continue;

            // Compute returns
            BigDecimal return1d = computeReturn(entry, exit1);
            BigDecimal return5d = exit5 != null ? computeReturn(entry, exit5) : null;
            BigDecimal return21d = exit21 != null ? computeReturn(entry, exit21) : null;

            // Determine ground truth
            String label = classifyReturn(return1d); // UP/DOWN/FLAT

            // Compute market regime
            String regime = computeMarketRegime(symbol, entry); // BULL/BEAR/NEUTRAL

            // Store accuracy record
            SentimentAccuracyEntity record = new SentimentAccuracyEntity();
            record.setSymbol(symbol);
            record.setAnalysisDate(analysisDate);
            record.setLlmScore(sentiment.getScore().name());
            record.setLlmConfidence(sentiment.getConfidence());
            record.setNumericScore(mapToNumeric(sentiment.getScore()));
            record.setActualReturn1d(return1d.doubleValue());
            record.setActualReturn5d(return5d != null ? return5d.doubleValue() : null);
            record.setActualReturn21d(return21d != null ? return21d.doubleValue() : null);
            record.setGroundTruthLabel(label);
            record.setWasCorrect(wasCorrect(sentiment.getScore(), label));
            record.setMarketRegime(regime);
            record.setPromptHash(sentiment.getPromptHash());
            record.setModelVersion(sentiment.getModelVersion());
            record.setEvaluatedAt(LocalDate.now());

            accuracyRepository.save(record);
        }
    }
}
```

### 5.3 Market Regime Detection

```java
public String computeMarketRegime(String symbol, OhlcvCandleEntity reference) {
    // Use 200-day moving average of close price
    List<OhlcvCandleEntity> candles = candleRepository
        .findAllBySymbolOrderByDateAsc(symbol);

    if (candles.size() < 200) return "NEUTRAL";

    BigDecimal sma200 = candles.stream()
        .skip(candles.size() - 200)
        .map(OhlcvCandleEntity::getClosePrice)
        .reduce(BigDecimal.ZERO, BigDecimal::add)
        .divide(BigDecimal.valueOf(200), 4, RoundingMode.HALF_UP);

    BigDecimal price = reference.getClosePrice();

    if (price.doubleValue() > sma200.doubleValue() * 1.02) return "BULL";
    if (price.doubleValue() < sma200.doubleValue() * 0.98) return "BEAR";
    return "NEUTRAL";
}
```

---

## 6. Metrics Queries

### 6.1 Directional Accuracy (30-day rolling)

```sql
WITH window AS (
    SELECT *
    FROM sentiment_accuracy
    WHERE evaluated_at IS NOT NULL
      AND ground_truth_label IN ('UP', 'DOWN')
      AND evaluated_at >= NOW() - INTERVAL '30 days'
)
SELECT
    COUNT(*) FILTER (WHERE was_correct = true)::FLOAT /
    NULLIF(COUNT(*) FILTER (WHERE was_correct = true) +
           COUNT(*) FILTER (WHERE was_correct = false), 0) AS accuracy
FROM window;
```

### 6.2 Accuracy by Evaluation Window

```sql
SELECT
    CASE
        WHEN actual_return_1d IS NOT NULL THEN '1-day'
        WHEN actual_return_5d IS NOT NULL THEN '5-day'
        ELSE '21-day'
    END AS window,
    COUNT(*) AS total,
    AVG(CASE WHEN was_correct THEN 1.0 ELSE 0.0 END) AS accuracy
FROM sentiment_accuracy
WHERE evaluated_at IS NOT NULL
GROUP BY window;
```

### 6.3 Accuracy by Market Regime

```sql
SELECT
    market_regime,
    COUNT(*) AS total,
    AVG(llm_confidence) AS avg_confidence,
    AVG(CASE WHEN was_correct THEN 1.0 ELSE 0.0 END) AS accuracy
FROM sentiment_accuracy
WHERE evaluated_at IS NOT NULL
GROUP BY market_regime
ORDER BY accuracy DESC;
```

### 6.4 Accuracy by Symbol

```sql
SELECT
    symbol,
    COUNT(*) AS total_signals,
    AVG(CASE WHEN was_correct THEN 1.0 ELSE 0.0 END) AS accuracy,
    AVG(llm_confidence) AS avg_confidence
FROM sentiment_accuracy
WHERE evaluated_at IS NOT NULL
GROUP BY symbol
ORDER BY total_signals DESC;
```

### 6.5 Calibration Error (ECE)

```sql
WITH bins AS (
    SELECT
        FLOOR(llm_confidence * 10) / 10 AS confidence_bin,
        AVG(CASE WHEN was_correct THEN 1.0 ELSE 0.0 END) AS actual_accuracy,
        AVG(llm_confidence) AS predicted_confidence,
        COUNT(*) AS count
    FROM sentiment_accuracy
    WHERE evaluated_at IS NOT NULL
      AND llm_confidence IS NOT NULL
    GROUP BY confidence_bin
)
SELECT
    SUM(ABS(predicted_confidence - actual_accuracy) * count / SUM(count) OVER ()) AS ece
FROM bins;
```

### 6.6 Spearman IC (Rolling 30-day)

```sql
-- PostgreSQL does not have built-in Spearman correlation.
-- Use rank correlation via PERCENT_RANK:
WITH ranked AS (
    SELECT
        numeric_score,
        actual_return_5d,
        PERCENT_RANK() OVER (ORDER BY numeric_score) AS sentiment_rank,
        PERCENT_RANK() OVER (ORDER BY actual_return_5d) AS return_rank
    FROM sentiment_accuracy
    WHERE evaluated_at IS NOT NULL
      AND actual_return_5d IS NOT NULL
      AND evaluated_at >= NOW() - INTERVAL '30 days'
)
SELECT
    1 - (6.0 * SUM((sentiment_rank - return_rank) ^ 2)) /
        (COUNT(*) * (COUNT(*) ^ 2 - 1)) AS spearman_ic
FROM ranked;
```

### 6.7 Calibration Curve Data

```sql
SELECT
    confidence_bin,
    predicted_confidence,
    actual_accuracy,
    ABS(predicted_confidence - actual_accuracy) AS error,
    count
FROM bins
ORDER BY confidence_bin;
```

---

## 7. Alert Thresholds

| Metric | Warning | Critical |
|---|---|---|
| Directional accuracy (30d) | < 52% | < 48% |
| Spearman IC (30d) | < 0.08 | < 0.03 |
| Signal volume (7d avg) | < 50% of baseline | < 25% of baseline |
| Calibration error (max) | > 15% | > 25% |
| Accuracy in BEAR regime | < 40% | < 30% |

---

## 8. Dashboard Design

### 8.1 Accuracy Overview Tab

- **Directional accuracy** — current 30-day accuracy with trend line
- **Signal volume** — daily count of sentiment analyses
- **Calibration error** — current ECE value
- **Spearman IC** — 30-day rolling IC

### 8.2 Accuracy Breakdown Tab

- **By evaluation window** — accuracy at 1d, 5d, 21d
- **By market regime** — accuracy in BULL/BEAR/NEUTRAL
- **By symbol** — top/bottom symbols by accuracy
- **By LLM score** — POSITIVE/NEUTRAL/NEGATIVE breakdown

### 8.3 Calibration Tab

- **Calibration curve** — scatter plot of predicted vs actual accuracy
- **ECE trend** — historical ECE values
- **Confidence vs accuracy scatter** — per-signal plot

### 8.4 A/B Testing Tab

- **Prompt comparison** — accuracy by prompt_hash
- **Model comparison** — accuracy by model_version
- **Rolling accuracy by version** — time series per prompt/model

---

## 9. Implementation Plan

### Phase 1: Schema & Evaluation Job (Week 1)

1. Create migration `V__enhance_sentiment_accuracy.sql` with new columns
2. Add `findNthBySymbolAndDateAfterOrderByDateAsc` to OhlcvCandleRepository
3. Implement `SentimentEvaluationJob` with 5-day window
4. Add market regime detection
5. Add prompt_hash and model_version to SentimentResult

### Phase 2: Metrics Queries & API (Week 2)

1. Implement `SentimentAccuracyController` with endpoints:
   - `GET /api/sentiment/accuracy/summary` — aggregate accuracy stats
   - `GET /api/sentiment/accuracy/by-window` — accuracy by evaluation window
   - `GET /api/sentiment/accuracy/by-regime` — accuracy by market regime
   - `GET /api/sentiment/accuracy/by-symbol` — per-symbol accuracy
   - `GET /api/sentiment/accuracy/calibration` — calibration curve data
   - `GET /api/sentiment/accuracy/rolling-ic` — rolling Spearman IC
2. Implement continuous aggregates for daily summaries

### Phase 3: Dashboard (Week 3)

1. Create `SentimentAccuracyView.vue` with tabs
2. Add accuracy overview with charts (recharts)
3. Add breakdown views
4. Add calibration curve visualization
5. Wire up all API endpoints

### Phase 4: Alerts & Monitoring (Week 4)

1. Implement alert checks in a scheduled job
2. Add Discord/Telegram notifications for threshold breaches
3. Add signal volume monitoring
4. Add drift detection (KS-test on score distribution)

---

## 10. Related Work

### Academic Research

- Kirtac et al., LSE (2024) — Statistically significant next-day return prediction using LLM sentiment (Spearman rho 0.15-0.25)
- Tetlock et al. (2008) — Media optimism and stock returns (foundational media sentiment research)
- Loughran & McDonald (2011) — Financial sentiment dictionaries and their limitations

### Industry Practices

- Quant funds track sentiment as one alpha factor among many
- Calibration is critical — overconfident sentiment signals cause position sizing errors
- Market regime stratification is the single most important confounding control
- Signal volume monitoring catches data pipeline failures before they become accuracy issues

### MLOps Tools (Not Used)

- **LangSmith** — tracing, evaluation datasets, A/B testing
- **Langfuse** — open-source, self-hosted, integrates with PostgreSQL
- **Arize AI** — drift detection, SHAP explainability

These are not integrated because the custom PostgreSQL/TimescaleDB pipeline is simpler and more maintainable for this system.

---

## Appendix: Glossary

| Term | Definition |
|---|---|
| **Directional Accuracy** | Fraction of directional predictions (UP/DOWN) that match actual direction |
| **Spearman IC** | Rank correlation between sentiment scores and returns |
| **ECE** | Expected Calibration Error — average gap between predicted and actual accuracy |
| **Market Regime** | Broad market state: BULL (price > 200-day MA), BEAR (price < 200-day MA), NEUTRAL |
| **Signal Volume** | Number of sentiment signals produced per day |
| **Ground Truth** | Actual market outcome used to evaluate sentiment accuracy |
| **Evaluation Window** | Time period between sentiment analysis and outcome measurement |
| **Prompt Hash** | SHA-256 hash of the prompt template used, for A/B testing |
| **Model Version** | LLM model identifier (e.g., "gpt-4o-2024-05-13") |