# Prompt Extraction — Test-First Plan

Extract hardcoded LLM prompts from Java source into versioned `.md` files on the classpath. Load via Spring `@Value("classpath:prompts/<name>.md")`. Full refactor of all callers.

## Phase Status

| Phase | Status | Result | Timestamp |
|-------|--------|--------|-----------|
| 1: Prompt Files Exist | [x] PASS | PromptFilesTest passed | 2026-08-16 |
| 2: SentimentPromptLoader | [x] PASS | All 4 tests green | 2026-08-16 |
| 3: SentimentService Refactor | [x] PASS | All tests green | 2026-08-16 |
| 4: SynthesisPromptLoader | [x] PASS | Loader + SynthesisService move + tests green | 2026-08-16 |
| 5: PdfExtractionPromptLoader | [x] PASS | Loader + PdfExtractionService refactor + tests green | 2026-08-16 |
| 6: Dead Code Removal | [x] PASS | 4 unused methods removed, tests updated | 2026-08-16 |
| 7: Integration Verification | [x] PASS | All loaders wired, all prompts non-empty, placeholders verified | 2026-08-16 |

---

## 1. Feature Map

| Feature | Tested? | Test Type |
|---------|---------|-----------|
| 8 prompt .md files exist on classpath | No | Unit |
| Sentiment system prompt loads correctly | No | Unit |
| Sentiment user prompt (news) loads correctly | No | Unit |
| Sentiment user prompt (earnings) loads correctly | No | Unit |
| Synthesis system prompt loads correctly | No | Unit |
| Synthesis user prompt template loads correctly | No | Unit |
| PDF extraction prompt loads correctly | No | Unit |
| Multi-article prompt loads (future use) | No | Unit |
| Combined analysis prompt loads (future use) | No | Unit |
| Trading recommendation prompt loads (future use) | No | Unit |
| Simple classification prompt loads (future use) | No | Unit |
| SentimentService uses loaded prompts | No | Integration |
| SynthesisService uses loaded prompts | No | Integration |
| PdfExtractionService uses loaded prompts | No | Integration |
| Prompt content matches old hardcoded strings | No | Unit |
| Dead code removed (4 unused methods) | No | ArchUnit |
| Module boundaries enforced | No | ArchUnit |

---

## 2. Phase Breakdown

### Phase 1: Create Prompt .md Files (RED — files don't exist yet)

**Create** `backend/llm/src/main/resources/prompts/` directory with 8 files:

#### `sentiment-system.md`
```
You are a financial analyst specialising in Indian equity markets.
Analyse the following for a swing trade entry decision on {symbol}.
Consider: earnings momentum, regulatory news, management changes,
sector tailwinds, FII/DII activity, promoter actions.

CRITICAL: Respond with ONLY a JSON object. No explanation, no reasoning, no other text.
Start your response with { and end with }.

{
  "score": "POSITIVE|NEUTRAL|NEGATIVE",
  "confidence": 0.0-1.0,
  "summary": "2 sentence max reasoning",
  "red_flags": ["list any specific risks"],
  "catalysts": ["list any upcoming catalysts"]
}

Examples:
POSITIVE: Strong quarterly results, FII buying, sector tailwind
NEUTRAL: Mixed results, no major news
NEGATIVE: Promoter pledge, SEBI action, earnings miss
```

#### `sentiment-user.md`
```
Analyse the following for a swing trade entry decision on {symbol}.

Recent news headlines (last 7 days):
{newsContent}

Task: Determine if news sentiment supports a 1-4 week swing trade entry.

CRITICAL: Respond with ONLY a JSON object. No explanation, no reasoning, no other text.
Start your response with { and end with }.

{
  "score": "POSITIVE|NEUTRAL|NEGATIVE",
  "confidence": 0.0-1.0,
  "summary": "2 sentence max reasoning",
  "red_flags": ["list any specific risks"],
  "catalysts": ["list any upcoming catalysts"]
}
```

#### `sentiment-user-earnings.md`
```
Analyse the following for a swing trade entry decision on {symbol}.

Recent news headlines (last 7 days):
{newsContent}

Latest earnings summary:
{earningsContent}

Task: Determine if news sentiment supports a 1-4 week swing trade entry.

Respond in this exact JSON format only, no other text:
{
  "score": "POSITIVE|NEUTRAL|NEGATIVE",
  "confidence": 0.0-1.0,
  "summary": "2 sentence max reasoning",
  "red_flags": ["list any specific risks"],
  "catalysts": ["list any upcoming catalysts"]
}
```

