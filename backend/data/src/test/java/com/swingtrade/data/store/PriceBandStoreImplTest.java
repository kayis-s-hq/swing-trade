package com.swingtrade.data.store;

import com.swingtrade.data.entity.PriceBandEntity;
import com.swingtrade.data.repository.PriceBandRepository;
import com.swingtrade.domain.PriceBand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PriceBandStoreImplTest {
    @Mock
    private PriceBandRepository repository;

    @Test
    void saveUpdatesExistingDateWithoutViolatingUniqueConstraint() {
        PriceBandEntity existing = PriceBandEntity.fromDomain(new PriceBand("TCS",
            LocalDate.of(2026, 1, 2), bd("90"), bd("110")));
        existing.setId(7L);
        when(repository.findBySymbolAndDate("TCS", LocalDate.of(2026, 1, 2)))
            .thenReturn(Optional.of(existing));
        PriceBand replacement = new PriceBand("TCS", LocalDate.of(2026, 1, 2), bd("92"), bd("108"));

        new PriceBandStoreImpl(repository).save(replacement);

        verify(repository).save(existing);
        org.assertj.core.api.Assertions.assertThat(existing.getId()).isEqualTo(7L);
        org.assertj.core.api.Assertions.assertThat(existing.getLowerLimit()).isEqualByComparingTo(bd("92"));
        org.assertj.core.api.Assertions.assertThat(existing.getUpperLimit()).isEqualByComparingTo(bd("108"));
    }

    @Test
    void findMapsMissingBandToEmpty() {
        when(repository.findBySymbolAndDate("TCS", LocalDate.of(2026, 1, 2)))
            .thenReturn(Optional.empty());

        org.assertj.core.api.Assertions.assertThat(new PriceBandStoreImpl(repository)
            .findBySymbolAndDate("TCS", LocalDate.of(2026, 1, 2))).isEmpty();
    }

    private static BigDecimal bd(String value) { return new BigDecimal(value); }
}
