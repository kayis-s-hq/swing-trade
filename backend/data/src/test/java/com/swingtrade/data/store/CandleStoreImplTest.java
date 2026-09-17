package com.swingtrade.data.store;

import com.swingtrade.data.entity.OhlcvCandleEntity;
import com.swingtrade.data.repository.OhlcvCandleRepository;
import com.swingtrade.domain.OhlcvCandle;
import java.math.BigDecimal;
import java.time.LocalDate;
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

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