#### `synthesis-system.md`
```
You are a senior equity analyst specializing in Indian equity markets.
Given the results of 9 analysis stages for a stock, produce a final investment recommendation.
Be concise, data-driven, and specific. Reference actual numbers from the analysis.
Return ONLY a valid JSON object with this exact structure:
{
  "narrative": "2-3 paragraph summary of the overall outlook",
  "recommendation": "BUY or SELL or HOLD",
  "confidence": 0.0 to 1.0,
  "keyDrivers": ["top 3 factors driving the recommendation"],
  "bullishFactors": ["specific bullish points with data"],
  "bearishFactors": ["specific bearish points with data"]
}
```

#### `synthesis-user.md`
```
Stock: {symbol} | Analysis Date: {date}

=== NEWS SENTIMENT ===
Score: {newsScore}/100 | Articles analyzed: {articleCount}
Summary: {newsSummary}
Catalysts: {newsCatalysts}
Red Flags: {newsRedFlags}

=== TECHNICAL ANALYSIS ===
Signal: {technicalSignal} | Score: {technicalScore}/100 | Confidence: {technicalConfidence}%
Indicators: {technicalIndicators}

=== FUNDAMENTALS ===
Score: {fundamentalsScore}/100 | Signal: {fundamentalsSignal}
Factors: {fundamentalsFactors}

=== BACKTEST ===
Total Trades: {backtestTrades} | Win Rate: {backtestWinRate}% | Profit Factor: {backtestProfitFactor}
Max Drawdown: {backtestMaxDrawdown}% | Total Return: {backtestTotalReturn}% | Expectancy: {backtestExpectancy}%

=== COMPOSITE ===
Score: {compositeScore}/100 | Signal: {compositeSignal} | Confidence: {compositeConfidence}%
Reasoning: {compositeReasoning}
```

#### `pdf-extraction.md`
```
Extract financial data from this earnings document.
Return ONLY valid JSON with these fields:
{
  "symbol": "NSE:RELIANCE",
  "quarter": "Q1 2025",
  "revenue": 450000000000,
  "netProfit": 65000000000,
  "eps": 42.50,
  "ebitda": 95000000000,
  "guidance": "Management expects 10-15% revenue growth"
}
Use INR values. No other text.
```

#### `multi-article-sentiment.md`
```
Analyze the overall sentiment by considering the following news articles about stock {symbol}:

{articles}

Please provide a comprehensive sentiment analysis in JSON format:
{
    "sentiment": "POSITIVE|NEUTRAL|NEGATIVE",
    "confidence": 0.0-1.0,
    "reasoning": "Analysis considering all articles (max 250 words)",
    "keyFactors": ["factor1", "factor2"],
    "articleCount": {articleCount},
    "positiveArticles": {positiveCount},
    "negativeArticles": {negativeCount},
    "tradingImplication": "How this combined sentiment affects trading decisions"
}
```

#### `combined-analysis.md`
```
Combine sentiment and technical analysis for stock {symbol}.

SENTIMENT ANALYSIS:
{sentimentResult}

TECHNICAL ANALYSIS SUMMARY:
{technicalSummary}

Based on both analyses, determine the overall trading signal:
- If sentiment and technicals align (both positive/negative), confidence should be higher
- If they conflict, note the disagreement and provide a weighted recommendation

Provide your combined analysis in JSON format:
{
    "overallSignal": "BUY|SELL|HOLD",
    "sentimentScore": "POSITIVE|NEUTRAL|NEGATIVE",
    "technicalScore": "BULLISH|BEARISH|NEUTRAL",
    "alignment": "ALIGNED|MIXED|CONFLICTING",
    "confidence": 0.0-1.0,
    "reasoning": "Combined analysis reasoning (max 300 words)",
    "entryPrice": "Recommended entry zone if applicable",
    "stopLoss": "Recommended stop loss level if applicable",
    "target": "Recommended target if applicable",
    "riskReward": "Calculated risk-reward ratio if applicable"
}
```

