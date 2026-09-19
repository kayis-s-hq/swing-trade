package com.swingtrade.data.store;

import com.swingtrade.data.entity.SentimentResultEntity;
import com.swingtrade.data.repository.SentimentResultRepository;
import com.swingtrade.domain.SentimentResult;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SentimentStoreImplTest {
    private static final LocalDate DATE = LocalDate.of(2026, 1, 15);

    @Test
    void readsAndCountsSentimentResults() {
        SentimentResultRepository repository = mock(SentimentResultRepository.class);
        SentimentStoreImpl store = new SentimentStoreImpl(repository);
        SentimentResult result = result();
        SentimentResultEntity entity = mockEntity(result);
        when(repository.findAllBySymbol(anyString(), any())).thenReturn(List.of(entity));
        when(repository.findBySymbolAndDate("TCS", DATE)).thenReturn(Optional.of(entity));
        when(repository.findAllByDateBetween(DATE, DATE)).thenReturn(List.of(entity));
        when(repository.countByDateBetweenAndSentimentScore(DATE, DATE, "POSITIVE")).thenReturn(4L);
        when(repository.findAllDistinctSymbols()).thenReturn(List.of("TCS"));
        when(repository.findLatestBySymbol("TCS")).thenReturn(Optional.of(entity));
        when(repository.findAllByDateBeforeOrderByDateAsc(DATE)).thenReturn(List.of(entity));
        when(repository.findAll()).thenReturn(List.of(entity));

        assertThat(store.findBySymbol("TCS")).containsExactly(result);
        assertThat(store.findAllBySymbolOrderByDateDesc("TCS")).containsExactly(result);
        assertThat(store.findBySymbolAndDate("TCS", DATE)).contains(result);
        assertThat(store.findAllByDateBetween(DATE, DATE)).containsExactly(result);
        assertThat(store.countByDateBetweenAndScore(DATE, DATE, SentimentResult.SentimentScore.POSITIVE)).isEqualTo(4L);
        assertThat(store.findAllDistinctSymbols()).containsExactly("TCS");
        assertThat(store.findLatestBySymbol("TCS")).contains(result);
        assertThat(store.findAllByDateBeforeOrderByDateAsc(DATE)).containsExactly(result);
        assertThat(store.findAll()).containsExactly(result);
    }

    @Test
    void savesNewResultAndUpdatesExistingResult() {
        SentimentResultRepository repository = mock(SentimentResultRepository.class);
        SentimentStoreImpl store = new SentimentStoreImpl(repository);
        SentimentResult result = result();
        SentimentResultEntity persisted = mockEntity(result);
        when(repository.findBySymbolAndDate("TCS", DATE)).thenReturn(Optional.empty(), Optional.of(persisted));
        when(repository.save(any(SentimentResultEntity.class))).thenReturn(persisted);
        when(repository.saveAndFlush(persisted)).thenReturn(persisted);

        assertThat(store.save(result)).isEqualTo(result);
        assertThat(store.saveOrUpdate(result)).isEqualTo(result);
        assertThat(store.saveOrUpdate(result)).isEqualTo(result);
        verify(repository).saveAndFlush(persisted);
    }

    @Test
    void missingResultReturnsEmpty() {
        SentimentResultRepository repository = mock(SentimentResultRepository.class);
        when(repository.findBySymbolAndDate("TCS", DATE)).thenReturn(Optional.empty());

        assertThat(new SentimentStoreImpl(repository).findBySymbolAndDate("TCS", DATE)).isEmpty();
    }

    private static SentimentResult result() {
        return new SentimentResult(1L, "TCS", DATE, SentimentResult.SentimentScore.POSITIVE,
                "summary", "raw", .8, DATE, List.of("risk"), List.of("growth"),
                "prompt", "model", 2, "LLM", List.of(10L), "request");
    }

    private static SentimentResultEntity mockEntity(SentimentResult result) {
        SentimentResultEntity entity = mock(SentimentResultEntity.class);
        when(entity.toDomain()).thenReturn(result);
        return entity;
    }
}
