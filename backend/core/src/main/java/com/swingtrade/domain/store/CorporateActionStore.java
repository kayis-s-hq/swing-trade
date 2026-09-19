package com.swingtrade.domain.store;

import com.swingtrade.domain.CorporateAction;

import java.time.LocalDate;
import java.util.List;

public interface CorporateActionStore {
    List<CorporateAction> findBySymbolAndEffectiveDateBetween(String symbol, LocalDate from, LocalDate to);

    void save(CorporateAction action);
}
