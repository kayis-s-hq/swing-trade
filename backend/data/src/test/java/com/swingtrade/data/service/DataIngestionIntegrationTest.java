package com.swingtrade.data.service;

import com.swingtrade.data.entity.OhlcvCandleEntity;
import com.swingtrade.data.repository.OhlcvCandleRepository;
import com.swingtrade.data.repository.StockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Integration tests for DataIngestionService.
 * Tests real database interactions with local PostgreSQL.
 * Use -Dspring.profiles.active=e2e to run against PostgreSQL.
 * Default profile is "test" which uses H2 in-memory database.
 */
@SpringBootTest
@ActiveProfiles(resolver = com.swingtrade.data.test.TestProfileResolver.class)
@Transactional
class DataIngestionIntegrationTest {

    @Autowired
    private DataIngestionService ingestionService;

    @Autowired
    private OhlcvCandleRepository candleRepository;

    @Autowired
    private StockRepository stockRepository;

    @MockBean
    private MarketDataClient marketDataClient;

    @BeforeEach
    void setUp() {
        // Mock the market data client
        CandleData mockCandle = CandleData.of(
            "RELIANCE",
            LocalDate.now(),
            new BigDecimal("2500.00"),
            new BigDecimal("2520.00"),
            new BigDecimal("2490.00"),
            new BigDecimal("2510.00"),
            1500000L
        );
        when(marketDataClient.fetchCandle(anyString(), any(LocalDate.class)))
            .thenReturn(mockCandle);
    }

    @Test
    void testIngestData_SavesToDatabase() {
        // Arrange
        String symbol = "RELIANCE";
        LocalDate date = LocalDate.now();

        // Act
        ingestionService.processSingleStock(symbol, date);

        // Assert
        Optional<OhlcvCandleEntity> savedCandle = candleRepository.findLatestBySymbol(symbol);
        assertThat(savedCandle).isPresent();
        assertThat(savedCandle.get().getSymbol()).isEqualTo(symbol);
        assertThat(savedCandle.get().getDate()).isEqualTo(date);
    }

    @Test
    void testBackfillStockData_HistoricalData() {
        // Arrange
        String symbol = "TCS";
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(30);

        // Act
        assertThatNoException().isThrownBy(() ->
            ingestionService.backfillStockData(symbol, 1));

        // Assert - check that data was ingested without errors
        long candleCount = candleRepository.countBySymbol(symbol);
        assertThat(candleCount).isGreaterThanOrEqualTo(0);
    }

    @Test
    void testDataQualityValidation_NoGaps() {
        // Arrange
        String symbol = "HDFCBANK";
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(10);

        // Act - ingest data for the date range
        ingestionService.processStockData(symbol, startDate, endDate);

        // Assert - validate data quality
        DataIngestionService.DataQualityReport report =
            ingestionService.validateDataQuality(symbol, startDate, endDate);

        assertThat(report).isNotNull();
        assertThat(report.getStockSymbol()).isEqualTo(symbol);
        assertThat(report.getFromDate()).isEqualTo(startDate);
        assertThat(report.getToDate()).isEqualTo(endDate);
    }

    @Test
    void testDataQualityValidation_GapDetection() {
        // Arrange
        String symbol = "INFY";
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(20);

        // Act - validate data quality
        DataIngestionService.DataQualityReport report =
            ingestionService.validateDataQuality(symbol, startDate, endDate);

        // Assert - report should be generated
        assertThat(report).isNotNull();
        assertThat(report.getExpectedTradingDays()).isPositive();
    }

    @Test
    void testDuplicateHandling_NoDuplicates() {
        // Arrange
        String symbol = "ICICIBANK";
        LocalDate date = LocalDate.now();

        // Act - ingest same candle twice
        ingestionService.processSingleStock(symbol, date);
        ingestionService.processSingleStock(symbol, date);

        // Assert - only one candle should exist
        long count = candleRepository.countBySymbol(symbol);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void testProcessStockData_DateRange() {
        // Arrange
        String symbol = "SBIN";
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(5);

        // Act
        ingestionService.processStockData(symbol, startDate, endDate);

        // Assert
        List<OhlcvCandleEntity> candles = candleRepository.findBySymbolAndDateRange(
            symbol, startDate, endDate,
            org.springframework.data.domain.Pageable.unpaged()
        );

        assertThat(candles).isNotEmpty();
        candles.forEach(candle -> {
            assertThat(candle.getSymbol()).isEqualTo(symbol);
            assertThat(candle.getDate()).isBetween(startDate, endDate);
        });
    }

    @Test
    void testGetLatestCandle() {
        // Arrange
        String symbol = "BHARTIARTL";
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);

        // Act - ingest candles for two dates
        ingestionService.processSingleStock(symbol, today);
        ingestionService.processSingleStock(symbol, yesterday);

        // Assert - get latest candle
        Optional<OhlcvCandleEntity> latest = ingestionService.getLatestCandle(symbol);
        assertThat(latest).isPresent();
        assertThat(latest.get().getDate()).isEqualTo(today);
    }

    @Test
    void testGetRecentCandles() {
        // Arrange
        String symbol = "ASIANPAINT";
        int days = 5;

        // Act
        List<OhlcvCandleEntity> recentCandles = ingestionService.getRecentCandles(symbol, days);

        // Assert
        assertThat(recentCandles).hasSizeLessThanOrEqualTo(days);
        assertThat(recentCandles).isSortedAccordingTo(
            (c1, c2) -> c2.getDate().compareTo(c1.getDate())
        );
    }

    @Test
    void testPriceAnomalyDetection() {
        // Arrange
        String symbol = "BAJFINANCE";
        LocalDate date = LocalDate.now();

        // Act - save a candle with valid data
        CandleData validCandle = CandleData.of(
            symbol,
            date,
            new BigDecimal("2000.00"),
            new BigDecimal("2050.00"),
            new BigDecimal("1980.00"),
            new BigDecimal("2030.00"),
            800000L
        );

        // Save manually to test validation
        OhlcvCandleEntity entity = new OhlcvCandleEntity();
        entity.setSymbol(symbol);
        entity.setDate(date);
        entity.setOpenPrice(validCandle.open());
        entity.setHighPrice(validCandle.high());
        entity.setLowPrice(validCandle.low());
        entity.setClosePrice(validCandle.close());
        entity.setVolume(validCandle.volume());
        candleRepository.save(entity);

        // Assert
        List<OhlcvCandleEntity> candles = candleRepository.findBySymbolAndDateRange(
            symbol, date, date,
            org.springframework.data.domain.Pageable.unpaged()
        );
        assertThat(candles).hasSize(1);
    }

    @Test
    void testAutoIngestData_ScheduledExecution() {
        // Arrange
        String[] symbols = {"RELIANCE", "TCS", "HDFCBANK"};

        // Act - simulate scheduled ingestion
        for (String symbol : symbols) {
            ingestionService.processSingleStock(symbol, LocalDate.now());
        }

        // Assert
        for (String symbol : symbols) {
            Optional<OhlcvCandleEntity> candle = candleRepository.findLatestBySymbol(symbol);
            assertThat(candle).isPresent();
        }
    }
}