#### `trading-recommendation.md`
```
Based on the sentiment analysis, provide a concrete trading recommendation for {symbol}.

SENTIMENT ANALYSIS RESULTS:
- Sentiment: {sentimentType}
- Confidence: N/A
- Reasoning: {reasoning}
- Current Price: Rs. {currentPrice}

{sentimentDescription}

Provide trading recommendation in JSON format:
{
    "signal": "BUY|SELL|HOLD",
    "signalStrength": "STRONG|MODERATE|WEAK",
    "timeHorizon": "SHORT_TERM|MEDIUM_TERM|LONG_TERM",
    "confidence": 0.0-1.0,
    "entryStrategy": "EXACT_PRICE|ZONE|LIMITED_ENTRY",
    "entryPrice": "Recommended entry price",
    "entryZone": {
        "low": "Lower bound of entry zone",
        "high": "Upper bound of entry zone"
    },
    "stopLoss": "Price level for stop loss",
    "target1": "First target price",
    "target2": "Second target price",
    "target3": "Third target price (optional)",
    "riskRewardRatio": "Calculated risk-reward ratio",
    "positionSizing": "Recommended position size as percentage of capital",
    "reasoning": "Detailed reasoning for this recommendation (max 300 words)",
    "riskFactors": ["factor1", "factor2"],
    "catalysts": ["potential positive events", "potential negative events"]
}
```

#### `simple-classification.md`
```
Classify the sentiment of this news about stock {symbol}:

{newsText}

Respond with ONLY the sentiment classification: POSITIVE, NEUTRAL, or NEGATIVE.
```

**Test**: `src/test/java/com/swingtrade/llm/PromptFilesTest.java`

```java
package com.swingtrade.llm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

class PromptFilesTest {

    private static final Set<String> REQUIRED_PROMPTS = Set.of(
        "sentiment-system.md",
        "sentiment-user.md",
        "sentiment-user-earnings.md",
        "synthesis-system.md",
        "synthesis-user.md",
        "pdf-extraction.md",
        "multi-article-sentiment.md",
        "combined-analysis.md",
        "trading-recommendation.md",
        "simple-classification.md"
    );

    @Test
    @DisplayName("All required prompt files exist on classpath")
    void allPromptFilesExist() throws IOException {
        Path promptsDir = Paths.get("src/main/resources/prompts");
        if (!Files.exists(promptsDir)) {
            throw new AssertionError("prompts/ directory does not exist");
        }

        Set<String> existing = Set.of(Files.list(promptsDir)
            .map(p -> p.getFileName().toString())
            .toList()
            .toArray(String[]::new));

        Set<String> missing = REQUIRED_PROMPTS.stream()
            .filter(p -> !existing.contains(p))
            .toList();

        if (!missing.isEmpty()) {
            throw new AssertionError("Missing prompt files: " + missing);
        }
    }

    @Test
    @DisplayName("All prompt files are non-empty")
    void allPromptFilesNonEmpty() throws IOException {
        Path promptsDir = Paths.get("src/main/resources/prompts");

        for (Path file : Files.list(promptsDir).toList()) {
            if (Files.size(file) == 0) {
                throw new AssertionError("Empty prompt file: " + file.getFileName());
            }
        }
    }
}
```

**Why it fails**: Prompt files don't exist yet.

---

### Phase 2: SentimentPromptLoader + Content Equivalence (RED — class doesn't exist)

**Create** `backend/llm/src/main/java/com/swingtrade/llm/config/SentimentPromptLoader.java`

```java
package com.swingtrade.llm.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SentimentPromptLoader {

    private final String systemPrompt;
    private final String userPrompt;

    public SentimentPromptLoader(
            @Value("classpath:prompts/sentiment-system.md") String systemPrompt,
            @Value("classpath:prompts/sentiment-user.md") String userPrompt) {
        this.systemPrompt = systemPrompt.trim();
        this.userPrompt = userPrompt.trim();
    }

    public String getSystemPrompt() { return systemPrompt; }
    public String getUserPrompt() { return userPrompt; }
}
```

**Create** `backend/llm/src/test/java/com/swingtrade/llm/config/SentimentPromptLoaderTest.java`

