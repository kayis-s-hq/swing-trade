package com.swingtrade.api.config;

import com.swingtrade.api.controller.PositionController;
import com.swingtrade.api.controller.SignalController;
import com.swingtrade.domain.store.SentimentStore;
import com.swingtrade.domain.store.SignalStore;
import com.swingtrade.domain.store.StockStore;
import com.swingtrade.domain.store.WatchlistStore;
import com.swingtrade.llm.service.NewsIngestionService;
import com.swingtrade.llm.service.SentimentService;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import({PositionController.class, SignalController.class})
public class ErrorHandlingTestConfig {

    @Bean
    public com.swingtrade.api.service.PositionService positionService() {
        return org.mockito.Mockito.mock(com.swingtrade.api.service.PositionService.class);
    }

    @Bean
    public com.swingtrade.api.service.PerformanceService performanceService() {
        return org.mockito.Mockito.mock(com.swingtrade.api.service.PerformanceService.class);
    }

    @Bean
    public com.swingtrade.api.service.SignalService signalService() {
        return org.mockito.Mockito.mock(com.swingtrade.api.service.SignalService.class);
    }

    @Bean
    public com.swingtrade.api.service.ScanService scanService() {
        return org.mockito.Mockito.mock(com.swingtrade.api.service.ScanService.class);
    }

    @Bean
    public SignalStore signalStore() {
        return org.mockito.Mockito.mock(SignalStore.class);
    }

    @Bean
    public SentimentStore sentimentStore() {
        return org.mockito.Mockito.mock(SentimentStore.class);
    }

    @Bean
    public StockStore stockStore() {
        return org.mockito.Mockito.mock(StockStore.class);
    }

    @Bean
    public WatchlistStore watchlistStore() {
        return org.mockito.Mockito.mock(WatchlistStore.class);
    }

    @Bean
    public NewsIngestionService newsIngestionService() {
        return org.mockito.Mockito.mock(NewsIngestionService.class);
    }

    @Bean
    public SentimentService sentimentService() {
        return org.mockito.Mockito.mock(SentimentService.class);
    }
}
