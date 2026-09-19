package com.swingtrade.data.store;

import com.swingtrade.data.entity.CorporateActionEntity;
import com.swingtrade.data.repository.CorporateActionRepository;
import com.swingtrade.domain.CorporateAction;
import com.swingtrade.domain.store.CorporateActionStore;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class CorporateActionStoreImpl implements CorporateActionStore {
    private final CorporateActionRepository repository;
    public CorporateActionStoreImpl(CorporateActionRepository repository) { this.repository = repository; }
    public List<CorporateAction> findBySymbolAndEffectiveDateBetween(String symbol, LocalDate from, LocalDate to) {
        return repository.findBySymbolAndEffectiveDateBetweenOrderByEffectiveDateAsc(symbol, from, to).stream()
            .map(CorporateActionEntity::toDomain).toList();
    }
    public void save(CorporateAction action) {
        CorporateActionEntity entity = repository.findBySymbolAndEffectiveDateAndActionType(
            action.symbol(), action.effectiveDate(), action.actionType()).orElseGet(CorporateActionEntity::new);
        entity.setSymbol(action.symbol()); entity.setEffectiveDate(action.effectiveDate()); entity.setActionType(action.actionType());
        entity.setAdjustmentFactor(action.adjustmentFactor()); entity.setCashAmount(action.cashAmount());
        entity.setSource(action.source()); entity.setRecordedAt(action.recordedAt()); repository.save(entity);
    }
}
