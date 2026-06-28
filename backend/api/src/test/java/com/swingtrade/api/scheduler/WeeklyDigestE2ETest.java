package com.swingtrade.api.scheduler;

import com.swingtrade.api.app.SwingTradeApiApplication;
import com.swingtrade.data.entity.SentimentResultEntity;
import com.swingtrade.data.entity.StockEntity;
import com.swingtrade.data.repository.SentimentResultRepository;
import com.swingtrade.data.repository.StockRepository;
import com.swingtrade.domain.Stock;
import com.swingtrade.llm.service.SentimentAnalysisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E tests for Weekly Sector Digest generation with SentimentAnalysisService,
 * formatting, date range calculation (Sunday-Saturday IST), and error handling.
 *
 * Test coverage:
 * - generateSectorDigest() with populated sentiment results
 * - Date range calculation for last week (Sunday-Saturday IST)
 * - Sector grouping and sentiment aggregation
 * - Empty digest formatting
 * - Error handling for missing data
 *
 * Uses @SpringBootTest with TestContainers PostgreSQL for integration testing.
 */
@SpringBootTest(classes = SwingTradeApiApplication.class)
@ActiveProfiles("test")
@Testcontainers
@DisplayName("Weekly Sector Digest E2E Tests")
@org.junit.jupiter.api.Disabled("E2E test - requires Docker and full infrastructure")
class WeeklyDigestE2ETest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("swingtrade_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureTests(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
    }

    @Autowired
    private SentimentAnalysisService sentimentAnalysisService;

    @Autowired
    private SentimentResultRepository sentimentResultRepository;

    @Autowired
    private StockRepository stockRepository;

    @BeforeEach
    void setUp() {
        // Clear database before each test
        sentimentResultRepository.deleteAll();
        stockRepository.deleteAll();
    }

    // ===== Date Range Calculation Tests =====

    @Test
    @DisplayName("testGenerateSectorDigest_FormatsCompleteDigestWithRealService")
    void testGenerateSectorDigest_FormatsCompleteDigestWithRealService() {
        // Given: Populated sentiment results for a week (March 16-22, 2026)
        LocalDate startDate = LocalDate.of(2026, 3, 16);
        LocalDate endDate = LocalDate.of(2026, 3, 22);

        // Save test stocks in different sectors
        StockEntity reliance = createAndSaveStock("RELIANCE", "Reliance Industries", Stock.Sector.OTHERS);
        StockEntity tcs = createAndSaveStock("TCS", "Tata Consultancy Services", Stock.Sector.IT);
        StockEntity hdfc = createAndSaveStock("HDFCBANK", "HDFC Bank", Stock.Sector.BANK);
        StockEntity infy = createAndSaveStock("INFY", "Infosys", Stock.Sector.IT);
        StockEntity adani = createAndSaveStock("ADANIENT", "Adani Enterprises", Stock.Sector.OTHERS);

        // Save sentiment results for the week
        saveSentimentResult(reliance, startDate.plusDays(1), "POSITIVE", "Strong Q4 results", 0.85);
        saveSentimentResult(reliance, startDate.plusDays(3), "POSITIVE", "New project announced", 0.78);
        saveSentimentResult(tcs, startDate.plusDays(2), "POSITIVE", "Contract win", 0.82);
        saveSentimentResult(hdfc, startDate.plusDays(1), "NEUTRAL", "Stable outlook", 0.65);
        saveSentimentResult(infy, startDate.plusDays(2), "NEGATIVE", "Q4 miss", 0.70);
        saveSentimentResult(adani, startDate.plusDays(4), "NEGATIVE", "Regulatory issues", 0.75);

        // When: Generate sector digest for date range
        String digest = sentimentAnalysisService.generateSectorDigest(startDate, endDate);

        // Then: Verify formatted output contains expected sections
        assertThat(digest).isNotBlank();
        assertThat(digest).contains("Weekly Sector Sentiment Digest");
        assertThat(digest).contains("Date Range:");
        assertThat(digest).contains("Top Positive Sectors:");
        assertThat(digest).contains("Top Negative Sectors:");
        assertThat(digest).contains("Sector Summary:");
        assertThat(digest).contains("IT");
        assertThat(digest).contains("BANK");
    }

    @Test
    @DisplayName("testGenerateSectorDigest_FormatsEmptyDigest")
    void testGenerateSectorDigest_FormatsEmptyDigest() {
        // Given: No sentiment results for the week
        LocalDate startDate = LocalDate.of(2026, 3, 16);
        LocalDate endDate = LocalDate.of(2026, 3, 22);

        // When: Generate sector digest with no data
        String digest = sentimentAnalysisService.generateSectorDigest(startDate, endDate);

        // Then: Verify empty digest format
        assertThat(digest).isNotBlank();
        assertThat(digest).contains("Weekly Sector Sentiment Digest");
        assertThat(digest).contains("No sentiment data available");
    }

    @Test
    @DisplayName("testGenerateSectorDigest_GroupsBySector")
    void testGenerateSectorDigest_GroupsBySector() {
        // Given: Multiple stocks in same sector with different sentiments
        StockEntity stock1 = createAndSaveStock("RELIANCE", "Reliance Industries", Stock.Sector.OTHERS);
        StockEntity stock2 = createAndSaveStock("ADANIENT", "Adani Enterprises", Stock.Sector.OTHERS);
        StockEntity stock3 = createAndSaveStock("LTTS", "LTTS", Stock.Sector.OTHERS);

        LocalDate startDate = LocalDate.of(2026, 3, 16);
        LocalDate endDate = LocalDate.of(2026, 3, 22);

        // Positive: 2 stocks
        saveSentimentResult(stock1, startDate.plusDays(1), "POSITIVE", "Good results", 0.80);
        saveSentimentResult(stock2, startDate.plusDays(2), "POSITIVE", "Strong growth", 0.75);
        // Negative: 1 stock
        saveSentimentResult(stock3, startDate.plusDays(3), "NEGATIVE", "Profit booking", 0.68);

        // When: Generate sector digest
        String digest = sentimentAnalysisService.generateSectorDigest(startDate, endDate);

        // Then: Verify sector grouping
        assertThat(digest).contains("OTHERS");
        // The digest should aggregate by sector
    }

    @Test
    @DisplayName("testGenerateSectorDigest_HandlesSingleSector")
    void testGenerateSectorDigest_HandlesSingleSector() {
        // Given: Only stocks in one sector
        StockEntity stock1 = createAndSaveStock("RELIANCE", "Reliance Industries", Stock.Sector.OTHERS);
        StockEntity stock2 = createAndSaveStock("TCS", "Tata Consultancy Services", Stock.Sector.OTHERS);

        LocalDate startDate = LocalDate.of(2026, 3, 16);
        LocalDate endDate = LocalDate.of(2026, 3, 22);

        saveSentimentResult(stock1, startDate.plusDays(1), "POSITIVE", "Good results", 0.80);
        saveSentimentResult(stock2, startDate.plusDays(2), "NEUTRAL", "Mixed results", 0.60);

        // When: Generate sector digest
        String digest = sentimentAnalysisService.generateSectorDigest(startDate, endDate);

        // Then: Verify digest handles single sector
        assertThat(digest).contains("OTHERS");
        assertThat(digest).contains("1 Positive");
        assertThat(digest).contains("1 Neutral");
    }

    @Test
    @DisplayName("testGenerateSectorDigest_CalculatesCorrectTotals")
    void testGenerateSectorDigest_CalculatesCorrectTotals() {
        // Given: Sentiment results with known distribution
        StockEntity stock1 = createAndSaveStock("RELIANCE", "Reliance Industries", Stock.Sector.OTHERS);
        StockEntity stock2 = createAndSaveStock("TCS", "Tata Consultancy Services", Stock.Sector.IT);
        StockEntity stock3 = createAndSaveStock("HDFCBANK", "HDFC Bank", Stock.Sector.BANK);

        LocalDate startDate = LocalDate.of(2026, 3, 16);
        LocalDate endDate = LocalDate.of(2026, 3, 22);

        saveSentimentResult(stock1, startDate.plusDays(1), "POSITIVE", "Good results", 0.80);
        saveSentimentResult(stock2, startDate.plusDays(2), "POSITIVE", "Strong growth", 0.75);
        saveSentimentResult(stock3, startDate.plusDays(3), "NEGATIVE", "Profit booking", 0.68);

        // When: Generate sector digest
        String digest = sentimentAnalysisService.generateSectorDigest(startDate, endDate);

        // Then: Verify totals are calculated correctly
        assertThat(digest).contains("3 Total Stocks");
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

    /**
     * Saves a sentiment result entity for testing.
     */
    private void saveSentimentResult(StockEntity stock, LocalDate date, String score, String summary, double confidence) {
        SentimentResultEntity entity = new SentimentResultEntity();
        entity.setSymbol(stock.getSymbol());
        entity.setDate(date);
        entity.setSentimentScore(score);
        entity.setSummary(summary);
        entity.setConfidence(confidence);
        sentimentResultRepository.save(entity);
    }
}
