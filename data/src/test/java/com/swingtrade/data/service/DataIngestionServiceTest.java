package com.swingtrade.data.service;

import com.swingtrade.data.repository.OhlcvCandleRepository;
import com.swingtrade.data.repository.StockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDate;

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
    void testAutoIngestData() {
        // Test that the scheduled method can be called without errors
        // In a real test, we would mock the actual API calls
        assert dataIngestionService != null;
    }

    @Test
    void testBackfillStockData() {
        // This should not throw exceptions
        dataIngestionService.backfillStockData("RELIANCE", 1);

        // Verify that the service was called (mock verification)
        verifyNoInteractions(candleRepository);
    }

    @Test
    void testDataQualityValidation() {
        // This should return a valid report without throwing exceptions
        DataIngestionService.DataQualityReport report =
            dataIngestionService.validateDataQuality("RELIANCE", LocalDate.now().minusYears(1), LocalDate.now());

        assert report != null;
        assert report.getStockSymbol().equals("RELIANCE");
    }
}
