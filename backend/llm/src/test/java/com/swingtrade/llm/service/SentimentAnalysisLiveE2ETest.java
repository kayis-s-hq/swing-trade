package com.swingtrade.llm.service;

import com.swingtrade.domain.NewsArticle;
import com.swingtrade.data.entity.SentimentResultEntity;
import com.swingtrade.data.entity.StockEntity;
import com.swingtrade.data.repository.SentimentResultRepository;
import com.swingtrade.data.repository.StockRepository;
import com.swingtrade.domain.SentimentResult;
import com.swingtrade.domain.Stock;
import com.swingtrade.llm.config.TestLlmConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Live E2E tests for SentimentService that make real API calls to local llama.cpp.
 *
 * These tests verify the complete pipeline:
 * 1. News ingestion from RSS feeds (mocked for reliability)
 * 2. Sentiment analysis via real llama.cpp API call
 * 3. Result caching and persistence
 *
 * Requirements:
 * - LLM_BASE_URL environment variable set to llama.cpp endpoint
 *
 * Run with: ./gradlew :llm:test --tests SentimentAnalysisLiveE2ETest
 */
@SpringBootTest(classes = TestLlmConfig.class)
@ActiveProfiles("test")
@DisplayName("Sentiment Analysis Live E2E Tests")
@EnabledIfEnvironmentVariable(named = "LLM_BASE_URL", matches = ".*")
class SentimentAnalysisLiveE2ETest {

    @MockitoBean
    private NewsIngestionService newsIngestionService;

    @Autowired
    private SentimentService sentimentAnalysisService;

    @Autowired
    private SentimentResultRepository sentimentResultRepository;

    @Autowired
    private StockRepository stockRepository;

    @BeforeEach
    void setUp() {
        // Clear database before each test
        sentimentResultRepository.deleteAll();
        stockRepository.deleteAll();

        // Mock news articles for reliable testing
        mockNewsArticles();
    }

    private void mockNewsArticles() {
        // Create sample news articles for each stock - must contain the stock symbol
        // for the containsStockSymbol() filter to pass
        NewsArticle relianceArticle1 = new NewsArticle(
                "RELIANCE",
                "Reliance Industries reports strong quarterly earnings",
                "https://example.com/news1",
                "Reliance Industries announces better than expected quarterly results.",
                java.time.ZonedDateTime.now(),
                "https://feeds.economictimes.indiatimes.com/stocks",
                "Reliance Industries reports strong quarterly earnings with revenue growth."
        );

        NewsArticle tcsArticle1 = new NewsArticle(
                "TCS",
                "TCS reports strong digital services growth",
                "https://example.com/news2",
                "Tata Consultancy Services shows strong performance in digital services.",
                java.time.ZonedDateTime.now(),
                "https://feeds.economictimes.indiatimes.com/stocks",
                "TCS reports strong performance in digital services segment."
        );

        NewsArticle infyArticle1 = new NewsArticle(
                "INFY",
                "Infosys reports solid quarterly performance",
                "https://example.com/news3",
                "Infosys announces strong quarterly earnings beat.",
                java.time.ZonedDateTime.now(),
                "https://feeds.economictimes.indiatimes.com/stocks",
                "Infosys reports solid financial performance this quarter."
        );

        // Mock the news fetching for each stock symbol
        when(newsIngestionService.fetchStockNews("RELIANCE")).thenReturn(List.of(relianceArticle1));
        when(newsIngestionService.fetchStockNews("TCS")).thenReturn(List.of(tcsArticle1));
        when(newsIngestionService.fetchStockNews("INFY")).thenReturn(List.of(infyArticle1));

        // Clean news text returns the title and description
        when(newsIngestionService.cleanNewsText(any(NewsArticle.class))).thenAnswer(invocation -> {
            NewsArticle article = invocation.getArgument(0);
            return article.title() + " " + article.description();
        });
    }

    @Test
    @DisplayName("testAnalyzeStockSentiment_MakesRealLlamaCppCall")
    void testAnalyzeStockSentiment_MakesRealLlamaCppCall() {
        // Given: Stock and news available
        StockEntity stock = createAndSaveStock("RELIANCE", "Reliance Industries", Stock.Sector.OTHERS);

        // When: Analyze sentiment (makes real llama.cpp API call)
        SentimentResult result = sentimentAnalysisService.analyzeStockSentiment("RELIANCE", LocalDate.now());

        // Then: Verify sentiment analysis completed
        assertThat(result).isNotNull();
        assertThat(result.symbol()).isEqualTo("RELIANCE");
        assertThat(result.date()).isEqualTo(LocalDate.now());
        assertThat(result.confidence()).isBetween(0.0, 1.0);

        // Verify sentiment is one of the valid types
        assertThat(result.score()).isIn(
                SentimentResult.SentimentScore.POSITIVE,
                SentimentResult.SentimentScore.NEUTRAL,
                SentimentResult.SentimentScore.NEGATIVE
        );

        // Verify reasoning was generated
        assertThat(result.summary()).isNotBlank();
    }

