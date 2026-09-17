package com.swingtrade.data.service;

import com.swingtrade.data.entity.WatchlistEntity;
import com.swingtrade.data.repository.WatchlistRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EodIngestionSchedulerTest {

    @Mock private DataIngestionService dataIngestionService;
    @Mock private WatchlistRepository watchlistRepository;
    @Mock private NseHolidayService holidayService;

    @Test
    void alwaysIngestsNiftyBenchmarkAlongsideWatchlist() throws Exception {
        WatchlistEntity tcs = new WatchlistEntity("TCS", "TCS");
        when(watchlistRepository.findByIsActiveTrueOrderBySymbolAsc()).thenReturn(List.of(tcs));
        when(holidayService.isMarketClosed(org.mockito.ArgumentMatchers.any())).thenReturn(false);

        EodIngestionScheduler scheduler = new EodIngestionScheduler(
                dataIngestionService, watchlistRepository, holidayService);
        scheduler.ingestLatestForAll();

        verify(dataIngestionService).processSingleStock(eq("TCS"), org.mockito.ArgumentMatchers.any());
        verify(dataIngestionService).processSingleStock(eq(EodIngestionScheduler.NIFTY_50_SYMBOL),
                org.mockito.ArgumentMatchers.any());
    }
}