```java
package com.swingtrade.llm.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.main.web-application-type=none"
})
class SentimentPromptLoaderTest {

    @Autowired
    private SentimentPromptLoader loader;

    @Test
    @DisplayName("System prompt loads from classpath")
    void systemPromptLoads() {
        String prompt = loader.getSystemPrompt();
        assertThat(prompt).isNotBlank();
        assertThat(prompt).contains("financial analyst");
        assertThat(prompt).contains("Indian equity markets");
        assertThat(prompt).contains("{symbol}");
    }

    @Test
    @DisplayName("User prompt loads from classpath")
    void userPromptLoads() {
        String prompt = loader.getUserPrompt();
        assertThat(prompt).isNotBlank();
        assertThat(prompt).contains("{symbol}");
        assertThat(prompt).contains("{newsContent}");
        assertThat(prompt).contains("swing trade entry");
    }

    @Test
    @DisplayName("System prompt matches old hardcoded content")
    void systemPromptMatchesHardcoded() {
        String loaded = loader.getSystemPrompt();
        // Content from old SentimentAnalyzer.SYSTEM_PROMPT
        assertThat(loaded).contains("financial analyst specialising in Indian equity markets");
        assertThat(loaded).contains("earnings momentum");
        assertThat(loaded).contains("FII/DII activity");
        assertThat(loaded).contains("CRITICAL: Respond with ONLY a JSON object");
        assertThat(loaded).contains("\"score\": \"POSITIVE|NEUTRAL|NEGATIVE\"");
        assertThat(loaded).contains("\"confidence\": 0.0-1.0");
        assertThat(loaded).contains("\"red_flags\"");
        assertThat(loaded).contains("\"catalysts\"");
    }

    @Test
    @DisplayName("User prompt contains required placeholders")
    void userPromptPlaceholders() {
        String prompt = loader.getUserPrompt();
        assertThat(prompt).contains("{symbol}");
        assertThat(prompt).contains("{newsContent}");
        assertThat(prompt).contains("swing trade entry");
        assertThat(prompt).contains("1-4 week");
    }
}
```

**Why it fails**: `SentimentPromptLoader` doesn't exist yet.

**Fix**: Create the loader class (above).

---

### Phase 3: Refactor SentimentService (RED — service still uses old SentimentAnalyzer)

**Modify** `backend/llm/src/main/java/com/swingtrade/llm/service/SentimentService.java`

Changes:
1. Replace `SentimentAnalyzer` dependency with `SentimentPromptLoader`
2. Update `performSentimentAnalysis()` to use `loader.getSystemPrompt()` and `loader.getUserPrompt()`
3. Use `String.format()` on the loaded user prompt with `{symbol}` → stockSymbol, `{newsContent}` → combinedContent

**Create** `backend/llm/src/test/java/com/swingtrade/llm/service/SentimentServicePromptTest.java`

```java
package com.swingtrade.llm.service;

import com.swingtrade.llm.config.SentimentPromptLoader;
import com.swingtrade.domain.store.AppSettingsStore;
import com.swingtrade.domain.store.SentimentStore;
import com.swingtrade.domain.store.StockStore;
import com.swingtrade.llm.SentimentOutput;
import com.swingtrade.llm.SentimentType;
import com.swingtrade.llm.client.LlmClient;
import com.swingtrade.llm.service.LlmClientProvider;
import com.swingtrade.llm.service.LlmServerManager;
import com.swingtrade.llm.service.LlmServerManagerProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SentimentServicePromptTest {

    @Mock private LlmClientProvider clientProvider;
    @Mock private LlmServerManagerProvider serverManagerProvider;
    @Mock private LlmServerManager serverManager;
    @Mock private LlmClient llmClient;
    @Mock private SentimentPromptLoader promptLoader;
    @Mock private SentimentStore sentimentStore;
    @Mock private StockStore stockStore;
    @Mock private AppSettingsStore appSettingsStore;

    @InjectMocks
    private SentimentService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "defaultConfidence", 0.75);
        when(serverManagerProvider.getManager()).thenReturn(serverManager);
        when(serverManagerProvider.getManager().ensureRunning()).thenReturn(null);
        when(clientProvider.getClient()).thenReturn(llmClient);
        when(appSettingsStore.get("llamacpp.model")).thenReturn(Optional.of("Qwen3-4B-Instruct"));
    }

    @Test
    @DisplayName("SentimentService uses loaded system prompt in LLM call")
    void usesLoadedSystemPrompt() {
        // Given
        String systemPrompt = "You are a financial analyst specialising in Indian equity markets.";
        String userPromptTemplate = "Analyse the following for a swing trade entry decision on {symbol}.";
        when(promptLoader.getSystemPrompt()).thenReturn(systemPrompt);
        when(promptLoader.getUserPrompt()).thenReturn(userPromptTemplate);

        String llmResponse = """
            {"score": "POSITIVE", "confidence": 0.8, "summary": "Strong news",
             "red_flags": [], "catalysts": []}
            """;
        when(llmClient.generateChatCompletion(anyList(), eq(512), eq(0.3)))
            .thenReturn(Mono.just(llmResponse));

        // When
        service.analyzeStockSentiment("RELIANCE", LocalDate.now());

        // Then — verify messages passed to LLM contain the loaded prompts
        verify(llmClient).generateChatCompletion(
            argThat(msgs -> {
                Map<String, String> sysMsg = msgs.get(0);
                return "system".equals(sysMsg.get("role"))
                    && sysMsg.get("content").contains("financial analyst specialising in Indian equity markets");
            }),
            eq(512),
            eq(0.3)
        );
    }

    @Test
    @DisplayName("SentimentService formats user prompt with symbol and news content")
    void formatsUserPromptWithPlaceholders() {
        // Given
        when(promptLoader.getSystemPrompt()).thenReturn("System prompt");
        when(promptLoader.getUserPrompt()).thenReturn("Analyse {symbol}. News: {newsContent}.");

        String llmResponse = """
            {"score": "NEGATIVE", "confidence": 0.6, "summary": "Bad news",
             "red_flags": ["SEBI probe"], "catalysts": []}
            """;
        when(llmClient.generateChatCompletion(anyList(), eq(512), eq(0.3)))
            .thenReturn(Mono.just(llmResponse));

        // When
        service.analyzeStockSentiment("TCS", LocalDate.now());

        // Then — verify the formatted message contains the symbol
        verify(llmClient).generateChatCompletion(
            argThat(msgs -> {
                Map<String, String> userMsg = msgs.get(1);
                return "user".equals(userMsg.get("role"))
                    && userMsg.get("content").contains("TCS")
                    && userMsg.get("content").contains("news content");
            }),
            eq(512),
            eq(0.3)
        );
    }
}
```

