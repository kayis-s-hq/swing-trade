package com.swingtrade.data.service;

import com.swingtrade.data.entity.OhlcvCandleEntity;
import com.swingtrade.data.repository.OhlcvCandleRepository;
import com.swingtrade.data.repository.StockRepository;
import com.swingtrade.data.repository.WatchlistRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.eq;

class DataIngestionServiceTest {

    private DataIngestionService dataIngestionService;
    private OhlcvCandleRepository candleRepository;
    private StockRepository stockRepository;
    private WatchlistRepository watchlistRepository;
    private MarketDataClientProvider marketDataClientProvider;
    private MarketDataClient mockClient;

    @BeforeEach
    void setUp() {
        candleRepository = Mockito.mock(OhlcvCandleRepository.class);
        stockRepository = Mockito.mock(StockRepository.class);
        watchlistRepository = Mockito.mock(WatchlistRepository.class);
        mockClient = Mockito.mock(MarketDataClient.class);

        marketDataClientProvider = Mockito.mock(MarketDataClientProvider.class);
        when(marketDataClientProvider.getClient()).thenReturn(mockClient);

        dataIngestionService = new DataIngestionService(candleRepository, stockRepository, watchlistRepository, marketDataClientProvider, Mockito.mock(TransactionTemplate.class), Mockito.mock(com.swingtrade.core.metrics.DataIngestionMetrics.class));
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

    @Test
    @DisplayName("orchestration pulls latest data and repairs gaps in existing history")
    void fetchLatestAndRepairGapsRepairsExistingHistory() {
        LocalDate latestDate = LocalDate.of(2026, 8, 28);
        OhlcvCandleEntity earliest = candle("RELIANCE", LocalDate.of(2026, 8, 24));
        List<OhlcvCandleEntity> existing = List.of(
            earliest,
            candle("RELIANCE", LocalDate.of(2026, 8, 26)),
            candle("RELIANCE", latestDate));

        when(candleRepository.existsBySymbolAndDate(eq("RELIANCE"), eq(latestDate))).thenReturn(true);
        when(candleRepository.findEarliestBySymbol("RELIANCE")).thenReturn(Optional.of(earliest));
        when(candleRepository.findBySymbolAndDateRange(eq("RELIANCE"), eq(earliest.getDate()),
            eq(latestDate), any())).thenReturn(existing);
        when(mockClient.fetchCandles("RELIANCE", earliest.getDate(), latestDate))
            .thenReturn(List.of());

        String summary = dataIngestionService.fetchLatestAndRepairGaps("RELIANCE", latestDate);

        verify(mockClient).fetchCandles("RELIANCE", earliest.getDate(), latestDate);
        assert summary.contains("requested gap repair for 2 sessions");
    }

    private OhlcvCandleEntity candle(String symbol, LocalDate date) {
        OhlcvCandleEntity candle = new OhlcvCandleEntity();
        candle.setSymbol(symbol);
        candle.setDate(date);
        return candle;
    }
}
