package com.swingtrade.data.service;

import com.swingtrade.data.entity.StrategyConfigAuditEntity;
import com.swingtrade.data.entity.StrategyConfigEntity;
import com.swingtrade.data.repository.StrategyConfigAuditRepository;
import com.swingtrade.data.repository.StrategyConfigRepository;
import com.swingtrade.domain.StrategyConfig;
import com.swingtrade.domain.StrategyMode;
import com.swingtrade.domain.store.StrategyConfigStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * {@link StrategyConfigStore} implementation. All mutation is transactional so "flip previous
 * is_current off, insert new current row" (or "flip mode, write audit row") never partially
 * commits.
 */
@Service
public class StrategyConfigStoreImpl implements StrategyConfigStore {

    private static final List<String> ACTIVE_MODES = List.of(StrategyMode.SHADOW.name(), StrategyMode.CHAMPION.name());

    private final StrategyConfigRepository repository;
    private final StrategyConfigAuditRepository auditRepository;

    public StrategyConfigStoreImpl(StrategyConfigRepository repository, StrategyConfigAuditRepository auditRepository) {
        this.repository = repository;
        this.auditRepository = auditRepository;
    }

    @Override
    public Optional<StrategyConfig> findCurrent(String variantId) {
        return repository.findByVariantIdAndIsCurrentTrue(variantId).map(StrategyConfigEntity::toDomain);
    }

    @Override
    public List<StrategyConfig> findAllCurrent() {
        return repository.findByIsCurrentTrue().stream().map(StrategyConfigEntity::toDomain).toList();
    }

    @Override
    public List<StrategyConfig> findVersions(String variantId) {
        return repository.findByVariantIdOrderByVersionAsc(variantId).stream().map(StrategyConfigEntity::toDomain).toList();
    }

    @Override
    public Optional<StrategyConfig> findVersion(String variantId, int version) {
        return repository.findByVariantIdAndVersion(variantId, version).map(StrategyConfigEntity::toDomain);
    }

    @Override
    public Optional<StrategyConfig> findByParamsHash(String variantId, String paramsHash) {
        return repository.findByVariantIdAndParamsHash(variantId, paramsHash).map(StrategyConfigEntity::toDomain);
    }

    @Override
    @Transactional
    public StrategyConfig save(StrategyConfig config) {
        Optional<StrategyConfigEntity> previousCurrent = repository.findByVariantIdAndIsCurrentTrue(config.variantId());
        if (previousCurrent.isPresent()) {
            int expectedVersion = previousCurrent.get().getVersion() + 1;
            if (config.version() != expectedVersion) {
                throw new IllegalArgumentException(
                    "New version for " + config.variantId() + " must be " + expectedVersion + ", got " + config.version());
            }
            previousCurrent.get().setIsCurrent(false);
            repository.save(previousCurrent.get());
        } else if (config.version() != 1) {
            throw new IllegalArgumentException("First version for a new variant must be 1, got " + config.version());
        }

        StrategyConfigEntity entity = StrategyConfigEntity.fromDomain(config);
        entity.setCreatedAt(config.createdAt() == null ? java.time.LocalDateTime.now() : config.createdAt());
        StrategyConfigEntity saved = repository.save(entity);
        return saved.toDomain();
    }

    @Override
    @Transactional
    public void updateMode(String variantId, StrategyMode newMode) {
        StrategyConfigEntity current = repository.findByVariantIdAndIsCurrentTrue(variantId)
            .orElseThrow(() -> new IllegalArgumentException("No current version for variant " + variantId));
        String oldMode = current.getMode();
        current.setMode(newMode.name());
        repository.save(current);
        auditRepository.save(new StrategyConfigAuditEntity(variantId, current.getVersion(), oldMode, newMode.name(), null));
    }

    @Override
    public long countActive() {
        return repository.countByIsCurrentTrueAndModeIn(ACTIVE_MODES);
    }

    @Override
    public Optional<StrategyConfig> findCurrentChampion() {
        return repository.findByIsCurrentTrueAndMode(StrategyMode.CHAMPION.name()).map(StrategyConfigEntity::toDomain);
    }
}
