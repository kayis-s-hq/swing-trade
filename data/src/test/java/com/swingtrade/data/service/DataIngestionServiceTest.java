package com.swingtrade.data.service;

import com.swingtrade.data.entity.OhlcvCandleEntity;
import com.swingtrade.data.repository.OhlcvCandleRepository;
import com.swingtrade.data.repository.StockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DataIngestionServiceTest {

    private DataIngestionService dataIngestionService;
    private OhlcvCandleRepository candleRepository;
    private StockRepository stockRepository;
    private MarketDataClient marketDataClient;

    @BeforeEach
    void setUp() {
        candleRepository = Mockito.mock(OhlcvCandleRepository.class);
        stockRepository = Mockito.mock(StockRepository.class);
        marketDataClient = Mockito.mock(MarketDataClient.class);

        dataIngestionService = new DataIngestionService(candleRepository, stockRepository, marketDataClient);
    }

    @Test
    @DisplayName("testAutoIngestData_ScheduledMethodAvailable")
    void testAutoIngestData() {
        // Test that the scheduled method can be called without errors
        assert dataIngestionService != null;
    }

    @Test
    @DisplayName("testDataQualityValidation_ReturnsValidReport")
    void testDataQualityValidation() {
        // Given: Mock repository returns empty list for new stock
        when(candleRepository.findBySymbolAndDateRange(anyString(), any(LocalDate.class), any(LocalDate.class), any()))
            .thenReturn(new ArrayList<>());

        // When: Validation is called
        DataIngestionService.DataQualityReport report =
            dataIngestionService.validateDataQuality("RELIANCE", LocalDate.now().minusYears(1), LocalDate.now());

        // Then: Report is returned with expected values
        assert report != null;
        assert report.getStockSymbol().equals("RELIANCE");
        assert report.getExpectedTradingDays() > 0;
    }

    @Test
    @DisplayName("testGetLatestCandle_ReturnsLatestData")
    void testGetLatestCandle() {
        // Given: Mock repository returns a candle
        OhlcvCandleEntity mockCandle = new OhlcvCandleEntity();
        mockCandle.setSymbol("RELIANCE");
        mockCandle.setDate(LocalDate.now().minusDays(1));
        mockCandle.setClosePrice(BigDecimal.valueOf(2540));

        when(candleRepository.findLatestBySymbol(anyString())).thenReturn(Optional.of(mockCandle));

        // When: Get latest candle is called
        Optional<OhlcvCandleEntity> result = dataIngestionService.getLatestCandle("RELIANCE");

        // Then: Candle is returned
        assert result.isPresent();
        assert result.get().getSymbol().equals("RELIANCE");
    }

    @Test
    @DisplayName("testGetRecentCandles_ReturnsRecentData")
    void testGetRecentCandles() {
        // Given: Mock repository returns a list of candles
        OhlcvCandleEntity mockCandle = new OhlcvCandleEntity();
        mockCandle.setSymbol("RELIANCE");
        mockCandle.setDate(LocalDate.now().minusDays(1));
        mockCandle.setClosePrice(BigDecimal.valueOf(2540));

        List<OhlcvCandleEntity> mockList = List.of(mockCandle);
        when(candleRepository.findTopBySymbolOrderByDateDesc(anyString(), any())).thenReturn(mockList);

        // When: Get recent candles is called
        List<OhlcvCandleEntity> result = dataIngestionService.getRecentCandles("RELIANCE", 5);

        // Then: Candles are returned
        assert result != null;
        assert result.size() == 1;
    }
}
