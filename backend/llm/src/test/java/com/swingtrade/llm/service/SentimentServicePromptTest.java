package com.swingtrade.llm.service;

import com.swingtrade.domain.NewsArticle;
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

        when(newsIngestionService.fetchStockNews("RELIANCE")).thenReturn(List.of(
                new NewsArticle("RELIANCE", "Test headline", "Test URL", null, null, "Test source", null)));
        when(newsIngestionService.cleanNewsText(any(NewsArticle.class))).thenReturn("Test news content");

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

        when(newsIngestionService.fetchStockNews("TCS")).thenReturn(List.of(
                new NewsArticle("TCS", "Test headline", "Test URL", null, null, "Test source", null)));
        when(newsIngestionService.cleanNewsText(any(NewsArticle.class))).thenReturn("news content");

        // When
        service.analyzeStockSentiment("TCS", LocalDate.now());

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
}