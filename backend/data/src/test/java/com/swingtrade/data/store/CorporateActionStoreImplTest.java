package com.swingtrade.data.store;

import com.swingtrade.data.entity.CorporateActionEntity;
import com.swingtrade.data.repository.CorporateActionRepository;
import com.swingtrade.domain.CorporateAction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CorporateActionStoreImplTest {
    @Mock private CorporateActionRepository repository;

    @Test
    void saveUpdatesExistingSymbolDateAndType() {
        CorporateAction action = new CorporateAction("TCS", LocalDate.of(2024, 2, 1), "DIVIDEND", null,
            new BigDecimal("10"), "exchange", Instant.now());
        CorporateActionEntity existing = CorporateActionEntity.fromDomain(action);
        existing.setId(8L);
        when(repository.findBySymbolAndEffectiveDateAndActionType("TCS", action.effectiveDate(), "DIVIDEND"))
            .thenReturn(Optional.of(existing));

        new CorporateActionStoreImpl(repository).save(action);

        verify(repository).save(existing);
        org.assertj.core.api.Assertions.assertThat(existing.getId()).isEqualTo(8L);
    }
}
