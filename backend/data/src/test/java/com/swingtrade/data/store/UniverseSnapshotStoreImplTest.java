package com.swingtrade.data.store;

import com.swingtrade.data.entity.UniverseSnapshotEntity;
import com.swingtrade.data.repository.UniverseSnapshotRepository;
import com.swingtrade.domain.UniverseSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UniverseSnapshotStoreImplTest {
    @Mock private UniverseSnapshotRepository repository;

    @Test
    void saveUpdatesExistingSymbolDate() {
        UniverseSnapshot original = new UniverseSnapshot("TCS", LocalDate.of(2024, 1, 2), "NSE", null, true,
            "archive", Instant.now());
        UniverseSnapshotEntity existing = UniverseSnapshotEntity.fromDomain(original);
        existing.setId(4L);
        when(repository.findBySymbolAndSnapshotDate("TCS", original.snapshotDate())).thenReturn(Optional.of(existing));

        new UniverseSnapshotStoreImpl(repository).save(new UniverseSnapshot("TCS", original.snapshotDate(), "NSE", null,
            false, "archive", original.capturedAt()));

        verify(repository).save(existing);
        org.assertj.core.api.Assertions.assertThat(existing.isIncluded()).isFalse();
        org.assertj.core.api.Assertions.assertThat(existing.getId()).isEqualTo(4L);
    }
}