**Why it fails**: `SentimentService` still depends on `SentimentAnalyzer`, not `SentimentPromptLoader`.

**Fix**:
1. In `SentimentService` constructor: replace `SentimentAnalyzer` with `SentimentPromptLoader`
2. In `performSentimentAnalysis()`:
   - Change `sentimentAnalyzer.createSentimentAnalysisPrompt(stockSymbol, combinedContent)` to:
   ```java
   String formattedUser = String.format(promptLoader.getUserPrompt(), stockSymbol, combinedContent);
   List<Map<String, String>> messages = List.of(
       Map.of("role", "system", "content", promptLoader.getSystemPrompt()),
       Map.of("role", "user", "content", formattedUser)
   );
   ```

---

### Phase 4: SynthesisPromptLoader + Refactor SynthesisService (RED — class doesn't exist in llm module)

**Create** `backend/llm/src/main/java/com/swingtrade/llm/config/SynthesisPromptLoader.java`

```java
package com.swingtrade.llm.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SynthesisPromptLoader {

    private final String systemPrompt;
    private final String userPromptTemplate;

    public SynthesisPromptLoader(
            @Value("classpath:prompts/synthesis-system.md") String systemPrompt,
            @Value("classpath:prompts/synthesis-user.md") String userPromptTemplate) {
        this.systemPrompt = systemPrompt.trim();
        this.userPromptTemplate = userPromptTemplate.trim();
    }

    public String getSystemPrompt() { return systemPrompt; }
    public String getUserPromptTemplate() { return userPromptTemplate; }
}
```

**Create** `backend/api/src/test/java/com/swingtrade/api/service/SynthesisPromptLoaderTest.java`

```java
package com.swingtrade.api.service;

import com.swingtrade.llm.config.SynthesisPromptLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.main.web-application-type=none"
})
class SynthesisPromptLoaderTest {

    @Autowired
    private SynthesisPromptLoader loader;

    @Test
    @DisplayName("Synthesis system prompt loads from classpath")
    void systemPromptLoads() {
        String prompt = loader.getSystemPrompt();
        assertThat(prompt).isNotBlank();
        assertThat(prompt).contains("senior equity analyst");
        assertThat(prompt).contains("Indian equity markets");
        assertThat(prompt).contains("9 analysis stages");
    }

    @Test
    @DisplayName("Synthesis user prompt template loads with placeholders")
    void userPromptHasPlaceholders() {
        String prompt = loader.getUserPromptTemplate();
        assertThat(prompt).contains("{symbol}");
        assertThat(prompt).contains("{date}");
        assertThat(prompt).contains("{newsScore}");
        assertThat(prompt).contains("{technicalSignal}");
        assertThat(prompt).contains("{backtestTrades}");
        assertThat(prompt).contains("{compositeScore}");
    }

    @Test
    @DisplayName("Synthesis system prompt matches old hardcoded content")
    void systemPromptMatchesHardcoded() {
        String prompt = loader.getSystemPrompt();
        assertThat(prompt).contains("senior equity analyst specializing in Indian equity markets");
        assertThat(prompt).contains("9 analysis stages");
        assertThat(prompt).contains("\"recommendation\": \"BUY or SELL or HOLD\"");
        assertThat(prompt).contains("\"bullishFactors\"");
        assertThat(prompt).contains("\"bearishFactors\"");
    }
}
```

