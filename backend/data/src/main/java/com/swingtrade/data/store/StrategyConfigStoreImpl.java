package com.swingtrade.data.store;

import com.swingtrade.data.entity.StrategyConfigEntity;
import com.swingtrade.data.repository.StrategyConfigRepository;
import com.swingtrade.domain.StrategyConfig;
import com.swingtrade.domain.store.StrategyConfigStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class StrategyConfigStoreImpl implements StrategyConfigStore {
    private final StrategyConfigRepository repository;

    public StrategyConfigStoreImpl(StrategyConfigRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<StrategyConfig> findCurrentByVariantId(String variantId) {
        return repository.findByVariantIdAndCurrentTrue(variantId).map(StrategyConfigEntity::toDomain);
    }

    @Override
    public Optional<StrategyConfig> findByVariantIdAndVersion(String variantId, int version) {
        return repository.findByVariantIdAndVersion(variantId, version).map(StrategyConfigEntity::toDomain);
    }

    @Override
    public List<StrategyConfig> findByVariantId(String variantId) {
        return repository.findByVariantIdOrderByVersionDesc(variantId).stream()
            .map(StrategyConfigEntity::toDomain).toList();
    }

    @Override
    public StrategyConfig save(StrategyConfig config) {
        if (config.id() != null) {
            throw new IllegalArgumentException("Strategy configurations are append-only");
        }
        return repository.save(StrategyConfigEntity.fromDomain(config)).toDomain();
    }
}
