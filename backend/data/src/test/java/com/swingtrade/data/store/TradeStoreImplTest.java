package com.swingtrade.data.store;

import com.swingtrade.data.entity.TradeEntity;
import com.swingtrade.data.repository.TradeRepository;
import com.swingtrade.domain.Trade;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TradeStoreImplTest {
    private static final LocalDate DATE = LocalDate.of(2026, 1, 15);

    @Test
    void readsAllTradeViewsAndCounts() {
        TradeRepository repository = mock(TradeRepository.class);
        TradeStoreImpl store = new TradeStoreImpl(repository);
        Trade trade = Trade.open(5L, "TCS", DATE, bd("100"), 10, "signal", bd("1"));
        TradeEntity entity = mockEntity(trade);
        when(repository.findBySymbol("TCS")).thenReturn(List.of(entity));
        when(repository.findOpenByPositionId(5L)).thenReturn(Optional.of(entity));
        when(repository.findByEntryDateBetween(DATE, DATE)).thenReturn(List.of(entity));
        when(repository.findByExitDateBetween(DATE, DATE)).thenReturn(List.of(entity));
        when(repository.findByStatus("OPEN")).thenReturn(List.of(entity));
        when(repository.findAllOpenTrades()).thenReturn(List.of(entity));
        when(repository.findAllClosedTrades()).thenReturn(List.of(entity));
        when(repository.findAll()).thenReturn(List.of(entity));
        when(repository.countBySymbol("TCS")).thenReturn(2L);
        when(repository.countOpenTrades()).thenReturn(1L);

        assertThat(store.findBySymbol("TCS")).containsExactly(trade);
        assertThat(store.findOpenByPositionId(5L)).contains(trade);
        assertThat(store.findByEntryDateBetween(DATE, DATE)).containsExactly(trade);
        assertThat(store.findByExitDateBetween(DATE, DATE)).containsExactly(trade);
        assertThat(store.findByStatus(Trade.TradeStatus.OPEN)).containsExactly(trade);
        assertThat(store.findAllOpen()).containsExactly(trade);
        assertThat(store.findAllClosed()).containsExactly(trade);
        assertThat(store.findAll()).containsExactly(trade);
        assertThat(store.countBySymbol("TCS")).isEqualTo(2L);
        assertThat(store.countOpenTrades()).isEqualTo(1L);
    }

    @Test
    void savesTradeAndMapsMissingOpenTradeToEmpty() {
        TradeRepository repository = mock(TradeRepository.class);
        TradeStoreImpl store = new TradeStoreImpl(repository);
        Trade trade = Trade.open(5L, "TCS", DATE, bd("100"), 10, "signal", bd("1"));
        TradeEntity entity = mockEntity(trade);
        when(repository.save(any(TradeEntity.class))).thenReturn(entity);
        when(repository.findOpenByPositionId(99L)).thenReturn(Optional.empty());

        assertThat(store.save(trade)).isEqualTo(trade);
        assertThat(store.findOpenByPositionId(99L)).isEmpty();
    }

    private static TradeEntity mockEntity(Trade trade) {
        TradeEntity entity = mock(TradeEntity.class);
        when(entity.toDomain()).thenReturn(trade);
        return entity;
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
