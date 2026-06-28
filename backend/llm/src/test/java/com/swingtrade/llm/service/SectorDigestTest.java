package com.swingtrade.llm.service;

import com.swingtrade.data.entity.SentimentResultEntity;
import com.swingtrade.data.repository.SentimentResultRepository;
import com.swingtrade.data.repository.StockRepository;
import com.swingtrade.domain.SentimentResult;
import com.swingtrade.domain.Stock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Enhanced unit tests for SentimentAnalysisService sector digest functionality.
 * Tests the actual service methods that group sentiment results by sector and identify top sectors.
 *
 * Test coverage:
 * - groupBySectorAndSentiment() correctly groups results by sector and counts sentiment scores
 * - getTopSectors() identifies top positive sectors by count
 * - getTopSectors() identifies top negative sectors by count
 * - generateSectorDigest() formats complete digest with date range, sectors, and statistics
 */
@DisplayName("SectorDigest Service Tests")
@ExtendWith(MockitoExtension.class)
class SectorDigestTest {

    private SentimentAnalysisService sentimentAnalysisService;

    @Mock
    private SentimentResultRepository sentimentResultRepository;

    @Mock
    private StockRepository stockRepository;

    @BeforeEach
    void setUp() {
        // Create real SentimentAnalysisService with mocked repositories
        sentimentAnalysisService = new SentimentAnalysisService(
                null,  // vllmClient not needed for sector digest tests
                null,  // sentimentAnalyzer not needed
                null,  // newsIngestionService not needed
                null,  // sentimentCacheService not needed
                sentimentResultRepository,
                stockRepository,
                100,   // maxCacheSize
                60L,   // cacheExpiryMinutes
                true,  // enableCaching
                0.75   // defaultConfidence
        );
    }

    @Test
    @DisplayName("testGroupBySectorAndSentiment_CorrectlyGroupsBySector")
    void testGroupBySectorAndSentiment_CorrectlyGroupsBySector() {
        // Given: mock sentiment results with known sectors
        List<SentimentResult> results = new ArrayList<>();

        // BANK sector: 5 POSITIVE, 3 NEUTRAL, 2 NEGATIVE
        results.add(createSentimentResult("HDFCBANK", Stock.Sector.BANK, SentimentResult.SentimentScore.POSITIVE));
        results.add(createSentimentResult("HDFCBANK", Stock.Sector.BANK, SentimentResult.SentimentScore.POSITIVE));
        results.add(createSentimentResult("ICICIBANK", Stock.Sector.BANK, SentimentResult.SentimentScore.POSITIVE));
        results.add(createSentimentResult("ICICIBANK", Stock.Sector.BANK, SentimentResult.SentimentScore.POSITIVE));
        results.add(createSentimentResult("AXISBANK", Stock.Sector.BANK, SentimentResult.SentimentScore.POSITIVE));

        results.add(createSentimentResult("HDFCBANK", Stock.Sector.BANK, SentimentResult.SentimentScore.NEUTRAL));
        results.add(createSentimentResult("ICICIBANK", Stock.Sector.BANK, SentimentResult.SentimentScore.NEUTRAL));
        results.add(createSentimentResult("AXISBANK", Stock.Sector.BANK, SentimentResult.SentimentScore.NEUTRAL));

        results.add(createSentimentResult("HDFCBANK", Stock.Sector.BANK, SentimentResult.SentimentScore.NEGATIVE));
        results.add(createSentimentResult("ICICIBANK", Stock.Sector.BANK, SentimentResult.SentimentScore.NEGATIVE));

        // IT sector: 4 POSITIVE, 2 NEUTRAL, 1 NEGATIVE
        results.add(createSentimentResult("TCS", Stock.Sector.IT, SentimentResult.SentimentScore.POSITIVE));
        results.add(createSentimentResult("INFOSYS", Stock.Sector.IT, SentimentResult.SentimentScore.POSITIVE));
        results.add(createSentimentResult("WIPRO", Stock.Sector.IT, SentimentResult.SentimentScore.POSITIVE));
        results.add(createSentimentResult("TECHM", Stock.Sector.IT, SentimentResult.SentimentScore.POSITIVE));

        results.add(createSentimentResult("TCS", Stock.Sector.IT, SentimentResult.SentimentScore.NEUTRAL));
        results.add(createSentimentResult("INFOSYS", Stock.Sector.IT, SentimentResult.SentimentScore.NEUTRAL));

        results.add(createSentimentResult("WIPRO", Stock.Sector.IT, SentimentResult.SentimentScore.NEGATIVE));

        // PHARMA sector: 3 POSITIVE, 1 NEUTRAL, 2 NEGATIVE
        results.add(createSentimentResult("SUNPHARMA", Stock.Sector.PHARMA, SentimentResult.SentimentScore.POSITIVE));
        results.add(createSentimentResult("CIPLA", Stock.Sector.PHARMA, SentimentResult.SentimentScore.POSITIVE));
        results.add(createSentimentResult("LUPIN", Stock.Sector.PHARMA, SentimentResult.SentimentScore.POSITIVE));

        results.add(createSentimentResult("SUNPHARMA", Stock.Sector.PHARMA, SentimentResult.SentimentScore.NEUTRAL));

        results.add(createSentimentResult("CIPLA", Stock.Sector.PHARMA, SentimentResult.SentimentScore.NEGATIVE));
        results.add(createSentimentResult("LUPIN", Stock.Sector.PHARMA, SentimentResult.SentimentScore.NEGATIVE));

        // Mock stock repository to return correct sectors
        mockStockRepository(results);

        // When: calling groupBySectorAndSentiment
        Map<Stock.Sector, Map<SentimentResult.SentimentScore, Long>> grouped =
                sentimentAnalysisService.groupBySectorAndSentiment(results);

        // Then: verify correct grouping and counts
        assertThat(grouped).containsKeys(Stock.Sector.BANK, Stock.Sector.IT, Stock.Sector.PHARMA);

        // BANK sector counts
        assertThat(grouped.get(Stock.Sector.BANK).get(SentimentResult.SentimentScore.POSITIVE)).isEqualTo(5);
        assertThat(grouped.get(Stock.Sector.BANK).get(SentimentResult.SentimentScore.NEUTRAL)).isEqualTo(3);
        assertThat(grouped.get(Stock.Sector.BANK).get(SentimentResult.SentimentScore.NEGATIVE)).isEqualTo(2);

        // IT sector counts
        assertThat(grouped.get(Stock.Sector.IT).get(SentimentResult.SentimentScore.POSITIVE)).isEqualTo(4);
        assertThat(grouped.get(Stock.Sector.IT).get(SentimentResult.SentimentScore.NEUTRAL)).isEqualTo(2);
        assertThat(grouped.get(Stock.Sector.IT).get(SentimentResult.SentimentScore.NEGATIVE)).isEqualTo(1);

        // PHARMA sector counts
        assertThat(grouped.get(Stock.Sector.PHARMA).get(SentimentResult.SentimentScore.POSITIVE)).isEqualTo(3);
        assertThat(grouped.get(Stock.Sector.PHARMA).get(SentimentResult.SentimentScore.NEUTRAL)).isEqualTo(1);
        assertThat(grouped.get(Stock.Sector.PHARMA).get(SentimentResult.SentimentScore.NEGATIVE)).isEqualTo(2);
    }