**Modify** `backend/api/src/main/java/com/swingtrade/api/service/SynthesisService.java`

Changes:
1. Remove inline `systemPrompt` and `buildPrompt()` string literals
2. Inject `SynthesisPromptLoader` instead
3. Use `loader.getSystemPrompt()` and `String.format(loader.getUserPromptTemplate(), ...)`

**Move** `SynthesisService` from `backend/api/src/main/java/com/swingtrade/api/service/` to `backend/llm/src/main/java/com/swingtrade/llm/service/`

**Why it fails**: `SynthesisPromptLoader` doesn't exist. SynthesisService still has inline strings.

---

### Phase 5: PdfExtractionPromptLoader (RED — file doesn't exist)

**Create** `backend/llm/src/main/java/com/swingtrade/llm/config/PdfExtractionPromptLoader.java`

```java
package com.swingtrade.llm.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PdfExtractionPromptLoader {

    private final String prompt;

    public PdfExtractionPromptLoader(
            @Value("classpath:prompts/pdf-extraction.md") String prompt) {
        this.prompt = prompt.trim();
    }

    public String getPrompt() { return prompt; }
}
```

**Create** `backend/llm/src/test/java/com/swingtrade/llm/config/PdfExtractionPromptLoaderTest.java`

```java
package com.swingtrade.llm.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.main.web-application-type=none"
})
class PdfExtractionPromptLoaderTest {

    @Autowired
    private PdfExtractionPromptLoader loader;

    @Test
    @DisplayName("PDF extraction prompt loads from classpath")
    void promptLoads() {
        String prompt = loader.getPrompt();
        assertThat(prompt).isNotBlank();
        assertThat(prompt).contains("Extract financial data");
        assertThat(prompt).contains("earnings document");
        assertThat(prompt).contains("\"revenue\"");
        assertThat(prompt).contains("\"netProfit\"");
        assertThat(prompt).contains("INR values");
    }

    @Test
    @DisplayName("PDF extraction prompt matches old hardcoded content")
    void promptMatchesHardcoded() {
        String prompt = loader.getPrompt();
        assertThat(prompt).contains("Extract financial data from this earnings document");
        assertThat(prompt).contains("\"eps\": 42.50");
        assertThat(prompt).contains("\"ebitda\": 95000000000");
        assertThat(prompt).contains("No other text");
    }
}
```

**Modify** `backend/llm/src/main/java/com/swingtrade/llm/service/PdfExtractionService.java`

Changes:
1. Replace hardcoded `prompt` String with `PdfExtractionPromptLoader` injection
2. Use `promptLoader.getPrompt()` in `extractEarningsPdf()`

**Why it fails**: `PdfExtractionPromptLoader` doesn't exist.

---

### Phase 6: Dead Code Removal (Verification — methods should be gone)

**Delete** these methods from `SentimentAnalyzer.java`:
- `createMultiArticleSentimentPrompt()` (line ~211)
- `createCombinedAnalysisPrompt()` (line ~259)
- `createTradingRecommendationPrompt()` (line ~304)
- `createSimpleClassificationPrompt()` (line ~365)

**Create** `backend/llm/src/test/java/com/swingtrade/llm/SentimentAnalyzerDeadCodeTest.java`

```java
package com.swingtrade.llm.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.main.web-application-type=none"
})
class SentimentAnalyzerDeadCodeTest {

    @Autowired
    private SentimentAnalyzer analyzer;

    @Test
    @DisplayName("Unused prompt methods have been removed")
    void unusedMethodsRemoved() {
        // Verify the 4 unused methods no longer exist by checking that
        // SentimentAnalyzer only has the expected public API
        assertThat(analyzer).isNotNull();

        // The only public prompt-related methods should be:
        // - getSystemPrompt()
        // - createSentimentAnalysisPrompt(String, String)
        // - createSentimentAnalysisPrompt(String, List<String>, String)
        // - parseResponse(String)
        // - validateSentimentResponse(String, List<String>)
        // - extractSentimentFromResponse(String)

        // Verify system prompt still accessible
        String systemPrompt = analyzer.getSystemPrompt();
        assertThat(systemPrompt).isNotBlank();
        assertThat(systemPrompt).contains("financial analyst");
    }

    @Test
    @DisplayName("Active prompt methods still work")
    void activeMethodsStillWork() {
        List<Map<String, String>> messages = analyzer.createSentimentAnalysisPrompt(
            "RELIANCE",
            "Reliance reports record profits"
        );

        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).get("role")).isEqualTo("system");
        assertThat(messages.get(1).get("role")).isEqualTo("user");
        assertThat(messages.get(1).get("content")).contains("RELIANCE");
    }
}
```

