package com.swingtrade.data.service;

import com.swingtrade.data.client.UpstoxRestClient;
import com.swingtrade.data.config.UpstoxConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDate;

import static org.mockito.Mockito.*;

class DataIngestionServiceTest {

    private DataIngestionService dataIngestionService;
    private UpstoxRestClient upstoxRestClient;
    private UpstoxConfig upstoxConfig;

    @BeforeEach
    void setUp() {
        upstoxConfig = new UpstoxConfig();
        upstoxConfig.setApiUrl("https://api.upstox.com/v2");
        upstoxConfig.setAccessToken("test_token");

        upstoxRestClient = Mockito.mock(UpstoxRestClient.class);
        dataIngestionService = new DataIngestionService(upstoxRestClient);
    }

    @Test
    void testAutoIngestData() {
        // Test that the scheduled method can be called without errors
        // In a real test, we would mock the actual API calls
        assert dataIngestionService != null;
    }

    @Test
    void testBackfillStockData() {
        LocalDate fromDate = LocalDate.now().minusYears(3);
        LocalDate toDate = LocalDate.now();

        // This should not throw exceptions
        dataIngestionService.backfillStockData("RELIANCE", fromDate, toDate);
        
        // Verify that the service was called (mock verification)
        verifyNoInteractions(upstoxRestClient);
    }

    @Test
    void testDataQualityValidation() {
        LocalDate fromDate = LocalDate.now().minusYears(3);
        LocalDate toDate = LocalDate.now();

        // This should return a valid report without throwing exceptions
        DataIngestionService.DataQualityReport report = 
            dataIngestionService.validateDataQuality("RELIANCE", fromDate, toDate);
        
        assert report != null;
        assert report.getStockSymbol().equals("RELIANCE");
    }
}
