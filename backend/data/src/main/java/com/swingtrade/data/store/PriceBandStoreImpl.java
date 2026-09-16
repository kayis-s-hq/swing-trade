package com.swingtrade.data.store;

import com.swingtrade.data.entity.PriceBandEntity;
import com.swingtrade.data.repository.PriceBandRepository;
import com.swingtrade.domain.PriceBand;
import com.swingtrade.domain.store.PriceBandStore;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Optional;

@Service
public class PriceBandStoreImpl implements PriceBandStore {
    private final PriceBandRepository repository;

    public PriceBandStoreImpl(PriceBandRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<PriceBand> findBySymbolAndDate(String symbol, LocalDate date) {
        return repository.findBySymbolAndDate(symbol, date).map(PriceBandEntity::toDomain);
    }

    @Override
    public void save(PriceBand priceBand) {
        PriceBandEntity entity = repository.findBySymbolAndDate(priceBand.symbol(), priceBand.date())
            .orElseGet(PriceBandEntity::new);
        entity.setSymbol(priceBand.symbol());
        entity.setDate(priceBand.date());
        entity.setLowerLimit(priceBand.lowerLimit());
        entity.setUpperLimit(priceBand.upperLimit());
        repository.save(entity);
    }
}