    @Test
    @DisplayName("testGetTopSectors_IdentifiesTopPositiveAndNegativeSectors")
    void testGetTopSectors_IdentifiesTopPositiveAndNegativeSectors() {
        // Given: sentiment results with varying sector strengths
        List<SentimentResult> results = new ArrayList<>();

        // BANK: 50 POSITIVE, 10 NEUTRAL, 5 NEGATIVE
        for (int i = 0; i < 50; i++) {
            results.add(createSentimentResult("BANK" + i, Stock.Sector.BANK, SentimentResult.SentimentScore.POSITIVE));
        }
        for (int i = 0; i < 10; i++) {
            results.add(createSentimentResult("BANK" + i, Stock.Sector.BANK, SentimentResult.SentimentScore.NEUTRAL));
        }
        for (int i = 0; i < 5; i++) {
            results.add(createSentimentResult("BANK" + i, Stock.Sector.BANK, SentimentResult.SentimentScore.NEGATIVE));
        }

        // IT: 35 POSITIVE, 15 NEUTRAL, 8 NEGATIVE
        for (int i = 0; i < 35; i++) {
            results.add(createSentimentResult("IT" + i, Stock.Sector.IT, SentimentResult.SentimentScore.POSITIVE));
        }
        for (int i = 0; i < 15; i++) {
            results.add(createSentimentResult("IT" + i, Stock.Sector.IT, SentimentResult.SentimentScore.NEUTRAL));
        }
        for (int i = 0; i < 8; i++) {
            results.add(createSentimentResult("IT" + i, Stock.Sector.IT, SentimentResult.SentimentScore.NEGATIVE));
        }

        // PHARMA: 25 POSITIVE, 20 NEUTRAL, 20 NEGATIVE
        for (int i = 0; i < 25; i++) {
            results.add(createSentimentResult("PHARMA" + i, Stock.Sector.PHARMA, SentimentResult.SentimentScore.POSITIVE));
        }
        for (int i = 0; i < 20; i++) {
            results.add(createSentimentResult("PHARMA" + i, Stock.Sector.PHARMA, SentimentResult.SentimentScore.NEUTRAL));
        }
        for (int i = 0; i < 20; i++) {
            results.add(createSentimentResult("PHARMA" + i, Stock.Sector.PHARMA, SentimentResult.SentimentScore.NEGATIVE));
        }

        // METALS: 10 POSITIVE, 15 NEUTRAL, 40 NEGATIVE
        for (int i = 0; i < 10; i++) {
            results.add(createSentimentResult("METALS" + i, Stock.Sector.METALS, SentimentResult.SentimentScore.POSITIVE));
        }
        for (int i = 0; i < 15; i++) {
            results.add(createSentimentResult("METALS" + i, Stock.Sector.METALS, SentimentResult.SentimentScore.NEUTRAL));
        }
        for (int i = 0; i < 40; i++) {
            results.add(createSentimentResult("METALS" + i, Stock.Sector.METALS, SentimentResult.SentimentScore.NEGATIVE));
        }

        mockStockRepository(results);

        // When: grouping results
        Map<Stock.Sector, Map<SentimentResult.SentimentScore, Long>> grouped =
                sentimentAnalysisService.groupBySectorAndSentiment(results);

        // And: getting top positive sectors
        List<Stock.Sector> topPositive = sentimentAnalysisService.getTopSectors(grouped, 3, true);

        // Then: top positive should be BANK, IT, PHARMA in that order
        assertThat(topPositive).hasSize(3);
        assertThat(topPositive.get(0)).isEqualTo(Stock.Sector.BANK);      // 50 POSITIVE
        assertThat(topPositive.get(1)).isEqualTo(Stock.Sector.IT);        // 35 POSITIVE
        assertThat(topPositive.get(2)).isEqualTo(Stock.Sector.PHARMA);    // 25 POSITIVE

        // When: getting top negative sectors
        List<Stock.Sector> topNegative = sentimentAnalysisService.getTopSectors(grouped, 3, false);

        // Then: top negative should be METALS, PHARMA, IT in that order
        assertThat(topNegative).hasSize(3);
        assertThat(topNegative.get(0)).isEqualTo(Stock.Sector.METALS);    // 40 NEGATIVE
        assertThat(topNegative.get(1)).isEqualTo(Stock.Sector.PHARMA);    // 20 NEGATIVE
        assertThat(topNegative.get(2)).isEqualTo(Stock.Sector.IT);        // 8 NEGATIVE
    }

