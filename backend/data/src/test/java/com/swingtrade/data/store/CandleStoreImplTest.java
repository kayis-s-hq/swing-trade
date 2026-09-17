package com.swingtrade.data.store;

import com.swingtrade.data.entity.OhlcvCandleEntity;
import com.swingtrade.data.repository.OhlcvCandleRepository;
import com.swingtrade.domain.OhlcvCandle;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CandleStoreImplTest {
    @Test
    void findBySymbolAndDateUsesTheIndexedPointLookup() {
        OhlcvCandleRepository repository = mock(OhlcvCandleRepository.class);
        LocalDate date = LocalDate.of(2026, 1, 15);
        OhlcvCandle candle = OhlcvCandle.of("TCS", date, bd("100"), bd("105"), bd("99"), bd("104"), 1_000L);
        when(repository.findBySymbolAndDate("TCS", date))
                .thenReturn(Optional.of(OhlcvCandleEntity.fromDomain(candle)));

        assertThat(new CandleStoreImpl(repository).findBySymbolAndDate("TCS", date)).contains(candle);
        verify(repository).findBySymbolAndDate("TCS", date);
    }

    @Test
    void findBySymbolAndDateReturnsEmptyWhenCandleIsMissing() {
        OhlcvCandleRepository repository = mock(OhlcvCandleRepository.class);
        LocalDate date = LocalDate.of(2026, 1, 15);
        when(repository.findBySymbolAndDate("TCS", date)).thenReturn(Optional.empty());

        assertThat(new CandleStoreImpl(repository).findBySymbolAndDate("TCS", date)).isEmpty();
    }

    @Test
    void delegatesRangeLatestHistoryAndMutations() {
        OhlcvCandleRepository repository = mock(OhlcvCandleRepository.class);
        LocalDate date = LocalDate.of(2026, 1, 15);
        OhlcvCandleEntity entity = OhlcvCandleEntity.fromDomain(
            OhlcvCandle.of("TCS", date, bd("100"), bd("105"), bd("99"), bd("104"), 1_000L));
        when(repository.findAllBySymbolOrderByDateDesc("TCS")).thenReturn(List.of(entity));
        when(repository.findBySymbolAndDateRange(org.mockito.ArgumentMatchers.eq("TCS"),
            org.mockito.ArgumentMatchers.eq(date), org.mockito.ArgumentMatchers.eq(date),
            org.mockito.ArgumentMatchers.any())).thenReturn(List.of(entity));
        when(repository.findTopBySymbolOrderByDateDesc(org.mockito.ArgumentMatchers.eq("TCS"),
            org.mockito.ArgumentMatchers.any())).thenReturn(List.of(entity));
        when(repository.findLatestBySymbol("TCS")).thenReturn(Optional.of(entity));
        when(repository.findEarliestBySymbol("TCS")).thenReturn(Optional.of(entity));
        when(repository.findLatestBySymbolBeforeDate("TCS", date)).thenReturn(Optional.of(entity));
        when(repository.findFirstBySymbolAndDateAfterOrderByDateAsc("TCS", date)).thenReturn(Optional.of(entity));
        when(repository.findNthBySymbolAndDateAfterOrderByDateAsc("TCS", date, 2)).thenReturn(Optional.of(entity));
        when(repository.findLastNBySymbolBeforeDateAsc("TCS", date, 2)).thenReturn(List.of(entity));
        when(repository.countBySymbol("TCS")).thenReturn(1L);
        when(repository.findAllDistinctSymbols()).thenReturn(List.of("TCS"));
        CandleStoreImpl store = new CandleStoreImpl(repository);

        assertThat(store.findBySymbol("TCS")).hasSize(1);
        assertThat(store.findBySymbolAndDateRange("TCS", date, date)).hasSize(1);
        assertThat(store.findTopBySymbolOrderByDateDesc("TCS", 1)).hasSize(1);
        assertThat(store.findLatestBySymbol("TCS")).isPresent();
        assertThat(store.findEarliestBySymbol("TCS")).isPresent();
        assertThat(store.findLatestBySymbolBeforeDate("TCS", date)).isPresent();
        assertThat(store.findFirstBySymbolAndDateAfterOrderByDateAsc("TCS", date)).isPresent();
        assertThat(store.findNthBySymbolAndDateAfterOrderByDateAsc("TCS", date, 2)).isPresent();
        assertThat(store.findAllBySymbolOrderByDateDesc("TCS")).hasSize(1);
        assertThat(store.findLastNBySymbolBeforeDateAsc("TCS", date, 2)).hasSize(1);
        assertThat(store.findAllDistinctSymbols()).containsExactly("TCS");
        assertThat(store.countBySymbol("TCS")).isEqualTo(1L);
        assertThat(store.existsBySymbolAndDate("TCS", date)).isFalse();
        store.save(entity.toDomain());
        store.deleteBySymbol("TCS");
        verify(repository).save(org.mockito.ArgumentMatchers.any(OhlcvCandleEntity.class));
        verify(repository).deleteBySymbol("TCS");
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
