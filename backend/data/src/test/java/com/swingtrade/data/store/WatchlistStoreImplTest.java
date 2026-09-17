package com.swingtrade.data.store;

import com.swingtrade.data.entity.WatchlistEntity;
import com.swingtrade.data.repository.WatchlistRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WatchlistStoreImplTest {
    @Test
    void readsActiveSymbolsAndMapsExchange() {
        WatchlistRepository repository = mock(WatchlistRepository.class);
        WatchlistEntity entity = new WatchlistEntity("TCS", "Tata Consultancy Services");
        entity.setExchange("NSE");
        when(repository.findByIsActiveTrueOrderBySymbolAsc()).thenReturn(List.of(entity));
        when(repository.findByIsActiveAndExchange(true, "NSE")).thenReturn(List.of(entity));
        WatchlistStoreImpl store = new WatchlistStoreImpl(repository);

        assertThat(store.getWatchlist()).hasSize(1);
        assertThat(store.getWatchlistByExchange("NSE")).hasSize(1);
        assertThat(store.getActiveWatchlistSymbols()).containsExactly("TCS");
    }

    @Test
    void addSkipsExistingAndRemoveDeactivatesPresentRecord() {
        WatchlistRepository repository = mock(WatchlistRepository.class);
        WatchlistEntity entity = new WatchlistEntity("TCS", "TCS");
        when(repository.existsBySymbol("TCS")).thenReturn(true, false);
        when(repository.findBySymbol("TCS")).thenReturn(Optional.of(entity));
        WatchlistStoreImpl store = new WatchlistStoreImpl(repository);

        store.addToWatchlist("TCS");
        store.addToWatchlist("TCS");
        store.removeFromWatchlist("TCS");

        assertThat(entity.getIsActive()).isFalse();
        verify(repository).save(entity);
    }

    @Test
    void removeMissingSymbolHasNoSideEffect() {
        WatchlistRepository repository = mock(WatchlistRepository.class);
        when(repository.findBySymbol("MISSING")).thenReturn(Optional.empty());

        new WatchlistStoreImpl(repository).removeFromWatchlist("MISSING");

        verify(repository, org.mockito.Mockito.never()).save(any(WatchlistEntity.class));
    }
}