    @Test
    @DisplayName("testGenerateSectorDigest_FormatsCompleteDigest")
    void testGenerateSectorDigest_FormatsCompleteDigest() {
        // Given: sentiment results for a week
        LocalDate startDate = LocalDate.of(2026, 3, 16);
        LocalDate endDate = LocalDate.of(2026, 3, 22);

        List<SentimentResultEntity> results = new ArrayList<>();

        // Create diverse sector data as entities
        results.add(createSentimentResultEntity("HDFCBANK", Stock.Sector.BANK, SentimentResult.SentimentScore.POSITIVE, startDate));
        results.add(createSentimentResultEntity("ICICIBANK", Stock.Sector.BANK, SentimentResult.SentimentScore.POSITIVE, startDate));
        results.add(createSentimentResultEntity("AXISBANK", Stock.Sector.BANK, SentimentResult.SentimentScore.NEUTRAL, startDate));

        results.add(createSentimentResultEntity("TCS", Stock.Sector.IT, SentimentResult.SentimentScore.POSITIVE, startDate));
        results.add(createSentimentResultEntity("INFOSYS", Stock.Sector.IT, SentimentResult.SentimentScore.NEUTRAL, startDate));
        results.add(createSentimentResultEntity("WIPRO", Stock.Sector.IT, SentimentResult.SentimentScore.NEGATIVE, startDate));

        results.add(createSentimentResultEntity("SUNPHARMA", Stock.Sector.PHARMA, SentimentResult.SentimentScore.NEGATIVE, startDate));

        // Mock repository to return our test data
        when(sentimentResultRepository.findAllByDateBetween(startDate, endDate))
                .thenReturn(results);

        mockStockRepositoryForEntities(results);

        // When: generating sector digest for date range
        String digest = sentimentAnalysisService.generateSectorDigest(startDate, endDate);

        // Then: verify formatted output contains expected sections
        assertThat(digest).contains("Weekly Sector Sentiment Digest");
        assertThat(digest).contains("Week of: " + startDate + " to " + endDate);
        assertThat(digest).contains("Top Positive Sectors:");
        assertThat(digest).contains("Top Negative Sectors:");
        assertThat(digest).contains("Summary Statistics");
        assertThat(digest).contains("Total stocks analyzed:");
    }