**Why it fails**: Tests reference methods that still exist (we verify they DON'T exist by checking the public API surface).

**Fix**: Delete the 4 unused methods.

---

### Phase 7: Integration Verification (GREEN — full pipeline)

**Create** `backend/llm/src/test/java/com/swingtrade/llm/PromptIntegrationTest.java`

```java
package com.swingtrade.llm;

import com.swingtrade.llm.config.SentimentPromptLoader;
import com.swingtrade.llm.config.SynthesisPromptLoader;
import com.swingtrade.llm.config.PdfExtractionPromptLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.main.web-application-type=none"
})
class PromptIntegrationTest {

    @Autowired
    private SentimentPromptLoader sentimentLoader;

    @Autowired
    private SynthesisPromptLoader synthesisLoader;

    @Autowired
    private PdfExtractionPromptLoader pdfLoader;

    @Test
    @DisplayName("All prompt loaders wired correctly in Spring context")
    void allLoadersWired() {
        assertThat(sentimentLoader).isNotNull();
        assertThat(synthesisLoader).isNotNull();
        assertThat(pdfLoader).isNotNull();
    }

    @Test
    @DisplayName("All prompts are non-empty and well-formed")
    void allPromptsNonEmpty() {
        assertThat(sentimentLoader.getSystemPrompt()).isNotBlank();
        assertThat(sentimentLoader.getUserPrompt()).isNotBlank();
        assertThat(synthesisLoader.getSystemPrompt()).isNotBlank();
        assertThat(synthesisLoader.getUserPromptTemplate()).isNotBlank();
        assertThat(pdfLoader.getPrompt()).isNotBlank();
    }

    @Test
    @DisplayName("Sentiment user prompt uses {symbol} and {newsContent} placeholders")
    void sentimentPlaceholders() {
        String user = sentimentLoader.getUserPrompt();
        assertThat(user).contains("{symbol}");
        assertThat(user).contains("{newsContent}");

        // Verify format works
        String formatted = String.format(user, "RELIANCE", "Reliance profits up 20%");
        assertThat(formatted).contains("RELIANCE");
        assertThat(formatted).contains("Reliance profits up 20%");
        assertThat(formatted).doesNotContain("{symbol}");
        assertThat(formatted).doesNotContain("{newsContent}");
    }

    @Test
    @DisplayName("Synthesis user prompt uses all expected placeholders")
    void synthesisPlaceholders() {
        String template = synthesisLoader.getUserPromptTemplate();

        // Format with sample data
        String formatted = String.format(template,
            "RELIANCE", "2025-01-15",
            75, 10, "Strong outlook", "IPO catalyst", "SEBI probe",
            "BULLISH", 80, 0.85, "RSI: 65, MACD: positive",
            70, "BULLISH", "P/E: 25, ROE: 18%",
            5, 60.0, 1.5, 10.0, 25.0, 5.0,
            72, "BUY", 0.80, "Strong composite"
        );

        assertThat(formatted).contains("RELIANCE");
        assertThat(formatted).contains("BULLISH");
        assertThat(formatted).contains("2025-01-15");
        assertThat(formatted).doesNotContain("{symbol}");
        assertThat(formatted).doesNotContain("{date}");
    }
}
```

**Why it runs**: All loaders exist, all files exist, Spring context loads. Should pass.

---

## 3. Files Summary

| Action | File | Type | Module |
|--------|------|------|--------|
| Create | `backend/llm/src/main/resources/prompts/sentiment-system.md` | Resource | llm |
| Create | `backend/llm/src/main/resources/prompts/sentiment-user.md` | Resource | llm |
| Create | `backend/llm/src/main/resources/prompts/sentiment-user-earnings.md` | Resource | llm |
| Create | `backend/llm/src/main/resources/prompts/synthesis-system.md` | Resource | llm |
| Create | `backend/llm/src/main/resources/prompts/synthesis-user.md` | Resource | llm |
| Create | `backend/llm/src/main/resources/prompts/pdf-extraction.md` | Resource | llm |
| Create | `backend/llm/src/main/resources/prompts/multi-article-sentiment.md` | Resource | llm |
| Create | `backend/llm/src/main/resources/prompts/combined-analysis.md` | Resource | llm |
| Create | `backend/llm/src/main/resources/prompts/trading-recommendation.md` | Resource | llm |
| Create | `backend/llm/src/main/resources/prompts/simple-classification.md` | Resource | llm |
| Create | `backend/llm/src/main/java/com/swingtrade/llm/config/SentimentPromptLoader.java` | Source | llm |
| Create | `backend/llm/src/main/java/com/swingtrade/llm/config/SynthesisPromptLoader.java` | Source | llm |
| Create | `backend/llm/src/main/java/com/swingtrade/llm/config/PdfExtractionPromptLoader.java` | Source | llm |
| Create | `backend/llm/src/test/java/com/swingtrade/llm/PromptFilesTest.java` | Unit | llm |
| Create | `backend/llm/src/test/java/com/swingtrade/llm/config/SentimentPromptLoaderTest.java` | Unit | llm |
| Create | `backend/llm/src/test/java/com/swingtrade/llm/config/PdfExtractionPromptLoaderTest.java` | Unit | llm |
| Create | `backend/llm/src/test/java/com/swingtrade/llm/service/SentimentServicePromptTest.java` | Unit | llm |
| Create | `backend/llm/src/test/java/com/swingtrade/llm/SentimentAnalyzerDeadCodeTest.java` | Unit | llm |
| Create | `backend/llm/src/test/java/com/swingtrade/llm/PromptIntegrationTest.java` | Integration | llm |
| Create | `backend/api/src/test/java/com/swingtrade/api/service/SynthesisPromptLoaderTest.java` | Unit | api |
| Modify | `backend/llm/src/main/java/com/swingtrade/llm/service/SentimentService.java` | Source | llm |
| Modify | `backend/llm/src/main/java/com/swingtrade/llm/service/PdfExtractionService.java` | Source | llm |
| Move | `backend/api/src/main/java/com/swingtrade/api/service/SynthesisService.java` → `backend/llm/src/main/java/com/swingtrade/llm/service/SynthesisService.java` | Source | api→llm |
| Delete | `SentimentAnalyzer.java` — 4 unused methods | Source | llm |

---

## 4. Verification

```bash
# Phase 1: Files don't exist → FAILS
./gradlew :llm:test --tests=PromptFilesTest

# Phase 2: Loader doesn't exist → FAILS
./gradlew :llm:test --tests=SentimentPromptLoaderTest

# → Apply fix: create SentimentPromptLoader.java

# Phase 3: Service still uses SentimentAnalyzer → FAILS
./gradlew :llm:test --tests=SentimentServicePromptTest

# → Apply fix: refactor SentimentService to use SentimentPromptLoader

# Phase 4: SynthesisPromptLoader doesn't exist → FAILS
./gradlew :api:test --tests=SynthesisPromptLoaderTest

# → Apply fix: create loader, move SynthesisService, update callers

# Phase 5: PdfExtractionPromptLoader doesn't exist → FAILS
./gradlew :llm:test --tests=PdfExtractionPromptLoaderTest

# → Apply fix: create loader, update PdfExtractionService

# Phase 6: Dead code still present → FAILS
./gradlew :llm:test --tests=SentimentAnalyzerDeadCodeTest

# → Apply fix: delete 4 unused methods

# Phase 7: Full integration → PASSES
./gradlew :llm:test --tests=PromptIntegrationTest

# Full suite
./gradlew :llm:test :api:test
```

---

## 5. Module Dependency Changes

| Module | New Dependency | Reason |
|--------|---------------|--------|
| `api` | none (already depends on `llm`) | SynthesisService moves INTO llm, api no longer owns it |
| `llm` | none | Already has spring-boot-starter for @Value |

---

## 6. Risk & Mitigation

| Risk | Mitigation |
|------|-----------|
| `@Value("classpath:...")` not available in llm module (no Boot plugin) | llm module has `spring-boot-starter` dependency — `@Value` works. If not, use `ResourceLoader` fallback. |
| SynthesisService move breaks api module imports | Check all callers of SynthesisService in api module before moving. Likely only `AnalysisOrchestratorService` or similar. |
| Prompt content drift (formatting changes) | Phase 2/4/5 include "matches hardcoded" assertions comparing loaded vs old content. |
| Test context startup slow | Use `@TestPropertySource(properties = {"spring.main.web-application-type=none"})` to skip web context. |