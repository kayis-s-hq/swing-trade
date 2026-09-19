package com.swingtrade.data.service;

import com.swingtrade.data.entity.OhlcvCandleEntity;
import com.swingtrade.data.entity.StockEntity;
import com.swingtrade.data.entity.WatchlistEntity;
import com.swingtrade.data.repository.OhlcvCandleRepository;
import com.swingtrade.data.repository.StockRepository;
import com.swingtrade.data.repository.WatchlistRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class WatchlistServiceTest {
    @Mock private WatchlistRepository watchlistRepository;
    @Mock private StockRepository stockRepository;
    @Mock private OhlcvCandleRepository candleRepository;
    @Mock private DataIngestionService ingestionService;
    @Mock private MarketDataClientProvider provider;

    private WatchlistService service;

    @BeforeEach
    void setUp() {
        service = new WatchlistService(watchlistRepository, stockRepository, candleRepository,
            ingestionService, provider, 2);
    }

    @Test
    void delegatesWatchlistQueriesAndAddsNewStock() {
        WatchlistEntity added = new WatchlistEntity("ABC", "Alpha");
        when(watchlistRepository.findByIsActiveTrueOrderBySymbolAsc()).thenReturn(List.of(added));
        when(watchlistRepository.findAll()).thenReturn(List.of(added));
        when(watchlistRepository.findBySymbol("ABC")).thenReturn(Optional.empty());
        when(stockRepository.existsBySymbol("ABC")).thenReturn(false);
        when(watchlistRepository.save(any(WatchlistEntity.class))).thenReturn(added);

        assertThat(service.getActiveWatchlist()).containsExactly(added);
        assertThat(service.getAllWatchlist()).containsExactly(added);
        assertThat(service.getBySymbol("ABC")).isEmpty();
        assertThat(service.addToWatchlist("ABC", "Alpha", null)).isSameAs(added);
        verify(stockRepository).save(any(StockEntity.class));
        verify(watchlistRepository).save(any(WatchlistEntity.class));
    }

    @Test
    void reactivatesExistingAndSupportsRemoveAndToggle() {
        WatchlistEntity existing = new WatchlistEntity("ABC", "Alpha");
        existing.setIsActive(false);
        when(stockRepository.existsBySymbol("ABC")).thenReturn(true);
        when(watchlistRepository.findBySymbol("ABC")).thenReturn(Optional.of(existing));
        when(watchlistRepository.save(existing)).thenReturn(existing);

        assertThat(service.addToWatchlist("ABC", "Alpha", "BSE")).isSameAs(existing);
        assertThat(existing.getIsActive()).isTrue();
        assertThat(service.removeFromWatchlist("ABC")).contains(existing);
        assertThat(existing.getIsActive()).isFalse();
        assertThat(service.toggleActive("ABC", true)).isSameAs(existing);
        assertThat(existing.getIsActive()).isTrue();

        when(watchlistRepository.findBySymbol("missing")).thenReturn(Optional.empty());
        assertThat(service.removeFromWatchlist("missing")).isEmpty();
        assertThat(service.toggleActive("missing", true)).isNull();
    }

    @Test
    void reportsIngestionStatusAndQuality() {
        WatchlistEntity entry = new WatchlistEntity("ABC", "Alpha");
        entry.setExchange("NSE");
        when(watchlistRepository.findByIsActiveTrueOrderBySymbolAsc()).thenReturn(List.of(entry));
        OhlcvCandleEntity latest = new OhlcvCandleEntity();
        latest.setDate(LocalDate.now());
        OhlcvCandleEntity earliest = new OhlcvCandleEntity();
        earliest.setDate(LocalDate.now().minusYears(3));
        when(candleRepository.countBySymbol("ABC")).thenReturn(1000L);
        when(candleRepository.findLatestBySymbol("ABC")).thenReturn(Optional.of(latest));
        when(candleRepository.findEarliestBySymbol("ABC")).thenReturn(Optional.of(earliest));

        var status = service.getIngestionStatus();
        assertThat(status).hasSize(1);
        assertThat(status.get(0)).containsEntry("symbol", "ABC")
            .containsEntry("candleCount", 1000L).containsEntry("hasData", true);

        when(candleRepository.countBySymbol("ABC")).thenReturn(0L);
        when(candleRepository.findLatestBySymbol("ABC")).thenReturn(Optional.empty());
        when(candleRepository.findEarliestBySymbol("ABC")).thenReturn(Optional.empty());
        assertThat(service.getIngestionStatus().get(0)).containsEntry("dataQuality", "no_data");
    }

    @Test
    void validatesPullDatesAndTracksCompletedPull() throws Exception {
        WatchlistEntity entry = new WatchlistEntity("ABC", "Alpha");
        when(watchlistRepository.findByIsActiveTrueOrderBySymbolAsc()).thenReturn(List.of(entry));
        when(candleRepository.countBySymbol("ABC")).thenReturn(12L);

        assertThatThrownBy(() -> service.startPullAll(null, LocalDate.now()))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.startPullAll(LocalDate.now(), LocalDate.now().minusDays(1)))
            .isInstanceOf(IllegalArgumentException.class);

        String pullId = service.startPullAll(LocalDate.now().minusDays(2), LocalDate.now());
        for (int i = 0; i < 40 && service.getPullProgress(pullId).map(p -> !"completed".equals(p.getStatus())).orElse(true); i++) {
            Thread.sleep(10);
        }
        var progress = service.getPullProgress(pullId).orElseThrow();
        assertThat(progress.getStatus()).isEqualTo("completed");
        assertThat(progress.getCompleted()).isEqualTo(1);
        assertThat(progress.getFailed()).isZero();
        assertThat(progress.getPercentComplete()).isEqualTo(100.0);
        assertThat(service.getActivePullProgress()).isEmpty();
        verify(ingestionService).processIncrementalStockData(any(), any(), any());
    }

    @Test
    void tracksFailedPullAndProgressValueContracts() throws Exception {
        WatchlistEntity entry = new WatchlistEntity("BAD", "Bad");
        when(watchlistRepository.findByIsActiveTrueOrderBySymbolAsc()).thenReturn(List.of(entry));
        doThrow(new IllegalStateException("provider unavailable"))
            .when(ingestionService).processIncrementalStockData(any(), any(), any());
        String pullId = service.startPullAll(LocalDate.now().minusDays(1), LocalDate.now());
        for (int i = 0; i < 40 && service.getPullProgress(pullId).map(p -> !"completed".equals(p.getStatus())).orElse(true); i++) {
            Thread.sleep(10);
        }
        var progress = service.getPullProgress(pullId).orElseThrow();
        assertThat(progress.getFailed()).isEqualTo(1);
        assertThat(progress.getProcessed()).isEqualTo(1);
        assertThat(progress.getCurrentSymbol()).isEqualTo("BAD");

        var empty = new WatchlistService.PullProgress("empty", 0);
        assertThat(empty.getPercentComplete()).isZero();
        empty.updateCurrent("ABC");
        empty.incrementCompleted();
        empty.setStatus("cancelled");
        assertThat(empty.getCurrentSymbol()).isEqualTo("ABC");
        assertThat(empty.getStatus()).isEqualTo("cancelled");
    }
}
