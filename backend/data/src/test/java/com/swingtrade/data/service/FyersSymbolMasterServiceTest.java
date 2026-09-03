package com.swingtrade.data.service;

import com.swingtrade.data.repository.FyersSymbolRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.reactive.function.client.WebClient;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression coverage for the Fyers-active gate reading from
 * {@link MarketDataClientProvider} (the actual runtime source of truth for the
 * active broker) instead of {@code AppSettingsStore} directly. The two used to
 * be read independently by this class and by {@code MarketDataClientProvider}
 * itself, which could disagree after a restart (persisted "fyers" vs. an
 * in-memory default of "yahoo").
 */
class FyersSymbolMasterServiceTest {

    private FyersSymbolRepository repository;
    private MarketDataClientProvider provider;
    private FyersSymbolMasterService service;

    private void setUp(String activeBroker) {
        repository = mock(FyersSymbolRepository.class);
        provider = mock(MarketDataClientProvider.class);
        when(provider.getActiveBroker()).thenReturn(activeBroker);
        service = new FyersSymbolMasterService(WebClient.builder(), repository, provider);
    }

    @Test
    void refreshIfEmpty_skipsWhenProviderReportsYahooActive() {
        setUp("yahoo");

        service.refreshIfEmpty();

        verify(repository, never()).count();
    }

    @Test
    void refreshIfEmpty_proceedsWhenProviderReportsFyersActive() {
        setUp("fyers");
        when(repository.count()).thenReturn(5L);

        service.refreshIfEmpty();

        // Gate let it through and reached the emptiness check (refresh() itself
        // is skipped here since the table isn't empty, avoiding a real network call).
        verify(repository).count();
    }

    @Test
    void scheduledRefresh_skipsWhenProviderReportsYahooActive() {
        setUp("yahoo");

        service.scheduledRefresh();

        verify(repository, never()).count();
        Mockito.verifyNoMoreInteractions(repository);
    }

    @Test
    void gateDefaultsToActive_whenNoProviderWired() {
        // The 2-arg constructor (manual admin refreshes, or a caller with no
        // provider available) must not silently disable the refresh gate.
        FyersSymbolRepository repo = mock(FyersSymbolRepository.class);
        when(repo.count()).thenReturn(5L);
        FyersSymbolMasterService svc = new FyersSymbolMasterService(WebClient.builder(), repo);

        svc.refreshIfEmpty();

        verify(repo).count();
    }
}