    @Test
    @DisplayName("testEmptyDigestHandling")
    void testEmptyDigestHandling() {
        // Given: empty sentiment results
        LocalDate startDate = LocalDate.of(2026, 3, 16);
        LocalDate endDate = LocalDate.of(2026, 3, 22);

        // Mock repository to return empty list
        when(sentimentResultRepository.findAllByDateBetween(startDate, endDate))
                .thenReturn(new ArrayList<>());

        // When: generating digest with no data
        String digest = sentimentAnalysisService.generateSectorDigest(startDate, endDate);

        // Then: digest should handle empty case gracefully
        assertThat(digest).contains("Weekly Sector Sentiment Digest");
        assertThat(digest).contains("Week of: " + startDate + " to " + endDate);
        assertThat(digest).contains("No sentiment data");
    }

    // ===== Helper Methods =====

    /**
     * Creates a mock sentiment result with specified sector.
     */
    private SentimentResult createSentimentResult(
            String symbol,
            Stock.Sector sector,
            SentimentResult.SentimentScore score) {

        return SentimentResult.create(
                symbol,
                LocalDate.now(),
                score,
                "Mock sentiment for " + symbol,
                "Test content",
                0.85
        );
    }

    /**
     * Creates a mock sentiment result entity with specified sector and date.
     */
    private SentimentResultEntity createSentimentResultEntity(
            String symbol,
            Stock.Sector sector,
            SentimentResult.SentimentScore score,
            LocalDate date) {

        SentimentResultEntity entity = new SentimentResultEntity();
        entity.setId(null);
        entity.setSymbol(symbol.toUpperCase());
        entity.setDate(date);
        entity.setSentimentScore(score.name());
        entity.setSummary("Mock sentiment for " + symbol);
        entity.setRawContent("Test content");
        entity.setConfidence(0.85);
        entity.setAnalyzedAt(date);
        return entity;
    }

    /**
     * Mocks the stock repository to return correct sectors for test symbols.
     */
    private void mockStockRepository(List<SentimentResult> results) {
        // Build a set of unique symbols and their sectors
        for (SentimentResult result : results) {
            String symbol = result.symbol();

            // Determine sector based on symbol
            Stock.Sector sector = determineSectorForSymbol(symbol);

            // Mock the repository
            when(stockRepository.findBySymbol(symbol))
                    .thenReturn(java.util.Optional.of(createMockStockEntity(symbol, sector)));
        }
    }

    /**
     * Mocks the stock repository for entity-based tests.
     */
    private void mockStockRepositoryForEntities(List<SentimentResultEntity> results) {
        for (SentimentResultEntity entity : results) {
            String symbol = entity.getSymbol();

            // Determine sector based on symbol
            Stock.Sector sector = determineSectorForSymbol(symbol);

            // Mock the repository
            when(stockRepository.findBySymbol(symbol))
                    .thenReturn(java.util.Optional.of(createMockStockEntity(symbol, sector)));
        }
    }

    /**
     * Determines sector based on symbol prefix or full name matching.
     */
    private Stock.Sector determineSectorForSymbol(String symbol) {
        // Extract sector from symbol if it contains sector name
        if (symbol.startsWith("BANK") || symbol.contains("HDFCBANK") || symbol.contains("ICICIBANK") || symbol.contains("AXISBANK")) {
            return Stock.Sector.BANK;
        } else if (symbol.startsWith("IT") || symbol.contains("TCS") || symbol.contains("INFOSYS") || symbol.contains("WIPRO") || symbol.contains("TECHM")) {
            return Stock.Sector.IT;
        } else if (symbol.startsWith("PHARMA") || symbol.contains("SUNPHARMA") || symbol.contains("CIPLA") || symbol.contains("LUPIN")) {
            return Stock.Sector.PHARMA;
        } else if (symbol.startsWith("METALS") || symbol.contains("JSWSTEEL") || symbol.contains("TATASTEEL")) {
            return Stock.Sector.METALS;
        } else {
            return Stock.Sector.OTHERS;
        }
    }

    /**
     * Creates a mock stock entity for testing.
     */
    private com.swingtrade.data.entity.StockEntity createMockStockEntity(String symbol, Stock.Sector sector) {
        com.swingtrade.data.entity.StockEntity entity = new com.swingtrade.data.entity.StockEntity();
        entity.setSymbol(symbol);
        entity.setName("Test Company " + symbol);
        entity.setSector(sector.name());
        entity.setExchange("NSE");
        return entity;
    }
}