    @Test
    @DisplayName("testAnalyzeStockSentiment_PersistsToDatabase")
    void testAnalyzeStockSentiment_PersistsToDatabase() {
        // Given: Stock with no existing sentiment
        StockEntity stock = createAndSaveStock("TCS", "Tata Consultancy Services", Stock.Sector.IT);

        // When: Analyze sentiment
        SentimentResult result = sentimentAnalysisService.analyzeStockSentiment("TCS", LocalDate.now());

        // Then: Verify result exists in database
        assertThat(result).isNotNull();

        // Verify database persistence
        List<SentimentResultEntity> entities = sentimentResultRepository.findAll();
        assertThat(entities).isNotEmpty();

        // Verify saved entity matches our result
        boolean found = entities.stream()
                .anyMatch(e -> e.getSymbol().equals("TCS") && e.getDate().equals(LocalDate.now()));
        assertThat(found).isTrue();
    }

    @Test
    @DisplayName("testAnalyzeMultipleStocks_MakesConcurrentRealCalls")
    void testAnalyzeMultipleStocks_MakesConcurrentRealCalls() {
        // Given: Multiple stocks to analyze
        createAndSaveStock("RELIANCE", "Reliance Industries", Stock.Sector.OTHERS);
        createAndSaveStock("TCS", "Tata Consultancy Services", Stock.Sector.IT);

        // When: Batch analyze (makes real llama.cpp API calls)
        List<String> stocks = List.of("RELIANCE", "TCS");
        var results = sentimentAnalysisService.analyzeMultipleStocks(stocks, LocalDate.now());

        // Then: Verify all stocks were analyzed
        assertThat(results).hasSize(2);

        // Verify each result
        for (String symbol : stocks) {
            SentimentResult result = results.get(symbol);
            assertThat(result).isNotNull();
            assertThat(result.symbol()).isEqualTo(symbol);
            assertThat(result.date()).isEqualTo(LocalDate.now());
        }
    }

    @Test
    @DisplayName("testWeeklyDigest_MakesRealAnalysisCalls")
    void testWeeklyDigest_MakesRealAnalysisCalls() {
        // Given: Multiple sentiment results for a week
        StockEntity stock1 = createAndSaveStock("RELIANCE", "Reliance Industries", Stock.Sector.OTHERS);
        StockEntity stock2 = createAndSaveStock("TCS", "Tata Consultancy Services", Stock.Sector.IT);

        LocalDate startDate = LocalDate.now().minusDays(7);
        LocalDate endDate = LocalDate.now();

        // Analyze sentiment for both stocks
        sentimentAnalysisService.analyzeStockSentiment("RELIANCE", startDate.plusDays(1));
        sentimentAnalysisService.analyzeStockSentiment("TCS", startDate.plusDays(2));

        // When: Generate weekly sector digest
        String digest = sentimentAnalysisService.generateSectorDigest(startDate, endDate);

        // Then: Verify digest was generated with real analysis
        assertThat(digest).isNotBlank();
        assertThat(digest).contains("Weekly Sector Sentiment Digest");
        assertThat(digest).contains("Week of:");
        assertThat(digest).contains("Summary Statistics");
    }

    @Test
    @DisplayName("testSentimentCache_MakesRealCallOnlyOnce")
    void testSentimentCache_MakesRealCallOnlyOnce() {
        // Given: First analysis (cache miss)
        StockEntity stock = createAndSaveStock("INFY", "Infosys", Stock.Sector.IT);

        // When: First analysis
        SentimentResult firstResult = sentimentAnalysisService.analyzeStockSentiment("INFY", LocalDate.now());

        // Then: First result should be from analysis
        assertThat(firstResult).isNotNull();

        // When: Second analysis (cache hit)
        SentimentResult secondResult = sentimentAnalysisService.analyzeStockSentiment("INFY", LocalDate.now());

        // Then: Should return cached result (same ID)
        assertThat(secondResult).isNotNull();
        assertThat(secondResult.id()).isEqualTo(firstResult.id());
    }

    // ===== Helper Methods =====

    /**
     * Creates and saves a stock entity for testing.
     */
    private StockEntity createAndSaveStock(String symbol, String name, Stock.Sector sector) {
        StockEntity entity = new StockEntity();
        entity.setSymbol(symbol);
        entity.setName(name);
        entity.setSector(sector.name());
        entity.setExchange("NSE");
        return stockRepository.save(entity);
    }
}
