package com.swingtrade.llm.service;

import com.swingtrade.domain.NewsArticle;
import com.swingtrade.domain.PersistedNewsArticle;
import com.swingtrade.domain.store.AppSettingsStore;
import com.swingtrade.domain.store.SentimentStore;
import com.swingtrade.domain.store.StockStore;
import com.swingtrade.llm.client.LlmClient;
import com.swingtrade.llm.config.SentimentPromptLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    @Mock private NewsIngestionService newsIngestionService;

    private SentimentService service;

    @BeforeEach
    void setUp() {
        service = new SentimentService(
                clientProvider, serverManagerProvider, promptLoader, null,
                newsIngestionService, sentimentStore, stockStore, appSettingsStore,
                org.mockito.Mockito.mock(com.swingtrade.core.metrics.LlmMetrics.class),
                org.mockito.Mockito.mock(com.swingtrade.core.metrics.SentimentMetrics.class), 0.75);
        when(serverManagerProvider.getManager()).thenReturn(serverManager);
        doNothing().when(serverManager).ensureRunning();
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

        List<PersistedNewsArticle> articles = List.of(
                new PersistedNewsArticle(1L, new NewsArticle("RELIANCE", "Test headline", "Test URL", null, todayNoon(), "Test source", null), null));
        when(newsIngestionService.fetchPersistedStockNewsForDecisionDate(eq("RELIANCE"), org.mockito.ArgumentMatchers.nullable(LocalDate.class))).thenReturn(articles);
        when(newsIngestionService.cleanNewsText(any(NewsArticle.class))).thenReturn("Test news content");

        // When
        service.analyzeStockSentiment("RELIANCE", LocalDate.now(ZoneId.of("Asia/Kolkata")));

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

        List<PersistedNewsArticle> articles = List.of(
                new PersistedNewsArticle(1L, new NewsArticle("TCS", "Test headline", "Test URL", null, todayNoon(), "Test source", null), null));
        when(newsIngestionService.fetchPersistedStockNewsForDecisionDate(eq("TCS"), org.mockito.ArgumentMatchers.nullable(LocalDate.class))).thenReturn(articles);
        when(newsIngestionService.cleanNewsText(any(NewsArticle.class))).thenReturn("news content");

        // When
        service.analyzeStockSentiment("TCS", LocalDate.now(ZoneId.of("Asia/Kolkata")));

        // Then — verify the formatted message contains the symbol
        verify(llmClient).generateChatCompletion(
            argThat(msgs -> {
                Map<String, String> userMsg = msgs.get(1);
                return "user".equals(userMsg.get("role"))
                    && userMsg.get("content").contains("TCS");
            }),
            eq(512),
            eq(0.3)
        );
    }

    @Test
    @DisplayName("SentimentService uses the bounded Pi response budget")
    void usesBoundedPiResponseBudget() {
        when(clientProvider.getBackend()).thenReturn(LlmBackendSelector.Backend.PI_SSH);
        when(promptLoader.getSystemPrompt()).thenReturn("System prompt");
        when(promptLoader.getUserPrompt()).thenReturn("Analyse {symbol}. News: {newsContent}.");
        when(llmClient.generateChatCompletion(anyList(), eq(128), eq(0.3)))
                .thenReturn(Mono.just("{\"score\":\"NEUTRAL\",\"confidence\":0.5,\"summary\":\"Mixed\",\"red_flags\":[],\"catalysts\":[]}"));
        List<PersistedNewsArticle> articles = List.of(
                new PersistedNewsArticle(1L, new NewsArticle("TCS", "Test headline", "Test URL", null, todayNoon(), "Test source", null), null));
        when(newsIngestionService.fetchPersistedStockNewsForDecisionDate(eq("TCS"), org.mockito.ArgumentMatchers.nullable(LocalDate.class))).thenReturn(articles);
        when(newsIngestionService.cleanNewsText(any(NewsArticle.class))).thenReturn("news content");

        service.analyzeStockSentiment("TCS", LocalDate.now(ZoneId.of("Asia/Kolkata")));

        verify(llmClient).generateChatCompletion(anyList(), eq(128), eq(0.3));
    }

    @Test
    @DisplayName("SentimentService excludes articles published after the decision cutoff")
    void filtersFutureArticlesBeforePrompting() {
        when(promptLoader.getSystemPrompt()).thenReturn("System prompt");
        when(promptLoader.getUserPrompt()).thenReturn("News: {newsContent}");
        when(llmClient.generateChatCompletion(anyList(), eq(512), eq(0.3)))
            .thenReturn(Mono.just("{\"score\":\"NEUTRAL\",\"confidence\":0.5,\"summary\":\"Mixed\"}"));

        LocalDate decisionDate = LocalDate.of(2026, 9, 15);
        NewsArticle beforeCutoff = new NewsArticle("TCS", "Old", "u1", null,
            ZonedDateTime.of(2026, 9, 15, 15, 30, 0, 0, ZoneId.of("Asia/Kolkata")), "source", null);
        NewsArticle afterCutoff = new NewsArticle("TCS", "Future", "u2", null,
            ZonedDateTime.of(2026, 9, 15, 15, 31, 0, 0, ZoneId.of("Asia/Kolkata")), "source", null);
        when(newsIngestionService.fetchPersistedStockNewsForDecisionDate(eq("TCS"), eq(decisionDate)))
            .thenReturn(List.of(
                new PersistedNewsArticle(1L, beforeCutoff, null),
                new PersistedNewsArticle(2L, afterCutoff, null)));
        when(newsIngestionService.cleanNewsText(beforeCutoff)).thenReturn("old news");

        service.analyzeStockSentiment("TCS", decisionDate);

        verify(sentimentStore).saveOrUpdate(argThat(result -> result.articleCount() == 1));
        verify(sentimentStore).saveOrUpdate(argThat(result -> result.articleIds().contains(1L)));
        verify(newsIngestionService).cleanNewsText(beforeCutoff);
        org.mockito.Mockito.verify(newsIngestionService, org.mockito.Mockito.never())
            .cleanNewsText(afterCutoff);
        verify(llmClient).generateChatCompletion(
            argThat(msgs -> msgs.get(1).get("content").contains("old news")
                && !msgs.get(1).get("content").contains("future news")),
            eq(512), eq(0.3));
        org.mockito.Mockito.verify(newsIngestionService, org.mockito.Mockito.never())
            .fetchStockNews("TCS");
    }

    private static ZonedDateTime todayNoon() {
        ZoneId zone = ZoneId.of("Asia/Kolkata");
        return LocalDate.now(zone).atTime(LocalTime.NOON).atZone(zone);
    }
}
