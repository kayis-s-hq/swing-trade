package com.swingtrade.data.store;

import com.swingtrade.data.entity.PositionEntity;
import com.swingtrade.data.repository.PositionRepository;
import com.swingtrade.domain.Position;
import com.swingtrade.domain.PositionStatus;
import com.swingtrade.domain.TradeDirection;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PositionStoreImplTest {
    private static final LocalDate DATE = LocalDate.of(2026, 1, 15);

    @Test
    void readsPositionViewsAndDelegatesSave() {
        PositionRepository repository = mock(PositionRepository.class);
        PositionStoreImpl store = new PositionStoreImpl(repository);
        Position position = position();
        PositionEntity entity = mockEntity(position);
        when(repository.findAllOpenPositions()).thenReturn(List.of(entity));
        when(repository.findAll()).thenReturn(List.of(entity));
        when(repository.findById(4L)).thenReturn(Optional.of(entity));
        when(repository.findOpenBySymbol("TCS")).thenReturn(Optional.of(entity));
        when(repository.findBySymbolOrderByEntryDateDesc("TCS")).thenReturn(List.of(entity));
        when(repository.findByStatus("OPEN")).thenReturn(List.of(entity));
        when(repository.findByBrokerType("PAPER")).thenReturn(List.of(entity));
        when(repository.existsOpenBySymbol("TCS")).thenReturn(true);
        when(repository.save(any(PositionEntity.class))).thenReturn(entity);

        assertThat(store.findAllOpen()).containsExactly(position);
        assertThat(store.findAll()).containsExactly(position);
        assertThat(store.findById(4L)).contains(position);
        assertThat(store.findBySymbol("TCS")).contains(position);
        assertThat(store.findBySymbolOrderByEntryDateDesc("TCS")).containsExactly(position);
        assertThat(store.findByStatus(PositionStatus.OPEN)).containsExactly(position);
        assertThat(store.findByBrokerType("PAPER")).containsExactly(position);
        assertThat(store.existsOpenBySymbol("TCS")).isTrue();
        assertThat(store.save(position)).isEqualTo(position);
    }

    @Test
    void missingPositionMapsToEmpty() {
        PositionRepository repository = mock(PositionRepository.class);
        when(repository.findById(99L)).thenReturn(Optional.empty());
        when(repository.findOpenBySymbol("UNKNOWN")).thenReturn(Optional.empty());

        PositionStoreImpl store = new PositionStoreImpl(repository);

        assertThat(store.findById(99L)).isEmpty();
        assertThat(store.findBySymbol("UNKNOWN")).isEmpty();
    }

    private static Position position() {
        return new Position(4L, "PAPER", "TCS", bd("100"), DATE, 10,
                bd("95"), bd("110"), PositionStatus.OPEN, "signal", bd("104"), "POS_00000004",
                null, com.swingtrade.domain.Exchange.NSE, TradeDirection.LONG, bd("100"), bd("40"), BigDecimal.ZERO,
                bd("1000"), DATE.atStartOfDay(), null, null, List.of());
    }

    private static PositionEntity mockEntity(Position position) {
        PositionEntity entity = mock(PositionEntity.class);
        when(entity.toDomain()).thenReturn(position);
        return entity;
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
