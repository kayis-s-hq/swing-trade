package com.swingtrade.data.store;

import com.swingtrade.data.entity.SentimentAccuracyEntity;
import com.swingtrade.data.repository.SentimentAccuracyRepository;
import com.swingtrade.domain.SentimentAccuracy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SentimentAccuracyStoreImplTest {
    private static final LocalDate DATE = LocalDate.of(2026, 1, 15);
    private static final SentimentAccuracy ACCURACY = new SentimentAccuracy(1L, "TCS", DATE,
        "POSITIVE", .8f, .4f, bd(".01"), bd(".02"), bd(".03"), bd(".005"), bd(".01"), bd(".02"),
        "EXCESS_RETURN", "UP", true, bd(".03"), "BULL", "hash", "model", "NEWS",
        LocalDateTime.of(2026, 1, 16, 10, 0));

    @Test
    void delegatesQueriesSaveAndCounts() {
        SentimentAccuracyRepository repository = mock(SentimentAccuracyRepository.class);
        SentimentAccuracyEntity entity = SentimentAccuracyEntity.fromDomain(ACCURACY);
        when(repository.existsBySymbolAndAnalysisDate("TCS", DATE)).thenReturn(true);
        when(repository.findBySymbolAndAnalysisDate("TCS", DATE)).thenReturn(Optional.of(entity));
        when(repository.findBySymbolOrderByAnalysisDateDesc("TCS")).thenReturn(List.of(entity));
        when(repository.save(any(SentimentAccuracyEntity.class))).thenReturn(entity);
        when(repository.countAll()).thenReturn(2L);
        when(repository.countCorrect()).thenReturn(1L);
        SentimentAccuracyStoreImpl store = new SentimentAccuracyStoreImpl(repository);

        assertThat(store.existsBySymbolAndAnalysisDate("TCS", DATE)).isTrue();
        assertThat(store.findBySymbolAndAnalysisDate("TCS", DATE)).contains(ACCURACY);
        assertThat(store.findBySymbolOrderByAnalysisDateDesc("TCS")).containsExactly(ACCURACY);
        assertThat(store.save(ACCURACY)).isEqualTo(ACCURACY);
        assertThat(store.countAll()).isEqualTo(2L);
        assertThat(store.countCorrect()).isEqualTo(1L);
        verify(repository).save(any(SentimentAccuracyEntity.class));
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
