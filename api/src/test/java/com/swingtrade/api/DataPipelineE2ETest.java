package com.swingtrade.api;

import com.swingtrade.api.fixtures.DataPipelineFixtures;
import com.swingtrade.data.entity.OhlcvCandleEntity;
import com.swingtrade.data.repository.OhlcvCandleRepository;
import com.swingtrade.data.service.DataIngestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class DataPipelineE2ETest {

    @Container
    @ServiceConnection
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
    private DataIngestionService ingestionService;

    @Autowired
    private OhlcvCandleRepository candleRepository;

    @BeforeEach
    void setUp() {
        // Clear database before each test
        candleRepository.deleteAll();
    }

    @Test
    void testCompleteBackfillWorkflow() {
        // Given: Backfill data exists from previous tests or we can mock API
        // When: Trigger backfill for 1 year
        ingestionService.backfillStockData("RELIANCE", 1);

        // Then: Verify candles saved correctly
        long count = candleRepository.countBySymbol("RELIANCE");
        assertThat(count).isGreaterThan(200); // ~1 year of trading days

        // Verify candles retrieved
        List<OhlcvCandleEntity> candles = candleRepository.findAllBySymbol("RELIANCE");
        assertThat(candles).isNotNull();
        assertThat(candles.size()).isGreaterThan(0);

        // Verify price validity
        candles.forEach(c -> {
            assertThat(c.getHighPrice()).isGreaterThanOrEqualTo(c.getOpenPrice());
            assertThat(c.getHighPrice()).isGreaterThanOrEqualTo(c.getClosePrice());
            assertThat(c.getLowPrice()).isLessThanOrEqualTo(c.getOpenPrice());
            assertThat(c.getLowPrice()).isLessThanOrEqualTo(c.getClosePrice());
        });
    }

    @Test
    void testDataQualityValidation() {
        // Given: Backfilled data exists
        ingestionService.backfillStockData("HDFCBANK", 1);

        // When: Validate data quality
        DataIngestionService.DataQualityReport report =
            ingestionService.validateDataQuality(
                "HDFCBANK",
                LocalDate.now().minusYears(1),
                LocalDate.now()
            );

        // Then: Verify validation results
        assertThat(report.getExpectedTradingDays()).isGreaterThan(200);
        assertThat(report.getActualTradingDays()).isEqualTo(
            candleRepository.countBySymbol("HDFCBANK")
        );
        assertThat(report.hasIssues()).isFalse();
        assertThat(report.getGaps()).isEmpty();
        assertThat(report.getAnomalies()).isEmpty();
    }

    @Test
    void testRetrieveLatestCandle() {
        // Given: Data exists in DB
        ingestionService.backfillStockData("TCS", 1);

        // When: Get latest candle
        Optional<OhlcvCandleEntity> latest =
            ingestionService.getLatestCandle("TCS");

        // Then: Verify latest candle
        assertThat(latest).isPresent();
        assertThat(latest.get().getSymbol()).isEqualTo("TCS");

        // Verify it's actually the latest
        List<OhlcvCandleEntity> all =
            candleRepository.findAllBySymbol("TCS");
        assertThat(latest.get().getDate()).isEqualTo(all.get(0).getDate());
    }

    @Test
    void testRetrieveRecentCandles() {
        // Given: Data exists in DB
        ingestionService.backfillStockData("INFY", 2);

        // When: Get last 30 days
        List<OhlcvCandleEntity> recent =
            ingestionService.getRecentCandles("INFY", 30);

        // Then: Verify results
        assertThat(recent).hasSizeBetween(1, 30);
        assertThat(recent).isSortedAccordingTo(
            java.util.Comparator.comparing(OhlcvCandleEntity::getDate).reversed()
        );

        // Verify all candles match symbol
        recent.forEach(c -> assertThat(c.getSymbol()).isEqualTo("INFY"));
    }

    @Test
    void testPriceAnomalyDetection() {
        // Given: Inject invalid candle (High < Open)
        OhlcvCandleEntity invalid = new OhlcvCandleEntity();
        invalid.setSymbol("ADANIPORTS");
        invalid.setDate(LocalDate.now());
        invalid.setOpenPrice(new BigDecimal("1000.00"));
        invalid.setHighPrice(new BigDecimal("900.00")); // Invalid: High < Open
        invalid.setLowPrice(new BigDecimal("950.00"));
        invalid.setClosePrice(new BigDecimal("980.00"));
        invalid.setVolume(Long.valueOf(1000000L));
        invalid.setAdjClosePrice(new BigDecimal("980.00"));
        candleRepository.save(invalid);

        // When: Validate data quality
        DataIngestionService.DataQualityReport report =
            ingestionService.validateDataQuality(
                "ADANIPORTS",
                LocalDate.now().minusDays(1),
                LocalDate.now()
            );

        // Then: Verify anomaly detected
        assertThat(report.hasIssues()).isTrue();
        assertThat(report.getAnomalies()).hasSize(1);

        DataIngestionService.PriceAnomaly anomaly = report.getAnomalies().get(0);
        assertThat(anomaly.getType()).isEqualTo("High price invalid");
        assertThat(anomaly.getSymbol()).isEqualTo("ADANIPORTS");
    }
}
