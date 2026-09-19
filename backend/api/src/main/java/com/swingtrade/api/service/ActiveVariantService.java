package com.swingtrade.api.service;

import com.swingtrade.data.entity.StrategyConfigEntity;
import com.swingtrade.data.repository.StrategyConfigRepository;
import com.swingtrade.domain.StrategyConfig;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/** Read access to the current strategy variants for tournament, attribution and dashboard views. */
@Service
public class ActiveVariantService {

    private final StrategyConfigRepository repository;

    public ActiveVariantService(StrategyConfigRepository repository) {
        this.repository = repository;
    }

    /** Every variant's current version, in any mode, ordered by variant id. */
    public List<StrategyConfig> allCurrent() {
        return repository.findAll().stream()
            .map(StrategyConfigEntity::toDomain)
            .filter(StrategyConfig::current)
            .sorted(Comparator.comparing(StrategyConfig::variantId))
            .toList();
    }

    /** Current variants that trade paper books: SHADOW and CHAMPION. */
    public List<StrategyConfig> active() {
        return allCurrent().stream()
            .filter(c -> c.mode() == StrategyConfig.Mode.SHADOW || c.mode() == StrategyConfig.Mode.CHAMPION)
            .toList();
    }
}
